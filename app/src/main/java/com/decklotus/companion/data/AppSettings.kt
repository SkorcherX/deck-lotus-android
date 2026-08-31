package com.decklotus.companion.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val apiToken: String,
    val verifiedUsername: String? = null
)

data class AppSettings(
    val baseUrl: String = "http://192.168.1.100:3000",
    val apiToken: String = "",
    val userProfiles: List<UserProfile> = emptyList(),
    val activeProfileId: String = "",
    val useMockServer: Boolean = true,
    val autoScanEnabled: Boolean = true, // Continuous hands-free auto-scan
    val soundFeedbackEnabled: Boolean = true, // Zero-latency audio chime on card ingest
    val autoFocus: Boolean = true, // Default to continuous AF for handheld testing
    val autoExposure: Boolean = true, // Default to AE for room lighting
    val torchEnabled: Boolean = false,
    val exposureTimeNs: Long = 2_000_000L, // 1/500s when in manual mode
    val isoSensitivity: Int = 400,
    val focusDistanceDiopters: Float = 6.5f, // ~15cm fixed cradle distance
    val pinnedPhysicalCameraId: String = ""
) {
    val activeProfile: UserProfile?
        get() = userProfiles.firstOrNull { it.id == activeProfileId } ?: userProfiles.firstOrNull()

    val effectiveToken: String
        get() = activeProfile?.apiToken?.ifBlank { apiToken } ?: apiToken

    val displayName: String
        get() = activeProfile?.let { p ->
            if (!p.verifiedUsername.isNullOrBlank()) "${p.name} (@${p.verifiedUsername})" else p.name
        } ?: "Primary User"
}