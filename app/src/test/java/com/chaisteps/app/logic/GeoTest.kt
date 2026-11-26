package com.chaisteps.app.logic

import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.domain.Fitness
import com.chaisteps.app.domain.Geo
import com.chaisteps.app.domain.RouteFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {
    @Test fun haversineKnownDistance() {
        // One degree of latitude is ~111.2 km.
        assertEquals(111_195.0, Geo.distanceM(0.0, 0.0, 1.0, 0.0), 50.0)
        // London -> Paris ~343.5 km
        assertEquals(343_500.0, Geo.distanceM(51.5074, -0.1278, 48.8566, 2.3522), 1_500.0)
        assertEquals(0.0, Geo.distanceM(25.0, 55.0, 25.0, 55.0), 1e-9)
    }

    private fun p(lat: Double, lon: Double, t: Long, acc: Float = 5f) = RoutePoint(lat, lon, t, acc)

    @Test fun filterRejectsInaccurateFix() {
        assertEquals(RouteFilter.Decision.Reject, RouteFilter().evaluate(null, p(25.0, 55.0, 0, acc = 80f)))
    }

    @Test fun filterStartsAndAcceptsWalkingMovement() {
        val f = RouteFilter()
        val a = p(25.0, 55.0, 0)
        assertEquals(RouteFilter.Decision.Start, f.evaluate(null, a))
        // ~11 m north in 8 s = 1.4 m/s
        val d = f.evaluate(a, p(25.0001, 55.0, 8_000))
        assertTrue(d is RouteFilter.Decision.Accept)
        assertEquals(11.1, (d as RouteFilter.Decision.Accept).distanceM, 0.3)
    }

    @Test fun filterRejectsJitterAndTeleports() {
        val f = RouteFilter()
        val a = p(25.0, 55.0, 0)
        assertEquals(RouteFilter.Decision.Reject, f.evaluate(a, p(25.00001, 55.0, 2_000))) // 1 m
        assertEquals(RouteFilter.Decision.Reject, f.evaluate(a, p(25.01, 55.0, 2_000))) // 1.1 km in 2 s
        assertEquals(RouteFilter.Decision.Reject, f.evaluate(a, p(25.0001, 55.0, 0))) // no time passed
    }

    @Test fun fitnessHelpers() {
        assertEquals(24.2, Fitness.bmi(70.0, 170.0)!!, 0.1)
        assertNull(Fitness.bmi(70.0, null))
        assertEquals("Healthy", Fitness.bmiCategory(22.0))
        assertEquals("Obese", Fitness.bmiCategory(31.0))
        assertEquals(0.5f, Fitness.goalProgress(100.0, 95.0, 90.0)!!, 0.001f)
        assertEquals(1f, Fitness.goalProgress(100.0, 85.0, 90.0)!!, 0.001f)
        assertEquals(0f, Fitness.goalProgress(100.0, 102.0, 90.0)!!, 0.001f)
        // 5 km/h for 1 hour at 80 kg ~ 3.8 MET * 80 = 304 kcal
        assertEquals(304, Fitness.walkKcal(5_000.0, 3_600_000, 80.0))
        assertEquals(0, Fitness.walkKcal(0.0, 0, 80.0))
        assertEquals(0.7055, Fitness.strideM(170.0), 0.001)
    }
}
