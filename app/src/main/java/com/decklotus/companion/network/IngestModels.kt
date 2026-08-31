package com.decklotus.companion.network

import kotlinx.serialization.Serializable

enum class ScanTier(val key: String) {
    CONFIDENT("confident"),
    PROBABLE("probable"),
    PICK_PRINTING("pick-printing"),
    UNRESOLVED("unresolved");

    companion object {
        fun fromKey(key: String): ScanTier = entries.firstOrNull { it.key == key } ?: UNRESOLVED
    }
}

@Serializable
data class IngestOcrData(
    val setCode: String? = null,
    val collector: String? = null,
    val language: String? = null,
    val name: String? = null,
    val rawLines: List<String> = emptyList(),
    val confidence: Float = 0.0f
)

@Serializable
data class IngestCaptureMetadata(
    val exposureNs: Long = 0,
    val iso: Int = 0,
    val focusDist: Float = 0.0f,
    val device: String = "pixel-10-pro",
    val rig: String = "card-slinger-3.0"
)

@Serializable
data class IngestCommitOptions(
    val mode: String = "inventory", // "inventory" | "deck"
    val deckId: String? = null,
    val isFoil: Boolean = false
)

@Serializable
data class IngestRequest(
    val artHash: String,
    val frameHash: String? = null,
    val ocr: IngestOcrData,
    val capture: IngestCaptureMetadata,
    val commit: IngestCommitOptions
)

@Serializable
data class IngestResolvedPrinting(
    val printingId: Int? = null,
    val uuid: String,
    val name: String,
    val setCode: String,
    val collector: String,
    val isFoil: Boolean = false,
    val marketPriceUsd: Double? = null
)

@Serializable
data class IngestResponse(
    val tier: String, // confident | probable | pick-printing | unresolved
    val printing: IngestResolvedPrinting? = null,
    val candidates: List<IngestResolvedPrinting> = emptyList(),
    val committed: Boolean = false,
    val hashDistanceBits: Int? = null,
    val marketPriceUsd: Double? = null,
    val error: String? = null
)