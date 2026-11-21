package app.boomdoom.monitor.analysis

import android.graphics.Bitmap
import android.util.Log
import app.boomdoom.core.data.ModelFiles
import app.boomdoom.core.data.repo.TrackingRepository
import app.boomdoom.core.model.ClassifiedReel
import app.boomdoom.core.model.Signal
import app.boomdoom.ml.fusion.Fusion
import app.boomdoom.ml.text.CaptionClassifier
import app.boomdoom.ml.vision.FrameClassifier
import app.boomdoom.monitor.capture.CaptureHub
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Per-reel classification, following ml/README.md "Recommended live solution" (POC scope: caption + frames;
 * OCR and speech come later):
 *
 *  1. Wait briefly for the accessibility caption. Score it with the MiniLM NLI model.
 *     A decisive caption (top >= 0.80 and >= 0.15 ahead of the runner-up) finishes the reel.
 *  2. Otherwise take the first usable frame (>= 600 ms after the swipe, not blank, not the previous reel's
 *     frame; one retry), and score it with SigLIP 2.
 *  3. Fuse the available signals with the Tech Spec §5.6 weights and thresholds, then finalise the reel once.
 *
 * Runs on one background thread with at most [MAX_PENDING] reels in flight; when the user swipes faster,
 * the reel keeps its "other" placeholder. Pixels, captions and scores stay in memory.
 */
@Singleton
class ReelAnalyzer @Inject constructor(
    private val models: ModelFiles,
    private val hub: CaptureHub,
    private val tracking: TrackingRepository,
) {
    data class Result(val reelId: String, val reel: ClassifiedReel, val totalMs: Long)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, "boomdoom-ml").apply { priority = Thread.NORM_PRIORITY - 1 } }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val pending = AtomicInteger(0)
    private val captions = HashMap<String, CompletableDeferred<String?>>()
    private val fusion = Fusion()

    private var caption: CaptionClassifier? = null
    private var vision: FrameClassifier? = null
    private var loadedCustom: List<String> = emptyList()
    private var loadedVersion: String? = null
    private var lastThumb: IntArray? = null

    val available: Boolean get() = models.installed()

    /** Load models off the UI thread when content understanding is enabled (cold start is ~0.6–0.9 s). */
    fun warmUp(customLabels: List<String>) {
        if (!available) return
        scope.launch { runCatching { ensureLoaded(customLabels) }.onFailure { Log.w(TAG, "warm-up failed", it) } }
    }

    /** The watcher's caption text for [reelId] (or null when none is exposed). Memory only. */
    fun offerCaption(reelId: String, text: String?) {
        synchronized(captions) { captions[reelId] }?.complete(text?.takeIf { it.isNotBlank() })
    }

    fun analyze(reelId: String, transitionAt: Long, flagged: Set<String>, customLabels: List<String>,
                onResult: suspend (Result) -> Unit) {
        if (!available) return
        if (pending.incrementAndGet() > MAX_PENDING) { pending.decrementAndGet(); return }
        val captionSlot = CompletableDeferred<String?>().also { synchronized(captions) { captions[reelId] = it } }
        scope.launch {
            try {
                val start = System.currentTimeMillis()
                ensureLoaded(customLabels)
                val result = classify(captionSlot, transitionAt, flagged) ?: return@launch
                val saved = tracking.classifyReel(reelId, result.primaryLabel, result.confidence, result.secondaryLabels,
                    result.signals.fold(0) { m, s -> m or s.bit }, result.showFlagTag)
                if (saved) onResult(Result(reelId, result, System.currentTimeMillis() - start))
            } catch (e: Throwable) {
                Log.w(TAG, "analysis failed", e)
            } finally {
                synchronized(captions) { captions.remove(reelId) }
                pending.decrementAndGet()
            }
        }
    }

    private suspend fun classify(captionSlot: CompletableDeferred<String?>, transitionAt: Long, flagged: Set<String>): ClassifiedReel? {
        val scores = LinkedHashMap<Signal, Map<String, Double>>()
        val text = withTimeoutOrNull(CAPTION_WAIT_MS) { captionSlot.await() }
        if (text != null) caption?.score(text)?.takeIf { it.isNotEmpty() }?.let { scores[Signal.CAPTION] = it }

        val ranked = scores[Signal.CAPTION]?.values?.sortedDescending().orEmpty()
        val decisive = ranked.isNotEmpty() && ranked[0] >= CAPTION_EXIT && ranked[0] - (ranked.getOrNull(1) ?: 0.0) >= CAPTION_MARGIN
        if (!decisive) frameScores(transitionAt)?.let { scores[Signal.FRAMES] = it }
        if (scores.isEmpty()) return null // nothing to go on: keep the placeholder
        return fusion.classify(scores, flagged)
    }

    private suspend fun frameScores(transitionAt: Long): Map<String, Double>? {
        val capture = hub.capture ?: return null
        val vision = vision ?: return null
        val wait = transitionAt + FIRST_FRAME_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        repeat(2) { attempt ->
            val frame = capture.grab()
            if (frame != null) {
                try {
                    val thumb = thumbnail(frame)
                    val usable = !isBlank(thumb) && (lastThumb == null || meanDiff(thumb, lastThumb!!) >= SAME_FRAME_DIFF)
                    if (usable) {
                        lastThumb = thumb
                        return vision.score(frame)
                    }
                } finally {
                    frame.recycle()
                }
            }
            if (attempt == 0) delay(RETRY_MS) // stale or blank: the new reel may not be drawn yet
        }
        return null
    }

    private fun ensureLoaded(customLabels: List<String>) {
        val version = models.version()
        if (version != loadedVersion) { // a newer model set was downloaded: reload both
            caption?.close(); caption = null
            vision?.close(); vision = null
            loadedVersion = version
        }
        if (caption == null || loadedCustom != customLabels) {
            caption?.close()
            caption = CaptionClassifier(models.dir, customLabels)
            loadedCustom = customLabels
        }
        if (vision == null) vision = FrameClassifier(models.dir)
    }

    // ---- frame checks: 16x16 grey thumbnail (Tech Spec §4.4 "skip unchanged frames") ----
    private fun thumbnail(b: Bitmap): IntArray {
        val small = Bitmap.createScaledBitmap(b, 16, 16, true)
        val px = IntArray(256).also { small.getPixels(it, 0, 16, 0, 0, 16, 16) }
        small.recycle()
        return IntArray(256) { i -> val c = px[i]; (((c shr 16) and 255) * 3 + ((c shr 8) and 255) * 6 + (c and 255)) / 10 }
    }

    private fun isBlank(t: IntArray) = (t.max() - t.min()) < BLANK_RANGE
    private fun meanDiff(a: IntArray, b: IntArray) = a.indices.sumOf { abs(a[it] - b[it]) } / a.size

    companion object {
        private const val TAG = "ReelAnalyzer"
        private const val MAX_PENDING = 2
        private const val CAPTION_WAIT_MS = 800L
        private const val CAPTION_EXIT = 0.80
        private const val CAPTION_MARGIN = 0.15
        private const val FIRST_FRAME_MS = 600L
        private const val RETRY_MS = 600L
        private const val BLANK_RANGE = 6
        private const val SAME_FRAME_DIFF = 6
    }
}
