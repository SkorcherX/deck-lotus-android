package com.decklotus.companion.data

data class AppSettings(
    val baseUrl: String = "http://192.168.1.100:3000",
    val apiToken: String = "",
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
)