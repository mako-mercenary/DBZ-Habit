package com.saiyanhabits.sound

import com.saiyanhabits.domain.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SoundManifestTest {

    /** Unit tests run from the app module's folder. */
    private val dir = File("src/main/assets/sound")

    @Suppress("UNCHECKED_CAST")
    private val manifest = MiniJson.parse(File(dir, "manifest.json").readText()) as Map<String, Any?>

    @Test
    fun `every sound has a file that exists and is a format Android plays`() {
        assertEquals(SoundKey.entries.map { it.key }.toSet(), manifest.keys)
        for ((key, value) in manifest) {
            val name = value as? String
            assertTrue("$key has no file name", !name.isNullOrBlank())
            val file = File(dir, name!!)
            assertTrue("$key: ${file.path} is missing", file.isFile && file.length() > 0)
            assertTrue("$key: $name isn't WAV, MP3, OGG or M4A", file.extension.lowercase() in setOf("wav", "mp3", "ogg", "m4a"))
        }
    }

    @Test
    fun `the sound list names every key and its file`() {
        val list = File("../SOUND_LIST.md").readText()
        for ((key, name) in manifest) {
            assertTrue("SOUND_LIST.md doesn't mention $key", "`$key`" in list)
            assertTrue("SOUND_LIST.md doesn't name $name", "`$name`" in list)
        }
    }
}
