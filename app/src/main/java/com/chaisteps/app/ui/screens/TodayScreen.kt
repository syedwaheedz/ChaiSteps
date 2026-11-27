package com.chaisteps.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.data.CupSize
import com.chaisteps.app.data.Route
import com.chaisteps.app.domain.Fitness
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.domain.toEpochDay
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.Permissions
import com.chaisteps.app.ui.Tab
import com.chaisteps.app.ui.components.ActivityRings
import com.chaisteps.app.ui.components.BarChart
import com.chaisteps.app.ui.components.GoalBar
import com.chaisteps.app.ui.components.Metric
import com.chaisteps.app.ui.components.Ring
import com.chaisteps.app.ui.components.RingLegend
import com.chaisteps.app.ui.components.RouteSketch
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.rememberPermissionRequest
import com.chaisteps.app.ui.theme.Accents
import java.time.LocalTime
import kotlin.math.abs

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

    val steps = stats.stepsOn(today)
    val minutes = stats.walkMinutesOn(today) + ((active?.activeMillis ?: 0) / 60_000).toInt()
    val cups = stats.chaiOn(today).size
    val latest = stats.latestWeight()

    ScreenScaffold(
        title = if (p.name.isBlank()) greeting else "Hi, ${p.name}",
        subtitle = Format.longDay(today),
        actions = { IconButton(onClick = nav::settings) { Icon(Icons.Outlined.Settings, "Settings") } },
    ) {
        // ---- Rings ----
        SectionCard(title = "Activity", trailing = {
            IconButton(onClick = { showSteps = true }) { Icon(Icons.Filled.Edit, "Add or edit steps", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivityRings(
                    listOf(
                        Ring(steps / p.stepGoal.coerceAtLeast(1).toFloat(), Accents.steps),
                        Ring(minutes / p.activeMinutesGoal.coerceAtLeast(1).toFloat(), Accents.walk),
                        Ring(cups / p.chaiLimit.coerceAtLeast(1).toFloat(), if (cups > p.chaiLimit) Accents.danger else Accents.chai),
                    ),
                    size = 150.dp, stroke = 16.dp,
                )
                Spacer(Modifier.width(20.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                    RingLegend(Accents.steps, "Steps", Format.steps(steps), "/ ${Format.steps(p.stepGoal)}")
                    RingLegend(Accents.walk, "Active", "$minutes", "/ ${p.activeMinutesGoal} min")
                    RingLegend(if (cups > p.chaiLimit) Accents.danger else Accents.chai, "Chai", "$cups", "/ ${p.chaiLimit} cups")
                }
            }
            if (!sensor) {
                Spacer(Modifier.height(12.dp))
                Text("This phone has no step sensor. Tap the pencil to add steps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!stepsGranted) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { requestSteps(Permissions.steps) }) { Text("Allow step counting", color = Accents.steps) }
            }
        }

        // ---- Quick metrics ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(Modifier.weight(1f)) {
                Metric("Distance", Format.num(Format.distanceValue(Fitness.stepsToDistanceM(steps, p.heightCm), p.distanceUnit), 2), unit = Format.distanceLabel(p.distanceUnit), big = true)
            }
            SectionCard(Modifier.weight(1f)) {
                val kcal = Fitness.stepsKcal(steps, latest?.kg)
                Metric("Energy", "$kcal", unit = "kcal", big = true)
            }
        }

        // ---- Walk ----
        val walk = active
        if (walk != null) {
            SectionCard(title = "Walk in progress", accent = Accents.walk, onClick = { nav.tab(Tab.WALK) }, trailing = {
                Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Time", Format.duration(walk.activeMillis), Modifier.weight(1f), accent = Accents.walk)
                    Metric("Distance", Format.distance(walk.distanceM, p.distanceUnit), Modifier.weight(1f))
                    Metric("Steps", Format.steps(walk.steps), Modifier.weight(1f))
                }
            }
        } else {
            Button(
                onClick = { nav.tab(Tab.WALK) },
                colors = ButtonDefaults.buttonColors(containerColor = Accents.walk, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start a walk", style = MaterialTheme.typography.titleMedium)
            }
            data.walks.maxByOrNull { it.startMillis }?.let { last ->
                var route by remember(last.id) { mutableStateOf<Route?>(null) }
                LaunchedEffect(last.id) { route = vm.loadRoute(last.id) }
                SectionCard(title = "Last walk", accent = Accents.walk, onClick = { nav.walkDetail(last.id) }, trailing = {
                    Text(Format.day(last.startMillis.toEpochDay(vm.zone), today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RouteSketch(route?.segments.orEmpty(), Modifier.size(84.dp).clip(MaterialTheme.shapes.medium), strokeWidth = 3f)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Metric("Distance", Format.num(Format.distanceValue(last.distanceM, p.distanceUnit), 2), unit = Format.distanceLabel(p.distanceUnit), big = true)
                            Text(
                                "${Format.durationWords(last.activeMillis)} · ${Format.pace(last.paceSecPerKm, p.distanceUnit)}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---- Chai & weight ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(Modifier.weight(1f), title = "Chai", accent = Accents.chai, onClick = { nav.tab(Tab.CHAI) }) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Metric("Today", "$cups", Modifier.weight(1f), unit = "/ ${p.chaiLimit}", big = true, accent = if (cups > p.chaiLimit) Accents.danger else null)
                    FilledIconButton(
                        onClick = {
                            vm.addChai(p.favoriteChai, p.defaultSugarTsp, CupSize.REGULAR)
                            vm.showMessage("${p.favoriteChai.label} added")
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accents.chai, contentColor = Color.White),
                    ) { Icon(Icons.Filled.Add, "Add a cup of chai") }
                }
            }
            SectionCard(Modifier.weight(1f), title = "Weight", accent = Accents.weight, onClick = { nav.tab(Tab.WEIGHT) }) {
                if (latest == null) {
                    Metric("Latest", "–", big = true)
                    Text("Log your weight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Metric("Latest", Format.num(Format.kgToUnit(latest.kg, p.weightUnit), 1), unit = Format.weightLabel(p.weightUnit), big = true)
                    val goal = p.goalWeightKg
                    val start = stats.startWeightKg()
                    Spacer(Modifier.height(6.dp))
                    Fitness.goalProgress(start, latest.kg, goal)?.let { GoalBar(it, Accents.weight) }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            goal != null && abs(latest.kg - goal) < 0.05 -> "Goal reached!"
                            goal != null -> "${Format.weight(abs(latest.kg - goal), p.weightUnit)} to go"
                            start != null -> Format.signedWeight(latest.kg - start, p.weightUnit) + " since start"
                            else -> Format.day(latest.epochDay, today)
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    )
                }
            }
        }

        // ---- Week ----
        SectionCard(title = "Steps this week", accent = Accents.steps, onClick = { nav.tab(Tab.STATS) }, trailing = {
            Text("avg ${Format.steps(stats.averageSteps(7))}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }) {
            val week = stats.stepSeries(7)
            BarChart(
                week.map { it.value }, Accents.steps, height = 96.dp,
                labels = week.map { Format.weekdayLetter(it.epochDay) }, goal = p.stepGoal.toDouble(),
                description = "Steps over the last 7 days",
            )
            val streak = stats.stepGoalStreak()
            if (streak > 0) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(8.dp).clip(CircleShape).background(Accents.steps))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Step goal met ${if (streak == 1) "1 day" else "$streak days"} in a row",
                        style = MaterialTheme.typography.bodyMedium,
                    )
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
            manualSteps = { day -> data.stepDays.firstOrNull { it.epochDay == day }?.manualSteps ?: 0 },
            onUndoManual = { day ->
                vm.clearManualSteps(day)
                vm.showMessage("Manual steps removed")
            },
        )
    }
}
