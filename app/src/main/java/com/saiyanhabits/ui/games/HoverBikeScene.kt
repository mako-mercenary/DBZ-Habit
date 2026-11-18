package com.saiyanhabits.ui.games

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.google.android.filament.Renderer
import com.google.android.filament.View
import com.saiyanhabits.domain.games.HoverBike
import com.saiyanhabits.domain.games.HoverBikeState
import com.saiyanhabits.domain.games.RoadThing
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.node.CameraNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironment
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberView
import kotlin.math.floor
import kotlin.math.sin

/** Road width in metres: the rules' 0..1 across maps onto it. */
private const val ROAD_WIDTH = 10f
/** Metres of road one road model covers. */
private const val TILE = 20f
/** How far ahead scenery is shown. */
private const val SCENERY_AHEAD = 190f
/** One building per this many metres on each side. */
private const val BLOCK = 24f
private const val CAM_HEIGHT = 3.1f
private const val CAM_BACK = 7.5f

/**
 * Where things land on screen, as shares of its width and height from the top
 * left: the horizon, and the ground under the bike. The 2D skyline and rider
 * are drawn to match.
 */
data class ScreenPoints(val horizonY: Float = 0.4f, val bikeX: Float = 0.5f, val bikeY: Float = 0.85f, val bikeSideways: Float = 0f)

/** A model loaded once, with [count] copies that can each be placed anywhere. */
internal class Pool(val file: String, val count: Int) {
    var instances: List<ModelInstance> = emptyList()
    val nodes = ArrayList<ModelNode>()
    private var used = 0

    fun reset() { used = 0 }

    /** The next free copy, or null if all are in use this frame. */
    fun take(): ModelNode? = nodes.getOrNull(used)?.also { used++ }

    /** Hides the copies not used this frame. */
    fun hideRest() {
        for (i in used until nodes.size) nodes[i].isVisible = false
    }
}

/**
 * The hover-bike run as a real 3D scene: Kenney's CC0 models and a few made
 * by tools/prepare_models.py, lit by the sun, seen from a camera behind the
 * bike. Nothing in it is recomposed per frame: [update] moves pooled copies of
 * each model from the game loop.
 */
class HoverBikeWorld {
    private val road = Pool("road", 12)
    private val pavement = Pool("pavement", 24)
    private val ground = Pool("ground", 1)
    private val bike = Pool("bike", 1)
    private val rider = Pool("rider", 1)
    private val cars = listOf("car_sedan", "car_taxi", "car_police", "car_suv", "car_sports").map { Pool(it, 4) }
    private val trucks = listOf("van", "truck_delivery", "truck").map { Pool(it, 3) }
    private val robot = Pool("robot", 4)
    private val barrier = Pool("barrier", 6)
    private val cone = Pool("cone", 8)
    private val ball = Pool("dragon_ball", 18)
    private val boost = Pool("boost", 3)
    private val buildings = listOf("building_a", "building_b", "building_c", "building_d", "building_e", "building_f", "tower_a", "tower_b", "tower_c", "capsule_dome").map { Pool(it, 5) }
    private val palms = listOf("palm_tall", "palm_detailed", "palm_bend").map { Pool(it, 9) }
    private val lights = Pool("street_light", 18)

    private val all = listOf(road, pavement, ground, bike, rider, robot, barrier, cone, ball, boost, lights) + cars + trucks + buildings + palms

    /** False when a 2D rider is drawn over the scene instead of the 3D bike and rider. */
    var showModelRider = true

    /** True once every model has loaded. */
    var ready = false
        private set

    /** Loads each model once with all its copies. Call on the main thread. */
    fun load(loader: ModelLoader) {
        for (pool in all) {
            pool.instances = try {
                loader.createInstancedModel("models/${pool.file}.glb", pool.count)
            } catch (e: Exception) {
                Log.w("HoverBike3D", "Could not load ${pool.file}: ${e.message}")
                emptyList()
            }
        }
    }

    /** Every copy, for the scene to show. */
    internal val instances: List<Pair<Pool, ModelInstance>>
        get() = all.flatMap { pool -> pool.instances.map { pool to it } }

