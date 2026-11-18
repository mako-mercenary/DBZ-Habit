package com.saiyanhabits.ui.games

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.saiyanhabits.domain.games.HoverBike
import com.saiyanhabits.domain.games.HoverBikeState
import com.saiyanhabits.domain.games.RoadItem
import com.saiyanhabits.domain.games.RoadThing
import com.saiyanhabits.ui.components.drawStar
import kotlin.math.floor
import kotlin.math.sin

/** Road width in metres; the rules' 0..1 across maps onto it. */
private const val ROAD_WIDTH = 10f
private const val PAVEMENT = 4f
private const val CAM_HEIGHT = 2.6f
/** How far behind the bike the camera sits. */
private const val CAM_BACK = 5f
private const val FAR = 170f
/** Things beyond this start to fade into the haze. */
private const val HAZE_FROM = 90f
private const val BLOCK = 22f

private val CarPaint = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFFFDD835), Color(0xFF43A047),
    Color(0xFFF5F5F5), Color(0xFF8E24AA), Color(0xFFFF7043),
)

/** A point on screen and how many pixels a metre is there. */
private data class Projected(val x: Float, val y: Float, val scale: Float)

/** Everything the camera needs for one frame. */
private class Camera(val w: Float, val h: Float, bikeX: Float) {
    val horizon = h * 0.36f
    val focal = h * 0.9f
    /** The camera drifts with the bike, but less, so turns feel like turns. */
    val camX = (bikeX - 0.5f) * ROAD_WIDTH * 0.55f

    /** [x] metres across (0 = road centre), [y] up, [ahead] metres in front of the bike. */
    fun project(x: Float, y: Float, ahead: Float): Projected? {
        val z = ahead + CAM_BACK
        if (z < 0.6f) return null
        val s = focal / z
        return Projected(w / 2 + (x - camX) * s, horizon + (CAM_HEIGHT - y) * s, s)
    }
}

private fun haze(ahead: Float): Float = (1f - (ahead - HAZE_FROM) / (FAR - HAZE_FROM)).coerceIn(0f, 1f)

/** Something to draw at a distance; drawn far to near. */
private class Drawable(val ahead: Float, val draw: DrawScope.() -> Unit)

/** Draws one frame of the run from behind the bike. [lean] is the smoothed steer, -1 to 1. */
fun DrawScope.drawRun3d(state: HoverBikeState, lean: Float, art: BikeArtSet, clock: Float) {
    val cam = Camera(size.width, size.height, state.bikeX)
    drawSky(cam, art, state.distance)
    drawGround(cam, state.distance)

    val queue = ArrayList<Drawable>()
    addScenery(queue, cam, state.distance, art)
    for (item in state.items) {
        if (item.ahead < -2f || item.ahead > FAR) continue
        queue += Drawable(item.ahead) { drawItem(cam, item, art, clock) }
    }
    queue.sortByDescending { it.ahead }
    queue.forEach { it.draw(this) }

    drawBike(cam, state, lean, art, clock)
    if (state.boosting || state.speed > HoverBike.MAX_SPEED * 0.8f) drawSpeedLines(cam, state, clock)
}

// ---- Sky and ground ----

