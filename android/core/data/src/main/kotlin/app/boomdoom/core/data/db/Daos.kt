package app.boomdoom.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackingDao {
    @Upsert suspend fun upsertSession(s: SessionEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertReel(r: ReelEntity)
    @Upsert suspend fun upsertAggregate(a: DailyAggregateEntity)

    @Query("SELECT * FROM daily_aggregate WHERE dayKey = :dayKey")
    fun observeDay(dayKey: String): Flow<DailyAggregateEntity?>

    @Query("SELECT * FROM reel WHERE dayKey = :dayKey ORDER BY startedAt DESC")
    fun observeReels(dayKey: String): Flow<List<ReelEntity>>

    @Query("UPDATE reel SET correctedLabel = :label WHERE id = :reelId")
    suspend fun correct(reelId: String, label: String)

    @Query("DELETE FROM reel WHERE startedAt < :before") suspend fun deleteReelsBefore(before: Long)
    @Query("DELETE FROM session WHERE startedAt < :before") suspend fun deleteSessionsBefore(before: Long)
}

@Dao
interface LearningDao {
    @Upsert suspend fun upsertTopics(t: List<TopicEntity>)
    @Query("SELECT * FROM topic") fun observeTopics(): Flow<List<TopicEntity>>
    @Upsert suspend fun upsertLessons(l: List<LessonCacheEntity>)

    @Query("SELECT * FROM lesson_cache WHERE topicId = :topicId AND watchedAt IS NULL")
    fun observeUnwatched(topicId: String): Flow<List<LessonCacheEntity>>

    @Upsert suspend fun saveRecap(r: LearnedRecapEntity)
    @Insert suspend fun enqueue(e: OutboxEventEntity)
    @Query("SELECT * FROM outbox_event ORDER BY id LIMIT :limit") suspend fun peekOutbox(limit: Int = 50): List<OutboxEventEntity>
    @Query("DELETE FROM outbox_event WHERE id IN (:ids)") suspend fun ack(ids: List<Long>)
}
