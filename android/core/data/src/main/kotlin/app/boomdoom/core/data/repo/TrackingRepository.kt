package app.boomdoom.core.data.repo

import androidx.room.withTransaction
import app.boomdoom.core.common.DayKey
import app.boomdoom.core.data.db.BoomDoomDatabase
import app.boomdoom.core.data.db.DailyAggregateEntity
import app.boomdoom.core.data.db.LabelCount
import app.boomdoom.core.data.db.ReelEntity
import app.boomdoom.core.data.db.SessionEntity
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.data.settings.UserSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Today's numbers for Home / Insights. Everything comes from local records (US-09). */
data class TodayStats(
    val dayKey: String,
    val reels: Int = 0,
    val flagged: Int = 0,
    val lessons: Int = 0,
    val learningSec: Int = 0,
    val longestSessionSec: Int = 0,
    val sessions: Int = 0,
    val labelCounts: List<LabelCount> = emptyList(),
    val peakHour: Int? = null,
    val demo: Boolean = false,
) {
    /** Share of recognised content (excludes "other"), as in Tech Spec §6.3. */
    val recognised: List<Pair<String, Float>>
        get() {
            val known = labelCounts.filter { it.label != "other" }
            val total = known.sumOf { it.count }.toFloat()
            return if (total == 0f) emptyList() else known.map { it.label to it.count / total }
        }
    val hasTracking: Boolean get() = reels > 0
}

@Singleton
class TrackingRepository @Inject constructor(
    private val db: BoomDoomDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.tracking()

    fun dayKey(s: UserSettings, at: Long = System.currentTimeMillis()): String =
        DayKey.of(Instant.ofEpochMilli(at), ZoneId.systemDefault(), s.resetTime)

    /** Emits the current day key and again when the day rolls over at the reset time. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val dayKeys: Flow<String> = settings.settings.flatMapLatest { s ->
        flow { while (true) { emit(dayKey(s)); delay(30_000) } }
    }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeToday(): Flow<TodayStats> = dayKeys.flatMapLatest { key ->
        combine(dao.observeDay(key), dao.observeLabelCounts(key), dao.observePeakHour(key), dao.observeSessionCount(key)) {
                day, labels, peak, sessions ->
            TodayStats(
                dayKey = key, reels = day?.reels ?: 0, flagged = day?.flagged ?: 0, lessons = day?.lessons ?: 0,
                learningSec = day?.learningSec ?: 0, longestSessionSec = day?.longestSessionSec ?: 0,
                sessions = sessions, labelCounts = labels, peakHour = if (sessions >= 2) peak?.hour else null,
                demo = day?.demo ?: false,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReels(): Flow<List<ReelEntity>> = dayKeys.flatMapLatest { dao.observeReels(it) }

    fun observeDays(fromDayKey: String) = dao.observeDaysFrom(fromDayKey)

    suspend fun reelsToday(): Int = dao.day(dayKey(settings.current()))?.reels ?: 0

    suspend fun startSession(app: String, at: Long, dayKey: String): String {
        val id = UUID.randomUUID().toString()
        dao.upsertSession(SessionEntity(id, app, at, null, dayKey, 0, 0, null))
        return id
    }

    /** One finalised reel: insert + incremental aggregate update in one transaction (Tech Spec §6.3). */
    suspend fun recordReel(sessionId: String, dayKey: String, startedAt: Long, watchMs: Long, label: String,
                           confidence: Float, flagged: Boolean, signals: Int, budget: Int, demo: Boolean = false) {
        db.withTransaction {
            dao.insertReel(ReelEntity(UUID.randomUUID().toString(), sessionId, dayKey, startedAt, watchMs, label,
                confidence, "[]", signals, flagged, null, null, demo))
            val day = dao.day(dayKey) ?: DailyAggregateEntity(dayKey)
            dao.upsertDay(day.copy(reels = day.reels + 1, budget = budget, flagged = day.flagged + if (flagged) 1 else 0,
                demo = day.demo || demo))
            dao.session(sessionId)?.let { s ->
                dao.upsertSession(s.copy(reelCount = s.reelCount + 1, flaggedCount = s.flaggedCount + if (flagged) 1 else 0))
            }
        }
    }

    suspend fun endSession(sessionId: String, at: Long, reason: String) {
        db.withTransaction {
            val s = dao.session(sessionId) ?: return@withTransaction
            dao.upsertSession(s.copy(endedAt = at, endReason = reason))
            val sec = ((at - s.startedAt) / 1000).toInt()
            val day = dao.day(s.dayKey) ?: DailyAggregateEntity(s.dayKey)
            if (s.reelCount > 0 && sec > day.longestSessionSec) dao.upsertDay(day.copy(longestSessionSec = sec))
        }
    }

    suspend fun recordLessonCompleted(durationSec: Int) {
        val key = dayKey(settings.current())
        db.withTransaction {
            val day = dao.day(key) ?: DailyAggregateEntity(key)
            dao.upsertDay(day.copy(lessons = day.lessons + 1, learningSec = day.learningSec + durationSec))
        }
    }

    suspend fun correctLabel(reelId: String, newLabel: String) {
        val reel = dao.reel(reelId) ?: return
        dao.correct(reelId, newLabel)
        dao.insertCorrection(app.boomdoom.core.data.db.CorrectionEntity(reelId, reel.primaryLabel, newLabel, null,
            System.currentTimeMillis()))
    }

    /** US-29: clears reel counts, categories and insights. Learned lessons stay. */
    suspend fun deleteHistory() = dao.deleteHistory()

    /** Tech Spec §6.2: reels and sessions are kept 90 days. */
    suspend fun applyRetention(now: Long = System.currentTimeMillis()) {
        val cutoff = now - 90L * 24 * 3600 * 1000
        dao.deleteReelsBefore(cutoff)
        dao.deleteSessionsBefore(cutoff)
    }

    suspend fun clearDemo() {
        dao.deleteDemoReels(); dao.deleteDemoSessions(); dao.deleteDemoDays()
    }
}
