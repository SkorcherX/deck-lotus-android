package com.decklotus.companion.network

import kotlinx.serialization.Serializable

@Serializable
data class IngestRequest(
    val artHash: String,
    val frameHash: String,
    val ocr: IngestOcrData,
    val capture: IngestCaptureMetadata,
    val commit: IngestCommitOptions = IngestCommitOptions()
)

@Serializable
data class IngestOcrData(
    val name: String? = null,
    val setCode: String? = null,
    val collector: String? = null,
    val language: String? = null,
    val rawLines: List<String> = emptyList(),
    val confidence: Float = 0.0f
)

@Serializable
data class IngestCaptureMetadata(
    val exposureNs: Long,
    val iso: Int,
    val focusDist: Float,
    val device: String = "pixel-10-pro",
    val rig: String = "card-slinger-3.0"
)

@Serializable
data class IngestCommitOptions(
    val mode: String = "inventory",
    val deckId: String? = null,
    val isFoil: Boolean = false
)

@Serializable
data class IngestResponse(
    val tier: String, // "confident" | "probable" | "pick-printing" | "unresolved"
    val printing: IngestResolvedPrinting? = null,
    val candidates: List<IngestResolvedPrinting> = emptyList(),
    val committed: Boolean = false,
    val hashDistanceBits: Int? = null,
    val error: String? = null,
    val marketPriceUsd: Double? = 0.26
)

@Serializable
data class IngestResolvedPrinting(
    val uuid: String? = null,
    val name: String,
    val setCode: String,
    val collector: String,
    val isFoil: Boolean = false,
    val marketPriceUsd: Double? = 0.26
)

enum class ScanTier(val key: String) {
    CONFIDENT("confident"),
    PROBABLE("probable"),
    PICK_PRINTING("pick-printing"),
    UNRESOLVED("unresolved");

    companion object {
        fun fromKey(key: String): ScanTier = entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: UNRESOLVED
    }
}