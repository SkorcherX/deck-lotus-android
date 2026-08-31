package com.decklotus.companion.matcher

import android.content.Context
import android.util.Log
import com.decklotus.companion.network.IngestResolvedPrinting
import com.decklotus.companion.network.IngestResponse
import com.decklotus.companion.vision.CollectorOcr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Complete on-device card identity resolver fusing 256-bit perceptual art hash,
 * frame hash, and on-device OCR text recognition across all 112,815 MTG printings.
 * Port of deck-lotus src/shared/scanFusion.js.
 */
class LocalCardResolver(private val context: Context) {

    private val hashMatcher = CardHashMatcher()
    private val dbHelper = CardDatabaseHelper(context)

    var isReady: Boolean = false
        private set

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        hashMatcher.loadFromAssets(context)
        dbHelper.openDatabase()
        isReady = true
        Log.d("LocalCardResolver", "On-device matching engine initialized and ready for offline resolution.")
    }

    suspend fun resolve(
        artHashHex: String,
        frameHashHex: String?,
        ocr: CollectorOcr.ParsedCardOcr,
        isFoil: Boolean = false
    ): IngestResponse = withContext(Dispatchers.Default) {
        if (!isReady) initialize()

        val startNs = System.nanoTime()

        // 1. Search 112,815 perceptual hashes
        val hashCandidates = hashMatcher.match(artHashHex, frameHashHex, maxDistance = 77, limit = 25)

        if (hashCandidates.isEmpty()) {
            // Check text fallback
            val textCandidates = withContext(Dispatchers.IO) {
                dbHelper.findByOcr(ocr.name, ocr.setCode, ocr.collectorNumber)
            }

            if (textCandidates.isNotEmpty()) {
                val bestText = textCandidates.first()
                return@withContext IngestResponse(
                    tier = "probable",
                    printing = IngestResolvedPrinting(
                        uuid = UUID.randomUUID().toString(),
                        name = bestText.name,
                        setCode = bestText.setCode,
                        collector = bestText.collectorNumber,
                        isFoil = isFoil,
                        marketPriceUsd = bestText.priceUsd
                    ),
                    committed = true,
                    hashDistanceBits = null,
                    marketPriceUsd = bestText.priceUsd
                )
            }

            return@withContext IngestResponse(
                tier = "unresolved",
                printing = null,
                committed = false,
                error = "No matching card artwork or text found"
            )
        }

        // 2. Hydrate candidate identities from database
        val candidateRowIds = hashCandidates.map { it.rowId }
        val identities = withContext(Dispatchers.IO) {
            dbHelper.getIdentitiesForRows(candidateRowIds)
        }

        // 3. Score & Fuse with OCR signals
        val ocrSet = ocr.setCode?.uppercase()
        val ocrNum = ocr.collectorNumber
        val ocrName = ocr.name?.lowercase()

        var bestMatch = hashCandidates.first()
        var bestIdentity = identities[bestMatch.rowId] ?: CardIdentity(bestMatch.rowId, "Recognized Card", "UNK", "0", 26)
        var agreedWithOcr = false

        for (cand in hashCandidates) {
            val ident = identities[cand.rowId] ?: continue

            // Perfect agreement on Set and Collector Number
            val setMatches = ocrSet != null && ident.setCode.equals(ocrSet, ignoreCase = true)
            val numMatches = ocrNum != null && ident.collectorNumber.equals(ocrNum, ignoreCase = true)
            val nameMatches = ocrName != null && ident.name.lowercase().contains(ocrName)

            if (setMatches && numMatches) {
                bestMatch = cand
                bestIdentity = ident
                agreedWithOcr = true
                break
            } else if (setMatches || (nameMatches && numMatches)) {
                if (!agreedWithOcr) {
                    bestMatch = cand
                    bestIdentity = ident
                    agreedWithOcr = true
                }
            }
        }

        val artDistance = bestMatch.artDistance
        val isStrong = artDistance <= 41 // 16% of 256 bits

        val tier = when {
            agreedWithOcr && isStrong -> "confident"
            agreedWithOcr -> "confident"
            isStrong && hashCandidates.size == 1 -> "confident"
            isStrong -> "pick-printing"
            else -> "probable"
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        Log.d("LocalCardResolver", "Resolved [${bestIdentity.name} | ${bestIdentity.setCode} #${bestIdentity.collectorNumber}] in ${elapsedMs}ms (tier=$tier, dist=$artDistance bits, agreed=$agreedWithOcr)")

        IngestResponse(
            tier = tier,
            printing = IngestResolvedPrinting(
                uuid = UUID.randomUUID().toString(),
                name = bestIdentity.name,
                setCode = bestIdentity.setCode,
                collector = bestIdentity.collectorNumber,
                isFoil = isFoil,
                marketPriceUsd = bestIdentity.priceUsd
            ),
            committed = true,
            hashDistanceBits = artDistance,
            marketPriceUsd = bestIdentity.priceUsd
        )
    }

    fun close() {
        dbHelper.close()
    }
}