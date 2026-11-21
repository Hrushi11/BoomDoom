package app.boomdoom.ml.fusion

import app.boomdoom.core.model.BuiltInLabel
import app.boomdoom.core.model.Signal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FusionTest {
    private val fusion = Fusion()
    private val flagged = setOf("rage_bait")

    @Test fun lowConfidenceBecomesOther() {
        val r = fusion.classify(mapOf(Signal.FRAMES to mapOf("comedy" to 0.3, "food" to 0.2)), flagged)
        assertEquals(BuiltInLabel.OTHER, r.primaryLabel)
    }

    @Test fun weightsRenormaliseOverPresentSignals() {
        val fused = fusion.combine(mapOf(Signal.FRAMES to mapOf("finance" to 0.8), Signal.OCR to mapOf("finance" to 0.2)))
        assertEquals((0.45 * 0.8 + 0.15 * 0.2) / 0.6, fused.getValue("finance"), 1e-9)
    }

    @Test fun talkingHeadDownweightsFramesAndTriggersSpeech() {
        val scores = mapOf(
            Signal.FRAMES to mapOf(BuiltInLabel.TALKING_HEAD to 0.9, "comedy" to 0.7),
            Signal.CAPTION to mapOf("finance" to 0.9), // 0.25*0.9/0.45 = 0.5
        )
        assertEquals("finance", fusion.classify(scores, flagged).primaryLabel)
        assertTrue(fusion.needSpeech(scores, audioEnabled = true))
        assertFalse(fusion.needSpeech(scores, audioEnabled = false))
    }

    @Test fun flagTagOnlyAboveThreshold() {
        val strong = fusion.classify(mapOf(Signal.FRAMES to mapOf("rage_bait" to 0.8)), flagged)
        val weak = fusion.classify(mapOf(Signal.FRAMES to mapOf("rage_bait" to 0.6)), flagged)
        assertTrue(strong.showFlagTag)
        assertEquals("rage_bait", weak.primaryLabel)
        assertFalse(weak.showFlagTag)
    }

    @Test fun secondaryLabelsCoexist() {
        val r = fusion.classify(mapOf(Signal.FRAMES to mapOf("comedy" to 0.85, "cricket" to 0.7, "food" to 0.1)), flagged)
        assertEquals("comedy", r.primaryLabel)
        assertEquals(listOf("cricket"), r.secondaryLabels)
    }
}
