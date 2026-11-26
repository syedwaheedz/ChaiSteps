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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.data.DistanceUnit
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.domain.toEpochDay
import com.chaisteps.app.tracking.ActiveWalk
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.Permissions
import com.chaisteps.app.ui.components.EmptyHint
import com.chaisteps.app.ui.components.RouteMap
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.components.StatRow
import com.chaisteps.app.ui.components.StatTile
import com.chaisteps.app.ui.rememberPermissionRequest
import com.chaisteps.app.ui.theme.Accents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WalkScreen(vm: MainViewModel, nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val active by vm.activeWalk.collectAsStateWithLifecycle()
    val finished by vm.lastFinishedWalk.collectAsStateWithLifecycle()
    val unit = data.profile.distanceUnit
    val context = LocalContext.current

    val request = rememberPermissionRequest { result ->
        val location = result[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (result[android.Manifest.permission.ACTIVITY_RECOGNITION] == true) vm.onStepPermissionGranted()
        if (location) {
            vm.startWalk()
        } else {
            vm.showMessage("Location permission is needed to record your route")
            Permissions.openAppSettings(context)
        }
    }
    val start = {
        if (!Permissions.locationEnabled(context)) {
            vm.showMessage("Turn on Location to record your route")
            Permissions.openLocationSettings(context)
        } else {
            request(Permissions.walk)
        }
    }

    val walk = active
    if (walk != null) {
        ActiveWalkContent(walk, unit, onPause = vm::pauseWalk, onResume = vm::resumeWalk, onFinish = vm::finishWalk, onDiscard = vm::discardWalk)
    } else {
        WalkHome(vm, nav, data.walks, today, unit, onStart = start)
    }

    finished?.let { session ->
        AlertDialog(
            onDismissRequest = vm::consumeFinishedWalk,
            title = { Text("Great walk! 🎉") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow(
                        Triple("Distance", Format.distance(session.distanceM, unit), null),
                        Triple("Time", Format.duration(session.activeMillis), null),
                    )
                    StatRow(
                        Triple("Pace", Format.pace(session.paceSecPerKm, unit), null),
                        Triple("Steps", Format.steps(session.steps), null),
                    )
                    Text("About ${session.kcal} kcal burned", style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.consumeFinishedWalk()
                    nav.walkDetail(session.id)
                }) { Text("View route") }
            },
            dismissButton = { TextButton(onClick = vm::consumeFinishedWalk) { Text("Done") } },
        )
    }
}

