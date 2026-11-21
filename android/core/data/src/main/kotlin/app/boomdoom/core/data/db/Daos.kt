package app.boomdoom.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class LabelCount(val label: String, val count: Int)
data class HourCount(val hour: Int, val count: Int)

@Dao
interface TrackingDao {
    @Upsert suspend fun upsertSession(s: SessionEntity)
    @Query("SELECT * FROM session WHERE id = :id") suspend fun session(id: String): SessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReel(r: ReelEntity)
    @Query("SELECT * FROM reel WHERE id = :id") suspend fun reel(id: String): ReelEntity?

    @Query("SELECT * FROM daily_aggregate WHERE dayKey = :dayKey") suspend fun day(dayKey: String): DailyAggregateEntity?
    @Upsert suspend fun upsertDay(a: DailyAggregateEntity)
    @Query("SELECT * FROM daily_aggregate WHERE dayKey = :dayKey") fun observeDay(dayKey: String): Flow<DailyAggregateEntity?>

    @Query("SELECT * FROM reel WHERE dayKey = :dayKey ORDER BY startedAt DESC")
    fun observeReels(dayKey: String): Flow<List<ReelEntity>>

    @Query(
        "SELECT COALESCE(correctedLabel, primaryLabel) AS label, COUNT(*) AS count FROM reel " +
            "WHERE dayKey = :dayKey GROUP BY label ORDER BY count DESC",
    )
    fun observeLabelCounts(dayKey: String): Flow<List<LabelCount>>

    @Query(
        "SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour, COUNT(*) AS count " +
            "FROM reel WHERE dayKey = :dayKey GROUP BY hour ORDER BY count DESC LIMIT 1",
    )
    fun observePeakHour(dayKey: String): Flow<HourCount?>

    @Query("SELECT COUNT(*) FROM session WHERE dayKey = :dayKey AND reelCount > 0")
    fun observeSessionCount(dayKey: String): Flow<Int>

    @Query("SELECT * FROM daily_aggregate WHERE dayKey >= :fromDayKey ORDER BY dayKey")
    fun observeDaysFrom(fromDayKey: String): Flow<List<DailyAggregateEntity>>

    @Query("UPDATE reel SET correctedLabel = :label WHERE id = :reelId") suspend fun correct(reelId: String, label: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCorrection(c: CorrectionEntity)

    @Query("DELETE FROM reel WHERE startedAt < :before") suspend fun deleteReelsBefore(before: Long)
    @Query("DELETE FROM session WHERE startedAt < :before") suspend fun deleteSessionsBefore(before: Long)

    @Query("DELETE FROM reel") suspend fun deleteAllReels()
    @Query("DELETE FROM session") suspend fun deleteAllSessions()
    @Query("DELETE FROM correction") suspend fun deleteAllCorrections()
    @Query("UPDATE daily_aggregate SET reels = 0, flagged = 0, longestSessionSec = 0")
    suspend fun resetTrackingAggregates()

    @Query("DELETE FROM reel WHERE demo = 1") suspend fun deleteDemoReels()
    @Query("DELETE FROM session WHERE demo = 1") suspend fun deleteDemoSessions()
    @Query("DELETE FROM daily_aggregate WHERE demo = 1") suspend fun deleteDemoDays()

    @Transaction
    suspend fun deleteHistory() {
        deleteAllReels(); deleteAllSessions(); deleteAllCorrections(); resetTrackingAggregates()
    }
}

@Dao
interface LearningDao {
    // topics
    @Upsert suspend fun upsertTopic(t: TopicEntity)
    @Query("SELECT * FROM topic ORDER BY createdAt") fun observeTopics(): Flow<List<TopicEntity>>
    @Query("SELECT * FROM topic ORDER BY createdAt") suspend fun topics(): List<TopicEntity>
    @Query("SELECT * FROM topic WHERE id = :id") suspend fun topic(id: String): TopicEntity?
    @Query("SELECT * FROM topic WHERE backendId = :backendId") suspend fun topicByBackendId(backendId: String): TopicEntity?
    @Query("SELECT * FROM topic WHERE nameNorm = :nameNorm") suspend fun topicByName(nameNorm: String): TopicEntity?
    @Query("DELETE FROM topic WHERE id = :id") suspend fun deleteTopic(id: String)

    // lessons
    @Upsert suspend fun upsertLessons(l: List<LessonCacheEntity>)
    @Query("SELECT * FROM lesson_cache WHERE lessonId = :id") suspend fun lesson(id: String): LessonCacheEntity?
    @Query("SELECT * FROM lesson_cache WHERE lessonId = :id") fun observeLesson(id: String): Flow<LessonCacheEntity?>
    @Query(
        "SELECT * FROM lesson_cache WHERE watchedAt IS NULL AND skipped = 0 " +
            "AND (:topicId IS NULL OR topicId = :topicId) ORDER BY bundled ASC, fetchedAt ASC",
    )
    fun observeUnwatched(topicId: String?): Flow<List<LessonCacheEntity>>
    @Query("SELECT * FROM lesson_cache WHERE watchedAt IS NULL AND skipped = 0 ORDER BY bundled ASC, fetchedAt ASC")
    suspend fun unwatched(): List<LessonCacheEntity>
    @Query("SELECT * FROM lesson_cache WHERE videoPath IS NULL AND bundled = 0 AND watchedAt IS NULL AND skipped = 0")
    suspend fun needingDownload(): List<LessonCacheEntity>
    @Query("UPDATE lesson_cache SET videoPath = :path WHERE lessonId = :id") suspend fun setVideoPath(id: String, path: String)
    @Query("UPDATE lesson_cache SET startedAt = COALESCE(startedAt, :at) WHERE lessonId = :id")
    suspend fun markStarted(id: String, at: Long)
    @Query("UPDATE lesson_cache SET watchedAt = :at WHERE lessonId = :id AND watchedAt IS NULL")
    suspend fun markWatched(id: String, at: Long): Int
    @Query("UPDATE lesson_cache SET skipped = 1 WHERE lessonId = :id") suspend fun markSkipped(id: String)
    @Query("DELETE FROM lesson_cache WHERE topicId = :topicId AND watchedAt IS NULL") suspend fun deleteUnwatchedFor(topicId: String)
    @Query("SELECT COUNT(*) FROM lesson_cache WHERE watchedAt IS NOT NULL") fun observeCompletedCount(): Flow<Int>

    // learned
    @Upsert suspend fun saveRecap(r: LearnedRecapEntity)
    @Query("SELECT * FROM learned_recap ORDER BY savedAt DESC") fun observeLearned(): Flow<List<LearnedRecapEntity>>
    @Query("DELETE FROM learned_recap WHERE lessonId = :id") suspend fun deleteRecap(id: String)

    // outbox
    @Insert suspend fun enqueue(e: OutboxEventEntity)
    @Query("SELECT * FROM outbox_event ORDER BY id LIMIT :limit") suspend fun peekOutbox(limit: Int = 50): List<OutboxEventEntity>
    @Query("DELETE FROM outbox_event WHERE id IN (:ids)") suspend fun ack(ids: List<Long>)
    @Query("UPDATE outbox_event SET attempts = attempts + 1 WHERE id IN (:ids)") suspend fun bumpAttempts(ids: List<Long>)
    @Query("DELETE FROM outbox_event WHERE attempts >= 20") suspend fun dropPoisoned()
}
