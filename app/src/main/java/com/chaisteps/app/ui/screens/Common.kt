package com.chaisteps.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.chaisteps.app.domain.Format
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            if (onBack != null) {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                    actions = actions,
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = floatingActionButton,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    ) { padding ->
        val base = Modifier.fillMaxSize().padding(padding)
        Column(
            (if (scrollable) base.verticalScroll(rememberScrollState()) else base).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (onBack == null) {
                // Large title, as in the system fitness apps.
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (subtitle != null) {
                            Text(subtitle.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    actions()
                }
            }
            content()
            Spacer(Modifier.height(96.dp))
        }
    }
}

fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = true,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onValueChange(new.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(8)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(epochDay: Long, today: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.CalendarMonth, null)
        Text("  " + Format.day(epochDay, today))
    }
    if (open) {
        val todayMillis = LocalDate.ofEpochDay(today).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it / 86_400_000L) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Add steps to a day, or set its total. */
@Composable
fun StepsDialog(today: Long, currentTotal: (Long) -> Int, onAdd: (Long, Int) -> Unit, onSet: (Long, Int) -> Unit, onDismiss: () -> Unit) {
    var day by rememberSaveable { mutableStateOf(today) }
    var text by rememberSaveable { mutableStateOf("") }
    var setTotal by rememberSaveable { mutableStateOf(false) }
    val n = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (setTotal) "Set day's steps" else "Add steps") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(day, today) { day = it }
                Text("Currently ${Format.steps(currentTotal(day))} steps", style = MaterialTheme.typography.bodyMedium)
                NumberField(text, { text = it }, if (setTotal) "Total steps" else "Steps to add", decimal = false)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { setTotal = !setTotal }) {
                        Text(if (setTotal) "Add steps instead" else "Set the total instead")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = n != null && n <= 200_000, onClick = {
                if (n != null) { if (setTotal) onSet(day, n) else onAdd(day, n) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

val ContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
