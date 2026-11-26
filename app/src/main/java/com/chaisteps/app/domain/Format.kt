package com.chaisteps.app.domain

import com.chaisteps.app.data.DistanceUnit
import com.chaisteps.app.data.WeightUnit
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Format {
    const val KG_PER_LB = 0.45359237
    const val M_PER_MI = 1609.344

    fun kgToUnit(kg: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) kg else kg / KG_PER_LB
    fun unitToKg(value: Double, unit: WeightUnit) = if (unit == WeightUnit.KG) value else value * KG_PER_LB
    fun weightLabel(unit: WeightUnit) = if (unit == WeightUnit.KG) "kg" else "lb"

    fun weight(kg: Double, unit: WeightUnit, decimals: Int = 1): String =
        "${num(kgToUnit(kg, unit), decimals)} ${weightLabel(unit)}"

    fun signedWeight(kg: Double, unit: WeightUnit): String {
        val v = kgToUnit(kg, unit)
        val sign = if (v > 0.05) "+" else if (v < -0.05) "−" else ""
        return "$sign${num(abs(v), 1)} ${weightLabel(unit)}"
    }

    fun distanceLabel(unit: DistanceUnit) = if (unit == DistanceUnit.KM) "km" else "mi"

    fun distanceValue(m: Double, unit: DistanceUnit) = if (unit == DistanceUnit.KM) m / 1000 else m / M_PER_MI

    fun distance(m: Double, unit: DistanceUnit): String {
        if (unit == DistanceUnit.KM && m < 1000) return "${m.roundToInt()} m"
        val v = distanceValue(m, unit)
        return "${num(v, if (v < 100) 2 else 1)} ${distanceLabel(unit)}"
    }

    /** Pace per km or per mile as m:ss. */
    fun pace(secPerKm: Double?, unit: DistanceUnit): String {
        if (secPerKm == null || secPerKm.isNaN() || secPerKm.isInfinite() || secPerKm > 3600) return "–"
        val sec = if (unit == DistanceUnit.KM) secPerKm else secPerKm * M_PER_MI / 1000
        val total = sec.roundToInt()
        return String.format(Locale.US, "%d:%02d /%s", total / 60, total % 60, distanceLabel(unit))
    }

    fun speed(mps: Double, unit: DistanceUnit): String {
        val v = if (unit == DistanceUnit.KM) mps * 3.6 else mps * 3600 / M_PER_MI
        return "${num(v, 1)} ${if (unit == DistanceUnit.KM) "km/h" else "mph"}"
    }

    fun duration(millis: Long): String {
        val s = (millis / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%d:%02d", m, sec)
    }

    fun durationWords(millis: Long): String {
        val totalMin = millis / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun steps(n: Number): String = String.format(Locale.US, "%,d", n.toLong())

    fun num(v: Double, decimals: Int): String =
        if (decimals == 0) v.roundToInt().toString() else String.format(Locale.US, "%.${decimals}f", v)

    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val shortFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    fun day(epochDay: Long, today: Long): String = when (epochDay) {
        today -> "Today"
        today - 1 -> "Yesterday"
        else -> LocalDate.ofEpochDay(epochDay).let {
            if (it.year == LocalDate.ofEpochDay(today).year) it.format(dayFmt) else it.format(dayYearFmt)
        }
    }

    private val longFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())
    fun longDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(longFmt)

    fun shortDay(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(shortFmt)

    fun weekdayLetter(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault())
}
