package com.saiyanhabits.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanhabits.ui.theme.DisplayFont
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlin.math.min

/** The fill and frame of a [DbzPanel]. */
@Immutable
data class PanelStyle(
    val fillTop: Color,
    val fillBottom: Color,
    /** The frame's bright band, light to dark from top to bottom. */
    val frame: List<Color>,
    /** Drawn at reduced strength, for things that are locked or finished. */
    val dim: Boolean = false,
) {
    companion object {
        private val GoldFrame = listOf(SaiyanColors.FrameGoldLight, SaiyanColors.FrameGold, SaiyanColors.FrameGoldDark)

        /** The standard card: royal blue in a gold frame. */
        val Blue = PanelStyle(SaiyanColors.PanelBlueTop, SaiyanColors.PanelBlueBottom, GoldFrame)

        /** A chosen option: the same blue, framed in hot orange. */
        val Selected = PanelStyle(
            Color(0xFF2A5FC4),
            SaiyanColors.PanelBlueBottom,
            listOf(Color(0xFFFFD27A), SaiyanColors.Orange, SaiyanColors.OrangeDeep),
        )

        /** Banners and calls to action: orange in a gold frame. */
        val Orange = PanelStyle(SaiyanColors.BannerOrangeTop, SaiyanColors.BannerOrangeBottom, GoldFrame)

        /** The scouter screen. */
        val Green = PanelStyle(SaiyanColors.ScouterGreenTop, SaiyanColors.ScouterGreenBottom, GoldFrame)

        /** Locked, done or set aside. */
        val Dim = PanelStyle(Color(0xFF1A2A55), Color(0xFF0C1533), listOf(Color(0xFFB9A877), Color(0xFF8C7A44), Color(0xFF5E4E22)), dim = true)
    }
}

/**
 * The card every screen is built from: a gradient fill in a bevelled gold
 * frame with a dark outer line. [watermark] is drawn faintly behind the
 * content, clipped to the card.
 */
@Composable
fun DbzPanel(
    modifier: Modifier = Modifier,
    style: PanelStyle = PanelStyle.Blue,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    corner: Dp = 16.dp,
    watermark: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val clickable = if (onClick != null) {
        Modifier
            .clip(shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .drawBehind { drawPanel(style, shape) }
            .then(clickable),
    ) {
        if (watermark != null) {
            Box(Modifier.matchParentSize().clip(shape)) { watermark() }
        }
        CompositionLocalProvider(LocalContentColor provides SaiyanColors.TextHigh) {
            Box(if (style.dim) Modifier.alpha(0.75f) else Modifier) { content() }
        }
    }
}

/** Draws a panel's fill, sheen and frame inside the current bounds. */
fun DrawScope.drawPanel(style: PanelStyle, shape: Shape) {
    val outerLine = 1.4.dp.toPx()
    val goldBand = 2.8.dp.toPx()
    val innerLine = 1.dp.toPx()

    val outline = shape.createOutline(size, layoutDirection, this)
    val path = outline.toPath()
    val fade = if (style.dim) 0.7f else 1f

    // Fill, then a soft light falling from the top-left.
    drawPath(path, Brush.verticalGradient(listOf(style.fillTop, style.fillBottom)), alpha = fade)
    drawPath(
        path,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            start = Offset.Zero,
            end = Offset(size.width * 0.6f, size.height),
        ),
    )

    // Frame: dark outer line, a gold band, then a thin light line just inside.
    insetPath(shape, outerLine / 2f) { drawPath(it, SaiyanColors.FrameDark, style = Stroke(outerLine)) }
    insetPath(shape, outerLine + goldBand / 2f) {
        drawPath(it, Brush.verticalGradient(style.frame), alpha = fade, style = Stroke(goldBand))
    }
    insetPath(shape, outerLine + goldBand + innerLine / 2f) {
        drawPath(it, Color.Black.copy(alpha = 0.35f), style = Stroke(innerLine))
    }
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}

/** Runs [block] with [shape]'s outline shrunk by [inset] on every side. */
private inline fun DrawScope.insetPath(shape: Shape, inset: Float, block: DrawScope.(Path) -> Unit) {
    val inner = Size((size.width - inset * 2).coerceAtLeast(0f), (size.height - inset * 2).coerceAtLeast(0f))
    val path = shape.createOutline(inner, layoutDirection, this).toPath()
    translate(inset, inset) { block(path) }
}

