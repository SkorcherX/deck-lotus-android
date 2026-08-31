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
    val marketPriceUsd: Double = 0.26,
    val thumbnail: Bitmap? = null,
    val tier: String = "confident",
    val timestamp: Long = System.currentTimeMillis()
) {
    val totalItemPriceUsd: Double
        get() = marketPriceUsd * quantity
}