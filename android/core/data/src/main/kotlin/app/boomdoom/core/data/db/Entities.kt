package app.boomdoom.core.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Tech Spec §6.1. Frames, audio, transcripts, OCR text and captions are NEVER stored.

@Entity(tableName = "session")
data class SessionEntity(
    @PrimaryKey val id: String, val app: String,
    val startedAt: Long, val endedAt: Long?, val dayKey: String,
    val reelCount: Int, val flaggedCount: Int, val endReason: String?,
)

@Entity(tableName = "reel", indices = [Index("dayKey"), Index("primaryLabel")])
data class ReelEntity(
    @PrimaryKey val id: String, val sessionId: String, val dayKey: String,
    val startedAt: Long, val watchMs: Long,
    val primaryLabel: String, val confidence: Float, val secondaryLabels: String, // JSON array
    val signals: Int, // bitmask: frames|caption|ocr|speech
    val flagged: Boolean, val description: String?,
    val correctedLabel: String?, val demo: Boolean = false,
)

@Entity(tableName = "label")
data class LabelEntity(
    @PrimaryKey val key: String, val displayName: String,
    val type: String, // builtin|custom|internal
    val flagged: Boolean, val promptsJson: String, val createdAt: Long,
)

@Entity(tableName = "label_embedding", primaryKeys = ["labelKey", "modelVersion", "promptIndex"])
data class LabelEmbeddingEntity(
    val labelKey: String, val modelVersion: String, val promptIndex: Int,
    val vector: ByteArray, // float16 packed
)

@Entity(tableName = "correction")
data class CorrectionEntity(
    @PrimaryKey val reelId: String, val oldLabel: String, val newLabel: String,
    val embedding: ByteArray, val at: Long,
)

@Entity(tableName = "daily_aggregate", primaryKeys = ["dayKey"])
data class DailyAggregateEntity(
    val dayKey: String, val reels: Int, val budget: Int, val flagged: Int,
    val lessons: Int, val learningSec: Int, val labelCountsJson: String,
    val longestSessionSec: Int, val peakHour: Int?,
)

@Entity(tableName = "topic")
data class TopicEntity(
    @PrimaryKey val id: String, val name: String, val level: String, val goal: String?,
    val type: String, // preset|custom|suggested
    val status: String, // preparing|ready|failed
)

@Entity(tableName = "lesson_cache")
data class LessonCacheEntity(
    @PrimaryKey val lessonId: String, val topicId: String, val manifestJson: String,
    val videoPath: String?, val thumbPath: String?, val downloadedAt: Long?,
    val bundled: Boolean, val watchedAt: Long?,
)

@Entity(tableName = "learned_recap")
data class LearnedRecapEntity(
    @PrimaryKey val lessonId: String, val topicId: String, val concept: String,
    val recap: String, val savedAt: Long,
)

/** Lesson events/reports waiting to be sent. Only lesson data may enter the outbox. */
@Entity(tableName = "outbox_event")
data class OutboxEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, val payloadJson: String, val createdAt: Long, val attempts: Int = 0,
)
