package com.decklotus.companion.network

import kotlinx.serialization.Serializable

/**
 * What a scan resolved to.
 *
 * These started life as the wire format of a POST /api/scan/ingest that the
 * server never had; the matching now happens on-device in LocalCardResolver
 * and these are its result types, which is why the request half of the pair is
 * gone. Cards reach the server through POST /api/inventory/bulk-add — see
 * DeckLotusApiClient.
 */
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
data class IngestResolvedPrinting(
    val printingId: Int? = null,
    val uuid: String,
    val name: String,
    val setCode: String,
    val collector: String,
    val isFoil: Boolean = false,
    /** Null when this printing has no price at all. Never a stand-in figure. */
    val marketPriceUsd: Double? = null,
    /** 'normal', 'foil' when that is the only row there was, or null. */
    val priceType: String? = null
)

@Serializable
data class IngestResponse(
    val tier: String, // confident | probable | pick-printing | unresolved
    val printing: IngestResolvedPrinting? = null,
    val candidates: List<IngestResolvedPrinting> = emptyList(),
    val committed: Boolean = false,
    val hashDistanceBits: Int? = null,
    val marketPriceUsd: Double? = null,
    val priceType: String? = null,
    val error: String? = null
)