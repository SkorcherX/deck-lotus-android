package com.decklotus.companion.camera

data class LiveCaptureMetadata(
    val exposureTimeNs: Long = 0L,
    val isoSensitivity: Int = 0,
    val focusDistanceDiopters: Float = 0.0f,
    val isAfLocked: Boolean = false,
    val isAeLocked: Boolean = false,
    val physicalCameraId: String? = null,
    val fps: Double = 0.0,
    val sensorLuma: Double = 0.0
) {
    val shutterSpeedFraction: String
        get() = if (exposureTimeNs > 0) {
            val denom = (1_000_000_000.0 / exposureTimeNs).toInt()
            "1/${denom}s"
        } else "N/A"

    val focusDistanceCm: String
        get() = if (focusDistanceDiopters > 0) {
            val cm = (100.0 / focusDistanceDiopters).toInt()
            "~${cm}cm (${String.format("%.1f", focusDistanceDiopters)} dpt)"
        } else "Infinity"
}