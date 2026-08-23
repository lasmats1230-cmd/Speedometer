package com.lasse.speedometer.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import com.lasse.speedometer.data.db.WaypointEntity
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.widget.SpeedometerWidget
import com.lasse.speedometer.util.AppLocale
import com.lasse.speedometer.util.LocaleFormats
import com.lasse.speedometer.util.Formatters
import com.lasse.speedometer.util.GeoMath
import com.lasse.speedometer.util.TripNaming
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

    /**
     * Everything that touches the recorder runs here.
     *
     * Fixes arrive on the main looper, so the ticker, the persistence pass and
     * the stop handler have to as well: the recorder holds a plain list and a
     * plain state object, and reading them from a background thread while a
     * fix appends to them is a torn read at best and a
     * ConcurrentModificationException mid-ride at worst. The work itself is a
     * few sums and a list copy once every ten seconds; the database calls
     * switch to IO inside the repository.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val recorder = TripRecorder()
    private val voice by lazy { VoiceCoach(this) }

    /**
     * Resources here resolve in the app's own language, not the system's —
     * the recording notification and the spoken updates are as much part of
     * the app as any screen.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var tickerJob: Job? = null
    private var listening = false
    private var isForeground = false
    private var lastNotificationAt = 0L
    private var settings = AppSettings()

    /** Saved places, for the proximity alert. */
    private var waypoints: List<WaypointEntity> = emptyList()

    /** Waypoints already called out on this trip, so each is named once. */
    private val announcedWaypoints = mutableSetOf<Long>()

    /** The database row this recording is being written into. */
    @Volatile
    private var recordingTripId: Long? = null

    /**
     * The insert that opens that row. Stopping waits on it: a start followed
     * immediately by a stop used to read a null id, save a second trip, and
     * leave the first row marked in progress forever — offered back as a
     * recovery prompt for a ride that never happened.
     */
    private var openRowJob: Job? = null
    private var lastPersistAt = 0L

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
        scope.launch {
            app().tripRepository.waypoints.collect { waypoints = it }
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
            ACTION_MARK_WAYPOINT -> markWaypoint()
            // START_STICKY hands back a null intent when the system restarts
            // the service. There is no recording to resume — that is what the
            // in-progress row is for — so it should not sit there running.
            else -> stopSelfIfIdle()
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
        val startedAt = System.currentTimeMillis()
        recorder.start(startedAt)
        announcedWaypoints.clear()
        voice.reset()
        if (settings.voiceIntervalM > 0) voice.sayStarted()

        // The trip is written to the database from the first second rather
        // than held in memory until save: a service the system kills mid-ride
        // should cost seconds, not the whole ride.
        val activity = settings.activity
        val title = TripNaming.titleFor(this, startedAt, activity)
        lastPersistAt = 0L
        recordingTripId = null
        openRowJob = scope.launch {
            recordingTripId = app().tripRepository.startRecording(startedAt, activity, title)
        }
        publish()
        goForeground()
        acquireWakeLock()
        requestUpdates(fast = true)
        startTicker()
    }

    private fun pauseRecording() {
        if (recorder.state.status == TrackingStatus.IDLE) return
        recorder.pause(System.currentTimeMillis())
        if (settings.voiceIntervalM > 0) voice.sayPaused()
        publish()
        updateNotification(force = true)
    }

    private fun resumeRecording() {
        if (recorder.state.status != TrackingStatus.PAUSED) return
        recorder.resume(System.currentTimeMillis())
        if (settings.voiceIntervalM > 0) voice.sayResumed()
        publish()
        updateNotification(force = true)
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

        val keeping = save && points.size >= MIN_POINTS_TO_SAVE
        if (keeping && settings.voiceIntervalM > 0) voice.sayFinished()
        val autoSync = settings.autoSyncHealth
        val activity = settings.activity
        val title = TripNaming.titleFor(this, summary.startedAt, activity)
        val opening = openRowJob
        val application = app()

        // On the application's scope, not the service's: stopSelfIfIdle below
        // destroys this service, and a write cancelled halfway through is the
        // ride the user just pressed save on.
        application.applicationScope.launch {
            // The row is opened asynchronously, so a start immediately
            // followed by a stop can arrive before the insert has run.
            opening?.join()
            val tripId = recordingTripId
            val repository = application.tripRepository
            when {
                // No row at all, which now only means the insert failed. A
                // plain insert keeps that ride rather than dropping it.
                tripId == null -> if (keeping) {
                    val id = repository.saveTrip(summary, points, activity, title)
                    TrackingController.publishSavedTrip(id)
                    SpeedometerWidget.refresh(application)
                    if (autoSync) {
                        runCatching { application.healthConnectManager.writeTrip(id) }
                    }
                }

                keeping -> {
                    repository.finishRecording(tripId, summary, points)
                    TrackingController.publishSavedTrip(tripId)
                    SpeedometerWidget.refresh(application)
                    if (autoSync) {
                        runCatching { application.healthConnectManager.writeTrip(tripId) }
                    }
                }
                // Too short to keep, or discarded outright: the row goes with
                // it, and its points go with the row.
                else -> repository.discardRecording(tripId)
            }
            recordingTripId = null
        }
        openRowJob = null

        recorder.reset()
        publish()
        stopSelfIfIdle()
    }

    /**
     * Saves the spot the recording is at, from the notification.
     *
     * Marking a water tap or a turning otherwise means stopping, unlocking the
     * phone, finding the map and long-pressing it — by which point you are a
     * hundred metres past the thing you wanted to remember. The label carries
     * the time, since naming it properly can wait until later.
     */
    private fun markWaypoint() {
        val state = recorder.state
        val latitude = state.latitude ?: return
        val longitude = state.longitude ?: return
        val label = getString(
            R.string.waypoint_marked,
            LocaleFormats.format("HH:mm", System.currentTimeMillis()),
        )
        buzz()
        scope.launch {
            runCatching {
                app().tripRepository.addWaypoint(
                    label = label,
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        }
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

        val gpsAvailable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val providers = buildList {
            if (gpsAvailable) add(LocationManager.GPS_PROVIDER)
            // Network fixes are worth having while idle — they put the map
            // somewhere before the first satellite lands. Mixed into a
            // recording they are poison: the position comes from whichever
            // cell or access point answered, it repeats unchanged for as long
            // as that stays true, and it arrives interleaved with real fixes
            // wearing an accuracy good enough to pass the gate. The recorder
            // then sees the ride stop and restart. Satellites only, whenever
            // there are satellites to be had.
            val wantNetwork = !fast || !gpsAvailable
            if (wantNetwork && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
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
            alertOnNearbyWaypoint(fix)
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
        speedAccuracyMps = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
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
                announceProgress()
                persistProgress()
                publish()
                updateNotification()
            }
        }
    }

    private fun publish() = TrackingController.publish(recorder.state)

    /**
     * Buzzes once when a saved waypoint comes within reach, and names it if
     * spoken updates are on.
     *
     * The point of a waypoint is that it is useful again on a later ride — a
     * water tap, a locked gate, a turning that is easy to miss — and a note
     * you have to be looking at the screen to see is no use on a bicycle.
     */
    private fun alertOnNearbyWaypoint(fix: Fix) {
        if (!settings.waypointAlerts || waypoints.isEmpty()) return
        val nearby = waypoints.firstOrNull { waypoint ->
            waypoint.id !in announcedWaypoints &&
                GeoMath.distanceMeters(
                    fix.latitude,
                    fix.longitude,
                    waypoint.latitude,
                    waypoint.longitude,
                ) <= WAYPOINT_ALERT_RADIUS_M
        } ?: return

        announcedWaypoints += nearby.id
        buzz()
        if (settings.voiceIntervalM > 0) {
            voice.say(getString(R.string.waypoint_nearby, nearby.label))
        }
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<android.os.VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<android.os.Vibrator>()
        } ?: return
        if (!vibrator.hasVibrator()) return
        vibrator.vibrate(
            android.os.VibrationEffect.createOneShot(
                WAYPOINT_BUZZ_MS,
                android.os.VibrationEffect.DEFAULT_AMPLITUDE,
            )
        )
    }

    /**
     * Appends whatever the recorder has gathered since the last write.
     *
     * Every few seconds rather than every fix: the write is an append of a
     * handful of rows, and doing it once a second would spin the disk for a
     * whole ride to save at most a second of track.
     */
    private fun persistProgress() {
        val tripId = recordingTripId ?: return
        if (recorder.state.status == TrackingStatus.IDLE) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastPersistAt < PERSIST_INTERVAL_MS) return
        lastPersistAt = now

        val summary = recorder.summary(System.currentTimeMillis())
        val points = recorder.points.toList()
        scope.launch {
            runCatching { app().tripRepository.appendRecording(tripId, summary, points) }
        }
    }

    /** Hands the current figures to the voice coach, which decides if it speaks. */
    private fun announceProgress() {
        val interval = settings.voiceIntervalM
        if (interval <= 0.0 || recorder.state.status != TrackingStatus.RECORDING) return
        val state = recorder.state
        voice.onProgress(
            distanceM = state.distanceM,
            elapsedMs = state.elapsedMs,
            avgSpeedMps = state.avgSpeedMps,
            intervalM = interval,
            units = settings.units,
            prefersPace = settings.activity.prefersPace,
        )
    }

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

    /**
     * Rebuilding a notification and handing it to the system every second is
     * real work for a panel nobody is looking at. Extreme mode stretches that
     * out; the GNSS subscription is untouched either way, so what lands in the
     * database is identical.
     */
    private fun updateNotification(force: Boolean = false) {
        if (!isForeground) return
        val now = android.os.SystemClock.elapsedRealtime()
        val interval = if (settings.batterySaver == BatterySaverMode.EXTREME) {
            EXTREME_NOTIFICATION_INTERVAL_MS
        } else {
            NOTIFICATION_INTERVAL_MS
        }
        if (!force && now - lastNotificationAt < interval) return
        lastNotificationAt = now
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
            // The app's own glyph rather than a platform menu icon: a
            // notification small icon is drawn from its alpha channel alone,
            // and the stock drawables are not designed for that.
            .setSmallIcon(R.drawable.ic_notification_recording)
            .setContentTitle(
                when {
                    paused -> getString(R.string.status_paused)
                    // Naming the activity makes the shade readable at a
                    // glance when several things are running.
                    else -> getString(
                        R.string.notif_title_activity,
                        getString(settings.activity.labelRes),
                    )
                }
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
            .addAction(
                0,
                getString(R.string.waypoint_mark),
                servicePendingIntent(ACTION_MARK_WAYPOINT, 3),
            )
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
        voice.shutdown()
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
        const val ACTION_MARK_WAYPOINT = "com.lasse.speedometer.MARK_WAYPOINT"

        private const val CHANNEL_ID = "trip_recording"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_MS = 1000L
        private const val NOTIFICATION_INTERVAL_MS = 2_000L
        private const val EXTREME_NOTIFICATION_INTERVAL_MS = 20_000L
        private const val RECORDING_INTERVAL_MS = 1000L
        private const val IDLE_INTERVAL_MS = 3000L
        private const val LAST_KNOWN_MAX_AGE_MS = 5 * 60 * 1000L
        private const val WAKE_LOCK_TIMEOUT_MS = 12 * 60 * 60 * 1000L
        private const val MIN_POINTS_TO_SAVE = 2
        private const val PERSIST_INTERVAL_MS = 10_000L

        /** Close enough that you can still act on being told. */
        private const val WAYPOINT_ALERT_RADIUS_M = 60.0
        private const val WAYPOINT_BUZZ_MS = 180L
    }
}
