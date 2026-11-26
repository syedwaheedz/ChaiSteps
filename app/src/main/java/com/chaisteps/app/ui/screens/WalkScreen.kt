package com.chaisteps.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.data.DistanceUnit
import com.chaisteps.app.data.Route
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.domain.toEpochDay
import com.chaisteps.app.tracking.ActiveWalk
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.Permissions
import com.chaisteps.app.ui.components.EmptyHint
import com.chaisteps.app.ui.components.Metric
import com.chaisteps.app.ui.components.RouteMap
import com.chaisteps.app.ui.components.RouteSketch
import com.chaisteps.app.ui.components.RowDivider
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.components.StatRow
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
            title = { Text("Nice walk!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    StatRow(
                        Triple("Distance", Format.distance(session.distanceM, unit), null),
                        Triple("Time", Format.duration(session.activeMillis), null),
                    )
                    StatRow(
                        Triple("Pace", Format.pace(session.paceSecPerKm, unit), null),
                        Triple("Steps", Format.steps(session.steps), null),
                    )
                    Text("About ${session.kcal} kcal burned", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        SectionCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Outdoor walk", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tracks your route, distance, pace and steps with GPS — even with the screen off.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
                )
                FilledIconButton(
                    onClick = onStart,
                    modifier = Modifier.size(96.dp).shadow(10.dp, CircleShape, ambientColor = Accents.walk, spotColor = Accents.walk)
                        .semantics { contentDescription = "Start walk" },
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accents.walk, contentColor = Color.White),
                ) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(48.dp)) }
                Text("Start", style = MaterialTheme.typography.titleMedium, color = Accents.walk, modifier = Modifier.padding(top = 10.dp))
            }
        }

        val week = stats.weekTotals()
        val month = stats.monthTotals()
        SectionCard(title = "This week", accent = Accents.walk) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Distance", Format.num(Format.distanceValue(week.distanceM, unit), 1), Modifier.weight(1f), unit = Format.distanceLabel(unit), big = true)
                Metric("Walks", week.count.toString(), Modifier.weight(0.7f), big = true)
                Metric("Time", Format.durationWords(week.activeMillis), Modifier.weight(1f), big = true)
            }
            Spacer(Modifier.height(14.dp))
            StatRow(
                Triple("This month", Format.distance(month.distanceM, unit), "${month.count} walks"),
                Triple("Avg speed", Format.speed(week.avgSpeedMps, unit), "this week"),
            )
        }

        Text("History", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        if (walks.isEmpty()) {
            EmptyHint("No walks yet. Your first one will appear here.")
        } else {
            SectionCard {
                walks.sortedByDescending { it.startMillis }.forEachIndexed { i, w ->
                    if (i > 0) RowDivider()
                    WalkRow(vm, w, today, unit, vm.zone) { nav.walkDetail(w.id) }
                }
            }
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

fun formatTime(millis: Long, zone: ZoneId): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFmt)

@Composable
fun WalkRow(vm: MainViewModel, w: WalkSession, today: Long, unit: DistanceUnit, zone: ZoneId, onClick: () -> Unit) {
    var route by remember(w.id) { mutableStateOf<Route?>(null) }
    LaunchedEffect(w.id) { route = vm.loadRoute(w.id) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RouteSketch(route?.segments.orEmpty(), Modifier.size(56.dp).clip(MaterialTheme.shapes.small), strokeWidth = 2.5f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(Format.distance(w.distanceM, unit), style = MaterialTheme.typography.titleMedium)
            Text(
                "${Format.durationWords(w.activeMillis)} · ${Format.pace(w.paceSecPerKm, unit)} · ${Format.steps(w.steps)} steps",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )
            Text("${Format.day(w.startMillis.toEpochDay(zone), today)} · ${formatTime(w.startMillis, zone)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
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
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            // Full-bleed live map.
            Box(Modifier.fillMaxWidth().weight(1f)) {
                RouteMap(walk.segments, Modifier.fillMaxSize(), follow = true)
                Row(
                    Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp)
                        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (walk.hasFix) Icons.Filled.GpsFixed else Icons.Filled.GpsNotFixed, null,
                        tint = if (walk.hasFix) Accents.steps else Accents.danger, modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            walk.lastAccuracyM == null -> "Searching for GPS…"
                            walk.lastAccuracyM > 30 -> "Weak GPS · ±${walk.lastAccuracyM.toInt()} m"
                            else -> "GPS · ±${walk.lastAccuracyM.toInt()} m"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            // Stats sheet.
            Column(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (walk.paused) "PAUSED" else "WALKING",
                    style = MaterialTheme.typography.labelLarge, color = if (walk.paused) MaterialTheme.colorScheme.onSurfaceVariant else Accents.walk,
                )
                Text(
                    Format.duration(walk.activeMillis),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 64.sp),
                    color = if (walk.paused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Distance", Format.num(Format.distanceValue(walk.distanceM, unit), 2), Modifier.weight(1f), unit = Format.distanceLabel(unit), big = true)
                    Metric("Pace", Format.pace(walk.paceSecPerKm, unit).substringBefore(" "), Modifier.weight(1f), unit = "/${Format.distanceLabel(unit)}", big = true)
                    Metric("Steps", Format.steps(walk.steps), Modifier.weight(1f), big = true)
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundControl(
                        icon = if (walk.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        label = if (walk.paused) "Resume" else "Pause",
                        container = if (walk.paused) Accents.steps else MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = if (walk.paused) Color.White else MaterialTheme.colorScheme.onSurface,
                        onClick = if (walk.paused) onResume else onPause,
                    )
                    RoundControl(Icons.Filled.Stop, "Finish", Accents.danger, Color.White) { confirmFinish = true }
                }
                Spacer(Modifier.height(8.dp))
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
private fun RoundControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, container: Color, content: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(76.dp).semantics { contentDescription = label },
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
        ) { Icon(icon, null, Modifier.size(36.dp)) }
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun WalkDetailScreen(vm: MainViewModel, nav: Nav, id: String) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val walk = data.walks.firstOrNull { it.id == id }
    val unit = data.profile.distanceUnit
    var route by remember(id) { mutableStateOf<Route?>(null) }
    LaunchedEffect(id) { route = vm.loadRoute(id) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(title = "Walk details", onBack = { nav.back() }) {
        if (walk == null) {
            EmptyHint("This walk was deleted.")
            return@ScreenScaffold
        }
        Text(
            "${Format.day(walk.startMillis.toEpochDay(vm.zone), today)} · ${formatTime(walk.startMillis, vm.zone)} – ${formatTime(walk.endMillis, vm.zone)}",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val r = route
        if (r != null && r.allPoints.isNotEmpty()) {
            RouteMap(r.segments, Modifier.fillMaxWidth().height(320.dp).clip(MaterialTheme.shapes.large))
        } else if (r != null) {
            EmptyHint("No GPS route was recorded for this walk.")
        }
        if (walk.recovered) {
            Text("Recovered after the app was interrupted; time is counted up to the last save.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("Distance", Format.num(Format.distanceValue(walk.distanceM, unit), 2), Modifier.weight(1f), unit = Format.distanceLabel(unit), big = true, accent = Accents.walk)
                Metric("Moving time", Format.duration(walk.activeMillis), Modifier.weight(1f), big = true)
            }
            Spacer(Modifier.height(16.dp))
            StatRow(
                Triple("Avg pace", Format.pace(walk.paceSecPerKm, unit), null),
                Triple("Avg speed", Format.speed(walk.avgSpeedMps, unit), null),
            )
            Spacer(Modifier.height(16.dp))
            StatRow(
                Triple("Steps", Format.steps(walk.steps), null),
                Triple("Energy", "${walk.kcal} kcal", null),
            )
            Spacer(Modifier.height(16.dp))
            StatRow(
                Triple("Top speed", Format.speed(walk.maxSpeedMps, unit), null),
                Triple("Climb", "${walk.elevationGainM.toInt()} m", null),
            )
            val total = walk.endMillis - walk.startMillis
            if (total - walk.activeMillis > 60_000) {
                Spacer(Modifier.height(16.dp))
                Metric("Paused", Format.durationWords(total - walk.activeMillis))
            }
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
