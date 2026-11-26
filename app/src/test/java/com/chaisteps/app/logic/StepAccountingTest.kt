package com.chaisteps.app.logic

import com.chaisteps.app.data.StepAccounting
import com.chaisteps.app.data.StepCounterState
import org.junit.Assert.assertEquals
import org.junit.Test

class StepAccountingTest {
    private val boot = 1_700_000_000_000L

    @Test fun firstReadingIsBaselineOnly() {
        val r = StepAccounting.onReading(StepCounterState(), 5_000, boot)
        assertEquals(0, r.delta)
        assertEquals(5_000L, r.state.lastValue)
    }

    @Test fun deltaBetweenReadings() {
        val s = StepAccounting.onReading(StepCounterState(), 5_000, boot).state
        val r = StepAccounting.onReading(s, 5_350, boot + 1_000)
        assertEquals(350, r.delta)
    }

    @Test fun sameReadingAddsNothing() {
        val s = StepCounterState(5_000, boot)
        assertEquals(0, StepAccounting.onReading(s, 5_000, boot).delta)
    }

    @Test fun rebootDetectedByBootTime() {
        val s = StepCounterState(8_000, boot)
        // After reboot the counter restarted and is already above the old value.
        val r = StepAccounting.onReading(s, 9_000, boot + 3_600_000)
        assertEquals(9_000, r.delta)
    }

    @Test fun counterResetWithoutBootChange() {
        val s = StepCounterState(8_000, boot)
        assertEquals(120, StepAccounting.onReading(s, 120, boot).delta)
    }

    @Test fun smallClockDriftIsNotAReboot() {
        val s = StepCounterState(8_000, boot)
        assertEquals(100, StepAccounting.onReading(s, 8_100, boot + 30_000).delta)
    }

    @Test fun implausibleJumpIgnoredButBaselineMoves() {
        val s = StepCounterState(1_000, boot)
        val r = StepAccounting.onReading(s, 1_000 + StepAccounting.MAX_DELTA + 1, boot)
        assertEquals(0, r.delta)
        assertEquals(1_000 + StepAccounting.MAX_DELTA + 1, r.state.lastValue)
    }
}
