package app.boomdoom.monitor.watcher

/**
 * Reel transition detection (Tech Spec §4.1), free of Android types so it can be replayed against
 * recorded accessibility logs (spike S1).
 *
 * Primary signal: the Reels pager reports which item is visible (AccessibilityEvent.fromIndex). A new
 * highest index = a new reel. Swiping back to an earlier reel never counts, and returning to a reel
 * already counted doesn't count again (US-11: no double counting).
 * Fallback when the pager exposes no index: settled scroll distance ≥ 60% of the screen.
 */
class ReelTransitionDetector(
    private val screenHeightPx: Int,
    private val debounceMs: Long = 400,
    private val minScrollFraction: Float = 0.6f,
) {
    private var lastTransitionAt = Long.MIN_VALUE / 2
    private var maxIndexSeen = -1

    /** Call when the Reels screen (re)appears: the next index observed becomes the baseline. */
    fun reset() {
        maxIndexSeen = -1
    }

    /** @return number of new reels (0 or 1) for a pager now showing item [index]. */
    fun onIndex(at: Long, index: Int): Int {
        if (index < 0) return 0
        if (maxIndexSeen < 0) { maxIndexSeen = index; return 0 } // baseline: the reel already on screen
        if (index <= maxIndexSeen) return 0                     // back-scroll or same reel
        maxIndexSeen = index
        if (at - lastTransitionAt < debounceMs) return 0          // fling settle noise
        lastTransitionAt = at
        return 1
    }

    /**
     * Fallback path. @param scrollDeltaPx signed vertical delta after the scroll settled; positive = forward.
     * @param frameChanged result of the frame-difference check if FrameSampler is active, else null.
     */
    fun onScrollSettled(at: Long, scrollDeltaPx: Int, frameChanged: Boolean?): Boolean {
        if (at - lastTransitionAt < debounceMs) return false
        if (scrollDeltaPx <= 0) return false
        val confirmed = frameChanged ?: (scrollDeltaPx >= minScrollFraction * screenHeightPx)
        if (confirmed) lastTransitionAt = at
        return confirmed
    }
}
