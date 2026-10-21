package com.sruti.music.data

import android.accounts.AccountManager
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sruti_user_prefs")

class UserPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val SELECTED_THEME_ID = stringPreferencesKey("selected_theme_id")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode") // 0: OFF, 1: ALL, 2: ONE
        val LAST_PLAYED_TRACK_ID = stringPreferencesKey("last_played_track_id")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val BACKGROUND_STYLE = stringPreferencesKey("background_style") // "dynamic", "sruti_gradient", "solid_dark"
        val BACKGROUND_INTENSITY = stringPreferencesKey("background_intensity") // "subtle", "balanced", "immersive"
    }

    fun getDetectedAccountInfo(): Pair<String, String> {
        try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType("com.google")
            if (accounts.isNotEmpty()) {
                val email = accounts[0].name
                val name = when {
                    email.contains("vaishnavi", ignoreCase = true) -> "Vaishnavi L"
                    email.contains("@") -> {
                        val userPart = email.substringBefore("@")
                        userPart.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                    }
                    else -> "Music Lover"
                }
                return Pair(name, email)
            }
        } catch (_: Exception) {}
        return Pair("Vaishnavi L", "vaishnavil4433@gmail.com")
    }

    val userDisplayName: Flow<String> = context.dataStore.data.map { preferences ->
        val saved = preferences[PreferencesKeys.USER_DISPLAY_NAME]
        if (saved.isNullOrBlank() || saved.equals("Crishtofer", ignoreCase = true)) {
            getDetectedAccountInfo().first
        } else {
            saved
        }
    }

    val userEmail: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.USER_EMAIL] ?: getDetectedAccountInfo().second
    }

    suspend fun saveUserDisplayName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_DISPLAY_NAME] = name
        }
    }

    val selectedThemeId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SELECTED_THEME_ID] ?: "midnight_neon"
    }

    suspend fun saveSelectedThemeId(themeId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_THEME_ID] = themeId
        }
    }

    val shuffleEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SHUFFLE_ENABLED] ?: false
    }

    suspend fun saveShuffleEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHUFFLE_ENABLED] = enabled
        }
    }

    val repeatMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.REPEAT_MODE] ?: 0
    }

    suspend fun saveRepeatMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.REPEAT_MODE] = mode
        }
    }

    val lastPlayedTrackId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_PLAYED_TRACK_ID]
    }

    suspend fun saveLastPlayedTrackId(trackId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_PLAYED_TRACK_ID] = trackId
        }
    }

    val backgroundStyle: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BACKGROUND_STYLE] ?: "dynamic"
    }

    suspend fun saveBackgroundStyle(style: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_STYLE] = style
        }
    }

    val backgroundIntensity: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BACKGROUND_INTENSITY] ?: "balanced"
    }

    suspend fun saveBackgroundIntensity(intensity: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_INTENSITY] = intensity
        }
    }
}