private fun DrawScope.drawSky(cam: Camera, art: BikeArtSet, distance: Float) {
    val sky = art[BikeArt.SKY]
    if (sky != null) {
        // Fill down to the horizon, keeping the picture's shape; it slides a little as you ride.
        val height = cam.horizon + cam.h * 0.04f
        val width = sky.width * height / sky.height
        var x = -((distance * 0.4f) % width)
        while (x < cam.w) {
            drawImage(sky, IntOffset.Zero, IntSize(sky.width, sky.height), IntOffset(x.toInt(), 0), IntSize(width.toInt() + 1, height.toInt()))
            x += width
        }
        return
    }
    drawRect(Brush.verticalGradient(listOf(Color(0xFF2E7BE6), Color(0xFF8CCBFF)), endY = cam.horizon), Offset.Zero, Size(cam.w, cam.horizon + 2f))
    // Clouds.
    for (i in 0 until 5) {
        val cx = ((i * 260f - distance * 0.15f) % (cam.w + 300f) + cam.w + 300f) % (cam.w + 300f) - 150f
        val cy = cam.horizon * (0.18f + 0.13f * (i % 3))
        for (k in 0 until 3) drawCircle(Color(0xCCFFFFFF), 34f + k * 8f, Offset(cx + k * 38f, cy + (k % 2) * 10f))
    }
    // A far skyline: towers and domes, pale with distance.
    val far = Color(0xFFB3D4F2)
    for (i in 0 until 14) {
        val bx = ((i * 97f - distance * 0.05f) % (cam.w + 120f) + cam.w + 120f) % (cam.w + 120f) - 60f
        val bh = 30f + (i * 37 % 70)
        if (i % 4 == 0) {
            drawCircle(far, 34f, Offset(bx, cam.horizon))
        } else {
            drawRect(far, Offset(bx, cam.horizon - bh), Size(26f, bh))
            if (i % 3 == 0) drawOval(far, Offset(bx - 14f, cam.horizon - bh - 16f), Size(54f, 20f))
        }
    }
}

