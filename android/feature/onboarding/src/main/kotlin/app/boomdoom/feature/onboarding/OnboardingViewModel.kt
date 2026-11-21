package app.boomdoom.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.boomdoom.core.data.BundledLessons
import app.boomdoom.core.data.MonitoringGateway
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.model.Level
import app.boomdoom.core.model.PresetTopic
import app.boomdoom.core.network.LessonSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Step(val number: Int?) { WELCOME(null), BUDGET(2), LABELS(3), FLAGS(4), TOPICS(5), TRY(6), PERMISSIONS(null) }

data class OnboardingState(
    val step: Step = Step.WELCOME,
    val budget: Int = 300,
    val checkIn: Int = 15,
    val customLabels: List<String> = emptyList(),
    val flagged: Set<String> = setOf("rage_bait"),
    val topics: Set<String> = emptySet(),
    val customTopics: List<String> = emptyList(),
    val starterLessonId: String? = null,
    val accessibility: Boolean = false,
    val overlay: Boolean = false,
    val notifications: Boolean = false,
    val accessibilityAttempts: Int = 0,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
    private val learning: LearningRepository,
    val monitoring: MonitoringGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = settings.current()
            _state.update {
                it.copy(budget = s.budget, checkIn = s.checkInInterval, customLabels = s.customLabels,
                    flagged = s.flaggedLabels, topics = s.selectedTopics)
            }
        }
        refreshPermissions()
    }

    fun go(step: Step) = _state.update { it.copy(step = step) }

    fun back() = _state.update { s ->
        val prev = Step.entries.getOrNull(s.step.ordinal - 1) ?: Step.WELCOME
        s.copy(step = prev)
    }

    fun setBudget(v: Int) = _state.update { it.copy(budget = v) }
    fun setCheckIn(v: Int) = _state.update { it.copy(checkIn = v) }

    fun saveBudget() = viewModelScope.launch {
        settings.setGoals(budget = state.value.budget, checkIn = state.value.checkIn)
        go(Step.LABELS)
    }

    fun addLabel(name: String) = _state.update { s ->
        val clean = name.trim()
        if (clean.isEmpty() || s.customLabels.size >= 2 || s.customLabels.any { it.equals(clean, true) }) s
        else s.copy(customLabels = s.customLabels + clean)
    }

    fun removeLabel(name: String) = _state.update { it.copy(customLabels = it.customLabels - name) }

    fun saveLabels(skip: Boolean = false) = viewModelScope.launch {
        if (!skip) settings.setCustomLabels(state.value.customLabels)
        go(Step.FLAGS)
    }

    fun toggleFlag(key: String) = _state.update { s ->
        s.copy(flagged = if (key in s.flagged) s.flagged - key else s.flagged + key)
    }

    fun saveFlags(skip: Boolean = false) = viewModelScope.launch {
        if (!skip) settings.setFlagged(state.value.flagged)
        go(Step.TOPICS)
    }

    fun toggleTopic(id: String) = _state.update { s ->
        s.copy(topics = if (id in s.topics) s.topics - id else s.topics + id)
    }

    fun addCustomTopic(name: String) = _state.update { s ->
        val clean = name.trim()
        if (clean.isEmpty() || s.customTopics.any { it.equals(clean, true) }) s else s.copy(customTopics = s.customTopics + clean)
    }

    fun saveTopics() = viewModelScope.launch {
        val s = state.value
        learning.setPresetTopics(s.topics)
        s.customTopics.forEach { learning.addTopic(it, Level.BEGINNER) }
        learning.importBundled(BundledLessons.load(ctx))
        LessonSyncScheduler.syncNow(ctx) // starts lesson generation on the backend (needs network; retried later)
        val first = s.topics.mapNotNull { PresetTopic.byId(it) }.firstOrNull()
        _state.update { it.copy(starterLessonId = first?.let { p -> "starter-${p.id}" }, step = Step.TRY) }
    }

    fun refreshPermissions() = _state.update {
        it.copy(accessibility = monitoring.accessibilityEnabled(), overlay = monitoring.overlayAllowed(),
            notifications = monitoring.notificationsAllowed())
    }

    fun accessibilityRequested() = _state.update { it.copy(accessibilityAttempts = it.accessibilityAttempts + 1) }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        settings.setOnboardingDone(true)
        LessonSyncScheduler.schedulePeriodic(ctx)
        onDone()
    }
}
