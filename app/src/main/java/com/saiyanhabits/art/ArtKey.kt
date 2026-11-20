package com.saiyanhabits.art

import com.saiyanhabits.domain.Boss
import com.saiyanhabits.domain.Mentor

/** How an image is shown, which says how to crop and scale it. */
enum class ArtShape { CIRCLE, FREE, FULL, ICON }

/**
 * Every image the app uses. The manifest (assets/art/manifest.json) maps each
 * key to a file; ART_LIST.md at the repository root describes them for whoever
 * makes the art. The unit tests keep the three in step.
 */
enum class ArtKey(
    val key: String,
    val label: String,
    val width: Int,
    val height: Int,
    val shape: ArtShape,
    /** Optional images have no built-in file: until one is supplied, the app draws something else. */
    val optional: Boolean = false,
) {
    MENTOR_ROSHI("mentor_roshi_portrait", "Roshi", 512, 512, ArtShape.CIRCLE),
    MENTOR_GOKU("mentor_goku_portrait", "Goku", 512, 512, ArtShape.CIRCLE),
    MENTOR_PICCOLO("mentor_piccolo_portrait", "Piccolo", 512, 512, ArtShape.CIRCLE),
    MENTOR_BULMA("mentor_bulma_portrait", "Bulma", 512, 512, ArtShape.CIRCLE),
    MENTOR_VEGETA("mentor_vegeta_portrait", "Vegeta", 512, 512, ArtShape.CIRCLE),
    BOSS_RADITZ("boss_raditz", "Raditz", 1024, 1024, ArtShape.FREE),
    BOSS_NAPPA("boss_nappa", "Nappa", 1024, 1024, ArtShape.FREE),
    BOSS_FRIEZA("boss_frieza", "Frieza", 1024, 1024, ArtShape.FREE),
    BOSS_CELL("boss_cell", "Cell", 1024, 1024, ArtShape.FREE),
    BOSS_BUU("boss_buu", "Majin Buu", 1024, 1024, ArtShape.FREE),
    BOSS_BROLY("boss_broly", "Broly", 1024, 1024, ArtShape.FREE),
    RIVAL("rival_figure", "Rival", 512, 512, ArtShape.CIRCLE),
    DRAGON("dragon", "Dragon", 1024, 1536, ArtShape.FREE),
    PLAYER_PORTRAIT("player_portrait", "You, base form", 512, 512, ArtShape.CIRCLE),
    PLAYER_FORM_1("player_form_1", "You, Super Saiyan", 512, 512, ArtShape.CIRCLE),
    PLAYER_FORM_2("player_form_2", "You, Super Saiyan 2", 512, 512, ArtShape.CIRCLE),
    PLAYER_FORM_3("player_form_3", "You, Super Saiyan 3", 512, 512, ArtShape.CIRCLE),
    BACKGROUND_HOME("background_home", "Home background", 1080, 2340, ArtShape.FULL),
    APP_ICON("app_icon", "Launcher icon", 432, 432, ArtShape.ICON),
    SCOUTER_DEVICE("scouter_device", "Scouter earpiece", 280, 560, ArtShape.FREE),
    CLOUD_ORNAMENT("cloud_ornament", "Cloud ornament", 600, 360, ArtShape.FREE),
    EMBLEM_CAPSULE("emblem_capsule", "Capsule emblem", 512, 512, ArtShape.FREE),
    BIKE_RIDER("bike_rider", "Rider on the hover bike, from behind", 768, 768, ArtShape.FREE),
    BIKE_SKYLINE("bike_skyline", "City skyline", 2048, 512, ArtShape.FREE),
    CARD_BOARD("card_board", "Card battle board", 1086, 1448, ArtShape.FULL),
    BIKE_BUILDING_1("bike_building_1", "Roadside building 1", 1024, 1024, ArtShape.FREE, optional = true),
    BIKE_BUILDING_2("bike_building_2", "Roadside building 2", 1024, 1024, ArtShape.FREE, optional = true),
    BIKE_BUILDING_3("bike_building_3", "Roadside building 3", 1024, 1024, ArtShape.FREE, optional = true),
    BIKE_BUILDING_4("bike_building_4", "Roadside building 4", 1024, 1024, ArtShape.FREE, optional = true),
    BIKE_CAR_1("bike_car_1", "Hover car 1, from behind", 512, 512, ArtShape.FREE, optional = true),
    BIKE_CAR_2("bike_car_2", "Hover car 2, from behind", 512, 512, ArtShape.FREE, optional = true),
    BIKE_CAR_3("bike_car_3", "Hover car 3, from behind", 512, 512, ArtShape.FREE, optional = true),
    BIKE_PALM("bike_palm", "Palm tree", 512, 1024, ArtShape.FREE, optional = true),
    BIKE_PERSON_1("bike_person_1", "Pedestrian 1", 256, 512, ArtShape.FREE, optional = true),
    BIKE_PERSON_2("bike_person_2", "Pedestrian 2", 256, 512, ArtShape.FREE, optional = true),
    BIKE_SIGN("bike_sign", "City sign over the road", 1024, 512, ArtShape.FREE, optional = true),
    ;

    /** True for images the app loads while running; the launcher icon is baked in at build time. */
    val isSwappableInApp: Boolean get() = this != APP_ICON

    companion object {
        fun mentor(mentor: Mentor): ArtKey = when (mentor) {
            Mentor.ROSHI -> MENTOR_ROSHI
            Mentor.GOKU -> MENTOR_GOKU
            Mentor.PICCOLO -> MENTOR_PICCOLO
            Mentor.BULMA -> MENTOR_BULMA
            Mentor.VEGETA -> MENTOR_VEGETA
        }

        fun boss(boss: Boss): ArtKey = when (boss) {
            Boss.RADITZ -> BOSS_RADITZ
            Boss.NAPPA -> BOSS_NAPPA
            Boss.FRIEZA -> BOSS_FRIEZA
            Boss.CELL -> BOSS_CELL
            Boss.BUU -> BOSS_BUU
            Boss.BROLY -> BOSS_BROLY
        }

        /** The player's look for a form: 0 is the base form. */
        fun playerForm(form: Int): ArtKey = when {
            form <= 0 -> PLAYER_PORTRAIT
            form == 1 -> PLAYER_FORM_1
            form == 2 -> PLAYER_FORM_2
            else -> PLAYER_FORM_3
        }

        fun fromKey(key: String): ArtKey? = entries.firstOrNull { it.key == key }

        /** The hover-bike run's roadside images, by what they replace. */
        val bikeBuildings = listOf(BIKE_BUILDING_1, BIKE_BUILDING_2, BIKE_BUILDING_3, BIKE_BUILDING_4)
        val bikeCars = listOf(BIKE_CAR_1, BIKE_CAR_2, BIKE_CAR_3)
        val bikePeople = listOf(BIKE_PERSON_1, BIKE_PERSON_2)
    }
}

/** Reads the manifest: a JSON object of key to filename. Unknown keys are kept, so extra entries do no harm. */
object ArtManifest {
    fun parse(json: String): Map<String, String> {
        val root = com.saiyanhabits.domain.MiniJson.parse(json) as? Map<*, *>
            ?: throw IllegalArgumentException("Expected the manifest to be an object")
        return root.entries.associate { (k, v) ->
            (k as? String ?: throw IllegalArgumentException("Bad key in the manifest")) to
                (v as? String ?: throw IllegalArgumentException("Expected a filename for \"$k\""))
        }
    }
}
