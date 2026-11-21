package app.boomdoom.ml.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/** Group logits measured with the reference SigLIP 2 model on Reels-styled frames (ml/README.md). */
class FrameCalibrationTest {
    private fun sig(z: Double) = 1 / (1 + exp(-z))
    private val groups = listOf("comedy", "fitness", "education", "food", "news", "finance", "rage_bait", "ads",
        "talking_head", "background")

    private fun raw(vararg top: Pair<String, Double>, rest: Double = -12.0) =
        groups.associateWith { g -> sig(top.firstOrNull { it.first == g }?.second ?: rest) }

    @Test fun clearFoodFrameIsConfidentFood() {
        val s = FrameClassifier.calibrate(raw("food" to -4.6, "background" to -9.0))
        assertTrue("food: ${s["food"]}", s["food"]!! > 0.9)
    }

    @Test fun selfieGoesToBackgroundNotACategory() {
        val s = FrameClassifier.calibrate(raw("background" to -5.0, "rage_bait" to -7.0, "news" to -8.0))
        assertTrue("all categories under Other's 0.45: $s", s.filterKeys { it != "talking_head" }.values.all { it < 0.45 })
    }

    @Test fun framesNeverVoteRageBaitOrBackground() {
        val s = FrameClassifier.calibrate(raw("rage_bait" to -3.0))
        assertFalse(s.containsKey("rage_bait")); assertFalse(s.containsKey("background"))
        assertTrue(s.values.all { it < 0.45 })
    }

    @Test fun frameMatchingNothingContributesAlmostNothing() {
        val s = FrameClassifier.calibrate(raw("food" to -11.0, rest = -11.6))
        assertTrue("gated: $s", s.filterKeys { it != "talking_head" }.values.all { it < 0.15 })
    }

    @Test fun talkingHeadKeepsRawSigmoid() {
        val r = raw("food" to -4.6, "talking_head" to 1.0)
        assertEquals(r["talking_head"]!!, FrameClassifier.calibrate(r)["talking_head"]!!, 1e-12)
    }
}
