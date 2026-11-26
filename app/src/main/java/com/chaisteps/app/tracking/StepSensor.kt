package com.chaisteps.app.tracking

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.chaisteps.app.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.coroutines.resume

object StepSensor {
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun isAvailable(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun bootMillis(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    /** Reads the cumulative step counter once, or null if unavailable / no permission / timed out. */
    suspend fun readOnce(context: Context, timeoutMs: Long = 10_000): Long? {
        if (!hasPermission(context)) return null
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0].toLong())
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }
}

/**
 * Live step counting while the app is visible or a walk is running. Users acquire/release it;
 * readings are published immediately and written to the repository at most every few seconds.
 */
class StepSensorMonitor(private val context: Context, private val repo: Repository) : SensorEventListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _reading = MutableStateFlow<Long?>(null)
    val reading: StateFlow<Long?> = _reading.asStateFlow()
    private var users = 0
    private var flushJob: Job? = null
    private var lastApplied: Long? = null

    @Synchronized
    fun acquire() {
        users++
        if (users == 1) register()
    }

    @Synchronized
    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) unregister()
    }

    private fun register() {
        if (!StepSensor.hasPermission(context)) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        flushJob = scope.launch {
            while (isActive) {
                delay(3_000)
                applyLatest()
            }
        }
    }

    private fun unregister() {
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(this)
        flushJob?.cancel()
        flushJob = null
        applyLatest()
    }

    /** Re-register after the permission has just been granted. */
    @Synchronized
    fun refresh() {
        if (users > 0) {
            unregister()
            register()
        }
    }

    private fun applyLatest() {
        val r = _reading.value ?: return
        if (r == lastApplied) return
        lastApplied = r
        repo.applyStepReading(r, StepSensor.bootMillis(), LocalDate.now().toEpochDay())
    }

    override fun onSensorChanged(event: SensorEvent) {
        _reading.value = event.values[0].toLong()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
