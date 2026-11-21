package app.boomdoom.monitor.watcher

/**
 * Reel transition algorithm (Tech Spec §4.1), without Android types so it can be replayed against
 * recorded accessibility logs (spike S1). One forward swipe = one new reel; back-scrolls don't count.
 */
class ReelTransitionDetector(
    private val screenHeightPx: Int,
    private val debounceMs: Long = 400,
    private val minScrollFraction: Float = 0.6f,
) {
    private var lastTransitionAt = Long.MIN_VALUE / 2

    /**
     * @param scrollDeltaPx signed vertical delta after the scroll settled; positive = forward (next reel).
     * @param frameChanged result of the frame-difference check if FrameSampler is active, else null.
     * @return true if this scroll counts as a new reel.
     */
    fun onScrollSettled(at: Long, scrollDeltaPx: Int, frameChanged: Boolean?): Boolean {
        if (at - lastTransitionAt < debounceMs) return false
        if (scrollDeltaPx <= 0) return false // back-scroll or no movement
        val confirmed = frameChanged ?: (scrollDeltaPx >= minScrollFraction * screenHeightPx)
        if (confirmed) lastTransitionAt = at
        return confirmed
    }
}
