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

class ReelIndexDetectionTest {
    @Test fun firstIndexIsBaselineThenEachNewReelCountsOnce() {
        val d = ReelTransitionDetector(2000)
        val counts = listOf(5, 5, 6, 6, 7).mapIndexed { i, idx -> d.onIndex(1_000L * (i + 1), idx) }
        org.junit.Assert.assertEquals(listOf(0, 0, 1, 0, 1), counts)
    }

    @Test fun backScrollAndReturnDoNotDoubleCount() {
        val d = ReelTransitionDetector(2000)
        val counts = listOf(0, 1, 2, 1, 2, 3).mapIndexed { i, idx -> d.onIndex(1_000L * (i + 1), idx) }
        org.junit.Assert.assertEquals(listOf(0, 1, 1, 0, 0, 1), counts)
    }

    @Test fun resetMakesNextIndexTheNewBaseline() {
        val d = ReelTransitionDetector(2000)
        d.onIndex(1_000, 0); d.onIndex(2_000, 1)
        d.reset()
        org.junit.Assert.assertEquals(0, d.onIndex(3_000, 0))
        org.junit.Assert.assertEquals(1, d.onIndex(4_000, 1))
    }
}
