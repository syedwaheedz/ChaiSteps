package com.chaisteps.app.domain

import com.chaisteps.app.data.RoutePoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanceM(a: RoutePoint, b: RoutePoint) = distanceM(a.lat, a.lon, b.lat, b.lon)

    fun segmentLengthM(points: List<RoutePoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> distanceM(a, b) }
}

/**
 * Decides which GPS fixes become part of a walking route. Rejects inaccurate fixes, GPS
 * "teleports" (speeds no walker reaches) and jitter while standing still.
 */
class RouteFilter(
    private val maxAccuracyM: Float = 30f,
    private val maxSpeedMps: Double = 9.0,
    private val minMoveM: Double = 3.0,
) {
    sealed interface Decision {
        data object Reject : Decision
        /** First point of a segment. */
        data object Start : Decision
        data class Accept(val distanceM: Double, val speedMps: Double) : Decision
    }

    fun evaluate(last: RoutePoint?, fix: RoutePoint): Decision {
        if (fix.accuracyM > maxAccuracyM) return Decision.Reject
        if (last == null) return Decision.Start
        val dt = (fix.timeMillis - last.timeMillis) / 1000.0
        if (dt <= 0) return Decision.Reject
        val d = Geo.distanceM(last, fix)
        // Movement smaller than the fixes' combined uncertainty is jitter.
        val threshold = maxOf(minMoveM, (fix.accuracyM + last.accuracyM) * 0.25)
        if (d < threshold) return Decision.Reject
        val speed = d / dt
        if (speed > maxSpeedMps) return Decision.Reject
        return Decision.Accept(d, speed)
    }
}

object Fitness {
    /** Approximate MET for walking at a given speed (Compendium of Physical Activities). */
    fun walkingMet(speedMps: Double): Double {
        val kmh = speedMps * 3.6
        return when {
            kmh < 0.5 -> 0.0
            kmh < 3.2 -> 2.0
            kmh < 4.0 -> 2.8
            kmh < 4.8 -> 3.3
            kmh < 5.6 -> 3.8
            kmh < 6.4 -> 4.5
            kmh < 7.2 -> 5.3
            kmh < 8.0 -> 7.0
            else -> 8.3
        }
    }

    fun walkKcal(distanceM: Double, activeMillis: Long, weightKg: Double): Int {
        if (activeMillis <= 0 || distanceM <= 0) return 0
        val hours = activeMillis / 3_600_000.0
        val speed = distanceM / (activeMillis / 1000.0)
        return (walkingMet(speed) * weightKg * hours).toInt()
    }

    /** Average stride length estimated from height (0.415 × height). */
    fun strideM(heightCm: Double?): Double = ((heightCm ?: 170.0) * 0.415) / 100.0

    fun stepsToDistanceM(steps: Int, heightCm: Double?): Double = steps * strideM(heightCm)

    /** Rough energy cost of steps: ~0.04 kcal per step for a 70 kg person, scaled by weight. */
    fun stepsKcal(steps: Int, weightKg: Double?): Int = (steps * 0.04 * ((weightKg ?: 70.0) / 70.0)).toInt()

    fun bmi(weightKg: Double, heightCm: Double?): Double? {
        val h = heightCm?.takeIf { it > 50 }?.div(100) ?: return null
        return weightKg / (h * h)
    }

    fun bmiCategory(bmi: Double): String = when {
        bmi < 18.5 -> "Underweight"
        bmi < 25 -> "Healthy"
        bmi < 30 -> "Overweight"
        else -> "Obese"
    }

    /** Fraction of the way from start to goal weight, 0..1. */
    fun goalProgress(start: Double?, current: Double?, goal: Double?): Float? {
        if (start == null || current == null || goal == null) return null
        val total = start - goal
        if (total == 0.0) return 1f
        return ((start - current) / total).toFloat().coerceIn(0f, 1f)
    }
}
