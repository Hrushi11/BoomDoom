package app.boomdoom.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.boomdoom.core.data.MonitoringGateway
import app.boomdoom.core.data.repo.LearningRepository
import app.boomdoom.core.data.repo.TodayStats
import app.boomdoom.core.data.repo.TrackingRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.data.settings.UserSettings
import app.boomdoom.core.model.MonitoringStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NextLesson(val id: String, val concept: String, val topic: String, val durationS: Int)

data class HomeUi(
    val settings: UserSettings = UserSettings(),
    val today: TodayStats = TodayStats(""),
    val status: MonitoringStatus = MonitoringStatus.Paused,
    val next: NextLesson? = null,
    val modelsReady: Boolean = false,
    val captureActive: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    tracking: TrackingRepository,
    private val learning: LearningRepository,
    private val monitoring: MonitoringGateway,
) : ViewModel() {
    private val statusTick = MutableStateFlow(0)

    private val next = learning.observeUnwatched().map { rows ->
        val row = rows.firstOrNull { it.videoPath != null || it.bundled } ?: rows.firstOrNull() ?: return@map null
        val p = learning.playable(row.lessonId) ?: return@map null
        NextLesson(row.lessonId, row.concept, p.topicName, row.durationS.toInt())
    }

    val ui: StateFlow<HomeUi> = combine(
        combine(settingsRepo.settings, tracking.observeToday(), next) { s, t, n -> Triple(s, t, n) },
        statusTick, monitoring.live, monitoring.captureActive,
    ) { (s, today, n), _, _, capture ->
        HomeUi(s, today, monitoring.status(s.monitoringPaused), n, monitoring.modelsInstalled(), capture)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    /** Permissions can change in system settings; re-check on resume (US-10, US-32). */
    fun refreshStatus() { statusTick.value++ }

    fun captureIntent() = monitoring.screenCaptureIntent()
    fun startCapture(resultCode: Int, data: android.content.Intent) = monitoring.startScreenCapture(resultCode, data)

    fun setPaused(paused: Boolean) = viewModelScope.launch { settingsRepo.setMonitoringPaused(paused) }

    fun fixIntent(status: MonitoringStatus) = when (status) {
        is MonitoringStatus.SetupRequired -> monitoring.accessibilitySettingsIntent()
        else -> monitoring.overlaySettingsIntent()
    }
}
