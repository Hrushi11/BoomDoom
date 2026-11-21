package app.boomdoom.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Tech Spec §6.1. Frames, audio, transcripts, OCR text and captions are NEVER stored.

@Entity(tableName = "session", indices = [Index("dayKey")])
data class SessionEntity(
    @PrimaryKey val id: String,
    val app: String,
    val startedAt: Long,
    val endedAt: Long?,
    val dayKey: String,
    val reelCount: Int,
    val flaggedCount: Int,
    val endReason: String?, // timeout|done|screen_off
    val demo: Boolean = false,
)

@Entity(tableName = "reel", indices = [Index("dayKey"), Index("primaryLabel"), Index("sessionId")])
data class ReelEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val dayKey: String,
    val startedAt: Long,
    val watchMs: Long,
    val primaryLabel: String,
    val confidence: Float,
    val secondaryLabels: String, // JSON array
    val signals: Int, // bitmask: frames|caption|ocr|speech
    val flagged: Boolean,
    val description: String?,
    val correctedLabel: String?,
    val demo: Boolean = false,
)

@Entity(tableName = "label_embedding", primaryKeys = ["labelKey", "modelVersion", "promptIndex"])
data class LabelEmbeddingEntity(val labelKey: String, val modelVersion: String, val promptIndex: Int, val vector: ByteArray)

@Entity(tableName = "correction")
data class CorrectionEntity(
    @PrimaryKey val reelId: String,
    val oldLabel: String,
    val newLabel: String,
    val embedding: ByteArray?,
    val at: Long,
)

@Entity(tableName = "daily_aggregate")
data class DailyAggregateEntity(
    @PrimaryKey val dayKey: String,
    val reels: Int = 0,
    val budget: Int = 0,
    val flagged: Int = 0,
    val lessons: Int = 0,
    val learningSec: Int = 0,
    val longestSessionSec: Int = 0,
    val demo: Boolean = false,
)

/** A learning topic. `id` is local; `backendId` is set once the backend has accepted it. */
@Entity(tableName = "topic", indices = [Index(value = ["nameNorm"], unique = true)])
data class TopicEntity(
    @PrimaryKey val id: String,
    val backendId: String?,
    val name: String,
    val nameNorm: String,
    val presetId: String?, // PresetTopic id, null for custom topics
    val level: String,
    val goal: String?,
    val status: String, // preparing|ready|failed (bundled-only topics are "ready")
    val readyCount: Int = 0,
    val createdAt: Long,
)

@Entity(tableName = "lesson_cache", indices = [Index("topicId"), Index("watchedAt")])
data class LessonCacheEntity(
    @PrimaryKey val lessonId: String,
    val topicId: String, // local TopicEntity.id
    val concept: String,
    val level: String,
    val durationS: Double,
    val manifestJson: String,
    val videoUrl: String?, // signed URL (expires) or asset:///
    val videoPath: String?, // downloaded file
    val thumbUrl: String?,
    val bundled: Boolean,
    val fetchedAt: Long,
    val startedAt: Long? = null,
    val watchedAt: Long? = null,
    val skipped: Boolean = false,
)

@Entity(tableName = "learned_recap", indices = [Index("topicId")])
data class LearnedRecapEntity(
    @PrimaryKey val lessonId: String,
    val topicId: String,
    val topicName: String,
    val concept: String,
    val recap: String,
    val savedAt: Long,
)

/** Lesson events/reports waiting to be sent. Only lesson data may enter the outbox. */
@Entity(tableName = "outbox_event")
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // event|report
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int = 0,
)
