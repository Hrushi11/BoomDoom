package app.boomdoom.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.boomdoom.core.data.MonitoringGateway
import app.boomdoom.core.data.repo.TrackingRepository
import app.boomdoom.core.data.settings.SettingsRepository
import app.boomdoom.core.network.AccountManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val repo: SettingsRepository,
    private val tracking: TrackingRepository,
    private val account: AccountManager,
    val monitoring: MonitoringGateway,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setName(name: String) = viewModelScope.launch { repo.setDisplayName(name) }
    fun setGoals(budget: Int? = null, checkIn: Int? = null, backoff: Boolean? = null, overBudget: Int? = null,
                 resetHour: Int? = null, resetMinute: Int? = null) =
        viewModelScope.launch { repo.setGoals(budget, checkIn, backoff, overBudget, resetHour, resetMinute) }
    fun setPaused(p: Boolean) = viewModelScope.launch { repo.setMonitoringPaused(p) }
    fun setLearningNudgeEnabled(enabled: Boolean) = viewModelScope.launch { repo.setLearningNudgeEnabled(enabled) }
    fun toggleFlag(key: String) = viewModelScope.launch {
        val cur = repo.current().flaggedLabels
        repo.setFlagged(if (key in cur) cur - key else cur + key)
    }
    fun setCustomLabels(labels: List<String>) = viewModelScope.launch { repo.setCustomLabels(labels) }

    fun deleteHistory() = viewModelScope.launch { tracking.deleteHistory() }

    fun deleteAccount(onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(account.deleteAccount()) }

    // ---- demo mode (US-34) ----
    fun setDemo(on: Boolean) = viewModelScope.launch {
        repo.setDemoMode(on)
        if (!on) tracking.clearDemo()
    }
    fun simulate(n: Int) = monitoring.simulateReels(n)
    fun leaveReels() = monitoring.simulateLeaveReels()
    fun resetDemo() = viewModelScope.launch { tracking.clearDemo() }
}
