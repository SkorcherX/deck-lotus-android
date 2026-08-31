package com.decklotus.companion.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.decklotus.companion.data.AppSettings
import com.decklotus.companion.data.SettingsRepository
import com.decklotus.companion.network.DeckLotusApiClient
import com.decklotus.companion.network.ServerConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val apiClient = DeckLotusApiClient()

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _connectionStatus = MutableStateFlow<ServerConnectionStatus>(ServerConnectionStatus.Idle)
    val connectionStatus: StateFlow<ServerConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isPortalOpen = MutableStateFlow(false)
    val isPortalOpen: StateFlow<Boolean> = _isPortalOpen.asStateFlow()

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun testConnection() {
        val current = settings.value
        _connectionStatus.value = ServerConnectionStatus.Checking
        viewModelScope.launch {
            val result = apiClient.testConnection(current.baseUrl, current.apiToken)
            _connectionStatus.value = result
        }
    }

    fun openCloudflarePortal() {
        _isPortalOpen.value = true
    }

    fun closeCloudflarePortal() {
        _isPortalOpen.value = false
    }

    fun onCloudflareAuthSuccess() {
        _isPortalOpen.value = false
        testConnection()
    }
}