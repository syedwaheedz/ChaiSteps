package com.chaisteps.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chaisteps.app.domain.DayValue
import com.chaisteps.app.domain.Format
import com.chaisteps.app.domain.Stats
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.Nav
import com.chaisteps.app.ui.components.BarChart
import com.chaisteps.app.ui.components.SectionCard
import com.chaisteps.app.ui.components.StatRow
import com.chaisteps.app.ui.theme.Accents

@Composable
fun StatsScreen(vm: MainViewModel, nav: Nav) {
    val data by vm.data.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val stats = remember(data, today) { Stats(data, today, vm.zone) }
    val p = data.profile
    val unit = p.distanceUnit
    var days by rememberSaveable { mutableIntStateOf(7) }

    ScreenScaffold(title = "Stats") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 30, 90).forEach { d -> FilterChip(selected = days == d, onClick = { days = d }, label = { Text("$d days") }) }
        }

        // ---- Steps ----
        SectionCard(title = "Steps", accent = Accents.steps) {
            val series = stats.stepSeries(days)
            BarChart(series.map { it.value }, Accents.steps, labels = axisLabels(series), goal = p.stepGoal.toDouble(), description = "Steps per day")
            Spacer(Modifier.height(12.dp))
            val best = stats.bestStepDay()
            val metGoal = series.count { it.value >= p.stepGoal }
            StatRow(
                Triple("Daily avg", Format.steps(stats.averageSteps(days, includeToday = true)), "last $days days"),
                Triple("Goal met", "$metGoal / $days", "days"),
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                Triple("Best day", best?.let { Format.steps(it.value.toLong()) } ?: "–", best?.let { Format.shortDay(it.epochDay) }),
                Triple("Streak", stats.stepGoalStreak().let { if (it == 1) "1 day" else "$it days" }, "goal met"),
                Triple("All time", Format.steps(stats.totalSteps()), "steps"),
            )
        }

        // ---- Walking ----
        SectionCard(title = "Walking", accent = Accents.walk) {
            val weekly = stats.weeklyWalkDistance(if (days <= 7) 8 else if (days <= 30) 12 else 16)
            Text("Distance per week", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            BarChart(
                weekly.map { Format.distanceValue(it.value, unit) }, Accents.walk,
                labels = weekly.mapIndexed { i, d -> if (i % 4 == 0 || i == weekly.lastIndex) Format.shortDay(d.epochDay) else "" },
                description = "Walking distance per week",
            )
            Spacer(Modifier.height(12.dp))
            val period = stats.totals(stats.walksBetween(today - days + 1, today))
            val all = stats.allTimeTotals()
            StatRow(
                Triple("Walks", period.count.toString(), "last $days days"),
                Triple("Distance", Format.distance(period.distanceM, unit), "last $days days"),
                Triple("Time", Format.durationWords(period.activeMillis), "moving"),
            )
            Spacer(Modifier.height(8.dp))
            val longest = stats.longestWalk()
            val fastest = stats.fastestWalk()
            StatRow(
                Triple("Longest", longest?.let { Format.distance(it.distanceM, unit) } ?: "–", longest?.let { Format.shortDay(it.startMillis.let { m -> java.time.Instant.ofEpochMilli(m).atZone(vm.zone).toLocalDate().toEpochDay() }) }),
                Triple("Best pace", fastest?.let { Format.pace(it.paceSecPerKm, unit) } ?: "–", "walks ≥ 500 m"),
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                Triple("All-time distance", Format.distance(all.distanceM, unit), "${all.count} walks"),
                Triple("Energy", "${all.kcal} kcal", "all walks"),
            )
        }

        // ---- Weight ----
        SectionCard(title = "Weight", accent = Accents.weight) {
            val w = p.weightUnit
            StatRow(
                Triple("7 days", stats.weightChange(7)?.let { Format.signedWeight(it, w) } ?: "–", null),
                Triple("30 days", stats.weightChange(30)?.let { Format.signedWeight(it, w) } ?: "–", null),
                Triple("Since start", stats.latestWeight()?.let { l -> stats.startWeightKg()?.let { Format.signedWeight(l.kg - it, w) } } ?: "–", null),
            )
            Spacer(Modifier.height(8.dp))
            StatRow(
                Triple("Weekly trend", stats.weeklyTrendKg()?.let { Format.signedWeight(it, w) } ?: "–", "last 4 weeks"),
                Triple("Weigh-ins", data.weights.size.toString(), "total"),
            )
        }

        // ---- Chai ----
        SectionCard(title = "Chai", accent = Accents.chai) {
            val series = stats.chaiSeries(days)
            BarChart(series.map { it.value }, Accents.chai, height = 100.dp, labels = axisLabels(series), goal = p.chaiLimit.toDouble(), description = "Cups of chai per day")
            Spacer(Modifier.height(12.dp))
            val cups = series.sumOf { it.value }.toInt()
            val inPeriod = data.chai.filter { c -> series.any { it.epochDay == java.time.Instant.ofEpochMilli(c.timeMillis).atZone(vm.zone).toLocalDate().toEpochDay() } }
            StatRow(
                Triple("Cups", cups.toString(), "last $days days"),
                Triple("Per day", Format.num(cups / days.toDouble(), 1), "average"),
                Triple("Sugar", "${Format.num(inPeriod.sumOf { it.sugarGrams } / 1000.0, 2)} kg", "last $days days"),
            )
        }
    }
}

private fun axisLabels(series: List<DayValue>): List<String> = when {
    series.size <= 7 -> series.map { Format.weekdayLetter(it.epochDay) }
    else -> {
        val step = series.size / 4
        series.mapIndexed { i, d -> if (i % step == 0) Format.shortDay(d.epochDay) else "" }
    }
}
