package com.cdlstudy.manual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Your own copy of the manual.
 *
 * The app ships no manual text and no manual pages. The manual is AAMVA
 * material, and permission to reproduce it is given to state driver licence
 * agencies only. So you import your copy once, the app keeps it in its own
 * private folder, and it shows you the pages. Nothing leaves the phone.
 *
 * Get the file from the Utah Driver License Division at dld.utah.gov.
 */
class ManualStore(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)
    private val prefs get() = context.getSharedPreferences("manual", Context.MODE_PRIVATE)

    val isImported: Boolean get() = file.exists() && file.length() > 0

    /** The page count of the imported file, or 0 for no file. */
    val pageCount: Int get() = if (isImported) prefs.getInt(KEY_PAGES, 0) else 0

    val importedAt: Long get() = prefs.getLong(KEY_AT, 0)

    sealed interface ImportResult {
        data class Ok(val pageCount: Int) : ImportResult
        data class Failed(val reason: String) : ImportResult
    }

    /**
     * Copies the file the person picked, then opens it to check it is a PDF.
     * A file that will not open is deleted, so a half-copied file is never kept.
     */
    fun import(uri: Uri): ImportResult {
        val temp = File(context.filesDir, "$FILE_NAME.part")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return ImportResult.Failed("That file could not be opened.")

            val pages = countPages(temp)
                ?: return ImportResult.Failed(
                    "That file is not a PDF the app can read. Pick the CDL manual PDF."
                )
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                return ImportResult.Failed("The file could not be saved.")
            }
            prefs.edit().putInt(KEY_PAGES, pages).putLong(KEY_AT, System.currentTimeMillis()).apply()
            return ImportResult.Ok(pages)
        } catch (e: Exception) {
            return ImportResult.Failed(e.message ?: "The file could not be copied.")
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun remove() {
        file.delete()
        prefs.edit().clear().apply()
        PageCache.clear()
    }

    private fun countPages(pdf: File): Int? = try {
        open(pdf).use { renderer -> renderer.pageCount }
    } catch (e: Exception) {
        null
    }

    private fun open(pdf: File): PdfRenderer =
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY))

    /**
     * Draws one page, counted from 1, at the width asked for.
     *
     * PdfRenderer holds one page open at a time, so every call is serialized.
     * Call it off the main thread.
     */
    fun render(page: Int, widthPx: Int): Bitmap? {
        if (!isImported || page < 1 || widthPx < 1) return null
        PageCache.get(page, widthPx)?.let { return it }
        synchronized(lock) {
            PageCache.get(page, widthPx)?.let { return it }
            return try {
                open(file).use { renderer ->
                    if (page > renderer.pageCount) return null
                    renderer.openPage(page - 1).use { pdfPage ->
                        val scale = widthPx.toFloat() / pdfPage.width
                        val height = (pdfPage.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                        // A PDF page is drawn on nothing, so white goes down first.
                        bitmap.eraseColor(Color.WHITE)
                        pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PageCache.put(page, widthPx, bitmap)
                        bitmap
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private companion object {
        const val FILE_NAME = "manual.pdf"
        const val KEY_PAGES = "pageCount"
        const val KEY_AT = "importedAt"
        val lock = Any()
    }
}
