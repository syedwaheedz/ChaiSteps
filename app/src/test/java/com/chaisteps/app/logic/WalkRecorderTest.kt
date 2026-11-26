package com.chaisteps.app.logic

import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.tracking.WalkRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkRecorderTest {
    private val t0 = 1_700_000_000_000L
    // ~11.1 m per 0.0001 degree latitude
    private fun fix(i: Int, t: Long, acc: Float = 5f, alt: Double? = null) = RoutePoint(25.0 + i * 0.0001, 55.0, t, acc, alt)

    @Test fun recordsDistanceTimeAndRoute() {
        val r = WalkRecorder.start("w", t0)
        for (i in 0..10) r.onFix(fix(i, t0 + i * 8_000L))
        val snap = r.snapshot(t0 + 80_000)
        assertEquals(111.2, snap.distanceM, 1.0)
        assertEquals(80_000, snap.activeMillis)
        assertTrue(snap.hasFix)
        val (session, route) = r.finish(t0 + 80_000, 80.0)
        assertEquals(11, route.allPoints.size)
        assertEquals(11, session.pointCount)
        assertEquals(1, route.segments.size)
    }

    @Test fun pauseExcludesTimeAndStartsNewSegment() {
        val r = WalkRecorder.start("w", t0)
        r.onFix(fix(0, t0))
        r.onFix(fix(1, t0 + 8_000))
        r.pause(t0 + 10_000)
        assertTrue(r.isPaused)
        // Fixes while paused are ignored.
        assertFalse(r.onFix(fix(50, t0 + 20_000)))
        r.resume(t0 + 70_000)
        r.onFix(fix(60, t0 + 72_000)) // starts a new segment: no distance across the gap
        r.onFix(fix(61, t0 + 80_000))
        val (session, route) = r.finish(t0 + 80_000, 70.0)
        assertEquals(10_000 + 10_000, session.activeMillis)
        assertEquals(2, route.segments.size)
        assertEquals(22.2, session.distanceM, 0.5)
    }

    @Test fun stepsCountedOnlyWhileActive() {
        val r = WalkRecorder.start("w", t0)
        r.onStepReading(1_000) // baseline
        r.onStepReading(1_100)
        r.pause(t0 + 1_000)
        r.onStepReading(1_300)
        r.resume(t0 + 2_000)
        r.onStepReading(1_350)
        assertEquals(150, r.snapshot(t0 + 3_000).steps)
    }

    @Test fun elevationGainIgnoresNoise() {
        val r = WalkRecorder.start("w", t0)
        val alts = listOf(10.0, 12.0, 9.0, 11.0, 15.0, 14.0, 20.0)
        alts.forEachIndexed { i, a -> r.onFix(fix(i, t0 + i * 8_000L, alt = a)) }
        val (session, _) = r.finish(t0 + 60_000, 70.0)
        // 10 -> 15 (+5) then 15 -> 20 (+5) with 4 m hysteresis; small wobbles ignored
        assertTrue("gain ${session.elevationGainM}", session.elevationGainM in 9.0..12.0)
    }

    @Test fun checkpointRestoreAndRecover() {
        val r = WalkRecorder.start("w", t0)
        for (i in 0..5) r.onFix(fix(i, t0 + i * 8_000L))
        val cp = r.checkpoint(t0 + 40_000)
        val (recovered, route) = WalkRecorder.recover(cp, 70.0)
        assertTrue(recovered.recovered)
        assertEquals(40_000, recovered.activeMillis)
        assertEquals(6, route.allPoints.size)
        assertEquals(r.snapshot(t0).distanceM, recovered.distanceM, 0.01)

        val restored = WalkRecorder.restore(cp)
        restored.onFix(fix(6, t0 + 48_000))
        restored.onFix(fix(7, t0 + 56_000))
        assertEquals(2, restored.finish(t0 + 56_000, 70.0).second.segments.size)
    }
}