/** A parallelogram: the slanted label tabs and ribbons. */
class SlantShape(private val slant: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = min(with(density) { slant.toPx() }, size.width / 3f)
        return Outline.Generic(
            Path().apply {
                moveTo(s, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - s, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/**
 * The slanted gold tab that names a card or section ("STREAK",
 * "TODAY'S TRAINING"). [red] makes it a red ribbon with light text.
 */
@Composable
fun TabLabel(text: String, modifier: Modifier = Modifier, red: Boolean = false, fontSize: Int = 17) {
    val shape = SlantShape(9.dp)
    Box(
        modifier = modifier.drawBehind {
            val outline = shape.createOutline(size, layoutDirection, this).toPath()
            val fill = if (red) {
                listOf(SaiyanColors.RibbonRedTop, SaiyanColors.RibbonRedBottom)
            } else {
                listOf(SaiyanColors.TabGoldTop, SaiyanColors.TabGoldBottom)
            }
            drawPath(outline, Brush.verticalGradient(fill))
            drawPath(outline, SaiyanColors.FrameDark, style = Stroke(1.4.dp.toPx(), join = StrokeJoin.Round))
        },
    ) {
        Text(
            text = text.uppercase(),
            fontFamily = DisplayFont,
            fontSize = fontSize.sp,
            letterSpacing = 0.8.sp,
            color = if (red) Color.White else SaiyanColors.TabInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 2.dp, bottom = 1.dp),
        )
    }
}

/**
 * Display lettering with a dark outline, like the mockup's big numbers and
 * headings. [fill] can be a gradient. The outline is drawn as a stroke under
 * the fill, so the letters keep their full width.
 */
@Composable
fun OutlinedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    fill: Brush = Brush.verticalGradient(listOf(Color.White, Color.White)),
    outline: Color = SaiyanColors.FrameDark,
    outlineWidth: Dp = 4.dp,
    maxLines: Int = 1,
) {
    Box(modifier) {
        Text(
            text = text,
            style = style.copy(
                color = outline,
                drawStyle = Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { outlineWidth.toPx() }, join = StrokeJoin.Round),
            ),
            maxLines = maxLines,
            softWrap = maxLines > 1,
            overflow = TextOverflow.Clip,
        )
        Text(
            text = text,
            style = style.copy(brush = fill, shadow = null),
            maxLines = maxLines,
            softWrap = maxLines > 1,
            overflow = TextOverflow.Clip,
        )
    }
}

/** Gold lettering, light at the top: the big numbers and headings. */
val GoldLettering: Brush = Brush.verticalGradient(listOf(Color(0xFFFFF7B0), Color(0xFFFFD21F), Color(0xFFF29A00)))

/** A large faint character in a card's corner: a mentor's mark, for instance. */
@Composable
fun BoxScope.KanjiWatermark(character: String, modifier: Modifier = Modifier) {
    Text(
        text = character,
        fontSize = 64.sp,
        color = Color.White.copy(alpha = 0.09f),
        modifier = modifier
            .align(Alignment.CenterEnd)
            .padding(end = 18.dp),
    )
}

/** A faint orb with stars in a card's corner. */
@Composable
fun BoxScope.OrbWatermark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .align(Alignment.CenterEnd)
            .padding(end = 10.dp)
            .size(78.dp)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.07f))
                val r = size.minDimension * 0.09f
                for ((dx, dy) in listOf(-0.15f to -0.08f, 0.18f to 0.06f)) {
                    drawStar(center + Offset(size.width * dx, size.height * dy), r, Color.White.copy(alpha = 0.1f))
                }
            },
    )
}

/** One of the art images, faint, in a card's corner. */
@Composable
fun BoxScope.ArtWatermark(key: com.saiyanhabits.art.ArtKey, modifier: Modifier = Modifier) {
    ArtImage(
        key = key,
        contentDescription = null,
        alpha = 0.16f,
        modifier = modifier
            .align(Alignment.CenterEnd)
            .padding(end = 10.dp)
            .size(76.dp),
    )
}

/** A five-pointed star. */
fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.45f
        val a = -Math.PI / 2 + i * Math.PI / 5
        val x = center.x + r * kotlin.math.cos(a).toFloat()
        val y = center.y + r * kotlin.math.sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/** A healing bean: a green kidney shape, or an empty outline when [held] is false. */
