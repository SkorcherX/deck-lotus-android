package com.decklotus.companion.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Summary representation of a Deck from GET /api/decks and POST /api/decks.
 */
@Serializable
data class DeckSummary(
    val id: Int,
    val name: String,
    val format: String? = "commander",
    val description: String? = null,
    val status: String? = "building",
    @SerialName("mainboard_count") val mainboardCount: Int = 0,
    @SerialName("sideboard_count") val sideboardCount: Int = 0,
    @SerialName("maybeboard_count") val maybeboardCount: Int = 0,
    @SerialName("preview_image") val previewImage: String? = null,
    @SerialName("preview_name") val previewName: String? = null,
    @SerialName("traded_away_count") val tradedAwayCount: Int = 0
) {
    val totalCount: Int
        get() = mainboardCount + sideboardCount + maybeboardCount

    val formatDisplayName: String
        get() = format?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } ?: "Commander"
}

@Serializable
data class DeckListResponse(
    val decks: List<DeckSummary> = emptyList()
)

@Serializable
data class CreateDeckRequest(
    val name: String,
    val format: String = "commander",
    val description: String? = null,
    val status: String = "building"
)

@Serializable
data class CreateDeckResponse(
    val deck: DeckSummary
)

/**
 * Item specification for POST /api/scan/shortfall.
 */
@Serializable
data class ScanShortfallItem(
    val printingId: Int,
    val quantity: Int = 1,
    val isFoil: Boolean = false
)

@Serializable
data class ScanShortfallRequest(
    val items: List<ScanShortfallItem>
)

@Serializable
data class OwnershipShortfall(
    val printingId: Int,
    val isFoil: Boolean = false,
    val needed: Int = 0,
    val owned: Int = 0,
    val short: Int = 0
)

@Serializable
data class ScanShortfallResponse(
    val shortfalls: List<OwnershipShortfall> = emptyList()
)

/**
 * Item specification for POST /api/scan/commit.
 */
@Serializable
data class ScanCommitItem(
    val printingId: Int,
    val quantity: Int = 1,
    val isFoil: Boolean = false,
    val boardType: String = "mainboard", // 'mainboard', 'sideboard', 'maybeboard'
    val isCommander: Boolean = false
)

@Serializable
data class AddedToCollectionSummary(
    val batchId: String? = null,
    val cards: Int = 0,
    val committed: Int = 0
)

@Serializable
data class ScanCommitRequest(
    val destination: String, // "collection" | "deck"
    val deckId: Int? = null,
    val items: List<ScanCommitItem>,
    val alsoAddToCollection: Boolean = false
)

@Serializable
data class ScanCommitResponse(
    val batchId: String? = null,
    val deckId: Int? = null,
    val cards: Int = 0,
    val committed: Int = 0,
    val addedToCollection: AddedToCollectionSummary? = null
)

/**
 * Outcome of committing to a Deck or Collection via /api/scan/commit.
 */
data class CommitDeckOutcome(
    val committedCards: Int,
    val totalCopies: Int,
    val deckId: Int?,
    val batchId: String?,
    val addedToCollectionCopies: Int = 0,
    val transportError: String? = null,
    val isCloudflareAuthRequired: Boolean = false
) {
    val isCleanSuccess: Boolean
        get() = transportError == null && !isCloudflareAuthRequired
}
