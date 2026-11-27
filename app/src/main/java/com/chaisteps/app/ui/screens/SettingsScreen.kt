package com.chaisteps.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.BuildConfig
import com.chaisteps.app.data.ChaiType
import com.chaisteps.app.data.DistanceUnit
import com.chaisteps.app.data.WeightUnit
import com.chaisteps.app.domain.Format
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class EditField(val title: String, val initial: String, val suffix: String?, val decimal: Boolean, val apply: (Double) -> Boolean)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val p = data.profile
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<EditField?>(null) }
    var editName by rememberSaveable { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val wl = Format.weightLabel(p.weightUnit)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray()) }
                }
            }.isSuccess
            vm.showMessage(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
            }.getOrNull()
            if (text == null) vm.showMessage("Couldn't read that file") else pendingImport = text
        }
    }

    ScreenScaffold(title = "Settings", onBack = { nav.back() }) {
        SectionCard(title = "Profile") {
            SettingRow("Name", p.name.ifBlank { "Not set" }) { editName = true }
            SettingRow("Height", p.heightCm?.let { "${Format.num(it, 0)} cm" } ?: "Not set") {
                editing = EditField("Height", p.heightCm?.let { Format.num(it, 0) } ?: "", "cm", true) { v ->
                    (v in 80.0..250.0).also { ok -> if (ok) vm.updateProfile { it.copy(heightCm = v) } }
                }
            }
            SettingRow("Starting weight", p.startWeightKg?.let { Format.weight(it, p.weightUnit) } ?: "First weigh-in") {
                editing = EditField("Starting weight", p.startWeightKg?.let { Format.num(Format.kgToUnit(it, p.weightUnit), 1) } ?: "", wl, true) { v ->
                    val kg = Format.unitToKg(v, p.weightUnit)
                    (kg in 20.0..400.0).also { ok -> if (ok) vm.updateProfile { it.copy(startWeightKg = kg) } }
                }
            }
        }

        SectionCard(title = "Goals") {
            SettingRow("Goal weight", p.goalWeightKg?.let { Format.weight(it, p.weightUnit) } ?: "Not set") {
                editing = EditField("Goal weight", p.goalWeightKg?.let { Format.num(Format.kgToUnit(it, p.weightUnit), 1) } ?: "", wl, true) { v ->
                    val kg = Format.unitToKg(v, p.weightUnit)
                    (kg in 20.0..400.0).also { ok -> if (ok) vm.updateProfile { it.copy(goalWeightKg = kg) } }
                }
            }
            SettingRow("Daily steps", Format.steps(p.stepGoal)) {
                editing = EditField("Daily step goal", p.stepGoal.toString(), "steps", false) { v ->
                    (v in 500.0..100_000.0).also { ok -> if (ok) vm.updateProfile { it.copy(stepGoal = v.toInt()) } }
                }
            }
            SettingRow("Chai limit", "${p.chaiLimit} cups / day") {
                editing = EditField("Daily chai limit", p.chaiLimit.toString(), "cups", false) { v ->
                    (v in 0.0..20.0).also { ok -> if (ok) vm.updateProfile { it.copy(chaiLimit = v.toInt()) } }
                }
            }
        }

        SectionCard(title = "Chai preferences") {
            Text("Quick-add chai", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChaiType.entries.forEach { t ->
                    FilterChip(selected = p.favoriteChai == t, onClick = { vm.updateProfile { it.copy(favoriteChai = t) } }, label = { Text(t.label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Usual sugar", style = MaterialTheme.typography.labelLarge)
            SugarStepper(p.defaultSugarTsp) { v -> vm.updateProfile { it.copy(defaultSugarTsp = v) } }
        }

        SectionCard(title = "Units") {
            Text("Weight", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WeightUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(p.weightUnit == u, { vm.updateProfile { it.copy(weightUnit = u) } }, SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(if (u == WeightUnit.KG) "Kilograms" else "Pounds")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Distance", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                DistanceUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(p.distanceUnit == u, { vm.updateProfile { it.copy(distanceUnit = u) } }, SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(if (u == DistanceUnit.KM) "Kilometres" else "Miles")
                    }
                }
            }
        }

        SectionCard(title = "Backup") {
            Text(
                "Your data stays on this phone. Save a backup file to move it to a new phone or keep it safe.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch("chai-steps-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) { Text("Export") }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, modifier = Modifier.weight(1f)) { Text("Import") }
            }
        }

        SectionCard(title = "About") {
            Text("Chai & Steps ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
            Text("Maps © OpenStreetMap contributors / Google", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    editing?.let { field ->
        EditNumberDialog(field) { editing = null }
    }
    if (editName) {
        var name by rememberSaveable { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = { OutlinedTextField(name, { name = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.updateProfile { it.copy(name = name.trim()) }; editName = false }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel") } },
        )
    }
    pendingImport?.let { text ->
        ConfirmDialog(
            "Replace all data?",
            "Importing a backup replaces everything currently in the app.",
            "Import",
            onConfirm = {
                scope.launch {
                    val result = vm.importJson(text)
                    vm.showMessage(if (result.isSuccess) "Backup imported" else "That file isn't a Chai & Steps backup")
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EditNumberDialog(field: EditField, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(field.initial) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.title) },
        text = { NumberField(text, { text = it; error = false }, field.title, suffix = field.suffix, decimal = field.decimal, isError = error) },
        confirmButton = {
            TextButton(onClick = {
                val v = parseNumber(text)
                if (v != null && field.apply(v)) onDismiss() else error = true
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
