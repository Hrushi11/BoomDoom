package app.boomdoom.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Built-in labels (Tech Spec §5.2). "other" is implicit; "talking_head" is internal only. */
enum class BuiltInLabel(val key: String, val displayName: String, val flaggedByDefault: Boolean) {
    COMEDY("comedy", "Comedy", false),
    FITNESS("fitness", "Fitness", false),
    EDUCATION("education", "Education", false),
    FOOD("food", "Food", false),
    NEWS("news", "News", false),
    FINANCE("finance", "Finance", false),
    RAGE_BAIT("rage_bait", "Rage-bait", true),
    ADS("ads", "Ads", false),
    ;

    companion object {
        const val OTHER = "other"
        const val TALKING_HEAD = "talking_head"
        const val MAX_CUSTOM_MVP = 2
    }
}

data class Label(
    val key: String,
    val displayName: String,
    val type: LabelType,
    val flagged: Boolean,
    val prompts: List<String>,
)

enum class LabelType { BUILTIN, CUSTOM, INTERNAL }

/** Signals that contributed to a reel's label (stored as a bitmask in Room). */
enum class Signal(val bit: Int) { FRAMES(1), CAPTION(2), OCR(4), SPEECH(8) }

data class ClassifiedReel(
    val primaryLabel: String,
    val confidence: Float,
    val secondaryLabels: List<String>,
    val signals: Set<Signal>,
    val showFlagTag: Boolean,
)

/** Monitoring status shown on Home (Tech Spec §4.6). */
sealed interface MonitoringStatus {
    data object Active : MonitoringStatus
    data object Paused : MonitoringStatus
    data object NeedsReconsent : MonitoringStatus
    data class Limited(val reasons: Set<Capability>) : MonitoringStatus
    data class SetupRequired(val reasons: Set<Capability>) : MonitoringStatus
}

enum class Capability { ACCESSIBILITY, OVERLAY, SCREEN_CAPTURE, AUDIO, NOTIFICATIONS }

// ---- Lessons (manifest from the backend, Tech Spec §7.1) ----

@Serializable
data class LessonManifest(
    @SerialName("lesson_id") val lessonId: String,
    val topic: TopicRef,
    val concept: String,
    val format: String,
    val level: String,
    val language: String,
    @SerialName("duration_s") val durationS: Double,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("thumb_url") val thumbUrl: String? = null,
    val segments: List<Segment>,
    val interactions: List<Interaction>,
    val recap: String,
    @SerialName("go_deeper") val goDeeper: List<String> = emptyList(),
    val disclaimer: String? = null,
    val version: Int = 1,
)

@Serializable data class TopicRef(val id: String, val name: String)

@Serializable data class Segment(val id: String, @SerialName("end_s") val endS: Double)

@Serializable
data class Interaction(
    val id: String,
    @SerialName("after_segment") val afterSegment: String,
    @SerialName("pause_at_s") val pauseAtS: Double,
    val type: String,
    val question: String,
    val options: List<String>,
    @SerialName("answer_index") val answerIndex: Int? = null,
    @SerialName("feedback_correct") val feedbackCorrect: String,
    @SerialName("feedback_wrong") val feedbackWrong: String,
)
