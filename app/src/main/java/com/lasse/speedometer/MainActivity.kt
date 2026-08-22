package com.lasse.speedometer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.data.prefs.BatterySaverMode
import com.lasse.speedometer.tracking.TrackingController
import com.lasse.speedometer.ui.ImmersiveMode
import com.lasse.speedometer.ui.nav.SpeedometerNavHost
import com.lasse.speedometer.ui.theme.SpeedometerTheme
import com.lasse.speedometer.util.AppLocale
import com.lasse.speedometer.util.BackgroundUsage
import com.lasse.speedometer.widget.SpeedometerWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val settingsState = MutableStateFlow(AppSettings())

    /**
     * Nothing to do with the result: the dialog either granted the exemption
     * or it did not, and both are the user's call. Registering a launcher is
     * simply how an activity is started from here without going through
     * `startActivity`, which would leave the back stack to sort itself out.
     */
    private val backgroundUsageDialog =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    /**
     * The launcher shortcut lands here. Recording starts only when permission
     * is already granted — a shortcut that opens straight into a permission
     * dialog is worse than one that opens the app.
     */
    private fun handleShortcut(intent: Intent?) {
        if (intent?.action != ACTION_SHORTCUT_START) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted && !TrackingController.state.value.isActive) {
            TrackingController.start(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /**
     * The widget reads the units and the weekly goal, and both are changed
     * from inside the app. Redrawing on the way out covers that without the
     * settings layer having to know a widget exists.
     */
    override fun onStop() {
        super.onStop()
        SpeedometerWidget.refresh(this)
    }

    /** Applies the in-app language before any resource is resolved. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleShortcut(intent)

        lifecycleScope.launch {
            app.settingsRepository.settings.collect { settingsState.value = it }
        }

        setContent {
            val settings by settingsState.collectAsState()
            val tracking by TrackingController.state.collectAsState()
            val immersive by ImmersiveMode.enabled.collectAsState()

            // The battery-saving readout wants the whole panel: no status bar,
            // no gesture bar. A swipe brings them back temporarily.
            LaunchedEffect(immersive) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                if (immersive) {
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            // Only hold the screen awake while something is actually
            // recording — and never in extreme mode, whose entire purpose is
            // letting the display sleep.
            val holdScreen = settings.keepScreenOn &&
                tracking.isActive &&
                settings.batterySaver != BatterySaverMode.EXTREME
            LaunchedEffect(holdScreen) {
                if (holdScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            SpeedometerTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                accent = settings.accentColor,
                pureBlack = settings.pureBlack,
            ) {
                SpeedometerNavHost(settings = settings)
            }
        }

        // After the content is set, so the dialog lands over the app rather
        // than over a blank window.
        askForUnrestrictedBackground()
    }

    /**
     * Puts Android's own unrestricted-background dialog up when the app opens
     * without the exemption.
     *
     * It is asked here rather than offered as a settings row, because the row
     * would only be able to hand the user off to the device settings; this
     * dialog decides it in place. Asking stops for good the moment the
     * exemption is held, and once per process keeps a language change — which
     * recreates the activity — from putting it up a second time.
     */
    private fun askForUnrestrictedBackground() {
        if (askedThisProcess) return
        if (BackgroundUsage.isUnrestricted(this)) return
        askedThisProcess = true
        // A build without the dialog activity would otherwise take the app
        // down on launch, which is a steep price for an optional permission.
        runCatching { backgroundUsageDialog.launch(BackgroundUsage.requestIntent(this)) }
    }

    private companion object {
        const val ACTION_SHORTCUT_START = "com.lasse.speedometer.SHORTCUT_START"

        /** Survives activity recreation, dies with the process. */
        var askedThisProcess = false
    }
}
