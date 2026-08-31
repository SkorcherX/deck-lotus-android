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
 * High-precision on-device MTG card identity resolver.
 * Fuses Tensor G5 OCR reads with the 112,815 MTG Card Database and 256-bit Perceptual Hashes.
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
        isFoil: Boolean = false,
        setBiasTally: Map<String, Int> = emptyMap()
    ): IngestResponse = withContext(Dispatchers.Default) {
        if (!isReady) initialize()

        val startNs = System.nanoTime()

        // 1. PRIMARY: Discover verified MTG card name across all recognized lines
        val candidates = withContext(Dispatchers.IO) {
            if (!ocr.name.isNullOrBlank()) {
                val direct = dbHelper.findCardsByName(ocr.name)
                if (direct.isNotEmpty()) return@withContext direct
            }
            dbHelper.findBestMatchingCardFromLines(ocr.rawLines)
        }

        if (candidates.isNotEmpty()) {
            val allOcrText = ocr.rawLines.joinToString(" ").uppercase()
            val ocrSetExplicit = ocr.setCode?.uppercase()
            val ocrNumExplicit = ocr.collectorNumber?.trimStart('0')?.ifEmpty { "0" }

            var bestPrinting: CardIdentity = candidates.first()
            var highestScore = -1

            for (cand in candidates) {
                var score = 0
                val candSet = cand.setCode.uppercase()
                val candNum = cand.collectorNumber.trimStart('0').ifEmpty { "0" }

                // A. Explicit Set Code match
                if (ocrSetExplicit != null && candSet == ocrSetExplicit) {
                    score += 60
                } else if (allOcrText.contains(Regex("""\b$candSet\b"""))) {
                    score += 40
                }

                // B. Collector Number match (with and without leading zeroes)
                if (ocrNumExplicit != null && candNum == ocrNumExplicit) {
                    score += 60
                } else if (allOcrText.contains(Regex("""\b(?:0*)$candNum\b"""))) {
                    score += 35
                }

                // C. Session Set Bias tie-breaker (cards in a stack typically share sets)
                val biasCount = setBiasTally[candSet] ?: 0
                if (biasCount > 0) {
                    score += kotlin.math.min(25, biasCount * 5)
                }

                if (score > highestScore) {
                    highestScore = score
                    bestPrinting = cand
                }
            }

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            Log.d("LocalCardResolver", "Resolved by Database Verification: \"${bestPrinting.name}\" (id=${bestPrinting.printingId}) [${bestPrinting.setCode} #${bestPrinting.collectorNumber}] (score=$highestScore) in ${elapsedMs}ms")

            return@withContext IngestResponse(
                tier = "confident",
                printing = IngestResolvedPrinting(
                    printingId = bestPrinting.printingId,
                    uuid = UUID.randomUUID().toString(),
                    name = bestPrinting.name,
                    setCode = bestPrinting.setCode,
                    collector = bestPrinting.collectorNumber,
                    isFoil = isFoil,
                    marketPriceUsd = bestPrinting.priceUsd
                ),
                committed = true,
                hashDistanceBits = 0,
                marketPriceUsd = bestPrinting.priceUsd
            )
        }

        // 2. SECONDARY: Global Art Hash Search across all 112,815 prints
        val hashCandidates = hashMatcher.match(artHashHex, frameHashHex, maxDistance = 55, limit = 20)

        if (hashCandidates.isNotEmpty()) {
            val candidateRowIds = hashCandidates.map { it.rowId }
            val identities = withContext(Dispatchers.IO) {
                dbHelper.getIdentitiesForRows(candidateRowIds)
            }

            val bestHash = hashCandidates.first()
            val bestIdentity = identities[bestHash.rowId]

            if (bestIdentity != null) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                Log.d("LocalCardResolver", "Resolved by Art Hash: \"${bestIdentity.name}\" (id=${bestIdentity.printingId}) [${bestIdentity.setCode} #${bestIdentity.collectorNumber}] in ${elapsedMs}ms (dist=${bestHash.artDistance})")

                return@withContext IngestResponse(
                    tier = if (bestHash.artDistance <= 41) "confident" else "pick-printing",
                    printing = IngestResolvedPrinting(
                        printingId = bestIdentity.printingId,
                        uuid = UUID.randomUUID().toString(),
                        name = bestIdentity.name,
                        setCode = bestIdentity.setCode,
                        collector = bestIdentity.collectorNumber,
                        isFoil = isFoil,
                        marketPriceUsd = bestIdentity.priceUsd
                    ),
                    committed = true,
                    hashDistanceBits = bestHash.artDistance,
                    marketPriceUsd = bestIdentity.priceUsd
                )
            }
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        Log.d("LocalCardResolver", "Unresolved after ${elapsedMs}ms")

        IngestResponse(
            tier = "unresolved",
            printing = null,
            committed = false,
            error = "Card not recognized"
        )
    }

    fun close() {
        dbHelper.close()
    }
}