@Composable
fun Bean(held: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(width = 26.dp, height = 32.dp)
            .drawBehind {
                val bean = Path().apply {
                    val w = size.width
                    val h = size.height
                    moveTo(w * 0.55f, 0f)
                    cubicTo(w * 1.05f, h * 0.05f, w * 1.05f, h * 0.95f, w * 0.5f, h)
                    cubicTo(w * -0.05f, h * 1.0f, w * -0.05f, h * 0.55f, w * 0.25f, h * 0.45f)
                    cubicTo(w * 0.45f, h * 0.38f, w * 0.05f, h * 0.02f, w * 0.55f, 0f)
                    close()
                }
                if (held) {
                    drawPath(bean, Brush.linearGradient(listOf(Color(0xFFB8F27C), Color(0xFF4CB82E), Color(0xFF237A16)), start = Offset.Zero, end = Offset(size.width, size.height)))
                    drawPath(bean, Color(0xFF103D0A), style = Stroke(1.4.dp.toPx()))
                    drawCircle(Color.White.copy(alpha = 0.6f), radius = size.width * 0.1f, center = Offset(size.width * 0.62f, size.height * 0.22f))
                } else {
                    drawPath(bean, Color.White.copy(alpha = 0.55f), style = Stroke(1.8.dp.toPx()))
                }
            },
    )
}

/** An orange framed button with display lettering: the screens' main actions. */
@Composable
fun DbzButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DbzPanel(
        modifier = modifier,
        style = if (enabled) PanelStyle.Orange else PanelStyle.Dim,
        onClick = onClick,
        enabled = enabled,
        corner = 14.dp,
    ) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            OutlinedText(text.uppercase(), MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp), outlineWidth = 3.5.dp)
        }
    }
}

/** A slanted red ribbon you can tap, like the mockup's "Read". */
@Composable
fun RibbonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(SlantShape(9.dp))
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        TabLabel(text, red = true, fontSize = 20)
    }
}

/** Each mentor's mark, shown faintly on their cards: a character on their gi, or Bulma's emblem. */
@Composable
fun BoxScope.MentorWatermark(mentor: com.saiyanhabits.domain.Mentor) {
    when (mentor) {
        com.saiyanhabits.domain.Mentor.ROSHI -> KanjiWatermark("亀")
        com.saiyanhabits.domain.Mentor.GOKU -> KanjiWatermark("悟")
        com.saiyanhabits.domain.Mentor.PICCOLO -> KanjiWatermark("魔")
        com.saiyanhabits.domain.Mentor.VEGETA -> KanjiWatermark("王")
        com.saiyanhabits.domain.Mentor.BULMA -> ArtWatermark(com.saiyanhabits.art.ArtKey.EMBLEM_CAPSULE)
    }
}

/** A gold ring with a dark edge around a round portrait. */
@Composable
fun GoldRing(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawWithContent {
                drawContent()
                val band = 3.dp.toPx()
                drawCircle(SaiyanColors.FrameDark, radius = size.minDimension / 2f - 0.7.dp.toPx(), style = Stroke(1.4.dp.toPx()))
                drawCircle(
                    Brush.verticalGradient(listOf(SaiyanColors.FrameGoldLight, SaiyanColors.FrameGold, SaiyanColors.FrameGoldDark)),
                    radius = size.minDimension / 2f - 1.4.dp.toPx() - band / 2f,
                    style = Stroke(band),
                )
            },
    ) { content() }
}

/** The main action button: orange with a gold frame. Same shape of call as Material's Button. */
@Composable
fun DbzPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    DbzPanel(
        modifier = modifier,
        style = if (enabled) PanelStyle.Orange else PanelStyle.Dim,
        onClick = onClick,
        enabled = enabled,
        corner = 14.dp,
    ) { ButtonContent(content) }
}

/** A secondary button: blue with a gold frame. Same shape of call as Material's OutlinedButton. */
@Composable
fun DbzSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    DbzPanel(
        modifier = modifier,
        style = if (enabled) PanelStyle.Blue else PanelStyle.Dim,
        onClick = onClick,
        enabled = enabled,
        corner = 14.dp,
    ) { ButtonContent(content) }
}

@Composable
private fun ButtonContent(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.labelLarge.copy(color = Color.White)) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            content = content,
        )
    }
}
