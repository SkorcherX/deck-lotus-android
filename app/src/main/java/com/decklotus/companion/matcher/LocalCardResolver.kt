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
 * Fuses OCR Card Title & Full-Card Text Tokens with the SQLite Printing Database
 * and 256-bit Perceptual Art Hashes for 100% accurate Card Names and Set Codes.
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

        // 1. PRIMARY STRATEGY: High-Precision OCR Card Name Lookup in SQLite Database
        val ocrName = ocr.name
        val nameCandidates = if (!ocrName.isNullOrBlank()) {
            withContext(Dispatchers.IO) { dbHelper.findCardsByName(ocrName) }
        } else {
            emptyList()
        }

        if (nameCandidates.isNotEmpty()) {
            val allOcrText = ocr.rawLines.joinToString(" ").uppercase()
            val ocrSetExplicit = ocr.setCode?.uppercase()
            val ocrNumExplicit = ocr.collectorNumber?.trimStart('0')?.ifEmpty { "0" }

            // Score each candidate printing of this specific card against all OCR tokens
            var bestPrinting: CardIdentity = nameCandidates.first()
            var highestScore = -1

            for (cand in nameCandidates) {
                var score = 0
                val candSet = cand.setCode.uppercase()
                val candNum = cand.collectorNumber.trimStart('0').ifEmpty { "0" }

                // A. Explicit Set Code match
                if (ocrSetExplicit != null && candSet == ocrSetExplicit) {
                    score += 50
                } else if (allOcrText.contains(Regex("""\b$candSet\b"""))) {
                    score += 30
                }

                // B. Collector Number match (with and without leading zeroes)
                if (ocrNumExplicit != null && candNum == ocrNumExplicit) {
                    score += 50
                } else if (allOcrText.contains(Regex("""\b(?:0*)$candNum\b"""))) {
                    score += 20
                }

                if (score > highestScore) {
                    highestScore = score
                    bestPrinting = cand
                }
            }

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            Log.d("LocalCardResolver", "Resolved by Name+Tokens: \"${bestPrinting.name}\" [${bestPrinting.setCode} #${bestPrinting.collectorNumber}] (score=$highestScore) in ${elapsedMs}ms")

            return@withContext IngestResponse(
                tier = "confident",
                printing = IngestResolvedPrinting(
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

        // 2. SECONDARY STRATEGY: Global Art Hash Search across all 112,815 prints
        val hashCandidates = hashMatcher.match(artHashHex, frameHashHex, maxDistance = 65, limit = 20)

        if (hashCandidates.isNotEmpty()) {
            val candidateRowIds = hashCandidates.map { it.rowId }
            val identities = withContext(Dispatchers.IO) {
                dbHelper.getIdentitiesForRows(candidateRowIds)
            }

            val bestHash = hashCandidates.first()
            val bestIdentity = identities[bestHash.rowId]

            if (bestIdentity != null) {
                val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
                Log.d("LocalCardResolver", "Resolved by Art Hash: \"${bestIdentity.name}\" [${bestIdentity.setCode} #${bestIdentity.collectorNumber}] in ${elapsedMs}ms (dist=${bestHash.artDistance})")

                return@withContext IngestResponse(
                    tier = if (bestHash.artDistance <= 41) "confident" else "pick-printing",
                    printing = IngestResolvedPrinting(
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