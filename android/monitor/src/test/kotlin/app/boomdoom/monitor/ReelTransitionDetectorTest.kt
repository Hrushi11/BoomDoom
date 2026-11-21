package app.boomdoom.monitor

import app.boomdoom.monitor.watcher.ReelTransitionDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelTransitionDetectorTest {
    private val d = ReelTransitionDetector(screenHeightPx = 2000)

    @Test fun fullSwipeCounts() = assertTrue(d.onScrollSettled(1_000, 1900, null))

    @Test fun debouncesSettleEvents() {
        assertTrue(d.onScrollSettled(1_000, 1900, null))
        assertFalse(d.onScrollSettled(1_200, 1900, null))
        assertTrue(d.onScrollSettled(1_500, 1900, null))
    }

    @Test fun backScrollAndSmallNudgesDoNotCount() {
        assertFalse(d.onScrollSettled(1_000, -1900, null))
        assertFalse(d.onScrollSettled(2_000, 500, null))
    }

    @Test fun frameDiffOverridesScrollHeuristic() {
        assertTrue(d.onScrollSettled(1_000, 300, frameChanged = true))
        assertFalse(d.onScrollSettled(2_000, 1900, frameChanged = false))
    }
}
