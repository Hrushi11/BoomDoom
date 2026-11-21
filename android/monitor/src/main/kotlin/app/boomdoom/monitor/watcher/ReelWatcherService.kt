package app.boomdoom.monitor.watcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.boomdoom.monitor.MonitorController
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import javax.inject.Inject

/**
 * Detects the Instagram Reels screen and reel transitions (Tech Spec §4.1) and forwards them to
 * [MonitorController]. Reads only layout structure (view ids, classes, bounds, item indexes) — never text.
 */
@AndroidEntryPoint
class ReelWatcherService : AccessibilityService() {

    @Inject lateinit var controller: MonitorController

    private lateinit var detector: ReelTransitionDetector
    private var pagerIds: List<String> = emptyList()
    private var classHints: List<String> = listOf("ViewPager", "RecyclerView")
    private var minHeightFraction = 0.8f
    private var reelsVisible = false
    private var lastRootCheck = 0L

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            reelsVisible = false
            controller.submit(WatcherEvent.ScreenOff(System.currentTimeMillis()))
        }
    }

    override fun onServiceConnected() {
        detector = ReelTransitionDetector(screenHeightPx = resources.displayMetrics.heightPixels)
        loadSelectors()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        controller.setConnected(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { unregisterReceiver(screenOff) }
        controller.setConnected(false)
        return super.onUnbind(intent)
    }

    private fun loadSelectors() = runCatching {
        val json = JSONObject(assets.open("reels_selectors.json").bufferedReader().use { it.readText() })
        val app = json.getJSONObject("apps").getJSONObject(MonitorController.INSTAGRAM)
        pagerIds = app.getJSONArray("pager_view_ids").let { a -> List(a.length()) { a.getString(it) } }
        classHints = app.getJSONArray("pager_class_hints").let { a -> List(a.length()) { a.getString(it) } }
        minHeightFraction = app.optDouble("min_height_fraction", 0.8).toFloat()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val now = System.currentTimeMillis()
        if (pkg != MonitorController.INSTAGRAM) {
            // Another app came to the front: the Reels context was exited (US-12/US-11 pause rules).
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && reelsVisible && pkg !in IGNORED_PACKAGES &&
                pkg != packageName) {
                setVisible(false, now)
            }
            return
        }
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event, now)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkRoot(now, force = true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkRoot(now, force = false)
        }
    }

    private fun onScrolled(event: AccessibilityEvent, now: Long) {
        val node = event.source ?: return
        if (!isReelsPager(node, event)) return
        setVisible(true, now)
        val index = event.fromIndex
        val newReel = if (index >= 0) detector.onIndex(now, index) == 1
        else detector.onScrollSettled(now, event.scrollDeltaY, frameChanged = null)
        if (newReel) controller.submit(WatcherEvent.ReelTransition(now))
    }

    /** Reels pager = known view id, or a (nearly) full-screen vertical pager showing exactly one item. */
    private fun isReelsPager(node: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        node.viewIdResourceName?.let { if (it in pagerIds) return true }
        val cls = event.className?.toString() ?: node.className?.toString() ?: return false
        if (classHints.none { cls.contains(it) }) return false
        val r = Rect().also { node.getBoundsInScreen(it) }
        val tall = r.height() >= resources.displayMetrics.heightPixels * minHeightFraction
        val singleItem = event.fromIndex >= 0 && event.fromIndex == event.toIndex
        return tall && singleItem
    }

    /** Detect entering/leaving Reels without a scroll, using the configured view ids (throttled). */
    private fun checkRoot(now: Long, force: Boolean) {
        if (pagerIds.isEmpty() || (!force && now - lastRootCheck < 1000)) return
        lastRootCheck = now
        val root = rootInActiveWindow ?: return
        val found = pagerIds.any { id -> root.findAccessibilityNodeInfosByViewId(id).isNotEmpty() }
        if (found) setVisible(true, now) else if (force && reelsVisible) setVisible(false, now)
    }

    private fun setVisible(visible: Boolean, now: Long) {
        if (visible == reelsVisible) return
        reelsVisible = visible
        if (visible) detector.reset()
        controller.submit(if (visible) WatcherEvent.ReelsVisible(now) else WatcherEvent.ReelsHidden(now))
    }

    override fun onInterrupt() = Unit

    companion object {
        /** Windows that appear over Instagram without leaving it (shade, keyboard, permission dialogs). */
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui", "com.google.android.inputmethod.latin", "com.samsung.android.honeyboard",
            "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        )
    }
}

sealed interface WatcherEvent {
    val at: Long
    data class ReelsVisible(override val at: Long) : WatcherEvent
    data class ReelsHidden(override val at: Long) : WatcherEvent
    data class ReelTransition(override val at: Long) : WatcherEvent
    data class ScreenOff(override val at: Long) : WatcherEvent
    data class Tick(override val at: Long) : WatcherEvent
    data class CaptionRead(override val at: Long, val text: String) : WatcherEvent // memory only, never stored
}
