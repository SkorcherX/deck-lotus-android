package com.decklotus.companion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deck_lotus_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val USE_MOCK_SERVER = booleanPreferencesKey("use_mock_server")
        val AUTO_EXPOSURE = booleanPreferencesKey("auto_exposure")
        val TORCH_ENABLED = booleanPreferencesKey("torch_enabled")
        val EXPOSURE_NS = longPreferencesKey("exposure_ns")
        val ISO = intPreferencesKey("iso")
        val FOCUS_DIST = floatPreferencesKey("focus_dist")
        val PINNED_CAMERA_ID = stringPreferencesKey("pinned_camera_id")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: "http://192.168.1.100:3000",
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            useMockServer = prefs[Keys.USE_MOCK_SERVER] ?: true,
            autoExposure = prefs[Keys.AUTO_EXPOSURE] ?: true,
            torchEnabled = prefs[Keys.TORCH_ENABLED] ?: false,
            exposureTimeNs = prefs[Keys.EXPOSURE_NS] ?: 8_000_000L,
            isoSensitivity = prefs[Keys.ISO] ?: 800,
            focusDistanceDiopters = prefs[Keys.FOCUS_DIST] ?: 4.5f,
            pinnedPhysicalCameraId = prefs[Keys.PINNED_CAMERA_ID] ?: ""
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                baseUrl = prefs[Keys.BASE_URL] ?: "http://192.168.1.100:3000",
                apiToken = prefs[Keys.API_TOKEN] ?: "",
                useMockServer = prefs[Keys.USE_MOCK_SERVER] ?: true,
                autoExposure = prefs[Keys.AUTO_EXPOSURE] ?: true,
                torchEnabled = prefs[Keys.TORCH_ENABLED] ?: false,
                exposureTimeNs = prefs[Keys.EXPOSURE_NS] ?: 8_000_000L,
                isoSensitivity = prefs[Keys.ISO] ?: 800,
                focusDistanceDiopters = prefs[Keys.FOCUS_DIST] ?: 4.5f,
                pinnedPhysicalCameraId = prefs[Keys.PINNED_CAMERA_ID] ?: ""
            )
            val updated = transform(current)
            prefs[Keys.BASE_URL] = updated.baseUrl
            prefs[Keys.API_TOKEN] = updated.apiToken
            prefs[Keys.USE_MOCK_SERVER] = updated.useMockServer
            prefs[Keys.AUTO_EXPOSURE] = updated.autoExposure
            prefs[Keys.TORCH_ENABLED] = updated.torchEnabled
            prefs[Keys.EXPOSURE_NS] = updated.exposureTimeNs
            prefs[Keys.ISO] = updated.isoSensitivity
            prefs[Keys.FOCUS_DIST] = updated.focusDistanceDiopters
            prefs[Keys.PINNED_CAMERA_ID] = updated.pinnedPhysicalCameraId
        }
    }
}