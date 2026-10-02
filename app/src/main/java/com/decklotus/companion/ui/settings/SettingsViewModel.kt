package com.decklotus.companion.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.decklotus.companion.data.AppSettings
import com.decklotus.companion.data.SettingsRepository
import com.decklotus.companion.data.UserProfile
import com.decklotus.companion.matcher.CardDatabaseHelper
import com.decklotus.companion.matcher.DatabaseStats
import com.decklotus.companion.network.DeckLotusApiClient
import com.decklotus.companion.network.ServerConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DatabaseSyncStatus {
    object Idle : DatabaseSyncStatus()
    data class Syncing(val step: String, val progress: Float) : DatabaseSyncStatus()
    data class Success(val message: String, val timestamp: Long) : DatabaseSyncStatus()
    data class Error(val message: String) : DatabaseSyncStatus()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository(application)
    val apiClient = DeckLotusApiClient()
    private val dbHelper = CardDatabaseHelper(application)

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _connectionStatus = MutableStateFlow<ServerConnectionStatus>(ServerConnectionStatus.Idle)
    val connectionStatus: StateFlow<ServerConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isPortalOpen = MutableStateFlow(false)
    val isPortalOpen: StateFlow<Boolean> = _isPortalOpen.asStateFlow()

    private val _dbStats = MutableStateFlow(DatabaseStats())
    val dbStats: StateFlow<DatabaseStats> = _dbStats.asStateFlow()

    private val _syncStatus = MutableStateFlow<DatabaseSyncStatus>(DatabaseSyncStatus.Idle)
    val syncStatus: StateFlow<DatabaseSyncStatus> = _syncStatus.asStateFlow()

    init {
        loadDatabaseStats()
    }

    fun loadDatabaseStats() {
        viewModelScope.launch {
            val stats = dbHelper.getStats()
            _dbStats.value = stats
        }
    }

    fun syncCardDatabase() {
        val current = settings.value
        if (current.baseUrl.isBlank()) {
            _syncStatus.value = DatabaseSyncStatus.Error("Set Server Base URL first")
            return
        }

        viewModelScope.launch {
            _syncStatus.value = DatabaseSyncStatus.Syncing("Connecting to server...", 0.02f)
            val result = apiClient.syncCardResources(
                baseUrl = current.baseUrl,
                token = current.effectiveToken,
                context = getApplication()
            ) { step, progress ->
                _syncStatus.value = DatabaseSyncStatus.Syncing(step, progress)
            }

            result.fold(
                onSuccess = { summary ->
                    dbHelper.reload()
                    val updatedStats = dbHelper.getStats()
                    _dbStats.value = updatedStats
                    _syncStatus.value = DatabaseSyncStatus.Success(
                        "✓ Synced ${summary.printingsCount} card printings & ${summary.hashesCount} art hashes (${summary.totalBytes / 1024} KB)",
                        System.currentTimeMillis()
                    )
                },
                onFailure = { error ->
                    _syncStatus.value = DatabaseSyncStatus.Error(error.localizedMessage ?: "Sync failed")
                }
            )
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun selectActiveProfile(profileId: String) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(activeProfileId = profileId) }
            testConnection()
        }
    }

    fun addProfile(name: String, token: String) {
        viewModelScope.launch {
            val newProfile = UserProfile(name = name.trim(), apiToken = token.trim())
            repository.updateSettings { current ->
                val list = current.userProfiles + newProfile
                val activeId = if (current.activeProfileId.isBlank()) newProfile.id else current.activeProfileId
                current.copy(userProfiles = list, activeProfileId = activeId)
            }
            verifyProfile(newProfile.id)
        }
    }

    fun updateProfile(id: String, name: String, token: String) {
        viewModelScope.launch {
            repository.updateSettings { current ->
                val list = current.userProfiles.map {
                    if (it.id == id) it.copy(name = name.trim(), apiToken = token.trim()) else it
                }
                current.copy(userProfiles = list)
            }
            verifyProfile(id)
        }
    }

    fun deleteProfile(id: String) {
        viewModelScope.launch {
            repository.updateSettings { current ->
                val list = current.userProfiles.filterNot { it.id == id }
                val nextActive = if (current.activeProfileId == id) list.firstOrNull()?.id ?: "" else current.activeProfileId
                current.copy(userProfiles = list, activeProfileId = nextActive)
            }
        }
    }

    fun verifyProfile(profileId: String) {
        val current = settings.value
        val profile = current.userProfiles.firstOrNull { it.id == profileId } ?: return
        viewModelScope.launch {
            val result = apiClient.testConnection(current.baseUrl, profile.apiToken)
            if (result is ServerConnectionStatus.Connected && !result.username.isNullOrBlank()) {
                repository.updateSettings { curr ->
                    val updatedList = curr.userProfiles.map {
                        if (it.id == profileId) it.copy(verifiedUsername = result.username) else it
                    }
                    curr.copy(userProfiles = updatedList)
                }
            }
        }
    }

    fun testConnection() {
        val current = settings.value
        _connectionStatus.value = ServerConnectionStatus.Checking
        viewModelScope.launch {
            val result = apiClient.testConnection(current.baseUrl, current.effectiveToken)
            _connectionStatus.value = result
            if (result is ServerConnectionStatus.Connected && !result.username.isNullOrBlank()) {
                val activeId = current.activeProfileId
                if (activeId.isNotBlank()) {
                    repository.updateSettings { curr ->
                        val updatedList = curr.userProfiles.map {
                            if (it.id == activeId) it.copy(verifiedUsername = result.username) else it
                        }
                        curr.copy(userProfiles = updatedList)
                    }
                }
            }
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