private fun DrawScope.quad(cam: Camera, x0: Float, x1: Float, a0: Float, a1: Float, color: Color) {
    val p00 = cam.project(x0, 0f, a0) ?: return
    val p10 = cam.project(x1, 0f, a0) ?: return
    val p01 = cam.project(x0, 0f, a1) ?: return
    val p11 = cam.project(x1, 0f, a1) ?: return
    val path = Path().apply {
        moveTo(p00.x, p00.y); lineTo(p10.x, p10.y); lineTo(p11.x, p11.y); lineTo(p01.x, p01.y); close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawGround(cam: Camera, distance: Float) {
    val near = -CAM_BACK + 0.7f
    drawRect(Color(0xFF8BC34A), Offset(0f, cam.horizon), Size(cam.w, cam.h - cam.horizon))
    val half = ROAD_WIDTH / 2
    // Pavements, then the road.
    quad(cam, -half - PAVEMENT, -half, near, FAR, Color(0xFFD7CCC8))
    quad(cam, half, half + PAVEMENT, near, FAR, Color(0xFFD7CCC8))
    quad(cam, -half, half, near, FAR, Color(0xFF5E6470))
    // Kerb stripes and lane dashes, moving with the ride.
    val stripe = 4f
    val phase = distance % (stripe * 2)
    var a = near - phase
    var i = 0
    while (a < FAR) {
        val color = if (i % 2 == 0) Color(0xFFE53935) else Color(0xFFF5F5F5)
        quad(cam, -half - 0.35f, -half, a, a + stripe, color)
        quad(cam, half, half + 0.35f, a, a + stripe, color)
        a += stripe
        i++
    }
    val dash = 5f
    val gap = 7f
    val dashPhase = distance % (dash + gap)
    for (lane in 1..2) {
        val x = -half + ROAD_WIDTH * lane / 3f
        var d = near - dashPhase
        while (d < FAR) {
            quad(cam, x - 0.12f, x + 0.12f, d, d + dash, Color(0xEEFFFFFF))
            d += dash + gap
        }
    }
    // Haze over the far road.
    drawRect(
        Brush.verticalGradient(listOf(Color(0x99B3D4F2), Color.Transparent), startY = cam.horizon, endY = cam.horizon + cam.h * 0.08f),
        Offset(0f, cam.horizon),
        Size(cam.w, cam.h * 0.08f),
    )
}

// ---- Scenery ----

/** A whole number from a position, the same every time, for choosing scenery. */
private fun hash(a: Int, b: Int): Int {
    var x = a * 73856093 xor b * 19349663
    x = x xor (x ushr 13)
    x *= 0x5bd1e995
    return x xor (x ushr 15)
}

private fun addScenery(queue: MutableList<Drawable>, cam: Camera, distance: Float, art: BikeArtSet) {
    val half = ROAD_WIDTH / 2
    val firstBlock = floor((distance - CAM_BACK) / BLOCK).toInt()
    for (b in firstBlock..firstBlock + (FAR / BLOCK).toInt() + 1) {
        val worldZ = b * BLOCK
        val ahead = worldZ - distance
        for (side in listOf(-1, 1)) {
            val h = hash(b, side)
            // A building per block on each side.
            val kind = Math.floorMod(h, 7)
            val x = side * (half + PAVEMENT + 7f + Math.floorMod(h ushr 3, 3))
            queue += Drawable(ahead + 6f) { drawBuilding(cam, x, ahead + 6f, kind, art, side) }
            // A palm tree between buildings, on the pavement's edge.
            if (Math.floorMod(h ushr 5, 3) != 0) {
                val px = side * (half + PAVEMENT - 0.8f)
                queue += Drawable(ahead) { drawPalm(cam, px, ahead, art) }
            }
            // Now and then someone on the pavement.
            if (Math.floorMod(h ushr 9, 4) == 0) {
                val px = side * (half + 1.5f + Math.floorMod(h ushr 11, 2))
                queue += Drawable(ahead + 3f) { drawPedestrian(cam, px, ahead + 3f, Math.floorMod(h ushr 13, 2), art) }
            }
        }
    }
    // A West City sign over the road every 500 m.
    val firstSign = floor((distance - CAM_BACK) / 500f).toInt() + 1
    for (s in firstSign..firstSign + 1) {
        val ahead = s * 500f - distance
        if (ahead < FAR) queue += Drawable(ahead) { drawSign(cam, ahead, art) }
    }
}

/** Draws [image] standing on the ground at [x], [ahead], [widthM] metres wide, keeping its shape. */
private fun DrawScope.billboard(cam: Camera, image: ImageBitmap, x: Float, ahead: Float, widthM: Float, lift: Float = 0f): Boolean {
    val base = cam.project(x, lift, ahead) ?: return false
    val w = widthM * base.scale
    val h = w * image.height / image.width
    drawImage(
        image,
        IntOffset.Zero,
        IntSize(image.width, image.height),
        IntOffset((base.x - w / 2).toInt(), (base.y - h).toInt()),
        IntSize(w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
        alpha = haze(ahead),
    )
    return true
}

private fun DrawScope.drawBuilding(cam: Camera, x: Float, ahead: Float, kind: Int, art: BikeArtSet, side: Int) {
    val image = when (kind) {
        0, 1 -> art[BikeArt.DOME]
        2, 3 -> art[BikeArt.BUILDING_1]
        4, 5 -> art[BikeArt.BUILDING_2]
        else -> art[BikeArt.BUILDING_3]
    } ?: art[BikeArt.DOME]
    if (image != null && billboard(cam, image, x, ahead, if (kind <= 1) 16f else 12f)) return
    val base = cam.project(x, 0f, ahead) ?: return
    val s = base.scale
    val alpha = haze(ahead)
    if (kind <= 1) {
        // A Capsule Corp-style dome.
        val w = 15f * s
        val hgt = 10f * s
        drawOval(Color(0xFFFAF7EE).copy(alpha = alpha), Offset(base.x - w / 2, base.y - hgt * 1.6f), Size(w, hgt * 2f))
        drawRect(Color(0xFF8CCBFF).copy(alpha = alpha), Offset(base.x - w / 2, base.y - hgt * 0.42f), Size(w, hgt * 0.16f))
        drawRect(Color(0xFF8BC34A), Offset(base.x - w / 2 - 2f, base.y), Size(w + 4f, hgt * 0.7f))
        if (s > 6f) {
            drawCircle(Color(0xFF263238).copy(alpha = alpha), hgt * 0.16f, Offset(base.x, base.y - hgt * 1.05f), style = Stroke(hgt * 0.04f))
        }
    } else {
        // A rounded tower block with window bands.
        val w = 10f * s
        val hgt = (12f + kind * 4f) * s
        val body = if (kind % 2 == 0) Color(0xFFF2F2F2) else Color(0xFFE3EEF7)
        drawRoundRect(body.copy(alpha = alpha), Offset(base.x - w / 2, base.y - hgt), Size(w, hgt), CornerRadius(w * 0.25f))
        var y = base.y - hgt + hgt * 0.12f
        while (y < base.y - hgt * 0.1f) {
            drawRect(Color(0xFF4A90D9).copy(alpha = alpha), Offset(base.x - w * 0.4f, y), Size(w * 0.8f, hgt * 0.05f))
            y += hgt * 0.12f
        }
        if (kind == 6) {
            // A saucer top, West City style.
            drawOval(Color(0xFFF2F2F2).copy(alpha = alpha), Offset(base.x - w * 0.8f, base.y - hgt - w * 0.25f), Size(w * 1.6f, w * 0.5f))
            drawRect(Color(0xFFE57373).copy(alpha = alpha), Offset(base.x - w * 0.8f, base.y - hgt - w * 0.03f), Size(w * 1.6f, w * 0.06f))
        }
    }
}

private fun DrawScope.drawPalm(cam: Camera, x: Float, ahead: Float, art: BikeArtSet) {
    art[BikeArt.PALM]?.let { if (billboard(cam, it, x, ahead, 4.5f)) return }
    val base = cam.project(x, 0f, ahead) ?: return
    val top = cam.project(x, 7f, ahead) ?: return
    val alpha = haze(ahead)
    drawLine(Color(0xFF8D6E63).copy(alpha = alpha), Offset(base.x, base.y), Offset(top.x + top.scale * 0.3f, top.y), top.scale * 0.35f)
    val leaf = Color(0xFF2E7D32).copy(alpha = alpha)
    for (k in 0 until 6) {
        val angle = k * 60f
        val dx = kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
        val dy = sin(Math.toRadians(angle.toDouble())).toFloat()
        drawLine(leaf, Offset(top.x, top.y), Offset(top.x + dx * top.scale * 2.4f, top.y + dy * top.scale * 1.1f + top.scale * 0.6f), top.scale * 0.45f)
    }
}

private fun DrawScope.drawPedestrian(cam: Camera, x: Float, ahead: Float, which: Int, art: BikeArtSet) {
    (if (which == 0) art[BikeArt.PEDESTRIAN_1] else art[BikeArt.PEDESTRIAN_2])?.let { if (billboard(cam, it, x, ahead, 0.7f)) return }
    val base = cam.project(x, 0f, ahead) ?: return
    val s = base.scale
    val alpha = haze(ahead)
    val shirt = if (which == 0) Color(0xFF7986CB) else Color(0xFFBA68C8)
    drawRect(Color(0xFF455A64).copy(alpha = alpha), Offset(base.x - 0.2f * s, base.y - 0.9f * s), Size(0.4f * s, 0.9f * s))
    drawRect(shirt.copy(alpha = alpha), Offset(base.x - 0.25f * s, base.y - 1.5f * s), Size(0.5f * s, 0.65f * s))
    drawCircle(Color(0xFFFFCC80).copy(alpha = alpha), 0.17f * s, Offset(base.x, base.y - 1.68f * s))
}

private val SignPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFF1E3A8A.toInt()
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
    textAlign = Paint.Align.CENTER
}

private fun DrawScope.drawSign(cam: Camera, ahead: Float, art: BikeArtSet) {
    val half = ROAD_WIDTH / 2
    art[BikeArt.SIGN]?.let { if (billboard(cam, it, 0f, ahead, ROAD_WIDTH + 2f)) return }
    val leftFoot = cam.project(-half - 0.5f, 0f, ahead) ?: return
    val rightFoot = cam.project(half + 0.5f, 0f, ahead) ?: return
    val boardTop = cam.project(-3.2f, 7.4f, ahead) ?: return
    val boardBottom = cam.project(3.2f, 5.6f, ahead) ?: return
    val alpha = haze(ahead)
    val post = Color(0xFF9E9E9E).copy(alpha = alpha)
    drawLine(post, Offset(leftFoot.x, leftFoot.y), Offset(leftFoot.x, boardTop.y + 6f), leftFoot.scale * 0.25f)
    drawLine(post, Offset(rightFoot.x, rightFoot.y), Offset(rightFoot.x, boardTop.y + 6f), rightFoot.scale * 0.25f)
    drawLine(post, Offset(leftFoot.x, (boardTop.y + boardBottom.y) / 2), Offset(rightFoot.x, (boardTop.y + boardBottom.y) / 2), leftFoot.scale * 0.2f)
    drawRoundRect(Color(0xFFF5F5F5).copy(alpha = alpha), Offset(boardTop.x, boardTop.y), Size(boardBottom.x - boardTop.x, boardBottom.y - boardTop.y), CornerRadius(8f))
    drawRoundRect(Color(0xFF1E3A8A).copy(alpha = alpha), Offset(boardTop.x, boardTop.y), Size(boardBottom.x - boardTop.x, boardBottom.y - boardTop.y), CornerRadius(8f), style = Stroke(3f))
    SignPaint.textSize = (boardBottom.y - boardTop.y) * 0.62f
    SignPaint.alpha = (alpha * 255).toInt()
    drawContext.canvas.nativeCanvas.drawText("WEST CITY", (boardTop.x + boardBottom.x) / 2, boardBottom.y - (boardBottom.y - boardTop.y) * 0.24f, SignPaint)
}

// ---- Traffic and pickups ----

private fun DrawScope.shadow(cam: Camera, x: Float, ahead: Float, widthM: Float) {
    val p = cam.project(x, 0f, ahead) ?: return
    val w = widthM * p.scale
    drawOval(Color(0x44000000), Offset(p.x - w / 2, p.y - w * 0.12f), Size(w, w * 0.24f))
}

private fun DrawScope.drawItem(cam: Camera, item: RoadItem, art: BikeArtSet, clock: Float) {
    val x = (item.x - 0.5f) * ROAD_WIDTH
    val ahead = item.ahead
    val alpha = haze(ahead)
    when (item.thing) {
        RoadThing.DRAGON_BALL -> {
            shadow(cam, x, ahead, 0.6f)
            val bob = 0.9f + sin(clock * 4f + item.id) * 0.12f
            val p = cam.project(x, bob, ahead) ?: return
            val r = 0.32f * p.scale
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59D), Color(0xFFFFB300), Color(0xFFE65100)), Offset(p.x - r * 0.3f, p.y - r * 0.3f), r * 1.4f), r, Offset(p.x, p.y), alpha = alpha)
            val stars = 1 + Math.floorMod(item.id, 4)
            for (k in 0 until stars) {
                val sx = p.x + (k - (stars - 1) / 2f) * r * 0.42f
                drawStar(Offset(sx, p.y + (k % 2) * r * 0.2f), r * 0.2f, Color(0xFFD50000).copy(alpha = alpha))
            }
            drawCircle(Color.White.copy(alpha = 0.6f * alpha), r * 0.18f, Offset(p.x - r * 0.4f, p.y - r * 0.45f))
        }
        RoadThing.BOOST -> {
            shadow(cam, x, ahead, 0.8f)
            val p = cam.project(x, 0.9f, ahead) ?: return
            val r = 0.38f * p.scale * (1f + 0.08f * sin(clock * 10f))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF4FC3F7), Color(0x0029B6F6)), Offset(p.x, p.y), r * 1.8f), r * 1.8f, Offset(p.x, p.y), alpha = alpha)
            drawCircle(Color(0xFF0288D1).copy(alpha = alpha), r * 0.6f, Offset(p.x, p.y))
        }
        RoadThing.BARRIER -> {
            art[BikeArt.BARRIER]?.let { if (billboard(cam, it, x, ahead, 2f)) return }
            val p = cam.project(x, 0f, ahead) ?: return
            val w = 2f * p.scale
            val h = 1.1f * p.scale
            drawRect(Color(0xFFBDBDBD).copy(alpha = alpha), Offset(p.x - w / 2, p.y - h), Size(w, h))
            val stripeH = h * 0.42f
            val y = p.y - h + h * 0.08f
            val stripes = 6
            for (k in 0 until stripes) {
                val color = if (k % 2 == 0) Color(0xFFFFD600) else Color(0xFF212121)
                val path = Path().apply {
                    val x0 = p.x - w / 2 + k * w / stripes
                    moveTo(x0, y + stripeH); lineTo(x0 + w / stripes, y + stripeH); lineTo(x0 + w / stripes * 1.6f, y); lineTo(x0 + w / stripes * 0.6f, y); close()
                }
                drawPath(path, color.copy(alpha = alpha))
            }
        }
        RoadThing.ROBOT -> {
            shadow(cam, x, ahead, 1.1f)
            val p = cam.project(x, 0f, ahead) ?: return
            val s = p.scale
            drawRoundRect(Color(0xFF607D8B).copy(alpha = alpha), Offset(p.x - 0.45f * s, p.y - 1.5f * s), Size(0.9f * s, 1.1f * s), CornerRadius(0.2f * s))
            drawCircle(Color(0xFF78909C).copy(alpha = alpha), 0.35f * s, Offset(p.x, p.y - 1.75f * s))
            drawCircle(Color(0xFFFF1744).copy(alpha = alpha), 0.12f * s, Offset(p.x, p.y - 1.75f * s))
            drawRect(Color(0xFF455A64).copy(alpha = alpha), Offset(p.x - 0.35f * s, p.y - 0.4f * s), Size(0.7f * s, 0.4f * s))
        }
        RoadThing.CAR, RoadThing.TRUCK -> {
            val truck = item.thing == RoadThing.TRUCK
            val image = if (truck) art[BikeArt.TRUCK] else art[BikeArt.CAR]
            shadow(cam, x, ahead, if (truck) 2.6f else 2.1f)
            if (image != null && billboard(cam, image, x, ahead, if (truck) 2.4f else 1.9f)) return
            val p = cam.project(x, 0f, ahead) ?: return
            val s = p.scale
            val w = (if (truck) 2.4f else 1.9f) * s
            val h = (if (truck) 2.8f else 1.45f) * s
            val color = if (truck) Color(0xFFE0E0E0) else CarPaint[item.id % CarPaint.size]
            drawRoundRect(color.copy(alpha = alpha), Offset(p.x - w / 2, p.y - h - 0.25f * s), Size(w, h), CornerRadius(0.25f * s))
            // Rear window, lights, wheels.
            drawRoundRect(Color(0xFF263238).copy(alpha = alpha), Offset(p.x - w * 0.36f, p.y - h - 0.25f * s + h * 0.1f), Size(w * 0.72f, h * (if (truck) 0.3f else 0.38f)), CornerRadius(0.12f * s))
            drawRect(Color(0xFFD50000).copy(alpha = alpha), Offset(p.x - w * 0.47f, p.y - 0.25f * s - h * 0.38f), Size(w * 0.14f, h * 0.1f))
            drawRect(Color(0xFFD50000).copy(alpha = alpha), Offset(p.x + w * 0.33f, p.y - 0.25f * s - h * 0.38f), Size(w * 0.14f, h * 0.1f))
            drawRect(Color(0xFF212121).copy(alpha = alpha), Offset(p.x - w * 0.45f, p.y - 0.35f * s), Size(w * 0.2f, 0.35f * s))
            drawRect(Color(0xFF212121).copy(alpha = alpha), Offset(p.x + w * 0.25f, p.y - 0.35f * s), Size(w * 0.2f, 0.35f * s))
        }
    }
}

