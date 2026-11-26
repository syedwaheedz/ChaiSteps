package com.chaisteps.app.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.chaisteps.app.ChaiStepsApp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.chaisteps.app.MainActivity
import com.chaisteps.app.R
import com.chaisteps.app.data.Repository
import com.chaisteps.app.data.RoutePoint
import com.chaisteps.app.domain.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground service that records a walk: GPS route, distance, time and steps. */
class WalkService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recorder: WalkRecorder? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var stepsAcquired = false
    private var fusedClient: FusedLocationProviderClient? = null
    private var fusedCallback: LocationCallback? = null
    private val app get() = application as ChaiStepsApp

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> recorder?.let { it.pause(now()); publish() }
            ACTION_RESUME -> recorder?.let { it.resume(now()); publish() }
            ACTION_STOP -> stop(save = true)
            ACTION_DISCARD -> stop(save = false)
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun now() = System.currentTimeMillis()

    @SuppressLint("MissingPermission")
    private fun start() {
        if (recorder != null) return
        if (!hasLocationPermission(this)) {
            stopSelf()
            return
        }
        createChannel(this)
        val started = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, buildNotification(null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
        }.isSuccess
        if (!started) {
            stopSelf()
            return
        }
        recorder = WalkRecorder.start(Repository.newId(), now())

        startLocationUpdates()

        app.stepMonitor.acquire()
        stepsAcquired = true
        scope.launch {
            app.stepMonitor.reading.collect { r -> if (r != null) recorder?.onStepReading(r) }
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChaiSteps:walk").apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L)
        }

        scope.launch {
            var tick = 0
            while (isActive) {
                publish()
                if (tick % 15 == 0) recorder?.let { app.walkTracker.writeCheckpoint(it.checkpoint(now())) }
                tick++
                delay(1_000)
            }
        }
    }

    /**
     * Uses Google Play services' fused location provider (the same engine Google Fit uses: GPS
     * combined with Wi-Fi, cell and motion sensors) and falls back to the raw GPS chip on phones
     * without Play services.
     */
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val playServices = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
        if (playServices) {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setWaitForAccurateLocation(true)
                .build()
            val client = LocationServices.getFusedLocationProviderClient(this)
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { onLocationChanged(it) }
                }
            }
            runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
                .onSuccess {
                    fusedClient = client
                    fusedCallback = callback
                    return
                }
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        val provider = when {
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        lm.requestLocationUpdates(provider, 2_000L, 0f, this, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedCallback?.let { cb -> fusedClient?.removeLocationUpdates(cb) }
        fusedCallback = null
        fusedClient = null
        (getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(this)
    }

    private fun publish() {
        val r = recorder ?: return
        val snapshot = r.snapshot(now())
        app.walkTracker.publish(snapshot)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(NOTIFICATION_ID, buildNotification(snapshot))
        }
    }

    private fun stop(save: Boolean) {
        val r = recorder
        recorder = null
        cleanup()
        if (r != null && save) {
            val weight = app.repository.data.value.weights.maxByOrNull { it.epochDay }?.kg
                ?: app.repository.data.value.profile.startWeightKg ?: 70.0
            val (session, route) = r.finish(now(), weight)
            app.repository.addWalk(session, route)
            app.walkTracker.finished(session)
        }
        app.walkTracker.clearCheckpoint()
        app.walkTracker.publish(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        stopLocationUpdates()
        if (stepsAcquired) {
            app.stepMonitor.release()
            stepsAcquired = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        scope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
    }

    override fun onDestroy() {
        if (recorder != null) {
            // Destroyed without an explicit stop: keep the checkpoint so the walk is recovered.
            recorder?.let { app.walkTracker.writeCheckpoint(it.checkpoint(now())) }
            recorder = null
            app.walkTracker.publish(null)
        }
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        val r = recorder ?: return
        val point = RoutePoint(
            lat = location.latitude,
            lon = location.longitude,
            timeMillis = location.time.takeIf { it > 0 } ?: now(),
            accuracyM = if (location.hasAccuracy()) location.accuracy else 50f,
            altitudeM = if (location.hasAltitude()) location.altitude else null,
        )
        r.onFix(point, if (location.hasSpeed()) location.speed.toDouble() else null)
        publish()
    }

    // Overridden explicitly: on Android 10 and older these are abstract and the system calls them.
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    private fun buildNotification(walk: ActiveWalk?): Notification {
        val unit = app.repository.data.value.profile.distanceUnit
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when {
            walk == null -> "Starting walk…"
            walk.paused -> "Walk paused"
            else -> "Walking · ${Format.duration(walk.activeMillis)}"
        }
        val text = if (walk == null) "Waiting for GPS" else
            "${Format.distance(walk.distanceM, unit)} · ${Format.steps(walk.steps)} steps · ${Format.pace(walk.paceSecPerKm, unit)}"
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_walk)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (walk != null) {
            if (walk.paused) builder.addAction(0, "Resume", action(ACTION_RESUME, 1))
            else builder.addAction(0, "Pause", action(ACTION_PAUSE, 2))
            builder.addAction(0, "Finish", action(ACTION_STOP, 3))
        }
        return builder.build()
    }

    private fun action(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, WalkService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_START = "com.chaisteps.walk.START"
        const val ACTION_PAUSE = "com.chaisteps.walk.PAUSE"
        const val ACTION_RESUME = "com.chaisteps.walk.RESUME"
        const val ACTION_STOP = "com.chaisteps.walk.STOP"
        const val ACTION_DISCARD = "com.chaisteps.walk.DISCARD"
        private const val CHANNEL_ID = "walk"
        private const val NOTIFICATION_ID = 42

        fun hasLocationPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun send(context: Context, action: String) {
            val intent = Intent(context, WalkService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        private fun createChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_walk), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.channel_walk_desc)
                    setShowBadge(false)
                },
            )
        }
    }
}
