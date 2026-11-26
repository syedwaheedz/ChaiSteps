package com.chaisteps.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.domain.Fitness
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.domain.toEpochDay
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.Permissions
import com.chaisteps.app.ui.Tab
import com.chaisteps.app.ui.components.BarChart
import com.chaisteps.app.ui.components.BigNumber
import com.chaisteps.app.ui.components.GoalBar
import com.chaisteps.app.ui.components.ProgressRing
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.components.StatTile
import com.chaisteps.app.ui.rememberPermissionRequest
import com.chaisteps.app.ui.theme.Accents
import java.time.LocalTime

@Composable
fun TodayScreen(vm: MainViewModel, nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val active by vm.activeWalk.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val stats = remember(data, today) { Stats(data, today, vm.zone) }
    val p = data.profile
    var showSteps by rememberSaveable { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }
    val stepsGranted = remember(permissionTick) { vm.stepsPermissionGranted() }
    val sensor = remember { vm.stepsSensorAvailable() }
    val requestSteps = rememberPermissionRequest { result ->
        permissionTick++
        if (result.values.any { it }) vm.onStepPermissionGranted()
        else Permissions.openAppSettings(context).also { vm.showMessage("Allow \"Physical activity\" in Permissions to count steps") }
    }

    val greeting = when (LocalTime.now().hour) {
        in 4..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    ScreenScaffold(
        title = if (p.name.isBlank()) greeting else "$greeting, ${p.name}",
        actions = { IconButton(onClick = nav::settings) { Icon(Icons.Filled.Settings, "Settings") } },
    ) {
        Text(Format.longDay(today),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // ---- Steps ----
        val steps = stats.stepsOn(today)
        SectionCard(title = "Steps", icon = Icons.AutoMirrored.Filled.DirectionsWalk, accent = Accents.steps, trailing = {
            IconButton(onClick = { showSteps = true }) { Icon(Icons.Filled.Edit, "Add or edit steps") }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(steps / p.stepGoal.coerceAtLeast(1).toFloat(), Accents.steps, size = 150.dp) {
                    BigNumber(Format.steps(steps), "of ${Format.steps(p.stepGoal)}")
                }
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    StatTile("Distance", Format.distance(Fitness.stepsToDistanceM(steps, p.heightCm), p.distanceUnit), Modifier.fillMaxWidth())
                    StatTile("Energy", "${Fitness.stepsKcal(steps, stats.latestWeight()?.kg)} kcal", Modifier.fillMaxWidth())
                    val streak = stats.stepGoalStreak()
                    StatTile("Goal streak", if (streak == 1) "1 day" else "$streak days", Modifier.fillMaxWidth())
                }
            }
            if (!sensor) {
                Spacer(Modifier.height(8.dp))
                Text("This phone has no step sensor. Add steps manually with the pencil.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!stepsGranted) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { requestSteps(Permissions.steps) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Allow step counting")
                }
            }
            Spacer(Modifier.height(12.dp))
            val week = stats.stepSeries(7)
            BarChart(
                week.map { it.value }, Accents.steps, height = 64.dp,
                labels = week.map { Format.weekdayLetter(it.epochDay) }, goal = p.stepGoal.toDouble(),
                description = "Steps over the last 7 days",
            )
        }

        // ---- Walk ----
        val walk = active
        SectionCard(
            title = if (walk != null) "Walk in progress" else "Walk",
            icon = Icons.Filled.DirectionsRun,
            accent = Accents.walk,
            modifier = Modifier.clickable { nav.tab(Tab.WALK) },
        ) {
            if (walk != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Time", Format.duration(walk.activeMillis), Modifier.weight(1f), accent = Accents.walk)
                    StatTile("Distance", Format.distance(walk.distanceM, p.distanceUnit), Modifier.weight(1f))
                    StatTile("Steps", Format.steps(walk.steps), Modifier.weight(1f))
                }
            } else {
                val last = data.walks.maxByOrNull { it.startMillis }
                if (last != null) {
                    Text(
                        "Last walk: ${Format.distance(last.distanceM, p.distanceUnit)} in ${Format.durationWords(last.activeMillis)} · " +
                            Format.day(last.startMillis.toEpochDay(vm.zone), today),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Button(
                    onClick = { nav.tab(Tab.WALK) },
                    colors = ButtonDefaults.buttonColors(containerColor = Accents.walk),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Start a walk")
                }
            }
        }

        // ---- Chai & weight ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val cups = stats.chaiOn(today).size
            SectionCard(Modifier.weight(1f).clickable { nav.tab(Tab.CHAI) }, title = "Chai", icon = Icons.Filled.EmojiFoodBeverage, accent = Accents.chai) {
                Text("$cups / ${p.chaiLimit}", style = MaterialTheme.typography.headlineMedium, color = if (cups > p.chaiLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Text(if (cups > p.chaiLimit) "Over today's limit" else "cups today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = {
                        vm.addChai(p.favoriteChai, p.defaultSugarTsp, com.chaisteps.app.data.CupSize.REGULAR)
                        vm.showMessage("${p.favoriteChai.label} added ☕")
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text(" 1 cup", textAlign = TextAlign.Center)
                }
            }
            SectionCard(Modifier.weight(1f).clickable { nav.tab(Tab.WEIGHT) }, title = "Weight", icon = Icons.Filled.MonitorWeight, accent = Accents.weight) {
                val latest = stats.latestWeight()
                if (latest == null) {
                    Text("–", style = MaterialTheme.typography.headlineMedium)
                    Text("Log your weight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(Format.weight(latest.kg, p.weightUnit), style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                    val goal = p.goalWeightKg
                    val start = stats.startWeightKg()
                    Text(
                        when {
                            goal != null -> {
                                val left = latest.kg - goal
                                if (kotlin.math.abs(left) < 0.05) "Goal reached! 🎉" else "${Format.weight(kotlin.math.abs(left), p.weightUnit)} to go"
                            }
                            start != null -> Format.signedWeight(latest.kg - start, p.weightUnit) + " since start"
                            else -> Format.day(latest.epochDay, today)
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    Fitness.goalProgress(start, latest.kg, goal)?.let { GoalBar(it, Accents.weight) }
                }
            }
        }
    }

    if (showSteps) {
        StepsDialog(
            today = today,
            currentTotal = { stats.stepsOn(it) },
            onAdd = vm::addManualSteps,
            onSet = vm::setDayTotal,
            onDismiss = { showSteps = false },
        )
    }
}
