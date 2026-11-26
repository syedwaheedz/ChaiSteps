package com.chaisteps.app.ui

import com.chaisteps.app.data.AppData
import com.chaisteps.app.data.ChaiEntry
import com.chaisteps.app.data.ChaiType
import com.chaisteps.app.data.CupSize
import com.chaisteps.app.data.Profile
import com.chaisteps.app.data.Route
import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.data.StepDay
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.data.WeightEntry
import com.chaisteps.app.domain.Geo
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Realistic two months of data for UI tests and screenshots. */
object SampleData {
    fun build(today: Long = LocalDate.now().toEpochDay(), zone: ZoneId = ZoneId.systemDefault()): Pair<AppData, Map<String, Route>> {
        val rnd = Random(7)
        val profile = Profile(
            name = "Waheed", heightCm = 175.0, startWeightKg = 92.0, goalWeightKg = 80.0,
            stepGoal = 8_000, chaiLimit = 3, onboarded = true, favoriteChai = ChaiType.KARAK,
        )
        val weights = (0..60 step 3).map { back ->
            val day = today - 60 + back
            WeightEntry("w$back", day, 92.0 - back * 0.07 + rnd.nextDouble(-0.3, 0.3))
        }
        val steps = (0..45).map { back -> StepDay(today - back, if (back == 0) 5_430 else rnd.nextInt(3_000, 12_500)) }
        fun at(day: Long, hour: Int, min: Int = 0) =
            LocalDate.ofEpochDay(day).atStartOfDay(zone).plusHours(hour.toLong()).plusMinutes(min.toLong()).toInstant().toEpochMilli()
        val chai = (0..20).flatMap { back ->
            val day = today - back
            val n = if (back == 0) 2 else rnd.nextInt(1, 6)
            (0 until n).map { i ->
                ChaiEntry("c$back-$i", at(day, 7 + i * 3, 15), ChaiType.entries[rnd.nextInt(0, 4)], rnd.nextInt(0, 3).toDouble(), CupSize.REGULAR)
            }
        }
        val routes = mutableMapOf<String, Route>()
        val walks = listOf(1L, 2L, 4L, 6L, 9L, 12L, 15L, 20L).mapIndexed { i, back ->
            val id = "walk$i"
            val start = at(today - back, 18, 30)
            val route = loop(25.2048, 55.2708, 0.003 + i * 0.0004, start)
            routes[id] = route
            val pts = route.allPoints
            val dist = Geo.segmentLengthM(pts)
            val active = pts.last().timeMillis - pts.first().timeMillis
            WalkSession(id, start, start + active, active, dist, (dist / 0.72).toInt(), (dist / 1000 * 55).toInt(), 1.8, pts.size, 6.0)
        }
        return AppData(profile = profile, weights = weights, stepDays = steps.sortedBy { it.epochDay }, chai = chai.sortedBy { it.timeMillis }, walks = walks) to routes
    }

    /** A wobbly loop around a centre, one point every 5 s at ~1.4 m/s. */
    fun loop(lat: Double, lon: Double, radiusDeg: Double, start: Long): Route {
        val n = 160
        val shape = (0..n).map { k ->
            val a = 2 * Math.PI * k / n
            val r = radiusDeg * (1 + 0.25 * sin(3 * a))
            lat + r * sin(a) to lon + r * cos(a) / cos(Math.toRadians(lat))
        }
        // Timestamps follow distance at a walking speed of ~1.4 m/s.
        var t = start
        val pts = shape.mapIndexed { k, (la, lo) ->
            if (k > 0) t += (Geo.distanceM(shape[k - 1].first, shape[k - 1].second, la, lo) / 1.4 * 1000).toLong()
            RoutePoint(la, lo, t, 4f)
        }
        return Route(listOf(pts))
    }
}
