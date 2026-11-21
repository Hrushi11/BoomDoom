package app.boomdoom.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/** Lifecycle + saved state for a ComposeView living in a service-owned window (Tech Spec §4.3). */
private class WindowOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

/** One overlay window. Interactive cards are opaque and full-screen so touches never reach the app below. */
internal class OverlayWindow(private val ctx: Context, private val interactive: Boolean, private val windowGravity: Int) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: ComposeView? = null
    private var owner: WindowOwner? = null

    val isShowing: Boolean get() = view != null

    fun show(content: @Composable () -> Unit): Boolean {
        if (!Settings.canDrawOverlays(ctx)) return false // US-12 fallback: counting continues without overlays
        if (view != null) return true
        val o = WindowOwner().also { it.start() }
        val v = ComposeView(ctx).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent(content)
        }
        val flags = if (interactive) {
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (interactive) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = windowGravity // (a param named `gravity` would shadow-assign the field to itself)
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return runCatching { wm.addView(v, params) }.onSuccess { view = v; owner = o }.isSuccess
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        owner?.destroy()
        view = null; owner = null
    }
}

internal val TOP = Gravity.TOP or Gravity.FILL_HORIZONTAL
internal val CENTER = Gravity.CENTER
