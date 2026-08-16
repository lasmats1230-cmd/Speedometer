package com.lasse.speedometer.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.lasse.speedometer.MainActivity
import com.lasse.speedometer.R
import com.lasse.speedometer.SpeedometerApp
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.util.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Owns the GNSS subscription and the in-progress recording.
 *
 * Runs in the foreground while recording so Android keeps delivering fixes
 * with the screen off; drops back to a plain started service when idle.
 */
class TrackingService : Service(), LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recorder = TripRecorder()

    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    private var listening = false
    private var isForeground = false
    private var settings = AppSettings()

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService()!!
        createChannel()
        scope.launch {
            app().settingsRepository.settings.collect {
                settings = it
                recorder.configure(it.minAccuracyM, it.autoPause, it.speedSource)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP_SAVE -> stopRecording(save = true)
            ACTION_STOP_DISCARD -> stopRecording(save = false)
            ACTION_IDLE_WATCH -> startIdleWatch()
            ACTION_IDLE_STOP -> stopIdleWatch()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // region commands

    private fun startRecording() {
        if (!hasLocationPermission()) {
            stopSelfIfIdle()
            return
        }
        recorder.start(System.currentTimeMillis())
        publish()
        goForeground()
        acquireWakeLock()
        requestUpdates(fast = true)
        startTicker()
    }

    private fun pauseRecording() {
        if (recorder.state.status == TrackingStatus.IDLE) return
        recorder.pause(System.currentTimeMillis())
        publish()
        updateNotification()
    }

    private fun resumeRecording() {
        if (recorder.state.status != TrackingStatus.PAUSED) return
        recorder.resume(System.currentTimeMillis())
        publish()
        updateNotification()
    }

    private fun stopRecording(save: Boolean) {
        val now = System.currentTimeMillis()
        recorder.tick(now)
        val summary = recorder.summary(now)
        val points = recorder.points.toList()

        tickerJob?.cancel()
        tickerJob = null
        releaseWakeLock()
        stopUpdates()
        leaveForeground()

        if (save && points.size >= MIN_POINTS_TO_SAVE) {
            scope.launch {
                val id = app().tripRepository.saveTrip(summary, points)
                TrackingController.publishSavedTrip(id)
                if (settings.autoSyncHealth) {
                    runCatching { app().healthConnectManager.writeTrip(id) }
                }
            }
        }

        recorder.reset()
        publish()
        stopSelfIfIdle()
    }

    private fun startIdleWatch() {
        if (recorder.state.isActive || !hasLocationPermission()) return
        requestUpdates(fast = false)
    }

    /**
     * Releases the GPS when no screen is showing a map. Without this the idle
     * subscription would outlive the UI that asked for it and quietly drain
     * the battery.
     */
    private fun stopIdleWatch() {
        if (recorder.state.isActive) return
        stopUpdates()
        stopSelf()
    }

    // endregion

    // region location

    private fun requestUpdates(fast: Boolean) {
        if (!hasLocationPermission()) return
        if (listening) stopUpdates()

        val intervalMs = if (fast) RECORDING_INTERVAL_MS else IDLE_INTERVAL_MS
        val minDistanceM = if (fast) 0f else 5f

        val providers = buildList {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        if (providers.isEmpty()) return

        try {
            providers.forEach { provider ->
                locationManager.requestLocationUpdates(
                    provider,
                    intervalMs,
                    minDistanceM,
                    this,
                    Looper.getMainLooper(),
                )
            }
            listening = true
            // Seed the map with the last known fix so it isn't blank while waiting.
            providers.firstNotNullOfOrNull { locationManager.getLastKnownLocation(it) }
                ?.takeIf { System.currentTimeMillis() - it.time < LAST_KNOWN_MAX_AGE_MS }
                ?.let { seed ->
                    if (!recorder.state.isActive) {
                        recorder.onIdleFix(seed.toFix())
                        publish()
                    }
                }
        } catch (_: SecurityException) {
            listening = false
        }
    }

    private fun stopUpdates() {
        if (!listening) return
        runCatching { locationManager.removeUpdates(this) }
        listening = false
    }

    override fun onLocationChanged(location: Location) {
        val fix = location.toFix()
        if (recorder.state.isActive) {
            recorder.onFix(fix, System.currentTimeMillis())
            updateNotification()
        } else {
            recorder.onIdleFix(fix)
        }
        publish()
    }

    /** The single place Android's Location type crosses into the recorder. */
    private fun Location.toFix() = Fix(
        timestamp = time,
        latitude = latitude,
        longitude = longitude,
        altitudeM = if (hasAltitude()) altitude else null,
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        bearingDeg = if (hasBearing()) bearing else null,
    )

    @Deprecated("Required by LocationListener on API < 30")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    // endregion

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(TICK_MS)
                recorder.tick(System.currentTimeMillis())
                publish()
                updateNotification()
            }
        }
    }

    private fun publish() = TrackingController.publish(recorder.state)

    // region foreground

    private fun goForeground() {
        if (isForeground) return
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        isForeground = true
    }

    private fun leaveForeground() {
        if (!isForeground) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        isForeground = false
    }

    private fun updateNotification() {
        if (!isForeground) return
        getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = recorder.state
        val units = settings.units
        val content = buildString {
            append(Formatters.speed(state.speedMps, units))
            append("  ·  ")
            append(Formatters.distance(state.distanceM, units))
            append("  ·  ")
            append(Formatters.duration(state.elapsedMs))
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val paused = state.status == TrackingStatus.PAUSED
        val toggleAction = if (paused) ACTION_RESUME else ACTION_PAUSE
        val toggleLabel = getString(if (paused) R.string.resume else R.string.pause)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle(
                if (paused) getString(R.string.status_paused) else getString(R.string.notif_title)
            )
            .setContentText(content)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, toggleLabel, servicePendingIntent(toggleAction, 1))
            .addAction(0, getString(R.string.stop), servicePendingIntent(ACTION_STOP_SAVE, 2))
            .build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, TrackingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
        }
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    // endregion

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "speedometer:recording")
            ?.apply { setReferenceCounted(false); acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun stopSelfIfIdle() {
        if (!recorder.state.isActive) stopSelf()
    }

    private fun app() = applicationContext as SpeedometerApp

    override fun onDestroy() {
        tickerJob?.cancel()
        releaseWakeLock()
        stopUpdates()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.lasse.speedometer.START"
        const val ACTION_PAUSE = "com.lasse.speedometer.PAUSE"
        const val ACTION_RESUME = "com.lasse.speedometer.RESUME"
        const val ACTION_STOP_SAVE = "com.lasse.speedometer.STOP_SAVE"
        const val ACTION_STOP_DISCARD = "com.lasse.speedometer.STOP_DISCARD"
        const val ACTION_IDLE_WATCH = "com.lasse.speedometer.IDLE_WATCH"
        const val ACTION_IDLE_STOP = "com.lasse.speedometer.IDLE_STOP"

        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 1000L
        private const val RECORDING_INTERVAL_MS = 1000L
        private const val IDLE_INTERVAL_MS = 3000L
        private const val LAST_KNOWN_MAX_AGE_MS = 5 * 60 * 1000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L
        private const val MIN_POINTS_TO_SAVE = 2
    }
}
