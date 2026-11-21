package app.boomdoom.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.boomdoom.core.designsystem.BoomColors

enum class BarState { NORMAL, NEAR, OVER }

fun barState(reels: Int, budget: Int): BarState = when {
    reels >= budget -> BarState.OVER
    reels >= budget * 0.8 -> BarState.NEAR
    else -> BarState.NORMAL
}

/** The red line (US-12): 4 dp, full width, just below the status bar. Animates within 1 s. */
@Composable
fun BudgetBar(reels: Int, budget: Int, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(
        targetValue = (reels.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "budget",
    )
    val color = when (barState(reels, budget)) {
        BarState.NORMAL -> BoomColors.Budget
        BarState.NEAR -> BoomColors.Warning
        BarState.OVER -> BoomColors.Budget
    }
    Box(modifier.fillMaxWidth().height(4.dp).background(BoomColors.Border.copy(alpha = 0.4f))) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color))
    }
}
