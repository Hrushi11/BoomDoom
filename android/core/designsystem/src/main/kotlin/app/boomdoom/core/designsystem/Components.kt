package app.boomdoom.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val CardShape = RoundedCornerShape(24.dp)
val PillShape = RoundedCornerShape(50)

// ---------------------------------------------------------------- buttons

/** One primary button per screen: pill, coral (night) / coral (paper), dark text. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                  leading: (@Composable () -> Unit)? = null, trailingArrow: Boolean = false) {
    val t = LocalBoomTokens.current
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().height(58.dp), shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = t.primaryCta, contentColor = t.onPrimaryCta,
            disabledContainerColor = t.primaryCta.copy(alpha = 0.4f), disabledContentColor = t.onPrimaryCta.copy(alpha = 0.6f)),
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(10.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (trailingArrow) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
        }
    }
}

/** Teal pill used for learning actions ("Start learning", "Another lesson"). */
@Composable
fun LearnButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 52.dp,
                leading: (@Composable () -> Unit)? = null) {
    Button(
        onClick = onClick, modifier = modifier.height(height), shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = BoomColors.LearnDeep, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(54.dp), shape = PillShape,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, underline: Boolean = true,
             color: Color = LocalBoomTokens.current.textSecondary) {
    Text(
        text, color = color, textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = if (underline) TextDecoration.Underline else null),
        modifier = modifier.clip(PillShape).clickable(role = Role.Button, onClick = onClick).padding(10.dp),
    )
}

// ---------------------------------------------------------------- surfaces

@Composable
fun BoomCard(modifier: Modifier = Modifier, color: Color = LocalBoomTokens.current.card,
             border: Color? = LocalBoomTokens.current.border, onClick: (() -> Unit)? = null,
             padding: Dp = 18.dp, content: @Composable ColumnScope.() -> Unit) {
    var m = modifier.clip(CardShape).background(color)
    if (border != null) m = m.border(1.dp, border, CardShape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

/** Cream paper card used for stickman content (lesson previews, empty states). */
@Composable
fun PaperPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(CardShape).background(BoomColors.Paper).border(1.dp, BoomColors.PaperBorder, CardShape), content = content)
}

@Composable
fun Illustration(@DrawableRes id: Int, modifier: Modifier = Modifier, description: String? = null,
                 contentScale: ContentScale = ContentScale.Fit) {
    Image(painterResource(id), contentDescription = description, modifier = modifier, contentScale = contentScale)
}

// ---------------------------------------------------------------- text

/** Headline with one word in coral, e.g. "Your scroll **budget**". */
@Composable
fun AccentHeadline(before: String, accent: String, after: String = "", modifier: Modifier = Modifier,
                   align: TextAlign = TextAlign.Center) {
    Text(
        buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(color = BoomColors.Boom)) { append(accent) }
            append(after)
        },
        style = MaterialTheme.typography.headlineLarge, textAlign = align, modifier = modifier,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
fun Secondary(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = LocalBoomTokens.current.textSecondary,
        textAlign = align, modifier = modifier)
}

// ---------------------------------------------------------------- chips & tags

@Composable
fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier,
               selectedColor: Color = BoomColors.Boom) {
    val t = LocalBoomTokens.current
    val bg = if (selected) selectedColor else t.card
    val fg = if (selected) BoomColors.Background else MaterialTheme.colorScheme.onBackground
    Box(
        modifier.clip(PillShape).background(bg)
            .border(1.dp, if (selected) selectedColor else t.border.copy(alpha = 1f).let { if (t.isPaper) it else Color(0xFF4A4D5A) }, PillShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = fg) }
}

/** Small rounded tag, e.g. "Psychology · 45 sec". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, color: Color = BoomColors.LearnTint, textColor: Color = BoomColors.Ink) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = textColor,
        modifier = modifier.clip(PillShape).background(color).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(PillShape).border(1.dp, dot.copy(alpha = 0.7f), PillShape).background(dot.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground)
    }
}

// ---------------------------------------------------------------- progress

/** Onboarding step dots + "Step n of total". */
@Composable
fun StepDots(step: Int, total: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(total) { i ->
                Box(Modifier.height(8.dp).width(if (i + 1 == step) 22.dp else 8.dp).clip(PillShape)
                    .background(if (i + 1 == step) BoomColors.Boom else Color(0xFF55586A)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Step $step of $total", style = MaterialTheme.typography.labelSmall, color = LocalBoomTokens.current.textSecondary)
    }
}

/** Budget progress: coral→amber gradient, solid red when over. */
@Composable
fun BudgetProgress(reels: Int, budget: Int, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val fraction = (reels.toFloat() / budget.coerceAtLeast(1)).coerceIn(0f, 1f)
    val pct = ((reels.toFloat() / budget.coerceAtLeast(1)) * 100).toInt().coerceAtMost(100)
    Row(modifier.fillMaxWidth().semantics { contentDescription = "$reels of $budget reels, $pct percent" },
        verticalAlignment = Alignment.CenterVertically) {
        // The red budget line: the same bar BoomDoom draws over Instagram.
        Box(Modifier.weight(1f).height(height).clip(PillShape).background(LocalBoomTokens.current.cardRaised)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(PillShape).background(BoomColors.Budget))
        }
        Spacer(Modifier.width(12.dp))
        Text("$pct%", style = MaterialTheme.typography.labelSmall, color = LocalBoomTokens.current.textSecondary)
    }
}

/** Donut chart for category shares. */
@Composable
fun Donut(slices: List<Pair<Float, Color>>, modifier: Modifier = Modifier, stroke: Dp = 22.dp,
          track: Color = Color(0xFF3A3D4A), center: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = stroke.toPx()
            val d = size.minDimension - w
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
            var start = -90f
            val total = slices.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
            slices.forEach { (v, c) ->
                val sweep = 360f * v / total
                drawArc(c, start, sweep, false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

/** Horizontal stacked bar (check-in card breakdown). */
@Composable
fun StackedBar(slices: List<Pair<Float, Color>>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.first.toDouble() }.toFloat().coerceAtLeast(0.0001f)
    Row(modifier.fillMaxWidth().height(22.dp).clip(PillShape)) {
        slices.forEach { (v, c) -> Box(Modifier.weight((v / total).coerceAtLeast(0.001f)).fillMaxHeight().background(c)) }
    }
}

/** Small stat tile: icon badge + value + label (Home "8 flagged"). */
@Composable
fun StatTile(value: String, label: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    BoomCard(modifier, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(LocalBoomTokens.current.cardRaised),
                contentAlignment = Alignment.Center) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides LocalBoomTokens.current.textSecondary, content = icon)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1, softWrap = false)
                Text(label, style = MaterialTheme.typography.bodySmall, color = LocalBoomTokens.current.textSecondary,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
fun RowScope.Weighted(content: @Composable () -> Unit) = Box(Modifier.weight(1f)) { content() }
