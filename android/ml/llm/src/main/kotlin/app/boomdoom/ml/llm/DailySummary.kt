package app.boomdoom.ml.llm

/** Deterministic fallback used whenever LFM2-VL is unavailable or fails guardrails (Tech Spec §5.8). */
object DailySummaryTemplate {
    fun render(reels: Int, topLabel: String?, topPct: Int, longestMin: Int, peakHour: String?, lessons: Int): String =
        buildString {
            append("You watched $reels reels today.")
            if (topLabel != null) append(" $topLabel made up $topPct% of recognised content.")
            if (peakHour != null) append(" Your longest session was $longestMin minutes around $peakHour.")
            append(" You completed $lessons lessons.")
        }

    /** Guardrail: every number in the model output must appear in the input stats. */
    fun numbersAreGrounded(output: String, allowed: Set<String>): Boolean =
        Regex("\\d+").findAll(output).all { it.value in allowed }
}