// ---- The bike ----

private fun DrawScope.drawBike(cam: Camera, state: HoverBikeState, lean: Float, art: BikeArtSet, clock: Float) {
    val x = (state.bikeX - 0.5f) * ROAD_WIDTH
    val ground = cam.project(x, 0f, 0f) ?: return
    val hover = cam.project(x, 0.35f + sin(clock * 6f) * 0.05f, 0f) ?: return
    // Flash while recovering from a hit.
    if (state.invulnerable > 0f && ((state.invulnerable * 10).toInt() % 2 == 0)) return
    val s = ground.scale
    drawOval(Color(0x55000000), Offset(ground.x - 0.8f * s, ground.y - 0.18f * s), Size(1.6f * s, 0.36f * s))
    val image = when {
        lean < -0.35f -> art[BikeArt.BIKE_LEFT] ?: art[BikeArt.BIKE]
        lean > 0.35f -> art[BikeArt.BIKE_RIGHT] ?: art[BikeArt.BIKE]
        else -> art[BikeArt.BIKE]
    }
    withTransform({ rotate(lean * 9f, Offset(hover.x, hover.y)) }) {
        // Thruster glow, bigger on boost.
        val glow = (if (state.boosting) 1.1f else 0.55f) * s * (1f + 0.1f * sin(clock * 30f))
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFF4FC3F7), Color.Transparent), Offset(hover.x, hover.y - 0.2f * s), glow), glow, Offset(hover.x, hover.y - 0.2f * s))
        if (state.boosting) {
            for (k in 1..4) drawCircle(Color(0xFF4FC3F7).copy(alpha = 0.35f / k), glow * (0.7f + k * 0.15f), Offset(hover.x, hover.y + k * 0.3f * s))
        }
        if (image != null) {
            val w = 1.7f * s
            val h = w * image.height / image.width
            drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), IntOffset((hover.x - w / 2).toInt(), (hover.y - h).toInt()), IntSize(w.toInt(), h.toInt()))
        } else {
            drawPlaceholderRider(hover, s)
        }
    }
}

