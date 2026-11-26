package com.chaisteps.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chaisteps.app.R
import com.chaisteps.app.data.Profile
import com.chaisteps.app.data.WeightUnit
import com.chaisteps.app.domain.Format
import com.chaisteps.app.ui.MainViewModel

@Composable
fun OnboardingScreen(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("") }
    var steps by rememberSaveable { mutableStateOf("10000") }
    var chai by rememberSaveable { mutableStateOf("3") }
    var unit by rememberSaveable { mutableStateOf(WeightUnit.KG) }

    val heightV = parseNumber(height)
    val weightKg = parseNumber(weight)?.let { Format.unitToKg(it, unit) }
    val goalKg = parseNumber(goal)?.let { Format.unitToKg(it, unit) }
    val heightOk = height.isBlank() || (heightV != null && heightV in 80.0..250.0)
    val weightOk = weight.isBlank() || (weightKg != null && weightKg in 20.0..400.0)
    val goalOk = goal.isBlank() || (goalKg != null && goalKg in 20.0..400.0)
    val stepsV = steps.toIntOrNull()
    val stepsOk = stepsV != null && stepsV in 500..100_000
    val chaiV = chai.toIntOrNull()
    val chaiOk = chaiV != null && chaiV in 0..20
    val valid = heightOk && weightOk && goalOk && stepsOk && chaiOk

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.size(112.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
                    .background(androidx.compose.ui.res.colorResource(R.color.ic_launcher_background)),
            ) { Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(112.dp)) }
            Text("Chai & Steps", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Track your weight-loss journey, steps, walks and daily chai — all on your phone, private to you.",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(name, { name = it.take(30) }, label = { Text("Your name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WeightUnit.entries.forEachIndexed { i, u ->
                    SegmentedButton(unit == u, { unit = u }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (u == WeightUnit.KG) "kg" else "lb") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(weight, { weight = it }, "Current weight", Modifier.weight(1f), suffix = Format.weightLabel(unit), isError = !weightOk)
                NumberField(goal, { goal = it }, "Goal weight", Modifier.weight(1f), suffix = Format.weightLabel(unit), isError = !goalOk)
            }
            NumberField(height, { height = it }, "Height", suffix = "cm", isError = !heightOk)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(steps, { steps = it }, "Daily steps goal", Modifier.weight(1f), decimal = false, isError = !stepsOk)
                NumberField(chai, { chai = it }, "Chai limit / day", Modifier.weight(1f), decimal = false, isError = !chaiOk)
            }
            Text("You can change all of this later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                enabled = valid,
                onClick = {
                    vm.completeOnboarding(
                        Profile(
                            name = name.trim(),
                            heightCm = heightV,
                            goalWeightKg = goalKg,
                            stepGoal = stepsV ?: 10_000,
                            chaiLimit = chaiV ?: 3,
                            weightUnit = unit,
                        ),
                        weightKg,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Get started") }
        }
    }
}
