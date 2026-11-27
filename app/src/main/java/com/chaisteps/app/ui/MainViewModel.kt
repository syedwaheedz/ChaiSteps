package com.chaisteps.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaisteps.app.ChaiStepsApp
import com.chaisteps.app.data.AppData
import com.chaisteps.app.data.ChaiType
import com.chaisteps.app.data.CupSize
import com.chaisteps.app.data.Profile
import com.chaisteps.app.data.Route
import com.chaisteps.app.data.WalkSession
import com.chaisteps.app.tracking.ActiveWalk
import com.chaisteps.app.tracking.StepSensor
import com.chaisteps.app.tracking.WalkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ChaiStepsApp
    private val repo = app.repository

    val data: StateFlow<AppData> = repo.data
    val activeWalk: StateFlow<ActiveWalk?> = app.walkTracker.active
    val lastFinishedWalk: StateFlow<WalkSession?> = app.walkTracker.lastFinished

    private val _today = MutableStateFlow(LocalDate.now().toEpochDay())
    val today: StateFlow<Long> = _today.asStateFlow()
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                refreshDay()
            }
        }
    }

    fun refreshDay() {
        _today.value = LocalDate.now().toEpochDay()
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun recoverInterruptedWalk() {
        val weight = data.value.weights.maxByOrNull { it.epochDay }?.kg ?: data.value.profile.startWeightKg ?: 70.0
        app.walkTracker.recoverInterrupted(repo, weight)?.let {
            showMessage("Recovered a walk that was interrupted")
        }
    }

    // ---------- Profile ----------

    fun updateProfile(transform: (Profile) -> Profile) = repo.updateProfile(transform)

    fun completeOnboarding(profile: Profile, currentWeightKg: Double?) {
        repo.updateProfile { profile.copy(onboarded = true, startWeightKg = currentWeightKg ?: profile.startWeightKg) }
        if (currentWeightKg != null) repo.addWeight(today.value, currentWeightKg)
    }

    // ---------- Weight ----------

    fun addWeight(epochDay: Long, kg: Double, note: String) = repo.addWeight(epochDay, kg, note)
    fun deleteWeight(id: String) = repo.deleteWeight(id)

    // ---------- Steps ----------

    fun addManualSteps(epochDay: Long, steps: Int) = repo.addManualSteps(epochDay, steps)
    fun setDayTotal(epochDay: Long, total: Int) = repo.setDayTotal(epochDay, total)
    fun clearManualSteps(epochDay: Long) = repo.clearManualSteps(epochDay)

    fun stepsSensorAvailable() = StepSensor.isAvailable(app)
    fun stepsPermissionGranted() = StepSensor.hasPermission(app)
    fun onStepPermissionGranted() = app.stepMonitor.refresh()

    // ---------- Chai ----------

    fun addChai(type: ChaiType, sugarTsp: Double, size: CupSize, timeMillis: Long = System.currentTimeMillis()) =
        repo.addChai(type, sugarTsp, size, timeMillis)

    fun deleteChai(id: String) = repo.deleteChai(id)

    // ---------- Walks ----------

    fun startWalk() = WalkService.send(app, WalkService.ACTION_START)
    fun pauseWalk() = WalkService.send(app, WalkService.ACTION_PAUSE)
    fun resumeWalk() = WalkService.send(app, WalkService.ACTION_RESUME)
    fun finishWalk() = WalkService.send(app, WalkService.ACTION_STOP)
    fun discardWalk() = WalkService.send(app, WalkService.ACTION_DISCARD)
    fun consumeFinishedWalk() = app.walkTracker.consumeFinished()
    fun deleteWalk(id: String) = repo.deleteWalk(id)

    suspend fun loadRoute(id: String): Route = withContext(Dispatchers.IO) { repo.loadRoute(id) }

    // ---------- Backup ----------

    suspend fun exportJson(): String = withContext(Dispatchers.IO) { repo.exportJson(System.currentTimeMillis()) }

    suspend fun importJson(text: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching { repo.importJson(text) } }
}
