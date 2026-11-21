package app.boomdoom.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType

/** Custom numeric value with a clear allowed range (scrolling goals, US-27). */
@Composable
fun NumberEntryDialog(
    title: String,
    initial: Int,
    range: IntRange,
    suffix: String,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    // Pre-select the current value so typing replaces it.
    var field by remember { mutableStateOf(TextFieldValue(initial.toString(), TextRange(0, initial.toString().length))) }
    val text = field.text
    val value = text.toIntOrNull()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { f -> field = f.copy(text = f.text.filter(Char::isDigit).take(4)) },
                    modifier = Modifier.focusRequester(focus),
                    singleLine = true, suffix = { Text(suffix) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = text.isNotEmpty() && !valid,
                    supportingText = { Text("Between ${range.first} and ${range.last}") },
                )
            }
        },
        confirmButton = { TextButton({ value?.let(onSave) }, enabled = valid) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

/** Any hour and minute (e.g. when the day resets). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEntryDialog(title: String, hour: Int, minute: Int, onDismiss: () -> Unit, onSave: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = false)
    val teal = BoomColors.LearnDeep
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BoomColors.Paper,
        title = { Text(title) },
        text = {
            TimePicker(
                state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = BoomColors.LearnTint.copy(alpha = 0.5f),
                    selectorColor = teal,
                    clockDialSelectedContentColor = Color.White,
                    timeSelectorSelectedContainerColor = BoomColors.LearnTint,
                    timeSelectorSelectedContentColor = BoomColors.Ink,
                    periodSelectorSelectedContainerColor = BoomColors.LearnTint,
                    periodSelectorSelectedContentColor = BoomColors.Ink,
                    periodSelectorBorderColor = BoomColors.PaperBorder,
                ),
            )
        },
        confirmButton = { TextButton({ onSave(state.hour, state.minute) }) { Text("Save", color = teal) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = teal) } },
    )
}

fun formatTime(hour: Int, minute: Int): String {
    val h = if (hour % 12 == 0) 12 else hour % 12
    return "%d:%02d %s".format(h, minute, if (hour < 12) "AM" else "PM")
}
