package app.boomdoom.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView

/**
 * Hosts overlays with TYPE_APPLICATION_OVERLAY (Tech Spec §4.3). Owned by MonitorService.
 *
 * TODO(A): give each ComposeView its own LifecycleOwner + SavedStateRegistryOwner; add flag tag,
 * speech label, check-in card and budget card (touchable, opaque, dim behind).
 */
class OverlayManager(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var bar: ComposeView? = null
    private val reels = mutableIntStateOf(0)
    private val budget = mutableIntStateOf(300)

    fun showBar() {
        if (bar != null) return
        val view = ComposeView(ctx).apply { setContent { BudgetBar(reels.intValue, budget.intValue) } }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP }
        wm.addView(view, params)
        bar = view
    }

    fun updateBar(todayReels: Int, dailyBudget: Int) {
        reels.intValue = todayReels
        budget.intValue = dailyBudget
    }

    fun hideAll() {
        bar?.let { wm.removeView(it) }
        bar = null
    }
}
