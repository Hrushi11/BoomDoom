package app.boomdoom.core.data.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private val Context.store by preferencesDataStore("settings")

/** Everything the user chooses, stored on the phone (Tech Spec §6.1). */
data class UserSettings(
    val onboardingDone: Boolean = false,
    val displayName: String = "",
    val budget: Int = 300,
    val checkInInterval: Int = 15,
    val backoffEnabled: Boolean = true,
    val overBudgetInterval: Int = 25,
    val resetHour: Int = 4,
    val resetMinute: Int = 0,
    val customLabels: List<String> = emptyList(),
    val flaggedLabels: Set<String> = setOf("rage_bait"),
    val selectedTopics: Set<String> = emptySet(), // PresetTopic ids
    val audioUnderstanding: Boolean = false,
    val topicSuggestions: Boolean = false,
    val monitoringPaused: Boolean = false,
    val demoMode: Boolean = false,
    val profileDirty: Boolean = true, // lesson profile changed since last sync
    val learningNudgeEnabled: Boolean = true,
) {
    val resetTime: LocalTime get() = LocalTime.of(resetHour, resetMinute)
    val greetingName: String get() = displayName.ifBlank { "there" }
}

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val ctx: Context) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarding_done")
        val name = stringPreferencesKey("display_name")
        val budget = intPreferencesKey("budget")
        val checkIn = intPreferencesKey("check_in_interval")
        val backoff = booleanPreferencesKey("backoff")
        val overBudget = intPreferencesKey("over_budget_interval")
        val resetHour = intPreferencesKey("reset_hour")
        val resetMinute = intPreferencesKey("reset_minute")
        val customLabels = stringSetPreferencesKey("custom_labels")
        val flagged = stringSetPreferencesKey("flagged_labels")
        val topics = stringSetPreferencesKey("selected_topics")
        val audio = booleanPreferencesKey("audio_understanding")
        val suggestions = booleanPreferencesKey("topic_suggestions")
        val paused = booleanPreferencesKey("monitoring_paused")
        val demo = booleanPreferencesKey("demo_mode")
        val profileDirty = booleanPreferencesKey("profile_dirty")
        val learningNudge = booleanPreferencesKey("learning_nudge_enabled")
    }

    private fun Preferences.toSettings(): UserSettings {
        val d = UserSettings()
        return UserSettings(
            onboardingDone = this[K.onboarded] ?: d.onboardingDone,
            displayName = this[K.name] ?: d.displayName,
            budget = this[K.budget] ?: d.budget,
            checkInInterval = this[K.checkIn] ?: d.checkInInterval,
            backoffEnabled = this[K.backoff] ?: d.backoffEnabled,
            overBudgetInterval = this[K.overBudget] ?: d.overBudgetInterval,
            resetHour = this[K.resetHour] ?: d.resetHour,
            resetMinute = this[K.resetMinute] ?: d.resetMinute,
            customLabels = (this[K.customLabels] ?: emptySet()).sorted(),
            flaggedLabels = this[K.flagged] ?: d.flaggedLabels,
            selectedTopics = this[K.topics] ?: d.selectedTopics,
            audioUnderstanding = this[K.audio] ?: d.audioUnderstanding,
            topicSuggestions = this[K.suggestions] ?: d.topicSuggestions,
            monitoringPaused = this[K.paused] ?: d.monitoringPaused,
            demoMode = this[K.demo] ?: d.demoMode,
            profileDirty = this[K.profileDirty] ?: d.profileDirty,
            learningNudgeEnabled = this[K.learningNudge] ?: d.learningNudgeEnabled,
        )
    }

    val settings: Flow<UserSettings> = ctx.store.data.map { it.toSettings() }

    suspend fun current(): UserSettings = settings.first()

    suspend fun setOnboardingDone(done: Boolean = true) = ctx.store.edit { it[K.onboarded] = done }
    suspend fun setDisplayName(name: String) = ctx.store.edit { it[K.name] = name.trim().take(30) }

    /** US-02/03/27. Over-budget interval is never below 10 (US-03). */
    suspend fun setGoals(budget: Int? = null, checkIn: Int? = null, backoff: Boolean? = null, overBudget: Int? = null,
                         resetHour: Int? = null, resetMinute: Int? = null) = ctx.store.edit { p ->
        budget?.let { p[K.budget] = it.coerceIn(10, 2000) }
        checkIn?.let { p[K.checkIn] = it.coerceIn(3, 100) }
        backoff?.let { p[K.backoff] = it }
        overBudget?.let { p[K.overBudget] = it.coerceIn(10, 100) }
        resetHour?.let { p[K.resetHour] = it.coerceIn(0, 23) }
        resetMinute?.let { p[K.resetMinute] = it.coerceIn(0, 59) }
    }

    suspend fun setCustomLabels(labels: Collection<String>) =
        ctx.store.edit { p -> p[K.customLabels] = labels.map { it.trim() }.filter { it.isNotEmpty() }.take(2).toSet() }

    suspend fun setFlagged(labels: Set<String>) = ctx.store.edit { it[K.flagged] = labels }

    suspend fun setSelectedTopics(ids: Set<String>) = ctx.store.edit { it[K.topics] = ids; it[K.profileDirty] = true }
    suspend fun markProfileDirty(dirty: Boolean) = ctx.store.edit { it[K.profileDirty] = dirty }

    suspend fun setAudioUnderstanding(on: Boolean) = ctx.store.edit { it[K.audio] = on }
    suspend fun setTopicSuggestions(on: Boolean) = ctx.store.edit { it[K.suggestions] = on }
    suspend fun setLearningNudgeEnabled(enabled: Boolean) = ctx.store.edit { it[K.learningNudge] = enabled }
    suspend fun setMonitoringPaused(paused: Boolean) = ctx.store.edit { it[K.paused] = paused }
    suspend fun setDemoMode(on: Boolean) = ctx.store.edit { it[K.demo] = on }

    suspend fun clearAll() = ctx.store.edit { it.clear() }
}
