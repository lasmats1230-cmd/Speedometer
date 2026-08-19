package com.lasse.speedometer

import android.app.Application
import android.content.Context
import com.lasse.speedometer.data.db.SpeedometerDatabase
import com.lasse.speedometer.data.io.BackupManager
import com.lasse.speedometer.data.io.TripExporter
import com.lasse.speedometer.data.prefs.SettingsRepository
import com.lasse.speedometer.data.repo.TripRepository
import com.lasse.speedometer.health.HealthConnectManager
import com.lasse.speedometer.util.AppLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

/**
 * Holds the singletons. Small enough a dependency-injection framework would
 * cost more than it saves.
 */
class SpeedometerApp : Application() {

    private val database by lazy { SpeedometerDatabase.get(this) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val tripRepository by lazy {
        TripRepository(
            database.tripDao(),
            database.tourDao(),
            database.routeDao(),
            database.waypointDao(),
            database.tripPhotoDao(),
        )
    }

    val tripExporter by lazy { TripExporter(this) }

    val backupManager by lazy { BackupManager(this, database) }

    val healthConnectManager by lazy { HealthConnectManager(this, tripRepository) }

    /**
     * Whether the map renderer loaded. False on a device whose ABI the native
     * library does not cover, where the rest of the app still works.
     */
    var mapsAvailable = false
        private set

    override fun onCreate() {
        super.onCreate()
        AppLocale.initialise(this)
        // Must run before any MapView is constructed. The tiles need no API
        // key, so there is no token to pass here.
        //
        // Guarded because this is native code: if it cannot load, the map is
        // gone but the speedometer, the recording and the history are not, and
        // taking the whole app down at startup would lose all of them over a
        // basemap.
        mapsAvailable = runCatching { MapLibre.getInstance(this) }.isSuccess

        // Trips recorded before thumbnails were stored get theirs derived
        // once, in the background, so the history list never has to read a
        // track again.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { tripRepository.backfillSketches() }
        }
    }
}

val Context.app: SpeedometerApp get() = applicationContext as SpeedometerApp
