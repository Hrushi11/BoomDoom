package app.boomdoom.monitor.capture

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Shares the active [ScreenCapture] (owned by [MonitorService]) with the analyzer and the UI. */
@Singleton
class CaptureHub @Inject constructor() {
    @Volatile var capture: ScreenCapture? = null
        private set
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    internal fun set(c: ScreenCapture?) {
        capture = c
        _active.value = c != null
    }
}
