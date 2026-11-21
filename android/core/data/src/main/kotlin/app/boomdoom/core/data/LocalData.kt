package app.boomdoom.core.data

import app.boomdoom.core.data.db.BoomDoomDatabase
import app.boomdoom.core.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Wipes everything stored on the phone (account deletion, US-29). */
@Singleton
class LocalData @Inject constructor(private val db: BoomDoomDatabase, private val settings: SettingsRepository) {
    suspend fun wipe() = withContext(Dispatchers.IO) {
        db.clearAllTables()
        settings.clearAll()
    }
}
