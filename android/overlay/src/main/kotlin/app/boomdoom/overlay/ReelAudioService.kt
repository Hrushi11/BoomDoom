package app.boomdoom.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow

/** Android 15 requires a foreground playback service to request audio focus over another app. */
internal class ReelAudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Lesson playback", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("BoomDoom lesson")
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .build()
        foreground.value = runCatching {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        }.isSuccess
        if (!foreground.value) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        foreground.value = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "reel_lesson_playback"
        private const val NOTIFICATION_ID = 4031
        private val foreground = MutableStateFlow(false)

        @Composable
        fun audioFocusAvailable(): Boolean {
            if (Build.VERSION.SDK_INT < 35) return true
            val context = LocalContext.current.applicationContext
            val service = remember(context) { Intent(context, ReelAudioService::class.java) }
            val ready by foreground.collectAsState()
            LaunchedEffect(service) {
                // A visible overlay is required for a background foreground-service start.
                repeat(2) { withFrameNanos {} }
                runCatching { context.startForegroundService(service) }
            }
            DisposableEffect(service) { onDispose { context.stopService(service) } }
            return ready
        }
    }
}

@Composable
internal fun rememberReelAudioFocus(requestFocus: Boolean): Boolean {
    val context = LocalContext.current
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var hasFocus by remember { mutableStateOf(false) }
    DisposableEffect(audioManager, requestFocus) {
        val request = if (requestFocus) AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { hasFocus = it == AudioManager.AUDIOFOCUS_GAIN }
            .build() else null
        if (request != null) hasFocus = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        onDispose {
            if (request != null) audioManager.abandonAudioFocusRequest(request)
            hasFocus = false
        }
    }
    return hasFocus
}
