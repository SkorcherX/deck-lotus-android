package com.decklotus.companion.data

data class AppSettings(
    val baseUrl: String = "http://192.168.1.100:3000",
    val apiToken: String = "",
    val useMockServer: Boolean = true,
    val autoExposure: Boolean = true, // Default to AE for room lighting / initial setup
    val torchEnabled: Boolean = false,
    val exposureTimeNs: Long = 8_000_000L, // 1/125s default for ambient light (can lock to 1/500s in rig)
    val isoSensitivity: Int = 800, // Higher default for ambient light
    val focusDistanceDiopters: Float = 4.5f, // ~22cm fixed cradle distance
    val pinnedPhysicalCameraId: String = ""
)