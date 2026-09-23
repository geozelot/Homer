package com.geozelot.homer.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An open PDF, one page at a time.
 *
 * ## Why the platform renderer and not a library
 *
 * `PdfRenderer` has been in Android since API 21. It adds no dependency, no native blob and no
 * licence, and it keeps a downloaded booklet readable with the radio off — which is the situation
 * this whole feature exists for. Handing the file to another app by intent would be less code, but
 * it needs a downloaded copy anyway, it leaves Homer, and it simply fails on a device with no PDF
 * reader installed.
 *
 * ## The two rules the platform imposes, both enforced here
 *
 * **One page open at a time**, per `PdfRenderer`'s own contract — opening a second while one is
 * open throws. And **`PdfRenderer` is not thread-safe.** A pager renders eagerly and a zoom
 * re-renders while a swipe is in flight, so concurrent calls are the normal case rather than the
 * exotic one. Both are answered by the same [Mutex]: every render takes it, so the pages are drawn
 * one after another however many composables ask at once.
 *
 * ## The white fill is not decoration
 *
 * `RENDER_MODE_FOR_DISPLAY` COMPOSITES onto the bitmap and paints nothing where the page is blank.
 * Left transparent on Homer's dark ground, a page of black text renders black-on-near-black and
 * reads as an empty document. Paper is white; the bitmap starts white.
 */
class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val lock = Mutex()

    val pageCount: Int = renderer.pageCount

    /**
     * Page [index] as a bitmap [widthPx] wide, or null if it could not be drawn.
     *
     * Capped by total pixels rather than by width, because a zoomed A4 page and a zoomed wide score
     * are the same number of megabytes only if the height is in the sum. Past the cap the request is
     * scaled down proportionally: a slightly soft page is a far better answer than the out-of-memory
     * a 3× zoom on a large page would otherwise be.
     */
    suspend fun render(index: Int, widthPx: Int): Bitmap? {
        if (index !in 0 until pageCount || widthPx <= 0) return null
        return lock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    renderer.openPage(index).use { page ->
                        var width = widthPx
                        var height = (widthPx.toFloat() * page.height / page.width)
                            .roundToInt().coerceAtLeast(1)
                        val pixels = width.toLong() * height
                        if (pixels > MaxPixels) {
                            val factor = sqrt(MaxPixels.toDouble() / pixels)
                            width = (width * factor).roundToInt().coerceAtLeast(1)
                            height = (height * factor).roundToInt().coerceAtLeast(1)
                        }
                        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                            it.eraseColor(Color.WHITE)
                            page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    // Reachable despite the cap: the cap bounds ONE page, and the pager may hold a
                    // neighbour's bitmap while this one is allocated. A missing page beats a crash.
                    Log.w(TAG, "out of memory rendering page $index", e)
                    null
                } catch (e: Exception) {
                    Log.w(TAG, "could not render page $index", e)
                    null
                }
            }
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private const val TAG = "HomerDocs"

        /** ~24MB at four bytes a pixel, for one page. */
        private const val MaxPixels = 6_000_000L

        /**
         * Opens [uri], or null when it cannot be read as a PDF.
         *
         * Null covers more than a missing file: a truncated download, something that is not a PDF
         * at all, and a password-protected one — `PdfRenderer` refuses all three, and the reader
         * has the same thing to say about each.
         */
        suspend fun open(context: Context, uri: Uri): PdfPages? = withContext(Dispatchers.IO) {
            val descriptor = try {
                if (uri.scheme == "file") {
                    uri.path?.let {
                        ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY)
                    }
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not open $uri", e)
                null
            } ?: return@withContext null
            try {
                PdfPages(descriptor, PdfRenderer(descriptor))
            } catch (e: Exception) {
                Log.w(TAG, "not a readable PDF: $uri", e)
                runCatching { descriptor.close() }
                null
            }
        }
    }
}
