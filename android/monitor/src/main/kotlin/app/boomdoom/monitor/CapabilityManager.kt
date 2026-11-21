package app.boomdoom.monitor

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import app.boomdoom.core.model.Capability
import app.boomdoom.core.model.MonitoringStatus
import app.boomdoom.monitor.watcher.ReelWatcherService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for the monitoring status on Home (Tech Spec §4.6, US-10, US-32). */
@Singleton
class CapabilityManager @Inject constructor(@ApplicationContext private val ctx: Context) {

    fun accessibilityEnabled(): Boolean {
        val am = ctx.getSystemService(AccessibilityManager::class.java)
        val ours = ComponentName(ctx, ReelWatcherService::class.java)
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.let { s -> ComponentName(s.packageName, s.name) == ours } }
    }

    fun overlayAllowed(): Boolean = Settings.canDrawOverlays(ctx)

    fun notificationsAllowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun status(paused: Boolean): MonitoringStatus = when {
        !accessibilityEnabled() -> MonitoringStatus.SetupRequired(setOf(Capability.ACCESSIBILITY))
        paused -> MonitoringStatus.Paused
        !overlayAllowed() -> MonitoringStatus.Limited(setOf(Capability.OVERLAY))
        // Screen understanding (classification) isn't part of the POC yet, so it doesn't limit the status.
        else -> MonitoringStatus.Active
    }

    // ---- intents to fix each capability (US-07, US-32) ----
    fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    fun overlaySettings() = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    /** Restricted-settings guide (US-07): App info -> ⋮ -> Allow restricted settings. */
    fun appInfo() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
