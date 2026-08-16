package com.lasse.speedometer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.lasse.speedometer.data.prefs.AppSettings
import com.lasse.speedometer.tracking.TrackingController
import com.lasse.speedometer.ui.nav.SpeedometerNavHost
import com.lasse.speedometer.ui.theme.SpeedometerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val settingsState = MutableStateFlow(AppSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            app.settingsRepository.settings.collect { settingsState.value = it }
        }

        setContent {
            val settings by settingsState.collectAsState()
            val tracking by TrackingController.state.collectAsState()

            // Only hold the screen awake while something is actually recording.
            LaunchedEffect(settings.keepScreenOn, tracking.isActive) {
                if (settings.keepScreenOn && tracking.isActive) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            SpeedometerTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
            ) {
                SpeedometerNavHost(settings = settings)
            }
        }
    }
}
