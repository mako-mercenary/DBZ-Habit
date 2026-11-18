package com.saiyanhabits.ui.games

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The hover-bike run's own pictures, all optional: each is a PNG in
 * assets/art/game/ (see ART_LIST.md). Anything missing is drawn in code
 * instead, so the game works without any of them.
 */
enum class BikeArt(val file: String) {
    BIKE("bulma_bike"),
    BIKE_LEFT("bulma_bike_left"),
    BIKE_RIGHT("bulma_bike_right"),
    DOME("capsule_corp_dome"),
    BUILDING_1("building_1"),
    BUILDING_2("building_2"),
    BUILDING_3("building_3"),
    PALM("palm_tree"),
    TRUCK("truck"),
    CAR("car"),
    BARRIER("barrier"),
    SIGN("west_city_sign"),
    SKY("sky"),
    PEDESTRIAN_1("pedestrian_1"),
    PEDESTRIAN_2("pedestrian_2"),
}

/** The pictures that exist, loaded once. */
class BikeArtSet(private val images: Map<BikeArt, ImageBitmap>) {
    operator fun get(art: BikeArt): ImageBitmap? = images[art]

    companion object {
        val NONE = BikeArtSet(emptyMap())

        suspend fun load(context: Context): BikeArtSet = withContext(Dispatchers.IO) {
            val names = context.assets.list("art/game").orEmpty().toSet()
            BikeArtSet(
                BikeArt.entries.mapNotNull { art ->
                    val file = "${art.file}.png"
                    if (file !in names) return@mapNotNull null
                    runCatching {
                        context.assets.open("art/game/$file").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                    }.getOrNull()?.let { art to it }
                }.toMap(),
            )
        }
    }
}
