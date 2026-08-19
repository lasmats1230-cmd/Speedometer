package com.lasse.speedometer

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.lasse.speedometer.data.db.SpeedometerDatabase
import com.lasse.speedometer.data.io.BackupManager
import com.lasse.speedometer.data.io.PhotoGrants
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

    /**
     * For work that has to finish even though whatever asked for it is gone.
     *
     * The one that matters is saving a trip: the service launches the write
     * and then stops itself, and a scope owned by the service would be
     * cancelled out from under the write — losing the ride at the exact moment
     * the user pressed save. Nothing here is tied to a screen or a service, so
     * nothing cancels it.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
        applicationScope.launch {
            runCatching { tripRepository.backfillSketches() }
            runCatching { releaseUnusedPhotoGrants() }
        }
    }

    /**
     * Hands back the read permissions for pictures nothing points at any more.
     *
     * Attaching a photo takes a persistable grant so it still loads after a
     * restart; removing one, deleting its trip, or wiping history dropped the
     * row and kept the grant. Android caps how many an app may hold, and once
     * the cap is reached new photos silently stop surviving a restart. Doing
     * it here rather than at each deletion covers the cascades too.
     */
    private suspend fun releaseUnusedPhotoGrants() {
        val stale = PhotoGrants.stale(
            persisted = contentResolver.persistedUriPermissions.map { it.uri.toString() },
            referenced = tripRepository.allPhotoUris(),
        )
        stale.forEach { uri ->
            runCatching {
                contentResolver.releasePersistableUriPermission(
                    uri.toUri(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }
}

val Context.app: SpeedometerApp get() = applicationContext as SpeedometerApp
