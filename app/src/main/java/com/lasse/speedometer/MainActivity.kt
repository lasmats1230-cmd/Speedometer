package com.lasse.speedometer

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

    /** Applies the in-app language before any resource is resolved. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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
        /** Survives activity recreation, dies with the process. */
        var askedThisProcess = false
    }
}
