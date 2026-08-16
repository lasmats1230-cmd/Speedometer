package com.lasse.speedometer

import android.app.Application
import android.content.Context
import com.lasse.speedometer.data.db.SpeedometerDatabase
import com.lasse.speedometer.data.io.TripExporter
import com.lasse.speedometer.data.prefs.SettingsRepository
import com.lasse.speedometer.data.repo.TripRepository
import com.lasse.speedometer.health.HealthConnectManager
import org.osmdroid.config.Configuration

/**
 * Holds the singletons. Small enough a dependency-injection framework would
 * cost more than it saves.
 */
class SpeedometerApp : Application() {

    private val database by lazy { SpeedometerDatabase.get(this) }

    val settingsRepository by lazy { SettingsRepository(this) }

    val tripRepository by lazy {
        TripRepository(database.tripDao(), database.tourDao(), database.routeDao())
    }

    val tripExporter by lazy { TripExporter(this) }

    val healthConnectManager by lazy { HealthConnectManager(this, tripRepository) }

    override fun onCreate() {
        super.onCreate()
        configureOsmdroid()
    }

    /**
     * osmdroid needs a writable tile cache and a User-Agent that isn't the
     * default, or the OSM tile servers refuse the requests.
     */
    private fun configureOsmdroid() {
        Configuration.getInstance().load(
            this,
            getSharedPreferences("osmdroid", MODE_PRIVATE),
        )
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = cacheDir.resolve("osmdroid").apply { mkdirs() }
            osmdroidTileCache = osmdroidBasePath.resolve("tiles").apply { mkdirs() }
            // A day's worth of trips shouldn't fill the phone up.
            tileFileSystemCacheMaxBytes = 200L * 1024 * 1024
            tileFileSystemCacheTrimBytes = 150L * 1024 * 1024
        }
    }
}

val Context.app: SpeedometerApp get() = applicationContext as SpeedometerApp
