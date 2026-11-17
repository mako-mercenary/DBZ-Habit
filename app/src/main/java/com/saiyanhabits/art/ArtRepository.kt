package com.saiyanhabits.art

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * Finds and decodes the app's images. For each key it prefers an image the
 * user picked in Settings > Art (kept in the app's own files folder), then
 * the bundled file named in the manifest.
 */
class ArtRepository(context: Context) {
    private val context = context.applicationContext
    private val overrideDir = File(this.context.filesDir, "art")

    private val manifest: Map<String, String> by lazy {
        try {
            ArtManifest.parse(this.context.assets.open("art/manifest.json").bufferedReader().use { it.readText() })
        } catch (e: Exception) {
            Log.e("Art", "Could not read the art manifest: ${e.message}")
            emptyMap()
        }
    }

    // Decoded images, up to about 48 MB of them; the rest are decoded again when needed.
    private val cache = object : LruCache<ArtKey, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: ArtKey, value: ImageBitmap): Int = value.width * value.height * 4
    }

    private val _version = MutableStateFlow(0)

    /** Goes up each time a picked image changes, so screens showing art reload it. */
    val version: StateFlow<Int> = _version

    /** The bundled file for [key], from the manifest. */
    fun bundledFileName(key: ArtKey): String = manifest[key.key] ?: "${key.key}.png"

    private fun overrideFile(key: ArtKey) = File(overrideDir, "${key.key}.png")

    fun hasOverride(key: ArtKey): Boolean = overrideFile(key).isFile

    /** Keys the user has picked their own image for. */
    fun overriddenKeys(): Set<ArtKey> = ArtKey.entries.filter { hasOverride(it) }.toSet()

    /** The image for [key], or null if neither a picked nor a bundled file could be read. */
    suspend fun image(key: ArtKey): ImageBitmap? {
        cache.get(key)?.let { return it }
        val decoded = withContext(Dispatchers.IO) { decode(key) } ?: return null
        cache.put(key, decoded)
        return decoded
    }

    private fun decode(key: ArtKey): ImageBitmap? {
        val override = overrideFile(key)
        if (override.isFile) {
            BitmapFactory.decodeFile(override.path)?.let { return it.asImageBitmap() }
            Log.e("Art", "Could not decode the picked image for ${key.key}; using the bundled one")
        }
        return try {
            context.assets.open("art/${bundledFileName(key)}").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        } catch (e: Exception) {
            Log.e("Art", "Could not read the bundled image for ${key.key}: ${e.message}")
            null
        }
    }

    /**
     * Copies the image at [uri] into the app's storage as the picture for [key],
     * scaled down to the key's recommended size if it is larger. False if the
     * image could not be read.
     */
    suspend fun setOverride(key: ArtKey, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeScaled(uri, key.width, key.height) ?: return@withContext false
            overrideDir.mkdirs()
            val tmp = File(overrideDir, "${key.key}.tmp")
            tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            if (!tmp.renameTo(overrideFile(key))) {
                overrideFile(key).delete()
                tmp.renameTo(overrideFile(key))
            }
            invalidate(key)
            true
        } catch (e: Exception) {
            Log.e("Art", "Could not save the picked image for ${key.key}: ${e.message}")
            false
        }
    }

    /** Goes back to the bundled image for [key]. */
    suspend fun clearOverride(key: ArtKey) = withContext(Dispatchers.IO) {
        overrideFile(key).delete()
        invalidate(key)
    }

    private fun invalidate(key: ArtKey) {
        cache.remove(key)
        _version.value++
    }

    /** Decodes [uri] no larger than [maxWidth] x [maxHeight], keeping its proportions. */
    private fun decodeScaled(uri: Uri, maxWidth: Int, maxHeight: Int): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also turns photos the right way up.
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val scale = min(1f, min(maxWidth.toFloat() / info.size.width, maxHeight.toFloat() / info.size.height))
                if (scale < 1f) {
                    decoder.setTargetSize(
                        (info.size.width * scale).toInt().coerceAtLeast(1),
                        (info.size.height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= maxHeight) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
        val scale = min(1f, min(maxWidth.toFloat() / decoded.width, maxHeight.toFloat() / decoded.height))
        if (scale >= 1f) return decoded
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).toInt().coerceAtLeast(1),
            (decoded.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }
}
