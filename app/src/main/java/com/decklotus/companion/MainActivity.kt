package com.decklotus.companion

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.decklotus.companion.ui.capture.CaptureScreen
import com.decklotus.companion.ui.capture.CaptureViewModel
import com.decklotus.companion.ui.settings.SettingsScreen
import com.decklotus.companion.ui.settings.SettingsViewModel
import com.decklotus.companion.ui.theme.BackgroundDark
import com.decklotus.companion.ui.theme.DeckLotusTheme

enum class Screen {
    CAPTURE,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val captureViewModel: CaptureViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep cradle viewfinder awake and prevent screen dimming/locking during scan sessions
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            DeckLotusTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundDark
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.CAPTURE) }

                    Crossfade(targetState = currentScreen, label = "ScreenTransition") { screen ->
                        when (screen) {
                            Screen.CAPTURE -> CaptureScreen(
                                viewModel = captureViewModel,
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                            )
                            Screen.SETTINGS -> SettingsScreen(
                                viewModel = settingsViewModel,
                                onNavigateBack = {
                                    captureViewModel.reloadResolver()
                                    currentScreen = Screen.CAPTURE
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}