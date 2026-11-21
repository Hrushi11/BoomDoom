package app.boomdoom.monitor.watcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.boomdoom.monitor.MonitorController
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import javax.inject.Inject

/**
 * Detects the Instagram Reels screen and reel transitions (Tech Spec §4.1) and forwards them to
 * [MonitorController]. Counting uses layout structure only (view ids, classes, bounds, item indexes). After a
 * transition it also reads the visible caption text for on-device classification (ml/README.md step 2); the
 * text is passed in memory and never stored.
 */
@AndroidEntryPoint
class ReelWatcherService : AccessibilityService() {

    @Inject lateinit var controller: MonitorController

    private lateinit var detector: ReelTransitionDetector
    private var pagerIds: List<String> = emptyList()
    private var classHints: List<String> = listOf("ViewPager", "RecyclerView")
    private var minHeightFraction = 0.8f
    private var observedPager: AccessibilityNodeInfo? = null
    private var reelsVisible = false
    private var lastRootCheck = 0L
    private val boomDoomActivity by lazy { packageManager.getLaunchIntentForPackage(packageName)?.component?.className }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            reelsVisible = false
            observedPager = null
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
        observedPager = null
        reelsVisible = false
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
            val appWindow = pkg != packageName || event.className?.toString() == boomDoomActivity
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && appWindow && !isSystemOrKeyboard(pkg)) {
                if (reelsVisible) setVisible(false, now)
                else if (controller.lessonShowing) controller.submit(WatcherEvent.ReelsHidden(now))
            }
            return
        }
        // Instagram can emit layout and scroll events behind the lesson window.
        if (controller.lessonShowing) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScrolled(event, now)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> checkRoot(now, force = true)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkRoot(now, force = false)
        }
    }

    private fun isSystemOrKeyboard(pkg: String): Boolean {
        val keyboard = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return pkg in IGNORED_PACKAGES || pkg == keyboard?.substringBefore('/')
    }

    private fun onScrolled(event: AccessibilityEvent, now: Long) {
        val node = event.source ?: return
        if (!isReelsPager(node, event)) return
        observedPager = node
        setVisible(true, now)
        val index = event.fromIndex
        val newReel = if (index >= 0) detector.onIndex(now, index) == 1
        else detector.onScrollSettled(now, event.scrollDeltaY, frameChanged = null)
        if (newReel) {
            controller.submit(WatcherEvent.ReelTransition(now))
            // Give Instagram a moment to draw the new reel's caption, then read it once.
            mainHandler.removeCallbacks(readCaption)
            mainHandler.postDelayed(readCaption, CAPTION_DELAY_MS)
        }
    }

    /**
     * Reels pager = known view id, or a (nearly) full-screen vertical pager showing exactly one item, and in
     * both cases only while Instagram's Reels player is actually on screen. The layout heuristic alone also
     * matched the Home feed whenever one big post filled the screen: Home scrolls turned the budget bar back on
     * and were counted as reels.
     */
    private fun isReelsPager(node: AccessibilityNodeInfo, event: AccessibilityEvent): Boolean {
        val byId = node.viewIdResourceName?.let { it in pagerIds } == true
        if (!byId) {
            val cls = event.className?.toString() ?: node.className?.toString() ?: return false
            if (classHints.none { cls.contains(it) }) return false
            val r = Rect().also { node.getBoundsInScreen(it) }
            val tall = r.height() >= resources.displayMetrics.heightPixels * minHeightFraction
            val singleItem = event.fromIndex >= 0 && event.fromIndex == event.toIndex
            if (!(tall && singleItem)) return false
        }
        return reelsPlayerOnScreen()
    }

    /** Instagram's full-height Reels player (verified on a Pixel 9: `clips_video_container`) is visible. */
    private fun reelsPlayerOnScreen(): Boolean {
        val root = rootInActiveWindow ?: return false
        if (root.packageName?.toString() != MonitorController.INSTAGRAM) return false
        return pagerIds.any { id -> root.findAccessibilityNodeInfosByViewId(id).any(::isFullScreenVisible) }
    }

    /**
     * Check known IDs and the pager that most recently produced a valid scroll event. A match must be visible
     * and (nearly) full screen: Instagram keeps the Reels fragment around after a tab switch and the Home feed
     * reuses video containers inline, which kept the budget bar on the Home feed.
     *
     * Tab switches inside Instagram only fire content-change events, so those may hide the bar too, but only
     * after [HIDE_AFTER_MISSES] consecutive misses (a swipe briefly re-lays out the pager).
     */
    private fun checkRoot(now: Long, force: Boolean) {
        if ((pagerIds.isEmpty() && observedPager == null) || (!force && now - lastRootCheck < 1000)) return
        lastRootCheck = now
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != MonitorController.INSTAGRAM) return
        val found = pagerIds.any { id -> root.findAccessibilityNodeInfosByViewId(id).any(::isFullScreenVisible) } ||
            observedPager?.let { it.windowId == root.windowId && it.refresh() && isFullScreenVisible(it) } == true
        when {
            found -> { misses = 0; setVisible(true, now) }
            !reelsVisible -> Unit
            force || ++misses >= HIDE_AFTER_MISSES -> { misses = 0; setVisible(false, now) }
        }
    }

    private var misses = 0

    private fun isFullScreenVisible(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val r = Rect().also(node::getBoundsInScreen)
        return r.height() >= resources.displayMetrics.heightPixels * minHeightFraction
    }

    private fun setVisible(visible: Boolean, now: Long) {
        if (visible == reelsVisible) return
        reelsVisible = visible
        if (visible) detector.reset()
        else observedPager = null
        controller.submit(if (visible) WatcherEvent.ReelsVisible(now) else WatcherEvent.ReelsHidden(now))
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val readCaption = Runnable {
        val read = runCatching { rootInActiveWindow?.let(::captionText) }.getOrNull()
        controller.submit(WatcherEvent.CaptionRead(System.currentTimeMillis(), read?.first.orEmpty(), read?.second == true))
    }

    /**
     * Caption extraction. Instagram (Nov 2026, Pixel 9) exposes the reel caption only as the content description
     * of an unnamed ViewGroup directly below the author row (`clips_author_username`), truncated with "…". Text
     * fields on the Reels screen are counters ("Reshare number is 270") and must never be read as a caption.
     * Anchored path first; if the author row is not found, fall back to prose-like texts minus UI labels.
     * Also reports Instagram's own sponsored marker. Memory only.
     */
    private fun captionText(root: AccessibilityNodeInfo): Pair<String, Boolean> {
        val screen = Rect(0, 0, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        val authorBottom = root.findAccessibilityNodeInfosByViewId(AUTHOR_ID).firstOrNull { it.isVisibleToUser }
            ?.let { Rect().also(it::getBoundsInScreen).bottom }
        val anchored = ArrayList<String>()
        val loose = ArrayList<String>()
        var sponsored = false
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || loose.size > 60) return
            if (node.isVisibleToUser) {
                val t = node.text?.toString()?.trim()
                val d = node.contentDescription?.toString()?.trim()
                if (!sponsored && (isSponsorMarker(t) || isSponsorMarker(d))) sponsored = true
                val r = Rect().also(node::getBoundsInScreen)
                if (Rect.intersects(r, screen)) {
                    val cls = node.className?.toString().orEmpty()
                    if (authorBottom != null && node.viewIdResourceName == null && cls.endsWith("ViewGroup") &&
                        r.top >= authorBottom && !d.isNullOrEmpty() && looksLikeCaption(d)) anchored += clean(d)
                    if (!t.isNullOrEmpty() && looksLikeCaption(t)) loose += clean(t)
                }
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        val picked = anchored.ifEmpty { loose }
        return picked.distinct().sortedByDescending { it.length }.take(3).joinToString(". ").take(600) to sponsored
    }

    private fun clean(t: String) = t.removeSuffix("more").trimEnd().removeSuffix("…").removeSuffix("...").trim()

    private fun looksLikeCaption(t: String): Boolean {
        if (t.length < 12) return false
        if (t.count { it.isLetter() } < 6) return false
        val lower = t.lowercase()
        if (UI_PATTERNS.any { it.containsMatchIn(lower) }) return false
        return UI_TEXT.none { lower == it || lower.startsWith("$it ") }
    }

    /** Instagram labels paid reels itself ("Ad" under the username, "Sponsored", "Paid partnership"). */
    private fun isSponsorMarker(t: String?): Boolean {
        if (t.isNullOrEmpty() || t.length > 40) return false
        val lower = t.lowercase()
        return lower == "ad" || lower == "sponsored" || lower.startsWith("paid partnership")
    }


    override fun onInterrupt() = Unit

    companion object {
        private const val CAPTION_DELAY_MS = 400L
        private const val HIDE_AFTER_MISSES = 2
        private const val AUTHOR_ID = "com.instagram.android:id/clips_author_username"
        /** Instagram's screen-reader labels for counters and controls on the Reels screen. */
        private val UI_PATTERNS = listOf(
            Regex("""\b(number is|reposted \d+|view likes|view comments|double tap to play|profile picture of)\b"""),
            Regex("""^reel by """),
        )
        private val UI_TEXT = setOf("follow", "following", "like", "comment", "share", "send", "reels", "original audio",
            "suggested for you", "add comment", "view all comments", "liked by", "remix", "use template")

        /** System windows that appear over Instagram without leaving it. */
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
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
    /** Caption text (memory only, never stored) and whether Instagram marked the reel as sponsored. */
    data class CaptionRead(override val at: Long, val text: String, val sponsored: Boolean = false) : WatcherEvent
}
