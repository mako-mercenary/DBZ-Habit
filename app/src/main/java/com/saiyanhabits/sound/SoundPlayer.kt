package com.saiyanhabits.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import com.saiyanhabits.domain.MiniJson
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** The sound effects. Each [key] maps to a file through assets/sound/manifest.json. */
enum class SoundKey(val key: String) {
    CHECK_OFF("check_off"),
    BOSS_HIT("boss_hit"),
    VICTORY("victory"),
    TRANSFORMATION("transformation"),
    BOMB_THROW("bomb_throw"),
    LEVEL_UP("level_up"),
    DRAGON_SUMMON("dragon_summon"),
    CARD_PLACE("card_place"),
    CARD_FLIP("card_flip"),
    CARD_COMBO("card_combo"),
    CARD_HOVER("card_hover"),
    CARD_WIN("card_win"),
    CARD_LOSE("card_lose"),
    PACK_OPEN("pack_open"),
    CARD_REVEAL_RARE("card_reveal_rare"),
}

/**
 * Plays the sound effects, when [enabled] says so. Sounds load in the
 * background when the app starts; one asked for before it has loaded is
 * skipped rather than delayed. A missing or unreadable file is skipped too.
 */
class SoundPlayer(private val context: Context, private val enabled: StateFlow<Boolean>) {

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val ids = mutableMapOf<SoundKey, Int>()
    private val loaded = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        val files = manifest()
        for (sound in SoundKey.entries) {
            val name = files[sound.key] ?: continue
            try {
                ids[sound] = load(name)
            } catch (e: Exception) {
                Log.w("Sound", "Could not load $name: ${e.message}")
            }
        }
    }

    fun play(sound: SoundKey, volume: Float = 1f) {
        if (!enabled.value) return
        val id = ids[sound] ?: return
        if (synchronized(loaded) { id !in loaded }) return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    private fun manifest(): Map<String, String> = try {
        val text = context.assets.open("$DIR/manifest.json").bufferedReader().use { it.readText() }
        @Suppress("UNCHECKED_CAST")
        (MiniJson.parse(text) as Map<String, Any?>).mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    } catch (e: Exception) {
        Log.w("Sound", "No sound manifest: ${e.message}")
        emptyMap()
    }

    /** Loads straight from the APK when the file is stored uncompressed, otherwise from a copy in the cache. */
    private fun load(name: String): Int = try {
        context.assets.openFd("$DIR/$name").use { pool.load(it, 1) }
    } catch (_: java.io.FileNotFoundException) {
        val copy = File(context.cacheDir, "sound-$name")
        context.assets.open("$DIR/$name").use { input -> copy.outputStream().use { input.copyTo(it) } }
        pool.load(copy.path, 1)
    }

    private companion object {
        const val DIR = "sound"
        const val MAX_STREAMS = 4
    }
}

/** The app's sound player, for screens to play effects. Null in previews and tests. */
val LocalSoundPlayer = staticCompositionLocalOf<SoundPlayer?> { null }
