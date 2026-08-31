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
 * Multi-Modal Fusion: Fuses Tensor G5 OCR reads with the 112,815 MTG Card Database and 256-bit Perceptual Hashes.
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

        // 1. Gather Candidate Printings (via OCR Title OR Visual Art Hash fallback)
        val candidates: List<CardIdentity> = withContext(Dispatchers.IO) {
            val nameCandidates = if (!ocr.name.isNullOrBlank()) {
                val direct = dbHelper.findCardsByName(ocr.name)
                if (direct.isNotEmpty()) direct else dbHelper.findBestMatchingCardFromLines(ocr.rawLines)
            } else {
                dbHelper.findBestMatchingCardFromLines(ocr.rawLines)
            }

            if (nameCandidates.isNotEmpty()) {
                nameCandidates
            } else {
                // Fallback: If OCR title missed entirely, search 112,815 perceptual hashes
                val hashMatches = hashMatcher.match(artHashHex, frameHashHex, maxDistance = 77, limit = 20)
                if (hashMatches.isNotEmpty()) {
                    val rowIds = hashMatches.map { it.rowId }
                    val idMap = dbHelper.getIdentitiesForRows(rowIds)
                    rowIds.mapNotNull { idMap[it] }
                } else emptyList()
            }
        }

        if (candidates.isNotEmpty()) {
            val allOcrText = ocr.rawLines.joinToString(" ").uppercase()
            val ocrSetExplicit = ocr.setCode?.uppercase()
            val ocrNumExplicit = ocr.collectorNumber?.trimStart('0')?.ifEmpty { "0" }
            val ocrNumRaw = ocr.collectorNumber

            var bestPrinting: CardIdentity = candidates.first()
            var highestScore = -9999
            var bestArtDist = 256
            var bestHasArtMatch = false
            var bestHasSetMatch = false
            var bestHasNumMatch = false

            for (cand in candidates) {
                var score = 0
                val candSet = cand.setCode.uppercase()
                val candNum = cand.collectorNumber.trimStart('0').ifEmpty { "0" }

                // A. 256-bit Perceptual Art Hash Verification
                val artDist = if (artHashHex.isNotBlank()) hashMatcher.getArtDistance(cand.rowId, artHashHex) else 256
                val hasArtMatch = artDist <= 77

                when {
                    artDist <= 38 -> score += 100 // Exact illustration match (< 15% bit error)
                    artDist <= 56 -> score += 75  // Strong illustration match (< 22%)
                    artDist <= 77 -> score += 40  // Valid illustration match (< 30%)
                    artDist >= 85 -> score -= 60  // Major penalty for different illustration (e.g. anime/showcase art vs regular)
                }

                // B. Set Code Match
                val hasSetMatch: Boolean
                if (ocrSetExplicit != null && candSet == ocrSetExplicit) {
                    score += 70
                    hasSetMatch = true
                } else if (allOcrText.contains(Regex("""\b$candSet\b"""))) {
                    score += 45
                    hasSetMatch = true
                } else {
                    hasSetMatch = false
                }

                // C. Collector Number Match
                val hasNumMatch: Boolean
                if (ocrNumRaw != null && cand.collectorNumber.equals(ocrNumRaw, ignoreCase = true)) {
                    score += 75
                    hasNumMatch = true
                } else if (ocrNumExplicit != null && candNum == ocrNumExplicit) {
                    score += 70
                    hasNumMatch = true
                } else if (allOcrText.contains(Regex("""\b(?:0*)$candNum\b"""))) {
                    score += 40
                    hasNumMatch = true
                } else {
                    // Check slight OCR typo on number (e.g. 018 vs 148, edit distance 1)
                    if (ocrNumExplicit != null && isNumTypo(candNum, ocrNumExplicit)) {
                        score += 25
                    }
                    hasNumMatch = false
                }

                // D. Session Set Bias tie-breaker
                val biasCount = setBiasTally[candSet] ?: 0
                if (biasCount > 0) {
                    score += kotlin.math.min(20, biasCount * 5)
                }

                if (score > highestScore) {
                    highestScore = score
                    bestPrinting = cand
                    bestArtDist = artDist
                    bestHasArtMatch = hasArtMatch
                    bestHasSetMatch = hasSetMatch
                    bestHasNumMatch = hasNumMatch
                }
            }

            val tier = when {
                (bestHasArtMatch && bestHasSetMatch) || (bestHasSetMatch && bestHasNumMatch) -> "confident"
                bestHasArtMatch || bestHasSetMatch || bestHasNumMatch -> "probable"
                else -> "pick-printing"
            }

            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
            Log.d("LocalCardResolver", "Multi-Modal Resolved: \"${bestPrinting.name}\" (id=${bestPrinting.printingId}) [${bestPrinting.setCode} #${bestPrinting.collectorNumber}] (artDist=$bestArtDist, score=$highestScore, tier=$tier) in ${elapsedMs}ms")

            return@withContext IngestResponse(
                tier = tier,
                printing = IngestResolvedPrinting(
                    printingId = bestPrinting.printingId,
                    uuid = UUID.randomUUID().toString(),
                    name = bestPrinting.name,
                    setCode = bestPrinting.setCode,
                    collector = bestPrinting.collectorNumber,
                    isFoil = isFoil,
                    marketPriceUsd = bestPrinting.priceUsd
                ),
                marketPriceUsd = bestPrinting.priceUsd,
                hashDistanceBits = if (bestArtDist < 256) bestArtDist else null
            )
        }

        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        Log.d("LocalCardResolver", "Unresolved card scan in ${elapsedMs}ms")

        IngestResponse(
            tier = "unresolved",
            printing = null,
            error = "Could not identify card from OCR and Art Hash"
        )
    }

    private fun isNumTypo(a: String, b: String): Boolean {
        if (a == b) return true
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        var diff = 0
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            if (a[i] != b[j]) {
                diff++
                if (diff > 1) return false
                if (a.length > b.length) i++
                else if (b.length > a.length) j++
                else { i++; j++ }
            } else {
                i++; j++
            }
        }
        return true
    }

    fun close() {
        try {
            dbHelper.close()
        } catch (_: Exception) {}
        isReady = false
    }
}