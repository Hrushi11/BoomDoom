package app.boomdoom.monitor.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import app.boomdoom.monitor.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service (type mediaProjection) that owns the screen capture while content understanding is on
 * (Tech Spec §3.4, §4.4). Android shows a persistent notification and may ask for consent again after a
 * restart; when the projection stops, the hub goes inactive and Home offers "Resume" (US-07, US-32).
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {
    @Inject lateinit var hub: CaptureHub

    private var projection: MediaProjection? = null
    private var capture: ScreenCapture? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) { stopCapture(); stopSelf(); return START_NOT_STICKY }
        // Must be in the foreground with type mediaProjection BEFORE getMediaProjection (Android 14+).
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), type)
        val code = intent?.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        @Suppress("DEPRECATION")
        val data: Intent? = intent?.getParcelableExtra(EXTRA_DATA)
        if (code != Activity.RESULT_OK || data == null) { stopSelf(); return START_NOT_STICKY }
        stopCapture()
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data) ?: run { stopSelf(); return START_NOT_STICKY }
        p.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture(); stopSelf() } // revoked, or "stop sharing"
        }, Handler(Looper.getMainLooper()))
        projection = p
        capture = ScreenCapture(p, resources.displayMetrics).also(hub::set)
        return START_NOT_STICKY // consent can't be restored automatically
    }

    private fun stopCapture() {
        hub.set(null)
        capture?.release(); capture = null
        runCatching { projection?.stop() }; projection = null
    }

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.monitor_channel),
            NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.monitor_notification))
            .setContentText(getString(R.string.monitor_notification_body))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "monitoring"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "app.boomdoom.STOP_CAPTURE"

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            ctx.startForegroundService(Intent(ctx, MonitorService::class.java)
                .putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data))
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }
}
