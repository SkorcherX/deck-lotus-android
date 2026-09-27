package com.decklotus.companion.data

import android.graphics.Bitmap
import java.util.UUID

/**
 * Data model for a card scanned and stored in the active session batch.
 */
data class ScannedCardItem(
    val id: String = UUID.randomUUID().toString(),
    val printingId: Int = 0,
    val name: String,
    val setCode: String,
    val collectorNumber: String,
    val language: String = "EN",
    val isFoil: Boolean = false,
    val quantity: Int = 1,
    /**
     * Null when the printing has no price. The default used to be 0.26, which
     * meant an unpriced card joined the session total as a bulk common and the
     * session total was quietly wrong by however many of those went through.
     */
    val marketPriceUsd: Double? = null,
    /** 'normal', 'foil' where that was the only row, or null. */
    val priceType: String? = null,
    val thumbnail: Bitmap? = null,
    val tier: String = "confident",
    val boardType: String = "mainboard",
    val isCommander: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** Null rather than 0 when unpriced, so a session total can say how much
     *  of itself it could not account for instead of absorbing it silently. */
    val totalItemPriceUsd: Double?
        get() = marketPriceUsd?.let { it * quantity }

    /** True when the only figure available came from the foil price row. */
    val isFoilDerivedPrice: Boolean
        get() = priceType == "foil"
}