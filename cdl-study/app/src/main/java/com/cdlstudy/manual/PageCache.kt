package com.cdlstudy.manual

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Holds the last pages drawn, so scrolling back up does not draw them again.
 *
 * A letter page at phone width is about 6 MB, so the cache is measured in bytes
 * and takes a small part of the heap. Android drops the oldest page by itself.
 */
object PageCache {

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun maxBytes(): Int {
        val heap = Runtime.getRuntime().maxMemory()
        // An eighth of the heap, held between 24 MB and 96 MB.
        return (heap / 8).coerceIn(24L * 1024 * 1024, 96L * 1024 * 1024).toInt()
    }

    private fun key(page: Int, width: Int) = "$page@$width"

    fun get(page: Int, width: Int): Bitmap? = cache.get(key(page, width))

    fun put(page: Int, width: Int, bitmap: Bitmap) {
        cache.put(key(page, width), bitmap)
    }

    fun clear() = cache.evictAll()
}
