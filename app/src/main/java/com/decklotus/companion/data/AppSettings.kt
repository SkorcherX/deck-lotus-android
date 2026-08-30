package com.decklotus.companion.data

data class AppSettings(
    val baseUrl: String = "http://192.168.1.100:3000",
    val apiToken: String = "",
    val useMockServer: Boolean = true,
    val exposureTimeNs: Long = 2_000_000L, // 1/500s
    val isoSensitivity: Int = 100,
    val focusDistanceDiopters: Float = 4.5f, // ~22cm fixed cradle distance
    val pinnedPhysicalCameraId: String = ""
)