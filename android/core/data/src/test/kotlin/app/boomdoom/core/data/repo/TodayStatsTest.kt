package app.boomdoom.core.data.repo

import app.boomdoom.core.data.db.LabelCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayStatsTest {
    /** The Pixel 9 test day: 70 reels, 59 counted before models arrived, 10 Other, 1 News (showed "News 100%"). */
    private val pixelDay = TodayStats("2026-11-21", reels = 70, labelCounts = listOf(
        LabelCount("unanalysed", 59), LabelCount("other", 10), LabelCount("news", 1)))

    @Test fun oneRecognisedReelIsNotAPattern() {
        assertEquals(1, pixelDay.recognisedCount)
        assertFalse(pixelDay.enoughRecognised)
    }

    @Test fun breakdownIsShareOfAllReels() {
        val b = pixelDay.breakdown.toMap()
        assertEquals(1 / 70f, b.getValue("news"), 1e-6f)
        assertEquals(10 / 70f, b.getValue("other"), 1e-6f)
        assertEquals(59 / 70f, b.getValue("unanalysed"), 1e-6f)
        assertEquals("categories first, then Other, then not analysed", listOf("news", "other", "unanalysed"), b.keys.toList())
    }

    @Test fun enoughRecognisedReelsShowShares() {
        val t = TodayStats("d", reels = 20, labelCounts = listOf(LabelCount("food", 6), LabelCount("other", 10), LabelCount("news", 4)))
        assertTrue(t.enoughRecognised)
        assertEquals(0.6f, t.recognised.toMap().getValue("food"), 1e-6f)
        assertEquals(0.3f, t.breakdown.toMap().getValue("food"), 1e-6f)
    }
}
