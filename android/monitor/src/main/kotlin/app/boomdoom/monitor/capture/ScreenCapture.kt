package app.boomdoom.monitor.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics

/**
 * MediaProjection -> VirtualDisplay -> in-memory RGBA ImageReader (Tech Spec §4.4, ml/README.md step 1).
 * The reader is drained continuously so a grab never returns a stale frame; only the newest Image is held.
 * Frames are copied to a Bitmap on request and never written to disk.
 */
class ScreenCapture(projection: MediaProjection, metrics: DisplayMetrics) {
    private val thread = HandlerThread("boomdoom-capture").apply { start() }
    private val handler = Handler(thread.looper)
    val width: Int
    val height: Int
    private val reader: ImageReader
    private val display: VirtualDisplay
    private val lock = Any()
    private var latest: Image? = null
    @Volatile var lastFrameAt = 0L
        private set

    init {
        // Short side 448 px: enough for SigLIP's 224 px input (and OCR later), small to copy.
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        val scale = SHORT_SIDE.toFloat() / shortSide
        width = (metrics.widthPixels * scale).toInt() and 1.inv()
        height = (metrics.heightPixels * scale).toInt() and 1.inv()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader.setOnImageAvailableListener({ r ->
            val img = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            synchronized(lock) { latest?.close(); latest = img; lastFrameAt = System.currentTimeMillis() }
        }, handler)
        display = projection.createVirtualDisplay("boomdoom-capture", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler)
    }

    /** Copy of the newest frame, or null if nothing has been drawn yet. Caller recycles the Bitmap. */
    fun grab(): Bitmap? = synchronized(lock) {
        val img = latest ?: return null
        val plane = img.planes[0]
        val rowPixels = plane.rowStride / plane.pixelStride
        val padded = Bitmap.createBitmap(rowPixels, img.height, Bitmap.Config.ARGB_8888)
        plane.buffer.rewind()
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPixels == img.width) padded
        else Bitmap.createBitmap(padded, 0, 0, img.width, img.height).also { padded.recycle() }
    }

    fun release() {
        runCatching { display.release() }
        synchronized(lock) { latest?.close(); latest = null }
        runCatching { reader.close() }
        thread.quitSafely()
    }

    companion object {
        const val SHORT_SIDE = 448
    }
}
