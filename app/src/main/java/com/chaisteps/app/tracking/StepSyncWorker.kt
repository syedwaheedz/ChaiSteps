package com.chaisteps.app.tracking

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chaisteps.app.ChaiStepsApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Samples the step counter in the background so steps land on the right day. */
class StepSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as ChaiStepsApp
        val reading = StepSensor.readOnce(applicationContext) ?: return Result.success()
        app.repository.applyStepReading(reading, StepSensor.bootMillis(), LocalDate.now().toEpochDay())
        app.repository.flush()
        return Result.success()
    }

    companion object {
        private const val NAME = "step-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<StepSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
