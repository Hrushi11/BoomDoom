package app.boomdoom.core.data

import android.content.Intent
import app.boomdoom.core.model.LiveState
import app.boomdoom.core.model.MonitoringStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * What feature modules may know about monitoring. Implemented in :monitor and bound with Hilt, so
 * feature modules never depend on the monitoring code itself (Tech Spec §3.2).
 */
interface MonitoringGateway {
    val live: StateFlow<LiveState>
    fun status(paused: Boolean): MonitoringStatus
    fun accessibilityEnabled(): Boolean
    fun overlayAllowed(): Boolean
    fun notificationsAllowed(): Boolean
    fun accessibilitySettingsIntent(): Intent
    fun overlaySettingsIntent(): Intent
    fun appInfoIntent(): Intent

    // ---- content understanding (screen capture + on-device models) ----
    val captureActive: StateFlow<Boolean>
    fun modelsInstalled(): Boolean
    /** System consent dialog for screen capture (MediaProjectionManager). */
    fun screenCaptureIntent(): Intent
    fun startScreenCapture(resultCode: Int, data: Intent)
    fun stopScreenCapture()

    /** Demo mode (US-34) only. */
    fun simulateReels(count: Int)
    fun simulateLeaveReels()
}