@Composable
private fun WalkHome(vm: MainViewModel, nav: Nav, walks: List<WalkSession>, today: Long, unit: DistanceUnit, onStart: () -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val stats = remember(data, today) { Stats(data, today, vm.zone) }
    ScreenScaffold(title = "Walk") {
        SectionCard(title = "Ready to walk?", icon = Icons.AutoMirrored.Filled.DirectionsWalk, accent = Accents.walk) {
            Text(
                "Records your GPS route, distance, pace and steps. Keeps going with the screen off.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = Accents.walk),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text("Start walk", fontSize = 18.sp)
            }
        }

        val week = stats.weekTotals()
        val month = stats.monthTotals()
        SectionCard(title = "This week") {
            StatRow(
                Triple("Walks", week.count.toString(), null),
                Triple("Distance", Format.distance(week.distanceM, unit), null),
                Triple("Time", Format.durationWords(week.activeMillis), null),
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                Triple("This month", Format.distance(month.distanceM, unit), "${month.count} walks"),
                Triple("Avg speed", Format.speed(week.avgSpeedMps, unit), "this week"),
            )
        }

        Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
        if (walks.isEmpty()) {
            EmptyHint("No walks yet. Your first one will appear here.")
        } else {
            SectionCard {
                walks.sortedByDescending { it.startMillis }.forEachIndexed { i, w ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    WalkRow(w, today, unit, vm.zone) { nav.walkDetail(w.id) }
                }
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun formatTime(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFmt)

@Composable
fun WalkRow(w: WalkSession, today: Long, unit: DistanceUnit, zone: ZoneId, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${Format.day(w.startMillis.toEpochDay(zone), today)} · ${formatTime(w.startMillis, zone)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Format.distance(w.distanceM, unit), style = MaterialTheme.typography.titleMedium)
            Text(
                "${Format.durationWords(w.activeMillis)} · ${Format.pace(w.paceSecPerKm, unit)} · ${Format.steps(w.steps)} steps",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActiveWalkContent(
    walk: ActiveWalk,
    unit: DistanceUnit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    onDiscard: () -> Unit,
) {
    var confirmFinish by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(title = if (walk.paused) "Paused" else "Walking") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Format.duration(walk.activeMillis),
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                color = if (walk.paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (walk.hasFix) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed, null,
                    tint = if (walk.hasFix) Accents.steps else MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        walk.lastAccuracyM == null -> "Searching for GPS…"
                        walk.lastAccuracyM > 30 -> "Weak GPS (±${walk.lastAccuracyM.toInt()} m)"
                        else -> "GPS ±${walk.lastAccuracyM.toInt()} m"
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        StatRow(
            Triple("Distance", Format.distance(walk.distanceM, unit), null),
            Triple("Pace", Format.pace(walk.paceSecPerKm, unit), null),
            accent = Accents.walk,
        )
        StatRow(
            Triple("Steps", Format.steps(walk.steps), null),
            Triple("Speed", Format.speed(walk.currentSpeedMps, unit), null),
        )
        RouteMap(walk.segments, Modifier.fillMaxWidth().height(280.dp), follow = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (walk.paused) {
                Button(onClick = onResume, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Accents.steps)) {
                    Icon(Icons.Filled.PlayArrow, null); Text("  Resume")
                }
            } else {
                OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.Pause, null); Text("  Pause")
                }
            }
            Button(onClick = { confirmFinish = true }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)) {
                Icon(Icons.Filled.Stop, null); Text("  Finish")
            }
        }
    }
    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text("Finish walk?") },
            text = { Text("${Format.distance(walk.distanceM, unit)} in ${Format.duration(walk.activeMillis)}") },
            confirmButton = { TextButton(onClick = { confirmFinish = false; onFinish() }) { Text("Save walk") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmFinish = false; onDiscard() }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { confirmFinish = false }) { Text("Keep going") }
                }
            },
        )
    }
}

@Composable
fun WalkDetailScreen(vm: MainViewModel, nav: Nav, id: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val walk = data.walks.firstOrNull { it.id == id }
    val unit = data.profile.distanceUnit
    var route by remember(id) { mutableStateOf<com.chaisteps.app.data.Route?>(null) }
    androidx.compose.runtime.LaunchedEffect(id) { route = vm.loadRoute(id) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "Walk details", onBack = { nav.back() }) {
        if (walk == null) {
            EmptyHint("This walk was deleted.")
            return@ScreenScaffold
        }
        Text(
            "${Format.day(walk.startMillis.toEpochDay(vm.zone), today)} · ${formatTime(walk.startMillis, vm.zone)} – ${formatTime(walk.endMillis, vm.zone)}",
            style = MaterialTheme.typography.titleMedium,
        )
        if (walk.recovered) {
            Text("Recovered after the app was interrupted; time is counted up to the last save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val r = route
        if (r != null && r.allPoints.isNotEmpty()) {
            RouteMap(r.segments, Modifier.fillMaxWidth().height(300.dp))
        } else if (r != null) {
            EmptyHint("No GPS route was recorded for this walk.")
        }
        StatRow(
            Triple("Distance", Format.distance(walk.distanceM, unit), null),
            Triple("Moving time", Format.duration(walk.activeMillis), null),
            accent = Accents.walk,
        )
        StatRow(
            Triple("Avg pace", Format.pace(walk.paceSecPerKm, unit), null),
            Triple("Avg speed", Format.speed(walk.avgSpeedMps, unit), null),
        )
        StatRow(
            Triple("Steps", Format.steps(walk.steps), null),
            Triple("Energy", "${walk.kcal} kcal", null),
        )
        StatRow(
            Triple("Top speed", Format.speed(walk.maxSpeedMps, unit), null),
            Triple("Climb", "${walk.elevationGainM.toInt()} m", null),
        )
        val total = walk.endMillis - walk.startMillis
        if (total - walk.activeMillis > 60_000) {
            StatTile("Paused", Format.durationWords(total - walk.activeMillis), Modifier.fillMaxWidth())
        }
        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete walk", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete && walk != null) {
        ConfirmDialog("Delete walk?", "This removes the walk and its route.", "Delete", onConfirm = {
            vm.deleteWalk(walk.id)
            nav.back()
        }, onDismiss = { confirmDelete = false })
    }
}
