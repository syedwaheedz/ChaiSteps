package com.chaisteps.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Single source of truth for all app data. Everything lives in memory as a [StateFlow] and is
 * written to a JSON file (atomically, via a temp file) after each change. GPS routes are kept in
 * separate files so the main file stays small.
 */
class Repository(
    private val dir: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + io),
) {
    private val dataFile = File(dir, "chaisteps.json")
    private val routesDir = File(dir, "routes")
    private val writeLock = Mutex()
    @Volatile private var pendingVersion = 0L
    @Volatile private var savedVersion = 0L

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private fun load(): AppData {
        dir.mkdirs()
        val candidates = listOf(dataFile, File(dir, "chaisteps.json.bak"))
        for (f in candidates) {
            if (!f.exists()) continue
            runCatching { return json.decodeFromString(AppData.serializer(), f.readText()) }
        }
        return AppData()
    }

    /** Applies [transform] to the data and schedules a save. */
    fun update(transform: (AppData) -> AppData) {
        _data.update(transform)
        val version = synchronized(this) { ++pendingVersion }
        scope.launch { save(version) }
    }

    private suspend fun save(version: Long) = writeLock.withLock {
        if (version <= savedVersion) return@withLock
        val snapshot = _data.value
        val target = pendingVersion
        writeAtomically(dataFile, json.encodeToString(AppData.serializer(), snapshot))
        savedVersion = target
    }

    /** Waits until every scheduled save has been written. */
    suspend fun flush() = withContext(io) {
        save(pendingVersion)
    }

    private fun writeAtomically(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (target.exists()) {
            target.copyTo(File(target.parentFile, target.name + ".bak"), overwrite = true)
        }
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    // ---------- Profile ----------

    fun updateProfile(transform: (Profile) -> Profile) = update { it.copy(profile = transform(it.profile)) }

    // ---------- Weight ----------

    fun addWeight(epochDay: Long, kg: Double, note: String = "") = update { d ->
        // One entry per day: logging again on the same day replaces it.
        val others = d.weights.filterNot { it.epochDay == epochDay }
        d.copy(weights = (others + WeightEntry(newId(), epochDay, kg, note)).sortedBy { it.epochDay })
    }

    fun deleteWeight(id: String) = update { d -> d.copy(weights = d.weights.filterNot { it.id == id }) }

    // ---------- Steps ----------

    fun applyStepReading(reading: Long, bootMillis: Long, epochDay: Long) = update { d ->
        val result = StepAccounting.onReading(d.stepCounter, reading, bootMillis)
        val withSteps = if (result.delta > 0) d.addSensorSteps(epochDay, result.delta) else d
        withSteps.copy(stepCounter = result.state)
    }

    fun addManualSteps(epochDay: Long, steps: Int) = update { d ->
        val day = d.stepDay(epochDay)
        d.putStepDay(day.copy(manualSteps = day.manualSteps + steps))
    }

    /** Sets the day's displayed total by adjusting the manual part. */
    fun setDayTotal(epochDay: Long, total: Int) = update { d ->
        val day = d.stepDay(epochDay)
        d.putStepDay(day.copy(manualSteps = total.coerceAtLeast(0) - day.sensorSteps))
    }

    /** Removes every manual adjustment for the day, leaving only sensor-counted steps. */
    fun clearManualSteps(epochDay: Long) = update { d ->
        val day = d.stepDay(epochDay)
        d.putStepDay(day.copy(manualSteps = 0))
    }

    // ---------- Chai ----------

    fun addChai(type: ChaiType, sugarTsp: Double, size: CupSize, timeMillis: Long) = update { d ->
        d.copy(chai = (d.chai + ChaiEntry(newId(), timeMillis, type, sugarTsp, size)).sortedBy { it.timeMillis })
    }

    fun deleteChai(id: String) = update { d -> d.copy(chai = d.chai.filterNot { it.id == id }) }

    // ---------- Walks & routes ----------

    fun addWalk(session: WalkSession, route: Route) {
        saveRoute(session.id, route)
        update { d -> d.copy(walks = (d.walks.filterNot { it.id == session.id } + session).sortedBy { it.startMillis }) }
    }

    fun deleteWalk(id: String) {
        update { d -> d.copy(walks = d.walks.filterNot { it.id == id }) }
        scope.launch { File(routesDir, "$id.json").delete() }
    }

    fun saveRoute(id: String, route: Route) {
        writeAtomically(File(routesDir, "$id.json"), json.encodeToString(Route.serializer(), route))
    }

    fun loadRoute(id: String): Route {
        val f = File(routesDir, "$id.json")
        if (!f.exists()) return Route()
        return runCatching { json.decodeFromString(Route.serializer(), f.readText()) }.getOrDefault(Route())
    }

    // ---------- Import / export ----------

    fun exportJson(nowMillis: Long): String {
        val d = _data.value
        val bundle = ExportBundle(exportedAtMillis = nowMillis, data = d, routes = d.walks.associate { it.id to loadRoute(it.id) })
        return prettyJson.encodeToString(ExportBundle.serializer(), bundle)
    }

    /** Replaces all data with an exported bundle. Throws if the text is not a valid export. */
    fun importJson(text: String) {
        val bundle = json.decodeFromString(ExportBundle.serializer(), text)
        require(bundle.app == "ChaiSteps") { "Not a Chai & Steps backup" }
        bundle.routes.forEach { (id, route) -> saveRoute(id, route) }
        update { bundle.data }
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
        private val prettyJson = Json(json) { prettyPrint = true }
        fun newId(): String = UUID.randomUUID().toString()
    }
}

fun AppData.stepDay(epochDay: Long): StepDay = stepDays.firstOrNull { it.epochDay == epochDay } ?: StepDay(epochDay)

fun AppData.putStepDay(day: StepDay): AppData =
    copy(stepDays = (stepDays.filterNot { it.epochDay == day.epochDay } + day).sortedBy { it.epochDay })

fun AppData.addSensorSteps(epochDay: Long, delta: Int): AppData {
    val day = stepDay(epochDay)
    return putStepDay(day.copy(sensorSteps = day.sensorSteps + delta))
}
