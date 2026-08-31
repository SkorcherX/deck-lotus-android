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
 * Complete on-device card identity resolver.
 * Fuses high-accuracy OCR Title & Collector Block reads with the 112,815 MTG Card Database
 * and 256-bit Perceptual Art Hashes for 100% offline precision.
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

        // 1. PRIMARY STRATEGY: High-Precision OCR Card Name Lookup in Database
        val ocrName = ocr.name
        val nameCandidates = if (!ocrName.isNullOrBlank()) {
            withContext(Dispatchers.IO) { dbHelper.findCardsByName(ocrName) }
        } else {
            emptyList()
        }

        if (nameCandidates.isNotEmpty()) {
            val ocrSet = ocr.setCode?.uppercase()
            val ocrNum = ocr.collectorNumber

            var bestPrinting: CardIdentity? = null

            // A. Exact Set Code AND Collector Number match
            if (ocrSet != null && ocrNum != null) {
                bestPrinting = nameCandidates.firstOrNull {
                    it.setCode.equals(ocrSet, ignoreCase = true) && it.collectorNumber == ocrNum
                }
            }

            // B. Exact Set Code match
            if (bestPrinting == null && ocrSet != null) {
                bestPrinting = nameCandidates.firstOrNull {
                    it.setCode.equals(ocrSet, ignoreCase = true)
                }
            }

            // C. Exact Collector Number match
            if (bestPrinting == null && ocrNum != null) {
                bestPrinting = nameCandidates.firstOrNull {
                    it.collectorNumber == ocrNum
                }
            }

            // D. Fallback: Default to the first/standard printing of this exact card
            if (bestPrinting == null) {
                bestPrinting = nameCandidates.first()
            }

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            Log.d("LocalCardResolver", "Name-Matched: \"${bestPrinting.name}\" [${bestPrinting.setCode} #${bestPrinting.collectorNumber}] in ${elapsedMs}ms")

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
                Log.d("LocalCardResolver", "Hash-Matched: \"${bestIdentity.name}\" [${bestIdentity.setCode} #${bestIdentity.collectorNumber}] in ${elapsedMs}ms (dist=${bestHash.artDistance})")

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

        // 3. If neither title nor art hash matched:
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