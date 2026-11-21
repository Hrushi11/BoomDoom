package app.boomdoom.ml.fusion

import app.boomdoom.core.model.BuiltInLabel
import app.boomdoom.core.model.ClassifiedReel
import app.boomdoom.core.model.Signal

/** Thresholds from remote config `ml_thresholds.json` (Tech Spec Appendix C). */
data class FusionConfig(
    val weights: Map<Signal, Double> = mapOf(
        Signal.FRAMES to 0.45, Signal.CAPTION to 0.25, Signal.OCR to 0.15, Signal.SPEECH to 0.35,
    ),
    val talkingHeadFrameWeight: Double = 0.2,
    val otherThreshold: Double = 0.45,
    val flagThreshold: Double = 0.70,
    val talkingHeadThreshold: Double = 0.60,
    val speechTrigger: Double = 0.55,
    val secondaryThreshold: Double = 0.60,
    val maxSecondary: Int = 2,
)

/** Per-signal probabilities: signal -> (label key -> probability). */
typealias SignalScores = Map<Signal, Map<String, Double>>

class Fusion(private val cfg: FusionConfig = FusionConfig()) {

    fun isTalkingHead(scores: SignalScores): Boolean =
        (scores[Signal.FRAMES]?.get(BuiltInLabel.TALKING_HEAD) ?: 0.0) >= cfg.talkingHeadThreshold

    /** Tech Spec §5.6: weighted average over present signals, renormalised. */
    fun combine(scores: SignalScores): Map<String, Double> {
        val weights = scores.keys.associateWith { s ->
            if (s == Signal.FRAMES && isTalkingHead(scores)) cfg.talkingHeadFrameWeight else cfg.weights.getValue(s)
        }
        val total = weights.values.sum().takeIf { it > 0 } ?: return emptyMap()
        val labels = scores.values.flatMap { it.keys }.toSet() - BuiltInLabel.TALKING_HEAD
        return labels.associateWith { label ->
            scores.entries.sumOf { (s, p) -> weights.getValue(s) * (p[label] ?: 0.0) } / total
        }
    }

    /** Whether to capture audio for this reel (only if the user enabled audio understanding). */
    fun needSpeech(scores: SignalScores, audioEnabled: Boolean): Boolean {
        if (!audioEnabled || Signal.SPEECH in scores) return false
        val best = combine(scores).values.maxOrNull() ?: 0.0
        return isTalkingHead(scores) || best < cfg.speechTrigger
    }

    fun classify(scores: SignalScores, flaggedLabels: Set<String>): ClassifiedReel {
        val fused = combine(scores)
        val best = fused.maxByOrNull { it.value }
        if (best == null || best.value < cfg.otherThreshold) {
            return ClassifiedReel(BuiltInLabel.OTHER, (best?.value ?: 0.0).toFloat(), emptyList(), scores.keys, false)
        }
        val secondary = fused.entries
            .filter { it.key != best.key && it.value >= cfg.secondaryThreshold }
            .sortedByDescending { it.value }
            .take(cfg.maxSecondary)
            .map { it.key }
        return ClassifiedReel(
            primaryLabel = best.key,
            confidence = best.value.toFloat(),
            secondaryLabels = secondary,
            signals = scores.keys,
            showFlagTag = best.key in flaggedLabels && best.value >= cfg.flagThreshold,
        )
    }
}
