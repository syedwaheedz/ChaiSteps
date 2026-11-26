package com.chaisteps.app.logic

import com.chaisteps.app.data.AppData
import com.chaisteps.app.data.ChaiEntry
import com.chaisteps.app.data.ChaiType
import com.chaisteps.app.data.Profile
import com.chaisteps.app.data.StepDay
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.data.WeightEntry
import com.chaisteps.app.domain.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 26).toEpochDay() // a Thursday
    private fun millis(day: Long, hour: Int = 9) = LocalDate.ofEpochDay(day).atStartOfDay(zone).plusHours(hour.toLong()).toInstant().toEpochMilli()

    @Test fun stepStreakAndAverages() {
        val data = AppData(
            profile = Profile(stepGoal = 8_000),
            stepDays = listOf(
                StepDay(today - 4, 3_000),
                StepDay(today - 3, 9_000),
                StepDay(today - 2, 8_500),
                StepDay(today - 1, 7_000, manualSteps = 1_500),
                StepDay(today, 2_000),
            ),
        )
        val s = Stats(data, today, zone)
        assertEquals(3, s.stepGoalStreak()) // today not reached yet; yesterday (8.5k incl manual) and 2 before
        assertEquals((3_000 + 9_000 + 8_500 + 8_500) / 4, s.averageSteps(7))
        assertEquals(9_000.0, s.bestStepDay()!!.value, 0.0)
        assertEquals(7, s.stepSeries(7).size)
        assertEquals(2_000.0, s.stepSeries(7).last().value, 0.0)
    }

    @Test fun weightTrendAndProjection() {
        // Losing 0.5 kg per week steadily.
        val weights = (0..8).map { w -> WeightEntry("w$w", today - 56 + w * 7, 90.0 - w * 0.5) }
        val data = AppData(profile = Profile(goalWeightKg = 85.0, startWeightKg = 90.0), weights = weights)
        val s = Stats(data, today, zone)
        assertEquals(-0.5, s.weeklyTrendKg(56)!!, 1e-6)
        assertEquals(-0.5, s.weeklyTrendKg()!!, 1e-6)
        assertEquals(86.0, s.latestWeight()!!.kg, 0.0)
        assertEquals(-2.0, s.weightChange(28)!!, 1e-6)
        // 1 kg to go at 0.5 kg/week = 14 days
        assertEquals(today + 14, s.projectedGoalDay())
    }

    @Test fun noProjectionWhenTrendingAway() {
        val weights = listOf(WeightEntry("a", today - 14, 80.0), WeightEntry("b", today, 81.0))
        val s = Stats(AppData(profile = Profile(goalWeightKg = 75.0), weights = weights), today, zone)
        assertNull(s.projectedGoalDay())
        assertNotNull(s.weeklyTrendKg())
    }

    @Test fun walkTotals() {
        val walks = listOf(
            WalkSession("a", millis(today), millis(today) + 1_800_000, 1_800_000, 2_500.0, 3_000, 150),
            WalkSession("b", millis(today - 3), millis(today - 3) + 3_600_000, 3_600_000, 5_000.0, 6_500, 300), // Monday
            WalkSession("c", millis(today - 10), millis(today - 10) + 600_000, 600_000, 400.0),
        )
        val s = Stats(AppData(walks = walks), today, zone)
        val week = s.weekTotals()
        assertEquals(2, week.count)
        assertEquals(7_500.0, week.distanceM, 0.0)
        assertEquals(3, s.allTimeTotals().count)
        assertEquals("b", s.longestWalk()!!.id)
        // c is under 500 m so not eligible; a (5 km/h) equals b (5 km/h) – either way not c
        assert(s.fastestWalk()!!.id != "c")
        assertEquals(4, s.weeklyWalkDistance(4).size)
        assertEquals(7_500.0, s.weeklyWalkDistance(4).last().value, 0.0)
    }

    @Test fun chaiStats() {
        val chai = listOf(
            ChaiEntry("1", millis(today - 2, 8), ChaiType.KARAK),
            ChaiEntry("2", millis(today - 2, 15), ChaiType.KARAK),
            ChaiEntry("3", millis(today - 2, 18), ChaiType.MASALA),
            ChaiEntry("4", millis(today - 2, 20), ChaiType.KARAK), // 4 cups: over the limit of 3
            ChaiEntry("5", millis(today - 1, 8), ChaiType.KARAK),
            ChaiEntry("6", millis(today, 8), ChaiType.SULAIMANI),
        )
        val s = Stats(AppData(profile = Profile(chaiLimit = 3), chai = chai), today, zone)
        assertEquals(1, s.chaiOn(today).size)
        assertEquals(2, s.chaiWithinLimitStreak())
        assertEquals(ChaiType.KARAK, s.favoriteChai())
        assertEquals(2.0, s.averageChai(30), 1e-9) // 6 cups over 3 days
        assertEquals(listOf(4.0, 1.0, 1.0), s.chaiSeries(3).map { it.value })
    }

    @Test fun chaiNutrition() {
        val c = ChaiEntry("x", 0, ChaiType.DOODH_PATTI, sugarTsp = 2.0)
        assertEquals(80 + 32, c.kcal)
        assertEquals(8.0, c.sugarGrams, 0.0)
    }
}
