package app.boomdoom.monitor

import android.content.Intent
import app.boomdoom.core.data.MonitoringGateway
import app.boomdoom.core.model.LiveState
import app.boomdoom.core.model.MonitoringStatus
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MonitoringGatewayImpl @Inject constructor(
    private val controller: MonitorController,
    private val caps: CapabilityManager,
) : MonitoringGateway {
    override val live: StateFlow<LiveState> get() = controller.live
    override fun status(paused: Boolean): MonitoringStatus = caps.status(paused)
    override fun accessibilityEnabled() = caps.accessibilityEnabled()
    override fun overlayAllowed() = caps.overlayAllowed()
    override fun notificationsAllowed() = caps.notificationsAllowed()
    override fun accessibilitySettingsIntent(): Intent = caps.accessibilitySettings()
    override fun overlaySettingsIntent(): Intent = caps.overlaySettings()
    override fun appInfoIntent(): Intent = caps.appInfo()
    override fun simulateReels(count: Int) = controller.simulateReels(count)
    override fun simulateLeaveReels() = controller.simulateLeaveReels()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MonitorModule {
    @Binds abstract fun gateway(impl: MonitoringGatewayImpl): MonitoringGateway
}
