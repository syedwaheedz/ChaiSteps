package com.chaisteps.app

import android.app.Application
import com.chaisteps.app.data.Repository
import com.chaisteps.app.tracking.StepSensorMonitor
import com.chaisteps.app.tracking.StepSyncWorker
import com.chaisteps.app.tracking.WalkTracker
import org.osmdroid.config.Configuration
import java.io.File

class ChaiStepsApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var walkTracker: WalkTracker
        private set
    lateinit var stepMonitor: StepSensorMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        val dir = File(filesDir, "data")
        repository = Repository(dir)
        walkTracker = WalkTracker(dir)
        stepMonitor = StepSensorMonitor(this, repository)
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(cacheDir, "osmdroid/tiles")
        }
        // WorkManager may be unavailable in some test environments; step sync is best-effort.
        runCatching { StepSyncWorker.schedule(this) }
    }
}
