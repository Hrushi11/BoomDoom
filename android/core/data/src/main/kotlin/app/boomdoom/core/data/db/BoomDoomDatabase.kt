package app.boomdoom.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class, ReelEntity::class, LabelEntity::class, LabelEmbeddingEntity::class,
        CorrectionEntity::class, DailyAggregateEntity::class, TopicEntity::class, LessonCacheEntity::class,
        LearnedRecapEntity::class, OutboxEventEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BoomDoomDatabase : RoomDatabase() {
    abstract fun tracking(): TrackingDao
    abstract fun learning(): LearningDao
}
