package com.sruti.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import com.sruti.music.data.UserPreferencesRepository
import com.sruti.music.playback.PlaybackStateManager
import com.sruti.music.theme.SrutiTheme
import com.sruti.music.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        PlaybackStateManager.init(this)

        setContent {
            val prefsRepo = remember { UserPreferencesRepository(applicationContext) }
            val themeId by prefsRepo.selectedThemeId.collectAsState(initial = "midnight_neon")

            SrutiTheme {
                MainScreen(themeId = themeId)
            }
        }
    }
}
