package com.chaisteps.app.data

import kotlin.math.abs

/**
 * Turns readings of the cumulative step counter (steps since boot) into per-reading deltas.
 * Handles the first reading (baseline only), reboots (counter restarts from zero) and
 * implausible jumps.
 */
object StepAccounting {
    /** Boot time moves slightly between reads because of clock adjustments; beyond this it is a reboot. */
    private const val BOOT_TOLERANCE_MS = 2 * 60_000L
    /** No real person walks this many steps between two reads (we read at least every few hours). */
    const val MAX_DELTA = 60_000L

    data class Result(val state: StepCounterState, val delta: Int)

    fun onReading(state: StepCounterState, reading: Long, bootMillis: Long): Result {
        val newState = StepCounterState(lastValue = reading, bootMillis = bootMillis)
        val last = state.lastValue ?: return Result(newState, 0)
        val rebooted = state.bootMillis != null && abs(bootMillis - state.bootMillis) > BOOT_TOLERANCE_MS
        val delta = when {
            rebooted -> reading
            reading < last -> reading // counter reset without a detected reboot
            else -> reading - last
        }
        if (delta < 0 || delta > MAX_DELTA) return Result(newState, 0)
        return Result(newState, delta.toInt())
    }
}
