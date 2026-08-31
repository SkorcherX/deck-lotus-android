package com.decklotus.companion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deck_lotus_settings")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val USER_PROFILES_JSON = stringPreferencesKey("user_profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val USE_MOCK_SERVER = booleanPreferencesKey("use_mock_server")
        val AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
        val AUTO_FOCUS = booleanPreferencesKey("auto_focus")
        val AUTO_EXPOSURE = booleanPreferencesKey("auto_exposure")
        val TORCH_ENABLED = booleanPreferencesKey("torch_enabled")
        val SHOW_DEBUG_INFO = booleanPreferencesKey("show_debug_info")
        val EXPOSURE_NS = longPreferencesKey("exposure_ns")
        val ISO = intPreferencesKey("iso")
        val FOCUS_DIST = floatPreferencesKey("focus_dist")
        val PINNED_CAMERA_ID = stringPreferencesKey("pinned_camera_id")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val legacyToken = prefs[Keys.API_TOKEN] ?: ""
        val profilesJson = prefs[Keys.USER_PROFILES_JSON]
        var profiles: List<UserProfile> = try {
            if (!profilesJson.isNullOrBlank()) {
                json.decodeFromString(profilesJson)
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        // Ensure legacy primary user profile is always preserved if not already in list
        if (legacyToken.isNotBlank() && profiles.none { it.apiToken == legacyToken }) {
            val primaryProfile = UserProfile(
                id = "primary_account",
                name = "My Account",
                apiToken = legacyToken
            )
            profiles = listOf(primaryProfile) + profiles
        }

        var activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: ""
        if (activeId.isBlank() && profiles.isNotEmpty()) {
            activeId = profiles.first().id
        } else if (profiles.none { it.id == activeId } && profiles.isNotEmpty()) {
            activeId = profiles.first().id
        }

        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: "http://192.168.1.100:3000",
            apiToken = legacyToken,
            userProfiles = profiles,
            activeProfileId = activeId,
            useMockServer = prefs[Keys.USE_MOCK_SERVER] ?: true,
            autoScanEnabled = prefs[Keys.AUTO_SCAN] ?: true,
            soundFeedbackEnabled = prefs[Keys.SOUND_FEEDBACK] ?: true,
            autoFocus = prefs[Keys.AUTO_FOCUS] ?: true,
            autoExposure = prefs[Keys.AUTO_EXPOSURE] ?: true,
            torchEnabled = prefs[Keys.TORCH_ENABLED] ?: false,
            showDebugInfo = prefs[Keys.SHOW_DEBUG_INFO] ?: false,
            exposureTimeNs = prefs[Keys.EXPOSURE_NS] ?: 2_000_000L,
            isoSensitivity = prefs[Keys.ISO] ?: 400,
            focusDistanceDiopters = prefs[Keys.FOCUS_DIST] ?: 6.5f,
            pinnedPhysicalCameraId = prefs[Keys.PINNED_CAMERA_ID] ?: ""
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val legacyToken = prefs[Keys.API_TOKEN] ?: ""
            val profilesJson = prefs[Keys.USER_PROFILES_JSON]
            var profiles: List<UserProfile> = try {
                if (!profilesJson.isNullOrBlank()) {
                    json.decodeFromString(profilesJson)
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            if (legacyToken.isNotBlank() && profiles.none { it.apiToken == legacyToken }) {
                val primaryProfile = UserProfile(
                    id = "primary_account",
                    name = "My Account",
                    apiToken = legacyToken
                )
                profiles = listOf(primaryProfile) + profiles
            }

            var activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: ""
            if (activeId.isBlank() && profiles.isNotEmpty()) {
                activeId = profiles.first().id
            }

            val current = AppSettings(
                baseUrl = prefs[Keys.BASE_URL] ?: "http://192.168.1.100:3000",
                apiToken = legacyToken,
                userProfiles = profiles,
                activeProfileId = activeId,
                useMockServer = prefs[Keys.USE_MOCK_SERVER] ?: true,
                autoScanEnabled = prefs[Keys.AUTO_SCAN] ?: true,
                soundFeedbackEnabled = prefs[Keys.SOUND_FEEDBACK] ?: true,
                autoFocus = prefs[Keys.AUTO_FOCUS] ?: true,
                autoExposure = prefs[Keys.AUTO_EXPOSURE] ?: true,
                torchEnabled = prefs[Keys.TORCH_ENABLED] ?: false,
                showDebugInfo = prefs[Keys.SHOW_DEBUG_INFO] ?: false,
                exposureTimeNs = prefs[Keys.EXPOSURE_NS] ?: 2_000_000L,
                isoSensitivity = prefs[Keys.ISO] ?: 400,
                focusDistanceDiopters = prefs[Keys.FOCUS_DIST] ?: 6.5f,
                pinnedPhysicalCameraId = prefs[Keys.PINNED_CAMERA_ID] ?: ""
            )

            val updated = transform(current)
            prefs[Keys.BASE_URL] = updated.baseUrl
            prefs[Keys.API_TOKEN] = updated.apiToken
            prefs[Keys.USER_PROFILES_JSON] = json.encodeToString(updated.userProfiles)
            prefs[Keys.ACTIVE_PROFILE_ID] = updated.activeProfileId
            prefs[Keys.USE_MOCK_SERVER] = updated.useMockServer
            prefs[Keys.AUTO_SCAN] = updated.autoScanEnabled
            prefs[Keys.SOUND_FEEDBACK] = updated.soundFeedbackEnabled
            prefs[Keys.AUTO_FOCUS] = updated.autoFocus
            prefs[Keys.AUTO_EXPOSURE] = updated.autoExposure
            prefs[Keys.TORCH_ENABLED] = updated.torchEnabled
            prefs[Keys.SHOW_DEBUG_INFO] = updated.showDebugInfo
            prefs[Keys.EXPOSURE_NS] = updated.exposureTimeNs
            prefs[Keys.ISO] = updated.isoSensitivity
            prefs[Keys.FOCUS_DIST] = updated.focusDistanceDiopters
            prefs[Keys.PINNED_CAMERA_ID] = updated.pinnedPhysicalCameraId
        }
    }
}