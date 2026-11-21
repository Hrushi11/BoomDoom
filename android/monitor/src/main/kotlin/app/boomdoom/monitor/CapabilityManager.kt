package app.boomdoom.monitor

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import app.boomdoom.core.model.Capability
import app.boomdoom.core.model.MonitoringStatus
import app.boomdoom.monitor.watcher.ReelWatcherService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for the monitoring status on Home (Tech Spec §4.6). */
@Singleton
class CapabilityManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    var projectionActive: Boolean = false
    var audioBlocked: Boolean = false

    fun accessibilityEnabled(): Boolean {
        val am = ctx.getSystemService(AccessibilityManager::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.name == ReelWatcherService::class.java.name }
    }

    fun status(paused: Boolean, audioEnabled: Boolean, hasRecordAudio: Boolean): MonitoringStatus {
        if (!accessibilityEnabled()) return MonitoringStatus.SetupRequired(setOf(Capability.ACCESSIBILITY))
        if (paused) return MonitoringStatus.Paused
        val limited = buildSet {
            if (!Settings.canDrawOverlays(ctx)) add(Capability.OVERLAY)
            if (!projectionActive) add(Capability.SCREEN_CAPTURE)
            if (audioEnabled && (!hasRecordAudio || audioBlocked)) add(Capability.AUDIO)
        }
        return if (limited.isEmpty()) MonitoringStatus.Active else MonitoringStatus.Limited(limited)
    }
}