/** Bulma and the bike from behind, in shapes, until the real picture is added. */
private fun DrawScope.drawPlaceholderRider(base: Projected, s: Float) {
    val x = base.x
    val y = base.y
    // Side pods and body.
    drawRoundRect(Color(0xFFE91E63), Offset(x - 0.78f * s, y - 0.75f * s), Size(0.32f * s, 0.6f * s), CornerRadius(0.12f * s))
    drawRoundRect(Color(0xFFE91E63), Offset(x + 0.46f * s, y - 0.75f * s), Size(0.32f * s, 0.6f * s), CornerRadius(0.12f * s))
    drawRoundRect(Color(0xFFF5F5F5), Offset(x - 0.5f * s, y - 0.9f * s), Size(1f * s, 0.85f * s), CornerRadius(0.3f * s))
    drawCircle(Color(0xFF263238), 0.16f * s, Offset(x, y - 0.55f * s), style = Stroke(0.05f * s))
    drawCircle(Color(0xFF0D47A1), 0.17f * s, Offset(x, y - 0.12f * s))
    // Rider: shorts, top, arms, hair.
    drawRoundRect(Color(0xFFF5F5F5), Offset(x - 0.32f * s, y - 1.2f * s), Size(0.64f * s, 0.36f * s), CornerRadius(0.15f * s))
    drawRoundRect(Color(0xFFFFEB3B), Offset(x - 0.26f * s, y - 1.6f * s), Size(0.52f * s, 0.42f * s), CornerRadius(0.1f * s))
    drawLine(Color(0xFFFFCC80), Offset(x - 0.24f * s, y - 1.5f * s), Offset(x - 0.48f * s, y - 1.15f * s), 0.11f * s)
    drawLine(Color(0xFFFFCC80), Offset(x + 0.24f * s, y - 1.5f * s), Offset(x + 0.48f * s, y - 1.15f * s), 0.11f * s)
    drawCircle(Color(0xFF26A69A), 0.25f * s, Offset(x, y - 1.82f * s))
    drawOval(Color(0xFF26A69A), Offset(x - 0.1f * s, y - 1.75f * s), Size(0.22f * s, 0.5f * s))
    drawCircle(Color(0xFFE53935), 0.07f * s, Offset(x, y - 2.0f * s))
}

private fun DrawScope.drawSpeedLines(cam: Camera, state: HoverBikeState, clock: Float) {
    val strength = if (state.boosting) 0.5f else 0.2f
    for (k in 0 until 14) {
        val side = if (k % 2 == 0) -1f else 1f
        val t = ((clock * 3f + k * 0.37f) % 1f)
        val y = cam.horizon + (cam.h - cam.horizon) * (0.2f + (k * 0.13f % 0.8f))
        val x0 = cam.w / 2 + side * cam.w * (0.42f + t * 0.1f)
        drawLine(Color.White.copy(alpha = strength * (1 - t)), Offset(x0, y), Offset(x0 + side * cam.w * 0.15f, y + (y - cam.horizon) * 0.15f), 3f)
    }
}
