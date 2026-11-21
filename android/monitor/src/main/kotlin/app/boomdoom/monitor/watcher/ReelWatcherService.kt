package app.boomdoom.monitor.watcher

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Detects the Reels screen and reel transitions (Tech Spec §4.1). Emits [WatcherEvent]s to the
 * SessionEngine through an in-process SharedFlow. Selectors come from `reels_selectors.json`.
 *
 * TODO(A, spike S1): Reels-screen detection via selectors; settle wait (600 ms) before calling the
 * detector; read caption/hashtags/creator if exposed (spike S2).
 */
class ReelWatcherService : AccessibilityService() {

    private lateinit var detector: ReelTransitionDetector

    override fun onServiceConnected() {
        detector = ReelTransitionDetector(screenHeightPx = resources.displayMetrics.heightPixels)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != INSTAGRAM) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val delta = event.scrollDeltaY
                if (detector.onScrollSettled(event.eventTime, delta, frameChanged = null)) {
                    _events.tryEmit(WatcherEvent.ReelTransition(System.currentTimeMillis()))
                }
            }
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // TODO: evaluate selectors to decide ReelsVisible / ReelsHidden
            }
        }
    }

    override fun onInterrupt() = Unit

    companion object {
        const val INSTAGRAM = "com.instagram.android"
        private val _events = MutableSharedFlow<WatcherEvent>(extraBufferCapacity = 64)
        val events: SharedFlow<WatcherEvent> = _events.asSharedFlow()
    }
}

sealed interface WatcherEvent {
    data class ReelsVisible(val at: Long) : WatcherEvent
    data class ReelsHidden(val at: Long) : WatcherEvent
    data class ReelTransition(val at: Long) : WatcherEvent
    data class CaptionRead(val at: Long, val text: String) : WatcherEvent // memory only, never stored
}
