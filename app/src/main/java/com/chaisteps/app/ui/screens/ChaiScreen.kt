package com.chaisteps.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.EmojiFoodBeverage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.data.ChaiEntry
import com.chaisteps.app.data.ChaiType
import com.chaisteps.app.data.CupSize
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.components.BarChart
import com.chaisteps.app.ui.components.EmptyHint
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.components.StatRow
import com.chaisteps.app.ui.theme.Accents

@Composable
fun ChaiScreen(vm: MainViewModel, @Suppress("UNUSED_PARAMETER") nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val stats = remember(data, today) { Stats(data, today, vm.zone) }
    val p = data.profile
    var showCustom by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ChaiEntry?>(null) }
    val todays = stats.chaiOn(today)
    val count = todays.size
    val over = count > p.chaiLimit

    ScreenScaffold(title = "Chai") {
        SectionCard(title = "Today", accent = Accents.chai) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$count", style = MaterialTheme.typography.displaySmall, color = if (over) MaterialTheme.colorScheme.error else Accents.chai)
                Text(
                    " of ${p.chaiLimit} cup${if (p.chaiLimit == 1) "" else "s"}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            CupRow(count, p.chaiLimit)
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    over -> "That's ${count - p.chaiLimit} over your limit. Maybe water next? 💧"
                    count == p.chaiLimit -> "You've reached today's limit."
                    count == 0 -> "No chai yet today."
                    else -> "${p.chaiLimit - count} more within your limit."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            StatRow(
                Triple("Caffeine", "${todays.sumOf { it.caffeineMg }} mg", null),
                Triple("Sugar", "${Format.num(todays.sumOf { it.sugarGrams }, 0)} g", null),
                Triple("Energy", "${todays.sumOf { it.kcal }} kcal", null),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        vm.addChai(p.favoriteChai, p.defaultSugarTsp, CupSize.REGULAR)
                        vm.showMessage("${p.favoriteChai.label} added ☕")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Accents.chai),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.Add, null)
                    Text(" ${p.favoriteChai.label}", maxLines = 1)
                }
                OutlinedButton(onClick = { showCustom = true }, modifier = Modifier.height(52.dp)) {
                    Icon(Icons.Filled.Tune, null)
                    Text(" Other")
                }
            }
        }

        if (todays.isNotEmpty()) {
            SectionCard(title = "Today's cups") {
                todays.asReversed().forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.type.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${formatTime(c.timeMillis, vm.zone)} · ${c.size.label} · ${sugarText(c.sugarTsp)} · ${c.kcal} kcal",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { toDelete = c }) { Icon(Icons.Filled.DeleteOutline, "Remove cup", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }

        SectionCard(title = "Last 7 days") {
            val week = stats.chaiSeries(7)
            BarChart(
                week.map { it.value }, Accents.chai, height = 110.dp,
                labels = week.map { Format.weekdayLetter(it.epochDay) }, goal = p.chaiLimit.toDouble(),
                description = "Cups of chai over the last 7 days",
            )
            Spacer(Modifier.height(12.dp))
            val streak = stats.chaiWithinLimitStreak()
            StatRow(
                Triple("Daily average", Format.num(stats.averageChai(30), 1), "last 30 days"),
                Triple("Within limit", "$streak day${if (streak == 1) "" else "s"}", "streak"),
                Triple("Favourite", stats.favoriteChai()?.label ?: "–", null),
            )
        }

        if (data.chai.isEmpty()) EmptyHint("Tap + to log your first cup.")
    }

    if (showCustom) {
        ChaiDialog(
            initialType = p.favoriteChai,
            initialSugar = p.defaultSugarTsp,
            onAdd = { type, sugar, size ->
                vm.addChai(type, sugar, size)
                vm.showMessage("${type.label} added ☕")
            },
            onDismiss = { showCustom = false },
        )
    }
    toDelete?.let { c ->
        ConfirmDialog("Remove this cup?", "${c.type.label} at ${formatTime(c.timeMillis, vm.zone)}", "Remove",
            onConfirm = { vm.deleteChai(c.id) }, onDismiss = { toDelete = null })
    }
}

fun sugarText(tsp: Double) = when (tsp) {
    0.0 -> "no sugar"
    1.0 -> "1 tsp sugar"
    else -> "${Format.num(tsp, if (tsp % 1.0 == 0.0) 0 else 1)} tsp sugar"
}

@Composable
private fun CupRow(count: Int, limit: Int) {
    val total = maxOf(count, limit).coerceAtMost(12)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(total) { i ->
            val filled = i < count
            Icon(
                if (filled) Icons.Filled.EmojiFoodBeverage else Icons.Outlined.EmojiFoodBeverage,
                contentDescription = null,
                tint = when {
                    filled && i >= limit -> MaterialTheme.colorScheme.error
                    filled -> Accents.chai
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChaiDialog(initialType: ChaiType, initialSugar: Double, onAdd: (ChaiType, Double, CupSize) -> Unit, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(initialType) }
    var sugar by rememberSaveable { mutableDoubleStateOf(initialSugar) }
    var size by rememberSaveable { mutableStateOf(CupSize.REGULAR) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add chai") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChaiType.entries.forEach { t -> FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }) }
                }
                Text("Cup", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CupSize.entries.forEach { s -> FilterChip(selected = size == s, onClick = { size = s }, label = { Text("${s.label} ${s.ml}ml") }) }
                }
                Text("Sugar", style = MaterialTheme.typography.labelLarge)
                SugarStepper(sugar) { sugar = it }
                val preview = ChaiEntry("", 0, type, sugar, size)
                Text("≈ ${preview.kcal} kcal · ${preview.caffeineMg} mg caffeine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(type, sugar, size); onDismiss() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SugarStepper(value: Double, onChange: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedIconButton(onClick = { onChange((value - 0.5).coerceAtLeast(0.0)) }, enabled = value > 0) { Icon(Icons.Filled.Remove, "Less sugar") }
        Text(sugarText(value), Modifier.width(120.dp), style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        OutlinedIconButton(onClick = { onChange((value + 0.5).coerceAtMost(5.0)) }, enabled = value < 5) { Icon(Icons.Filled.Add, "More sugar") }
    }
}