    /** Called by the scene for each copy once its node exists. */
    internal fun attach(p: Pool, node: ModelNode) {
        if (node !in p.nodes) p.nodes += node
        node.isVisible = false
        ready = all.all { it.nodes.size == it.instances.size }
    }

    private fun place(pool: Pool, x: Float, y: Float, z: Float, yaw: Float = 0f, roll: Float = 0f, pitch: Float = 0f): ModelNode? =
        pool.take()?.apply {
            position = Position(x, y, z)
            rotation = Rotation(pitch, yaw, roll)
            isVisible = true
        }

    /**
     * Moves everything to match [state]: the bike at z = 0, things ahead at
     * negative z. Returns where the horizon and the bike are on screen.
     */
    fun update(state: HoverBikeState, lean: Float, clock: Float, camera: CameraNode): ScreenPoints? {
        if (!ready) return null
        all.forEach { it.reset() }
        val bikeX = (state.bikeX - 0.5f) * ROAD_WIDTH
        val camX = bikeX * 0.75f
        val half = ROAD_WIDTH / 2

        // The ground follows the bike; road and pavements are tiled and slide past.
        place(ground, camX, 0f, 0f)
        val offset = state.distance % TILE
        for (k in 0 until road.count) {
            val z = offset - k * TILE + TILE // starts a tile behind the camera
            place(road, 0f, 0f, z)
            place(pavement, -half - 2.4f, 0f, z)
            place(pavement, half + 2.4f, 0f, z)
        }

        // Buildings, palms and lights, chosen by position so they stay put as you pass.
        val first = floor((state.distance - CAM_BACK) / BLOCK).toInt()
        for (b in first..first + (SCENERY_AHEAD / BLOCK).toInt()) {
            val ahead = b * BLOCK - state.distance
            for (side in intArrayOf(-1, 1)) {
                val h = hash(b, side)
                val kind = Math.floorMod(h, buildings.size)
                val dome = kind == buildings.lastIndex
                val x = side * (half + 4.8f + (if (dome) 11f else 7f) + Math.floorMod(h ushr 4, 3))
                // Fronts face the road.
                place(buildings[kind], x, 0f, -ahead, yaw = if (side < 0) 90f else -90f)
                val palm = palms[Math.floorMod(h ushr 7, palms.size)]
                place(palm, side * (half + 3.4f), 0f, -(ahead + BLOCK / 2), yaw = Math.floorMod(h ushr 9, 360).toFloat())
                place(lights, side * (half + 0.7f), 0f, -(ahead + 4f), yaw = if (side < 0) -90f else 90f)
            }
        }

        // Traffic and pickups.
        for (item in state.items) {
            if (item.ahead < -CAM_BACK || item.ahead > SCENERY_AHEAD) continue
            val x = (item.x - 0.5f) * ROAD_WIDTH
            val z = -item.ahead
            when (item.thing) {
                RoadThing.CAR -> place(cars[item.id % cars.size], x, 0f, z)
                RoadThing.TRUCK -> place(trucks[item.id % trucks.size], x, 0f, z)
                RoadThing.ROBOT -> place(robot, x, 0f, z, yaw = sin(clock * 2f + item.id) * 25f)
                RoadThing.BARRIER -> {
                    place(barrier, x, 0f, z)
                    place(cone, x - 1.6f, 0f, z + 0.6f)
                }
                RoadThing.DRAGON_BALL -> place(ball, x, 0.9f + sin(clock * 4f + item.id) * 0.12f, z, yaw = clock * 90f + item.id * 40f)
                RoadThing.BOOST -> place(boost, x, 0.9f, z, yaw = clock * 180f)
            }
        }

        // The bike and rider: hovering, leaning into turns, flashing after a hit.
        val flashing = state.invulnerable > 0f && ((state.invulnerable * 10).toInt() % 2 == 0)
        if (!flashing && showModelRider) {
            val bob = 0.25f + sin(clock * 6f) * 0.04f
            place(bike, bikeX, bob, 0f, roll = -lean * 14f, yaw = -lean * 6f)
            place(rider, bikeX, bob, 0f, roll = -lean * 18f, yaw = -lean * 6f)
        }
        all.forEach { it.hideRest() }

        // A chase camera, swinging a little with the bike; it shakes on boost.
        val shake = if (state.boosting) sin(clock * 60f) * 0.04f else 0f
        camera.position = Position(camX + shake, CAM_HEIGHT, CAM_BACK)
        camera.lookAt(Position(camX * 0.9f, 1.1f, -14f))
        return screenPoints(camera, bikeX)
    }

