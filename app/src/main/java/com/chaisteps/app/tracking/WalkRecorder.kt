package com.chaisteps.app.tracking

import com.chaisteps.app.data.Route
import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.domain.Fitness
import com.chaisteps.app.domain.RouteFilter
import kotlinx.serialization.Serializable

/** Live view of the walk in progress. */
data class ActiveWalk(
    val id: String,
    val startMillis: Long,
    val paused: Boolean,
    val activeMillis: Long,
    val distanceM: Double,
    val steps: Int,
    val currentSpeedMps: Double,
    val lastAccuracyM: Float?,
    val hasFix: Boolean,
    val segments: List<List<RoutePoint>>,
) {
    val avgSpeedMps: Double get() = if (activeMillis > 0) distanceM / (activeMillis / 1000.0) else 0.0
    val paceSecPerKm: Double? get() = if (distanceM >= 10) (activeMillis / 1000.0) / (distanceM / 1000.0) else null
}

/** Everything needed to resume or recover a walk; written to disk periodically. */
@Serializable
data class WalkCheckpoint(
    val id: String,
    val startMillis: Long,
    val accumulatedActiveMillis: Long,
    val resumedAtMillis: Long?,
    val distanceM: Double,
    val maxSpeedMps: Double,
    val elevationGainM: Double,
    val steps: Int,
    val segments: List<List<RoutePoint>>,
    val lastSavedMillis: Long,
)

/**
 * Pure walk-recording logic: GPS fixes in, distance/time/route out. No Android dependencies so it
 * can be unit tested; the foreground service feeds it.
 */
class WalkRecorder private constructor(
    val id: String,
    val startMillis: Long,
    private val filter: RouteFilter,
    private var accumulatedActiveMillis: Long,
    private var resumedAtMillis: Long?,
    private var distanceM: Double,
    private var maxSpeedMps: Double,
    private var elevationGainM: Double,
    private var steps: Int,
    segments: List<List<RoutePoint>>,
) {
    private val segments: MutableList<MutableList<RoutePoint>> = segments.map { it.toMutableList() }.toMutableList()
    private var lastStepReading: Long? = null
    private var currentSpeedMps = 0.0
    private var lastAccuracyM: Float? = null
    private var elevationRef: Double? = null
    private var newSegmentPending = segments.isNotEmpty()

    val isPaused: Boolean get() = resumedAtMillis == null

    fun activeMillis(now: Long): Long = accumulatedActiveMillis + (resumedAtMillis?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** Returns true if the fix was added to the route. */
    fun onFix(fix: RoutePoint, reportedSpeedMps: Double? = null): Boolean {
        lastAccuracyM = fix.accuracyM
        if (isPaused) return false
        val current = segments.lastOrNull()
        val last = if (newSegmentPending) null else current?.lastOrNull()
        return when (val d = filter.evaluate(last, fix)) {
            RouteFilter.Decision.Reject -> {
                if (last != null && fix.timeMillis - last.timeMillis > 10_000) currentSpeedMps = 0.0
                false
            }
            RouteFilter.Decision.Start -> {
                segments.add(mutableListOf(fix))
                newSegmentPending = false
                elevationRef = fix.altitudeM
                true
            }
            is RouteFilter.Decision.Accept -> {
                current!!.add(fix)
                distanceM += d.distanceM
                currentSpeedMps = reportedSpeedMps?.takeIf { it > 0 } ?: d.speedMps
                if (d.speedMps > maxSpeedMps) maxSpeedMps = d.speedMps
                trackElevation(fix.altitudeM)
                true
            }
        }
    }

    private fun trackElevation(alt: Double?) {
        if (alt == null) return
        val ref = elevationRef ?: run { elevationRef = alt; return }
        // Hysteresis so GPS altitude noise doesn't add up to fake climbing.
        if (alt - ref >= 4) {
            elevationGainM += alt - ref
            elevationRef = alt
        } else if (ref - alt >= 4) {
            elevationRef = alt
        }
    }

    fun onStepReading(value: Long) {
        val last = lastStepReading
        lastStepReading = value
        if (last == null || isPaused) return
        val delta = value - last
        if (delta in 1..5_000) steps += delta.toInt()
    }

    fun pause(now: Long) {
        val r = resumedAtMillis ?: return
        accumulatedActiveMillis += (now - r).coerceAtLeast(0)
        resumedAtMillis = null
        currentSpeedMps = 0.0
        newSegmentPending = true
    }

    fun resume(now: Long) {
        if (!isPaused) return
        resumedAtMillis = now
    }

    fun snapshot(now: Long) = ActiveWalk(
        id = id,
        startMillis = startMillis,
        paused = isPaused,
        activeMillis = activeMillis(now),
        distanceM = distanceM,
        steps = steps,
        currentSpeedMps = currentSpeedMps,
        lastAccuracyM = lastAccuracyM,
        hasFix = segments.isNotEmpty(),
        segments = segments.map { it.toList() },
    )

    fun checkpoint(now: Long) = WalkCheckpoint(
        id, startMillis, accumulatedActiveMillis, resumedAtMillis, distanceM, maxSpeedMps,
        elevationGainM, steps, segments.map { it.toList() }, now,
    )

    fun finish(now: Long, weightKg: Double, recovered: Boolean = false): Pair<WalkSession, Route> {
        val active = activeMillis(now)
        val route = Route(segments.filter { it.isNotEmpty() }.map { it.toList() })
        val session = WalkSession(
            id = id,
            startMillis = startMillis,
            endMillis = now,
            activeMillis = active,
            distanceM = distanceM,
            steps = steps,
            kcal = Fitness.walkKcal(distanceM, active, weightKg),
            maxSpeedMps = maxSpeedMps,
            pointCount = route.allPoints.size,
            elevationGainM = elevationGainM,
            recovered = recovered,
        )
        return session to route
    }

    companion object {
        fun start(id: String, now: Long, filter: RouteFilter = RouteFilter()) =
            WalkRecorder(id, now, filter, 0, now, 0.0, 0.0, 0.0, 0, emptyList())

        fun restore(c: WalkCheckpoint, filter: RouteFilter = RouteFilter()) = WalkRecorder(
            c.id, c.startMillis, filter, c.accumulatedActiveMillis, c.resumedAtMillis, c.distanceM,
            c.maxSpeedMps, c.elevationGainM, c.steps, c.segments,
        )

        /**
         * A walk interrupted by the app being killed: count active time only up to the last
         * checkpoint so hours of "walking" aren't invented.
         */
        fun recover(c: WalkCheckpoint, weightKg: Double): Pair<WalkSession, Route> {
            val r = restore(c)
            return r.finish(c.lastSavedMillis, weightKg, recovered = true)
        }
    }
}
