package com.chaisteps.app.domain

import com.chaisteps.app.data.AppData
import com.chaisteps.app.data.ChaiEntry
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.data.WeightEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun Long.toEpochDay(zone: ZoneId): Long = Instant.ofEpochMilli(this).atZone(zone).toLocalDate().toEpochDay()

data class DayValue(val epochDay: Long, val value: Double)

data class WalkTotals(
    val count: Int,
    val distanceM: Double,
    val activeMillis: Long,
    val steps: Int,
    val kcal: Int,
) {
    val avgSpeedMps: Double get() = if (activeMillis > 0) distanceM / (activeMillis / 1000.0) else 0.0
}

class Stats(private val data: AppData, private val today: Long, private val zone: ZoneId) {

    // ---------- Steps ----------

    fun stepsOn(day: Long): Int = data.stepDays.firstOrNull { it.epochDay == day }?.total ?: 0

    fun stepSeries(days: Int): List<DayValue> =
        (days - 1 downTo 0).map { back -> (today - back).let { DayValue(it, stepsOn(it).toDouble()) } }

    fun averageSteps(days: Int, includeToday: Boolean = false): Int {
        val range = if (includeToday) (today - days + 1)..today else (today - days) until today
        val recorded = range.filter { d -> data.stepDays.any { it.epochDay == d } }
        if (recorded.isEmpty()) return 0
        return (recorded.sumOf { stepsOn(it) } / recorded.size.toDouble()).toInt()
    }

    fun bestStepDay(): DayValue? =
        data.stepDays.maxByOrNull { it.total }?.takeIf { it.total > 0 }?.let { DayValue(it.epochDay, it.total.toDouble()) }

    /** Consecutive days meeting the goal, ending today (today counts only once reached) or yesterday. */
    fun stepGoalStreak(): Int {
        val goal = data.profile.stepGoal
        var day = if (stepsOn(today) >= goal) today else today - 1
        var streak = 0
        while (stepsOn(day) >= goal && goal > 0) {
            streak++
            day--
        }
        return streak
    }

    fun totalSteps(): Long = data.stepDays.sumOf { it.total.toLong() }

    // ---------- Walks ----------

    fun walksBetween(fromDay: Long, toDay: Long): List<WalkSession> =
        data.walks.filter { it.startMillis.toEpochDay(zone) in fromDay..toDay }

    fun totals(walks: List<WalkSession>) = WalkTotals(
        count = walks.size,
        distanceM = walks.sumOf { it.distanceM },
        activeMillis = walks.sumOf { it.activeMillis },
        steps = walks.sumOf { it.steps },
        kcal = walks.sumOf { it.kcal },
    )

    fun weekTotals(): WalkTotals {
        val monday = LocalDate.ofEpochDay(today).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toEpochDay()
        return totals(walksBetween(monday, today))
    }

    fun monthTotals(): WalkTotals {
        val first = LocalDate.ofEpochDay(today).withDayOfMonth(1).toEpochDay()
        return totals(walksBetween(first, today))
    }

    fun allTimeTotals(): WalkTotals = totals(data.walks)

    fun longestWalk(): WalkSession? = data.walks.maxByOrNull { it.distanceM }?.takeIf { it.distanceM > 0 }

    /** Fastest average pace among walks of at least 500 m. */
    fun fastestWalk(): WalkSession? = data.walks.filter { it.distanceM >= 500 }.maxByOrNull { it.avgSpeedMps }

    fun walkDistanceSeries(days: Int): List<DayValue> = (days - 1 downTo 0).map { back ->
        val d = today - back
        DayValue(d, walksBetween(d, d).sumOf { it.distanceM })
    }

    /** Distance per week for the last [weeks] weeks (oldest first), weeks starting Monday. */
    fun weeklyWalkDistance(weeks: Int): List<DayValue> {
        val monday = LocalDate.ofEpochDay(today).let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }.toEpochDay()
        return (weeks - 1 downTo 0).map { back ->
            val start = monday - back * 7
            DayValue(start, walksBetween(start, start + 6).sumOf { it.distanceM })
        }
    }

    // ---------- Weight ----------

    fun latestWeight(): WeightEntry? = data.weights.maxByOrNull { it.epochDay }

    fun startWeightKg(): Double? = data.profile.startWeightKg ?: data.weights.minByOrNull { it.epochDay }?.kg

    fun weightSeries(sinceDay: Long?): List<DayValue> =
        data.weights.filter { sinceDay == null || it.epochDay >= sinceDay }
            .sortedBy { it.epochDay }
            .map { DayValue(it.epochDay, it.kg) }

    /** Change over the last [days] days, comparing the latest entry with the closest entry at or before the window start. */
    fun weightChange(days: Int): Double? {
        val latest = latestWeight() ?: return null
        val cutoff = latest.epochDay - days
        val before = data.weights.filter { it.epochDay <= cutoff }.maxByOrNull { it.epochDay }
            ?: data.weights.minByOrNull { it.epochDay }?.takeIf { it.id != latest.id }
            ?: return null
        return latest.kg - before.kg
    }

    /** Average weekly change in kg (negative = losing) via least-squares over the last [days] days. */
    fun weeklyTrendKg(days: Int = 28): Double? {
        val latest = latestWeight() ?: return null
        val pts = data.weights.filter { it.epochDay >= latest.epochDay - days }
        if (pts.size < 2) return null
        val xs = pts.map { it.epochDay.toDouble() }
        val ys = pts.map { it.kg }
        val mx = xs.average()
        val my = ys.average()
        val den = xs.sumOf { (it - mx) * (it - mx) }
        if (den == 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / den
        return slope * 7
    }

    /** Projected date to reach goal weight given the current trend, or null if not trending towards it. */
    fun projectedGoalDay(): Long? {
        val goal = data.profile.goalWeightKg ?: return null
        val latest = latestWeight() ?: return null
        val trend = weeklyTrendKg() ?: return null
        val remaining = goal - latest.kg
        if (remaining == 0.0) return latest.epochDay
        if (trend == 0.0 || (remaining < 0) != (trend < 0)) return null
        val weeks = remaining / trend
        if (weeks > 520) return null
        return latest.epochDay + (weeks * 7).toLong()
    }

    // ---------- Chai ----------

    fun chaiOn(day: Long): List<ChaiEntry> = data.chai.filter { it.timeMillis.toEpochDay(zone) == day }

    fun chaiSeries(days: Int): List<DayValue> =
        (days - 1 downTo 0).map { back -> (today - back).let { DayValue(it, chaiOn(it).size.toDouble()) } }

    fun averageChai(days: Int): Double {
        val first = data.chai.minOfOrNull { it.timeMillis.toEpochDay(zone) } ?: return 0.0
        val span = (today - maxOf(first, today - days + 1) + 1).coerceAtLeast(1)
        val count = data.chai.count { it.timeMillis.toEpochDay(zone) in (today - span + 1)..today }
        return count / span.toDouble()
    }

    /** Consecutive days (ending yesterday, plus today if within limit so far) at or under the chai limit. */
    fun chaiWithinLimitStreak(): Int {
        val limit = data.profile.chaiLimit
        val first = data.chai.minOfOrNull { it.timeMillis.toEpochDay(zone) } ?: return 0
        var day = today
        var streak = 0
        while (day >= first && chaiOn(day).size <= limit) {
            streak++
            day--
        }
        return streak
    }

    fun favoriteChai() = data.chai.groupingBy { it.type }.eachCount().maxByOrNull { it.value }?.key
}
