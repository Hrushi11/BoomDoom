package app.boomdoom.core.data.di

import android.content.Context
import androidx.room.Room
import app.boomdoom.core.data.db.BoomDoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): BoomDoomDatabase =
        Room.databaseBuilder(ctx, BoomDoomDatabase::class.java, "boomdoom.db").build()

    @Provides fun tracking(db: BoomDoomDatabase) = db.tracking()
    @Provides fun learning(db: BoomDoomDatabase) = db.learning()
}
