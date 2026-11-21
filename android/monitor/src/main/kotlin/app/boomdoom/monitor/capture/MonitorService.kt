package app.boomdoom.monitor.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import app.boomdoom.monitor.R

/**
 * Foreground service (type mediaProjection) that owns FrameSampler, AudioSnipper and the overlays
 * while monitoring is active (Tech Spec §3.4, §4.4–4.5).
 *
 * TODO(A): accept the MediaProjection consent result, create ONE VirtualDisplay + ImageReader
 * (short side 448 px, RGBA_8888, 2 buffers), sample 1 frame / 2 s + ~700 ms after each transition,
 * skip unchanged frames (16x16 grey MAD < 6). Frames never touch disk.
 */
class MonitorService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.monitor_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.monitor_notification))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "monitoring"
        private const val NOTIFICATION_ID = 1001
    }
}