    private val viewMatrix = DoubleArray(16)
    private val projection = DoubleArray(16)

    /** Projects the far horizon and the ground under the bike through the camera. */
    private fun screenPoints(camera: CameraNode, bikeX: Float): ScreenPoints? {
        val cam = camera.camera
        cam.getViewMatrix(viewMatrix)
        cam.getProjectionMatrix(projection)
        val horizon = project(camera.position.x.toDouble(), 0.0, -100_000.0) ?: return null
        val bike = project(bikeX.toDouble(), 0.0, 0.0) ?: return null
        return ScreenPoints(horizonY = horizon.second, bikeX = bike.first, bikeY = bike.second, bikeSideways = bikeX)
    }

    /** A world point as shares of the screen from the top left, or null if it is behind the camera. */
    private fun project(x: Double, y: Double, z: Double): Pair<Float, Float>? {
        val v = DoubleArray(4)
        for (i in 0 until 4) v[i] = viewMatrix[i] * x + viewMatrix[4 + i] * y + viewMatrix[8 + i] * z + viewMatrix[12 + i]
        val c = DoubleArray(4)
        for (i in 0 until 4) c[i] = projection[i] * v[0] + projection[4 + i] * v[1] + projection[8 + i] * v[2] + projection[12 + i] * v[3]
        if (c[3] <= 1e-6) return null
        return ((c[0] / c[3] + 1) / 2).toFloat() to ((1 - c[1] / c[3]) / 2).toFloat()
    }

    private fun hash(a: Int, b: Int): Int {
        var x = a * 73856093 xor b * 19349663
        x = x xor (x ushr 13)
        x *= 0x5bd1e995
        return x xor (x ushr 15)
    }
}

/** The 3D view of the run. [world] is moved by the game loop; this only shows it. */
@Composable
fun HoverBikeScene(
    world: HoverBikeWorld,
    onCamera: (CameraNode) -> Unit,
    modifier: Modifier = Modifier,
    fogColor: FloatArray = floatArrayOf(0.72f, 0.85f, 0.97f),
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val view = rememberView(engine)
    val camera = rememberCameraNode(engine) {
        position = Position(0f, CAM_HEIGHT, CAM_BACK)
        lookAt(Position(0f, 1.1f, -14f))
    }
    remember(camera) { onCamera(camera); true }
    val loaded = remember(modelLoader) { world.load(modelLoader); world.instances }
    // Clear to transparent so the skyline drawn behind the scene shows through.
    val renderer = rememberRenderer(engine)
    remember(renderer, view) {
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }
        view.blendMode = View.BlendMode.TRANSLUCENT
        true
    }
    remember(view, fogColor.toList()) {
        // Haze in the distance, the colour of the skyline's foot, so far buildings fade into it.
        view.fogOptions = View.FogOptions().apply {
            enabled = true
            distance = 60f
            maximumOpacity = 1f
            density = 0.02f
            heightFalloff = 0.05f
            color = fogColor
        }
        true
    }
    SceneView(
        modifier = modifier,
        surfaceType = SurfaceType.TextureSurface,
        engine = engine,
        modelLoader = modelLoader,
        environmentLoader = environmentLoader,
        view = view,
        renderer = renderer,
        isOpaque = false,
        environment = rememberEnvironment(environmentLoader, isOpaque = false),
        frameRatePolicy = FrameRatePolicy.Continuous(),
        autoCenterContent = false,
        cameraNode = camera,
        cameraManipulator = null,
        onGestureListener = null,
    ) {
        for ((pool, instance) in loaded) {
            ModelNode(modelInstance = instance, autoAnimate = false, isVisible = false, apply = { world.attach(pool, this) })
        }
    }
}
