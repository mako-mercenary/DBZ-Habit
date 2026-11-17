package com.saiyanhabits.art

import com.saiyanhabits.domain.Boss
import com.saiyanhabits.domain.Mentor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

/** The art keys, the manifest, the bundled files and ART_LIST.md, kept in step. */
class ArtManifestTest {

    // Unit tests run from the app module's folder.
    private val artDir = File("src/main/assets/art")
    private val manifest: Map<String, String> by lazy {
        ArtManifest.parse(File(artDir, "manifest.json").readText())
    }

    @Test
    fun `every key is in the manifest and its file exists`() {
        for (key in ArtKey.entries) {
            val file = manifest[key.key]
            assertNotNull("${key.key} is missing from manifest.json", file)
            assertTrue("${key.key}: $file is not in assets/art", File(artDir, file!!).isFile)
        }
    }

    @Test
    fun `the manifest names only keys the app knows`() {
        for (key in manifest.keys) {
            assertNotNull("manifest.json has an unknown key \"$key\"", ArtKey.fromKey(key))
        }
    }

    @Test
    fun `every bundled image is a PNG of the recommended size`() {
        for (key in ArtKey.entries) {
            val bytes = File(artDir, manifest.getValue(key.key)).readBytes()
            assertTrue("${key.key} is not a PNG", bytes.size > 24 && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte())
            val header = ByteBuffer.wrap(bytes, 16, 8)
            val width = header.int
            val height = header.int
            assertEquals("${key.key} width", key.width, width)
            assertEquals("${key.key} height", key.height, height)
        }
    }

    @Test
    fun `ART_LIST describes every key with its size`() {
        val lines = File("../ART_LIST.md").readLines()
        for (key in ArtKey.entries) {
            val line = lines.firstOrNull { it.contains("`${key.key}`") }
            assertNotNull("ART_LIST.md does not list ${key.key}", line)
            assertTrue("ART_LIST.md gives the wrong size for ${key.key}", line!!.contains("${key.width} × ${key.height}"))
            assertTrue("ART_LIST.md does not name the file for ${key.key}", line.contains("`${manifest.getValue(key.key)}`"))
        }
    }

    @Test
    fun `keys are unique`() {
        assertEquals(ArtKey.entries.size, ArtKey.entries.map { it.key }.toSet().size)
    }

    @Test
    fun `every mentor, boss and form has a key`() {
        assertEquals(Mentor.entries.size, Mentor.entries.map { ArtKey.mentor(it) }.toSet().size)
        assertEquals(Boss.entries.size, Boss.entries.map { ArtKey.boss(it) }.toSet().size)
        assertEquals(ArtKey.PLAYER_PORTRAIT, ArtKey.playerForm(0))
        assertEquals(ArtKey.PLAYER_FORM_1, ArtKey.playerForm(1))
        assertEquals(ArtKey.PLAYER_FORM_3, ArtKey.playerForm(3))
        assertEquals(ArtKey.PLAYER_FORM_3, ArtKey.playerForm(9))
    }

    @Test
    fun `only the launcher icon is baked in at build time`() {
        assertEquals(listOf(ArtKey.APP_ICON), ArtKey.entries.filter { !it.isSwappableInApp })
    }

    @Test
    fun `the manifest reader takes an object of filenames and nothing else`() {
        assertEquals(mapOf("a" to "a.png"), ArtManifest.parse("""{"a": "a.png"}"""))
        for (bad in listOf("[]", "\"x\"", """{"a": 1}""")) {
            try {
                ArtManifest.parse(bad)
                fail("Expected $bad to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}
