package app.boomdoom.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store by preferencesDataStore("settings")

data class UserSettings(
    val budget: Int = 300,
    val checkInInterval: Int = 15,
    val overBudgetInterval: Int = 25,
    val backoffEnabled: Boolean = true,
    val resetHour: Int = 4,
    val audioUnderstanding: Boolean = false,
    val monitoringPaused: Boolean = false,
    val demoMode: Boolean = false,
)

/**
 * Settings store. Tech Spec §6.1 calls for Proto DataStore; Preferences DataStore is used for the
 * scaffold and can be swapped behind this repository without touching callers.
 */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val budget = intPreferencesKey("budget")
        val checkIn = intPreferencesKey("check_in_interval")
        val overBudget = intPreferencesKey("over_budget_interval")
        val backoff = booleanPreferencesKey("backoff")
        val resetHour = intPreferencesKey("reset_hour")
        val audio = booleanPreferencesKey("audio_understanding")
        val paused = booleanPreferencesKey("monitoring_paused")
        val demo = booleanPreferencesKey("demo_mode")
    }

    val settings: Flow<UserSettings> = ctx.store.data.map { p ->
        val d = UserSettings()
        UserSettings(
            budget = p[K.budget] ?: d.budget,
            checkInInterval = p[K.checkIn] ?: d.checkInInterval,
            overBudgetInterval = p[K.overBudget] ?: d.overBudgetInterval,
            backoffEnabled = p[K.backoff] ?: d.backoffEnabled,
            resetHour = p[K.resetHour] ?: d.resetHour,
            audioUnderstanding = p[K.audio] ?: d.audioUnderstanding,
            monitoringPaused = p[K.paused] ?: d.monitoringPaused,
            demoMode = p[K.demo] ?: d.demoMode,
        )
    }

    suspend fun setBudget(budget: Int) = ctx.store.edit { it[K.budget] = budget.coerceIn(10, 2000) }
    suspend fun setCheckInInterval(n: Int) = ctx.store.edit { it[K.checkIn] = n.coerceIn(5, 100) }
    suspend fun setMonitoringPaused(paused: Boolean) = ctx.store.edit { it[K.paused] = paused }
}
