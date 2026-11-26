package com.chaisteps.app.logic

import com.chaisteps.app.data.DistanceUnit
import com.chaisteps.app.data.WeightUnit
import com.chaisteps.app.domain.Format
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test fun weights() {
        assertEquals("80.0 kg", Format.weight(80.0, WeightUnit.KG))
        assertEquals("176.4 lb", Format.weight(80.0, WeightUnit.LB))
        assertEquals(80.0, Format.unitToKg(176.37, WeightUnit.LB), 0.01)
        assertEquals("−1.5 kg", Format.signedWeight(-1.5, WeightUnit.KG))
        assertEquals("+0.4 kg", Format.signedWeight(0.4, WeightUnit.KG))
        assertEquals("0.0 kg", Format.signedWeight(0.01, WeightUnit.KG))
    }

    @Test fun distances() {
        assertEquals("850 m", Format.distance(850.0, DistanceUnit.KM))
        assertEquals("1.23 km", Format.distance(1_234.0, DistanceUnit.KM))
        assertEquals("1.00 mi", Format.distance(1_609.344, DistanceUnit.MI))
    }

    @Test fun paceAndSpeed() {
        assertEquals("10:00 /km", Format.pace(600.0, DistanceUnit.KM))
        assertEquals("16:06 /mi", Format.pace(600.0, DistanceUnit.MI))
        assertEquals("–", Format.pace(null, DistanceUnit.KM))
        assertEquals("5.0 km/h", Format.speed(5 / 3.6, DistanceUnit.KM))
    }

    @Test fun durations() {
        assertEquals("0:59", Format.duration(59_000))
        assertEquals("1:01:05", Format.duration(3_665_000))
        assertEquals("1h 5m", Format.durationWords(3_900_000))
        assertEquals("45m", Format.durationWords(2_700_000))
        assertEquals("12,345", Format.steps(12_345))
    }

    @Test fun days() {
        assertEquals("Today", Format.day(100, 100))
        assertEquals("Yesterday", Format.day(99, 100))
    }
}
