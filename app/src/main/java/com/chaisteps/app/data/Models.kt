package com.chaisteps.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class WeightUnit { KG, LB }

@Serializable
enum class DistanceUnit { KM, MI }

@Serializable
data class Profile(
    val name: String = "",
    val heightCm: Double? = null,
    val startWeightKg: Double? = null,
    val goalWeightKg: Double? = null,
    val stepGoal: Int = 10_000,
    val activeMinutesGoal: Int = 30,
    val chaiLimit: Int = 3,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val onboarded: Boolean = false,
    val favoriteChai: ChaiType = ChaiType.DOODH_PATTI,
    val defaultSugarTsp: Double = 1.0,
)

@Serializable
data class WeightEntry(
    val id: String,
    /** Local date as epoch day. */
    val epochDay: Long,
    val kg: Double,
    val note: String = "",
)

/** Steps recorded for one local day: sensor counts plus any manual adjustment. */
@Serializable
data class StepDay(
    val epochDay: Long,
    val sensorSteps: Int = 0,
    val manualSteps: Int = 0,
) {
    val total: Int get() = (sensorSteps + manualSteps).coerceAtLeast(0)
}

/** Bookkeeping for the cumulative TYPE_STEP_COUNTER sensor (counts since boot). */
@Serializable
data class StepCounterState(
    val lastValue: Long? = null,
    /** Wall-clock millis of device boot when [lastValue] was read, to detect reboots. */
    val bootMillis: Long? = null,
)

@Serializable
enum class ChaiType(val label: String, val baseKcal: Int, val caffeineMg: Int, val hasMilk: Boolean) {
    DOODH_PATTI("Doodh Patti", 80, 55, true),
    KARAK("Karak", 110, 60, true),
    MASALA("Masala Chai", 70, 45, true),
    MILK_TEA("Milk Tea", 45, 45, true),
    IRANI("Irani Chai", 120, 50, true),
    KASHMIRI("Kashmiri Pink", 130, 20, true),
    SULAIMANI("Sulaimani", 3, 40, false),
    GREEN("Green Tea", 2, 30, false),
    BLACK("Black Tea", 2, 45, false),
}

@Serializable
enum class CupSize(val label: String, val ml: Int, val factor: Double) {
    SMALL("Small", 100, 0.7),
    REGULAR("Regular", 150, 1.0),
    LARGE("Mug", 250, 1.6),
}

@Serializable
data class ChaiEntry(
    val id: String,
    val timeMillis: Long,
    val type: ChaiType,
    val sugarTsp: Double = 1.0,
    val size: CupSize = CupSize.REGULAR,
) {
    /** ~16 kcal per teaspoon (4 g) of sugar. */
    val kcal: Int get() = (type.baseKcal * size.factor + sugarTsp * 16).toInt()
    val caffeineMg: Int get() = (type.caffeineMg * size.factor).toInt()
    val sugarGrams: Double get() = sugarTsp * 4.0
}

@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val timeMillis: Long,
    val accuracyM: Float = 0f,
    val altitudeM: Double? = null,
)

/** A finished walking session. The GPS route is stored separately (see [Repository.loadRoute]). */
@Serializable
data class WalkSession(
    val id: String,
    val startMillis: Long,
    val endMillis: Long,
    /** Moving time, excluding pauses. */
    val activeMillis: Long,
    val distanceM: Double,
    val steps: Int = 0,
    val kcal: Int = 0,
    val maxSpeedMps: Double = 0.0,
    val pointCount: Int = 0,
    val elevationGainM: Double = 0.0,
    val note: String = "",
    val recovered: Boolean = false,
) {
    val avgSpeedMps: Double get() = if (activeMillis > 0) distanceM / (activeMillis / 1000.0) else 0.0
    /** Seconds per kilometre, or null if there is no distance. */
    val paceSecPerKm: Double? get() = if (distanceM >= 10) (activeMillis / 1000.0) / (distanceM / 1000.0) else null
}

/** A route is a list of segments; a pause starts a new segment so no line is drawn across it. */
@Serializable
data class Route(val segments: List<List<RoutePoint>> = emptyList()) {
    val allPoints: List<RoutePoint> get() = segments.flatten()
}

@Serializable
data class AppData(
    val schemaVersion: Int = 1,
    val profile: Profile = Profile(),
    val weights: List<WeightEntry> = emptyList(),
    val stepDays: List<StepDay> = emptyList(),
    val stepCounter: StepCounterState = StepCounterState(),
    val chai: List<ChaiEntry> = emptyList(),
    val walks: List<WalkSession> = emptyList(),
)

/** Export file format: everything, including routes. */
@Serializable
data class ExportBundle(
    val app: String = "ChaiSteps",
    val exportedAtMillis: Long,
    val data: AppData,
    val routes: Map<String, Route> = emptyMap(),
)
