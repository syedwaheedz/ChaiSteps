package com.chaisteps.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.data.WeightEntry
import com.chaisteps.app.data.WeightUnit
import com.chaisteps.app.domain.Fitness
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.components.EmptyHint
import com.chaisteps.app.ui.components.GoalBar
import com.chaisteps.app.ui.components.LineChart
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.components.StatRow
import com.chaisteps.app.ui.theme.Accents
import kotlin.math.abs

private enum class Range(val label: String, val days: Long?) { M1("1M", 30), M3("3M", 90), M6("6M", 182), ALL("All", null) }

@Composable
fun WeightScreen(vm: MainViewModel, @Suppress("UNUSED_PARAMETER") nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val stats = remember(data, today) { Stats(data, today, vm.zone) }
    val p = data.profile
    val unit = p.weightUnit
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var range by rememberSaveable { mutableStateOf(Range.M3) }
    var toDelete by remember { mutableStateOf<WeightEntry?>(null) }
    val latest = stats.latestWeight()

    ScreenScaffold(
        title = "Weight",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Log weight") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            )
        },
    ) {
        SectionCard(title = "Journey", accent = Accents.weight) {
            if (latest == null) {
                EmptyHint("Log your first weigh-in to start tracking.")
            } else {
                val start = stats.startWeightKg()
                val goal = p.goalWeightKg
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Format.num(Format.kgToUnit(latest.kg, unit), 1), style = MaterialTheme.typography.displaySmall)
                    Text(" ${Format.weightLabel(unit)}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    if (start != null) {
                        val lost = start - latest.kg
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                if (lost >= 0) "${Format.weight(lost, unit)} lost" else "${Format.weight(-lost, unit)} gained",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (lost >= 0) Accents.steps else MaterialTheme.colorScheme.error,
                            )
                            Text("since start", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Fitness.goalProgress(start, latest.kg, goal)?.let { prog ->
                    Spacer(Modifier.height(12.dp))
                    GoalBar(prog, Accents.weight)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(Format.weight(start!!, unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("${(prog * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.weight(1f))
                        Text("Goal ${Format.weight(goal!!, unit)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(12.dp))
                val bmi = Fitness.bmi(latest.kg, p.heightCm)
                StatRow(
                    Triple("BMI", bmi?.let { Format.num(it, 1) } ?: "–", bmi?.let { Fitness.bmiCategory(it) } ?: "Add height"),
                    Triple("To go", goal?.let { if (abs(latest.kg - goal) < 0.05) "Done 🎉" else Format.weight(abs(latest.kg - goal), unit) } ?: "–", goal?.let { "goal ${Format.weight(it, unit)}" } ?: "Set a goal"),
                )
                Spacer(Modifier.height(8.dp))
                val trend = stats.weeklyTrendKg()
                val projected = stats.projectedGoalDay()
                StatRow(
                    Triple("Weekly trend", trend?.let { Format.signedWeight(it, unit) } ?: "–", "last 4 weeks"),
                    Triple("Goal date", projected?.let { Format.shortDay(it) } ?: "–", if (projected != null) "at this pace" else "need more data"),
                )
            }
        }

        if (data.weights.size >= 2) {
            SectionCard(title = "Progress", accent = Accents.weight) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Range.entries.forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) }) }
                }
                Spacer(Modifier.height(8.dp))
                val since = range.days?.let { (latest?.epochDay ?: today) - it }
                val series = stats.weightSeries(since)
                if (series.size < 2) {
                    EmptyHint("Not enough entries in this range.")
                } else {
                    val ys = series.map { Format.kgToUnit(it.value, unit) }
                    LineChart(
                        series.map { it.epochDay.toDouble() }, ys, Accents.weight,
                        goal = p.goalWeightKg?.let { Format.kgToUnit(it, unit) },
                        description = "Weight chart",
                    )
                    Row {
                        Text(Format.shortDay(series.first().epochDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("low ${Format.num(ys.min(), 1)} · high ${Format.num(ys.max(), 1)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(Format.shortDay(series.last().epochDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (data.weights.isNotEmpty()) {
            Text("Entries", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard {
                val sorted = data.weights.sortedByDescending { it.epochDay }
                sorted.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val prev = sorted.getOrNull(i + 1)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Format.weight(e.kg, unit), style = MaterialTheme.typography.titleMedium)
                            Text(
                                Format.day(e.epochDay, today) + (if (e.note.isNotBlank()) " · ${e.note}" else ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (prev != null) {
                            val diff = e.kg - prev.kg
                            Text(
                                Format.signedWeight(diff, unit),
                                style = MaterialTheme.typography.labelLarge,
                                color = when {
                                    diff < -0.05 -> Accents.steps
                                    diff > 0.05 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                        IconButton(onClick = { toDelete = e }) { Icon(Icons.Filled.DeleteOutline, "Delete entry", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }

    if (showAdd) {
        WeightDialog(
            today = today,
            unit = unit,
            initialKg = latest?.kg,
            onSave = { day, kg, note -> vm.addWeight(day, kg, note) },
            onDismiss = { showAdd = false },
        )
    }
    toDelete?.let { e ->
        ConfirmDialog("Delete entry?", "${Format.weight(e.kg, unit)} on ${Format.day(e.epochDay, today)}", "Delete",
            onConfirm = { vm.deleteWeight(e.id) }, onDismiss = { toDelete = null })
    }
}

@Composable
fun WeightDialog(today: Long, unit: WeightUnit, initialKg: Double?, onSave: (Long, Double, String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialKg?.let { Format.num(Format.kgToUnit(it, unit), 1) } ?: "") }
    var note by rememberSaveable { mutableStateOf("") }
    var day by rememberSaveable { mutableStateOf(today) }
    val value = parseNumber(text)
    val kg = value?.let { Format.unitToKg(it, unit) }
    val valid = kg != null && kg in 20.0..400.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(text, { text = it }, "Weight", suffix = Format.weightLabel(unit), isError = text.isNotEmpty() && !valid)
                DateField(day, today) { day = it }
                OutlinedTextField(note, { note = it.take(60) }, label = { Text("Note (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(day, kg!!, note.trim())
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
