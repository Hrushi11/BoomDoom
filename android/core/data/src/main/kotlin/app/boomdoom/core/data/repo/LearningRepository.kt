package app.boomdoom.core.data.repo

import app.boomdoom.core.data.db.BoomDoomDatabase
import app.boomdoom.core.data.db.LearnedRecapEntity
import app.boomdoom.core.data.db.LessonCacheEntity
import app.boomdoom.core.data.db.OutboxEventEntity
import app.boomdoom.core.data.db.TopicEntity
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.model.LessonManifest
import app.boomdoom.core.model.Level
import app.boomdoom.core.model.PresetTopic
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

val LessonJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

fun normalizeTopic(name: String): String =
    name.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), " ").replace(Regex("\\s+"), " ").trim()

/** A lesson ready for the player: cache row + parsed manifest + where to play it from. */
data class PlayableLesson(val row: LessonCacheEntity, val manifest: LessonManifest, val topicName: String) {
    val source: String? get() = row.videoPath?.let { "file://$it" } ?: row.videoUrl
}

@Singleton
class LearningRepository @Inject constructor(
    db: BoomDoomDatabase,
    private val settings: SettingsRepository,
    private val tracking: TrackingRepository,
) {
    private val dao = db.learning()

    // ---------------------------------------------------------------- topics

    fun observeTopics(): Flow<List<TopicEntity>> = dao.observeTopics()
    suspend fun topics() = dao.topics()
    suspend fun topic(id: String) = dao.topic(id)

    suspend fun addTopic(name: String, level: Level = Level.BEGINNER, goal: String? = null, preset: PresetTopic? = null): TopicEntity {
        val norm = normalizeTopic(preset?.displayName ?: name)
        dao.topicByName(norm)?.let { return it }
        val t = TopicEntity(
            id = UUID.randomUUID().toString(), backendId = null, name = preset?.displayName ?: name.trim(), nameNorm = norm,
            presetId = preset?.id, level = level.api, goal = goal?.trim()?.ifBlank { null }, status = "preparing",
            createdAt = System.currentTimeMillis(),
        )
        dao.upsertTopic(t)
        settings.markProfileDirty(true)
        return t
    }

    /** Onboarding (US-06): make the preset topic set match the selection. */
    suspend fun setPresetTopics(ids: Set<String>) {
        settings.setSelectedTopics(ids)
        ids.mapNotNull { PresetTopic.byId(it) }.forEach { addTopic(it.displayName, preset = it) }
        dao.topics().filter { it.presetId != null && it.presetId !in ids }.forEach { removeTopic(it.id) }
    }

    suspend fun updateTopic(id: String, level: Level, goal: String?) {
        val t = dao.topic(id) ?: return
        dao.upsertTopic(t.copy(level = level.api, goal = goal?.trim()?.ifBlank { null }))
        settings.markProfileDirty(true)
    }

    suspend fun removeTopic(id: String) {
        val t = dao.topic(id) ?: return
        dao.deleteUnwatchedFor(id)
        dao.deleteTopic(id)
        t.backendId?.let { enqueue("delete_topic", buildJsonObject { put("topic_id", it) }) }
        settings.markProfileDirty(true)
    }

    suspend fun applyBackendTopic(localId: String, backendId: String, status: String, readyCount: Int) {
        val t = dao.topic(localId) ?: return
        dao.upsertTopic(t.copy(backendId = backendId, status = status, readyCount = readyCount))
    }

    suspend fun topicByBackendId(backendId: String) = dao.topicByBackendId(backendId)

    // ---------------------------------------------------------------- lessons

    fun observeUnwatched(topicId: String? = null): Flow<List<LessonCacheEntity>> = dao.observeUnwatched(topicId)
    fun observeLesson(id: String) = dao.observeLesson(id)
    suspend fun unwatched() = dao.unwatched()
    suspend fun needingDownload() = dao.needingDownload()
    suspend fun setVideoPath(id: String, path: String) = dao.setVideoPath(id, path)

    suspend fun playable(lessonId: String): PlayableLesson? {
        val row = dao.lesson(lessonId) ?: return null
        val manifest = runCatching { LessonJson.decodeFromString<LessonManifest>(row.manifestJson) }.getOrNull() ?: return null
        val topicName = dao.topic(row.topicId)?.name ?: manifest.topic?.name ?: ""
        return PlayableLesson(row, manifest, topicName)
    }

    /** Next lesson to suggest (check-in card, Home, "Another lesson"). Prefers downloaded/bundled ones. */
    suspend fun nextLessonId(preferTopicId: String? = null, excluding: String? = null): String? {
        val all = dao.unwatched().filter { it.lessonId != excluding }
        val pool = all.filter { preferTopicId == null || it.topicId == preferTopicId }.ifEmpty { all }
        return (pool.firstOrNull { it.videoPath != null || it.bundled } ?: pool.firstOrNull())?.lessonId
    }

    /** Store lessons from the backend feed (signed URLs expire; files are downloaded separately). */
    suspend fun cacheFeed(topicId: String, manifests: List<LessonManifest>) {
        val now = System.currentTimeMillis()
        dao.upsertLessons(manifests.map { m ->
            val existing = dao.lesson(m.lessonId)
            LessonCacheEntity(
                lessonId = m.lessonId, topicId = topicId, concept = m.concept, level = m.level, durationS = m.durationS,
                manifestJson = LessonJson.encodeToString(LessonManifest.serializer(), m), videoUrl = m.videoUrl,
                videoPath = existing?.videoPath, thumbUrl = m.thumbUrl, bundled = false, fetchedAt = existing?.fetchedAt ?: now,
                startedAt = existing?.startedAt, watchedAt = existing?.watchedAt, skipped = existing?.skipped ?: false,
            )
        })
    }

    /** Bundled starter lessons (US-06A, Tech Spec §7.3): one per preset topic, playable offline. */
    suspend fun importBundled(lessons: List<Pair<LessonManifest, String>>) {
        val now = System.currentTimeMillis()
        for ((m, assetPath) in lessons) {
            val existing = dao.lesson(m.lessonId)
            if (existing != null) {
                // A newer bundled lesson (same id and video path, e.g. starters re-rendered as films) must replace the
                // stored manifest, or the new video plays with the old pause times. The user's progress is kept.
                val stored = runCatching { LessonJson.decodeFromString(LessonManifest.serializer(), existing.manifestJson) }.getOrNull()
                if (existing.bundled && (stored == null || stored.version < m.version)) {
                    dao.upsertLessons(listOf(existing.copy(
                        concept = m.concept, level = m.level, durationS = m.durationS,
                        manifestJson = LessonJson.encodeToString(LessonManifest.serializer(), m), videoUrl = "asset:///$assetPath",
                    )))
                }
                continue
            }
            val preset = PresetTopic.byId(m.topic?.id ?: "") ?: continue
            val topic = dao.topicByName(normalizeTopic(preset.displayName)) ?: continue // only for chosen topics
            dao.upsertLessons(listOf(LessonCacheEntity(
                lessonId = m.lessonId, topicId = topic.id, concept = m.concept, level = m.level, durationS = m.durationS,
                manifestJson = LessonJson.encodeToString(LessonManifest.serializer(), m), videoUrl = "asset:///$assetPath",
                videoPath = null, thumbUrl = null, bundled = true, fetchedAt = now,
            )))
        }
    }

    suspend fun started(lesson: PlayableLesson) {
        dao.markStarted(lesson.row.lessonId, System.currentTimeMillis())
        event("lesson_started", lesson)
    }

    suspend fun answered(lesson: PlayableLesson, interactionId: String, answer: Int, correct: Boolean?) =
        event("interaction_answered", lesson, buildJsonObject {
            put("interaction_id", interactionId); put("answer", answer); correct?.let { put("correct", it) }
        })

    /** Recorded once per lesson even if called repeatedly (US-18). Returns true the first time. */
    suspend fun completed(lesson: PlayableLesson): Boolean {
        val first = dao.markWatched(lesson.row.lessonId, System.currentTimeMillis()) == 1
        if (!first) return false
        dao.saveRecap(LearnedRecapEntity(lesson.row.lessonId, lesson.row.topicId, lesson.topicName,
            lesson.manifest.concept, lesson.manifest.recap, System.currentTimeMillis()))
        tracking.recordLessonCompleted(lesson.manifest.durationS.toInt())
        event("lesson_completed", lesson)
        return true
    }

    suspend fun skipped(lesson: PlayableLesson) {
        dao.markSkipped(lesson.row.lessonId)
        event("lesson_skipped", lesson)
    }

    suspend fun report(lesson: PlayableLesson, reason: String, note: String?, positionS: Double) {
        if (lesson.row.bundled) return
        enqueue("report", buildJsonObject {
            put("lesson_id", lesson.row.lessonId); put("reason", reason); note?.let { put("note", it) }
            put("position_s", positionS)
        })
    }

    private suspend fun event(type: String, lesson: PlayableLesson, data: JsonObject? = null) {
        if (lesson.row.bundled) return // bundled lessons are unknown to the backend
        enqueue("event", buildJsonObject {
            put("event_id", UUID.randomUUID().toString()); put("type", type); put("lesson_id", lesson.row.lessonId)
            put("at", Instant.now().toString()); if (data != null) put("data", data)
        })
    }

    private suspend fun enqueue(type: String, payload: JsonObject) =
        dao.enqueue(OutboxEventEntity(type = type, payloadJson = payload.toString(), createdAt = System.currentTimeMillis()))

    suspend fun peekOutbox() = dao.peekOutbox()
    suspend fun ackOutbox(ids: List<Long>) = dao.ack(ids)
    suspend fun failOutbox(ids: List<Long>) { dao.bumpAttempts(ids); dao.dropPoisoned() }

    // ---------------------------------------------------------------- learned

    fun observeLearned() = dao.observeLearned()
    fun observeCompletedCount() = dao.observeCompletedCount()
    suspend fun removeRecap(id: String) = dao.deleteRecap(id)
}

