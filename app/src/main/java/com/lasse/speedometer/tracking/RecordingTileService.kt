package com.lasse.speedometer.tracking

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.lasse.speedometer.MainActivity
import com.lasse.speedometer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Start and stop a recording from the notification shade.
 *
 * The moment worth saving is the one at the start of a ride, with gloves on
 * and the phone already half in a pocket. Two pulls and a tap beats unlocking,
 * finding the app and hitting play.
 *
 * The tile never starts a recording without location permission — there is no
 * way to ask for one from a tile — so it opens the app instead, where the
 * request has a screen to explain itself.
 */
class RecordingTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching: Job? = null

    /**
     * The tile follows the recording rather than being redrawn by hand.
     *
     * Tapping it sends an intent the service handles a moment later, so
     * refreshing straight after a tap drew the state the tile was *leaving* —
     * "Record a trip" on a tile that had just started recording.
     */
    override fun onStartListening() {
        super.onStartListening()
        watching?.cancel()
        watching = scope.launch { TrackingController.state.collect { refresh() } }
    }

    override fun onStopListening() {
        watching?.cancel()
        watching = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val state = TrackingController.state.value
        when {
            !hasLocationPermission() -> openApp()
            state.isActive -> TrackingController.stopAndSave(this)
            else -> TrackingController.start(this)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val recording = TrackingController.state.value.isActive
        tile.state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(if (recording) R.string.tile_stop else R.string.tile_start)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_recording)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (recording) R.string.tile_subtitle_recording else R.string.app_name
            )
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapseCompat(pending)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun startActivityAndCollapseCompat(pending: PendingIntent) {
        startActivityAndCollapse(pending)
    }

    private fun hasLocationPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
