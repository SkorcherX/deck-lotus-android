package com.decklotus.companion.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "deck_lotus_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val API_TOKEN = stringPreferencesKey("api_token")
        val USE_MOCK_SERVER = booleanPreferencesKey("use_mock_server")
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
            exposureTimeNs = prefs[Keys.EXPOSURE_NS] ?: 2_000_000L,
            isoSensitivity = prefs[Keys.ISO] ?: 100,
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
                exposureTimeNs = prefs[Keys.EXPOSURE_NS] ?: 2_000_000L,
                isoSensitivity = prefs[Keys.ISO] ?: 100,
                focusDistanceDiopters = prefs[Keys.FOCUS_DIST] ?: 4.5f,
                pinnedPhysicalCameraId = prefs[Keys.PINNED_CAMERA_ID] ?: ""
            )
            val updated = transform(current)
            prefs[Keys.BASE_URL] = updated.baseUrl
            prefs[Keys.API_TOKEN] = updated.apiToken
            prefs[Keys.USE_MOCK_SERVER] = updated.useMockServer
            prefs[Keys.EXPOSURE_NS] = updated.exposureTimeNs
            prefs[Keys.ISO] = updated.isoSensitivity
            prefs[Keys.FOCUS_DIST] = updated.focusDistanceDiopters
            prefs[Keys.PINNED_CAMERA_ID] = updated.pinnedPhysicalCameraId
        }
    }
}