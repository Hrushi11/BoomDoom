package app.boomdoom.core.network

import android.content.Context
import android.util.Log
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.model.Level
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.HttpException
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Talks to the lesson backend (Tech Spec §7.4). Sends ONLY the lesson profile and lesson events —
 * never reel, label or feed data (there is no API that accepts them).
 */
@Singleton
class LessonSync @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val api: BoomDoomApi,
    private val learning: LearningRepository,
    private val settings: SettingsRepository,
    private val auth: AuthStore,
) {
    data class Result(val anyPreparing: Boolean, val newLessons: Int)

    /** Plain client for presigned S3/CDN downloads: they must not carry our Authorization header. */
    private val downloads = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    suspend fun sync(): Result {
        pushProfileIfDirty()
        val remote = api.topics().associateBy { it.topicId }
        var newLessons = 0
        for (local in learning.topics()) {
            val backendId = local.backendId ?: continue
            val r = remote[backendId] ?: continue
            learning.applyBackendTopic(local.id, backendId, r.status, r.readyCount)
            if (r.readyCount > 0) {
                val feed = api.feed(backendId, limit = 10)
                newLessons += feed.size
                learning.cacheFeed(local.id, feed)
            }
        }
        flushOutbox()
        val preparing = learning.topics().any { it.backendId != null && it.status == "preparing" }
        return Result(preparing, newLessons)
    }

    private suspend fun pushProfileIfDirty() {
        val s = settings.current()
        if (!s.profileDirty) return
        val topics = learning.topics()
        if (topics.isEmpty()) { settings.markProfileDirty(false); return }
        val userId = ensureUserId()
        val resp = api.putProfile(userId, LessonProfileRequest(
            language = "en", voiceId = "af_heart", lengthS = 45,
            topics = topics.map { TopicIn(it.name, Level.of(it.level).api, it.goal, if (it.presetId != null) "preset" else "custom") },
        ))
        topics.zip(resp.topics).forEach { (local, r) -> learning.applyBackendTopic(local.id, r.topicId, r.status, local.readyCount) }
        settings.markProfileDirty(false)
    }

    private suspend fun ensureUserId(): String {
        // Any authenticated call registers the install if needed (see NetworkModule).
        return auth.userId ?: run { api.topics(); checkNotNull(auth.userId) { "registration failed" } }
    }

    private suspend fun flushOutbox() {
        val batch = learning.peekOutbox()
        if (batch.isEmpty()) return
        val events = batch.filter { it.type == "event" }
        if (events.isNotEmpty()) {
            val body = JsonObject(mapOf("events" to JsonArray(events.map { NetJson.parseToJsonElement(it.payloadJson) })))
            runCatching { api.events(body) }
                .onSuccess { learning.ackOutbox(events.map { it.id }) }
                .onFailure { e -> handleFailure(events.map { it.id }, e) }
        }
        for (e in batch.filter { it.type != "event" }) {
            val payload = NetJson.parseToJsonElement(e.payloadJson).jsonObject
            runCatching {
                when (e.type) {
                    "report" -> {
                        val id = payload.getValue("lesson_id").jsonPrimitive.content
                        api.report(id, JsonObject(payload - "lesson_id"))
                    }
                    "delete_topic" -> api.deleteTopic(payload.getValue("topic_id").jsonPrimitive.content)
                    else -> Unit
                }
            }.onSuccess { learning.ackOutbox(listOf(e.id)) }.onFailure { handleFailure(listOf(e.id), it) }
        }
    }

    private suspend fun handleFailure(ids: List<Long>, e: Throwable) {
        // 4xx (except 429) will never succeed: drop. Everything else retries later.
        if (e is HttpException && e.code() in 400..499 && e.code() != 429) learning.ackOutbox(ids)
        else learning.failOutbox(ids)
        Log.w("LessonSync", "outbox send failed: ${e.javaClass.simpleName}")
    }

    /** Download lesson videos so they play offline and start instantly (Tech Spec §7.4 PrefetchWorker). */
    suspend fun downloadPending(maxPerRun: Int = 10): Int {
        val dir = File(ctx.filesDir, "lessons").apply { mkdirs() }
        var done = 0
        for (lesson in learning.needingDownload().take(maxPerRun)) {
            val url = lesson.videoUrl ?: continue
            if (!url.startsWith("http")) continue
            val target = File(dir, "${lesson.lessonId}.mp4")
            val tmp = File(dir, "${lesson.lessonId}.part")
            val ok = runCatching {
                downloads.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    tmp.outputStream().use { out -> resp.body!!.byteStream().copyTo(out) }
                }
                tmp.renameTo(target)
            }.getOrDefault(false)
            if (ok) { learning.setVideoPath(lesson.lessonId, target.absolutePath); done++ } else tmp.delete()
        }
        return done
    }
}

internal val NetJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

