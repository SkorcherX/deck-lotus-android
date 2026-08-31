package com.decklotus.companion.vision

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optical character recognition for MTG card title and collector block.
 * Uses Google ML Kit Text Recognition v2 (Tensor G5 NPU on Pixel 10 Pro).
 */
object CollectorOcr {

    private const val TAG = "DeckLotusOCR"

    data class ParsedCardOcr(
        val name: String? = null,
        val setCode: String? = null,
        val collectorNumber: String? = null,
        val language: String? = null,
        val isFoil: Boolean = false,
        val rawLines: List<String> = emptyList(),
        val confidence: Float = 0.0f
    )

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): Text = suspendCancellableCoroutine { continuation ->
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText)
            }
            .addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
    }

    /**
     * Parse full card OCR with spatial zone filtering.
     */
    fun parseFromVisionText(visionText: Text): ParsedCardOcr {
        val allLinesWithBoxes = visionText.textBlocks.flatMap { block ->
            block.lines.map { line ->
                LineWithBox(line.text.trim(), line.boundingBox)
            }
        }.filter { it.text.isNotBlank() }

        if (allLinesWithBoxes.isEmpty()) {
            Log.d(TAG, "No OCR text detected on rectified frame")
            return ParsedCardOcr()
        }

        allLinesWithBoxes.forEach {
            Log.d(TAG, "Line: \"${it.text}\" @ Box: ${it.box}")
        }

        val maxBottom = allLinesWithBoxes.mapNotNull { it.box?.bottom }.maxOrNull() ?: 680
        val cardHeight = if (maxBottom > 100) maxBottom else 680

        // 1. Top Zone (Top 25% of card): Card Title
        val titleLines = allLinesWithBoxes.filter { item ->
            val top = item.box?.top ?: 0
            top < cardHeight * 0.28f
        }.map { it.text }

        val nameCandidate = titleLines.firstOrNull { line ->
            line.length >= 3 &&
            !line.startsWith("{") &&
            !line.contains("•") &&
            !line.contains("/") &&
            !line.all { it.isDigit() } &&
            !line.startsWith("Instant", ignoreCase = true) &&
            !line.startsWith("Sorcery", ignoreCase = true) &&
            !line.startsWith("Creature", ignoreCase = true) &&
            !line.startsWith("Enchantment", ignoreCase = true) &&
            !line.startsWith("Artifact", ignoreCase = true) &&
            !line.startsWith("Land", ignoreCase = true)
        } ?: allLinesWithBoxes.firstOrNull()?.text

        val cleanName = nameCandidate?.replace(Regex("""[0-9/\{\}]"""), "")?.trim()?.ifBlank { null }

        // 2. Bottom Zone (Bottom 30% of card): Collector Block
        val collectorLines = allLinesWithBoxes.filter { item ->
            val top = item.box?.top ?: cardHeight
            top >= cardHeight * 0.70f
        }.map { it.text }

        // If bottom zone was empty, try all lines as fallback
        val effectiveCollectorLines = collectorLines.ifEmpty { allLinesWithBoxes.map { it.text } }
        val parsedCollector = parseRawCollectorLines(effectiveCollectorLines)

        val hasValidData = cleanName != null || parsedCollector.collectorNumber != null || parsedCollector.setCode != null
        val confidence = when {
            cleanName != null && parsedCollector.collectorNumber != null && parsedCollector.setCode != null -> 0.98f
            cleanName != null && (parsedCollector.collectorNumber != null || parsedCollector.setCode != null) -> 0.85f
            hasValidData -> 0.50f
            else -> 0.0f
        }

        Log.d(TAG, "Parsed Card: Name=\"$cleanName\", Set=\"${parsedCollector.setCode}\", Num=\"${parsedCollector.collectorNumber}\", Lang=\"${parsedCollector.language}\"")

        return ParsedCardOcr(
            name = cleanName,
            setCode = parsedCollector.setCode,
            collectorNumber = parsedCollector.collectorNumber,
            language = parsedCollector.language,
            isFoil = parsedCollector.isFoil,
            rawLines = allLinesWithBoxes.map { it.text },
            confidence = confidence
        )
    }

    private data class LineWithBox(val text: String, val box: Rect?)

    fun parseRawCollectorLines(lines: List<String>): ParsedCardOcr {
        if (lines.isEmpty()) return ParsedCardOcr()

        val fullText = lines.joinToString("\n")
        var setCode: String? = null
        var collectorNumber: String? = null
        var language: String? = null
        var isFoil = false

        if (fullText.contains("★") || fullText.contains("☆") || fullText.contains("*F*", ignoreCase = true) || fullText.contains("(F)", ignoreCase = true)) {
            isFoil = true
        }

        // Pattern 1: Set • Lang (e.g. "SOA • EN", "FDN · EN", "WOE - EN", "MH3/EN", "BLB | EN", "OTJ I EN", "SOA EN")
        val setLangRegex = Regex("""\b([A-Za-z0-9]{3,4})\s*[\u2022\u2219\u00B7\u25CF\u25AA\.\-\/\\\|I\s]\s*([A-Za-z]{2,3})\b""", RegexOption.IGNORE_CASE)
        // Pattern 2: Collector number (e.g. "0045", "045/281", "U 0045", "R 0124", "124/281")
        val collectorRegex = Regex("""\b(?:U|R|M|C|L|T|S|P)?\s*(\d{1,4}[A-Za-z]?)(?:\s*\/\s*\d{1,4})?\b""", RegexOption.IGNORE_CASE)

        val nonSetTokens = setOf("THE", "AND", "NOT", "FOR", "ALL", "SET", "NEW", "CARD", "MTG", "DECK", "WOTC", "TM", "HAS", "CAN", "YOU", "GET", "ONE", "TWO")

        for (line in lines) {
            val clean = line.replace("★", "").replace("☆", "").trim()

            // Try set • lang match
            if (setCode == null) {
                val setMatch = setLangRegex.find(clean)
                if (setMatch != null) {
                    val candidateSet = setMatch.groupValues[1].uppercase()
                    val candidateLang = setMatch.groupValues[2].uppercase()
                    if (candidateSet.length in 3..4 && candidateSet !in nonSetTokens && candidateLang in setOf("EN", "JP", "JA", "DE", "FR", "IT", "ES", "PT", "RU", "KO", "ZHS", "ZHT", "CS", "CT")) {
                        setCode = candidateSet
                        language = candidateLang
                    }
                }
            }

            // Try collector number match
            if (collectorNumber == null) {
                val numMatch = collectorRegex.find(clean)
                if (numMatch != null) {
                    val candidate = numMatch.groupValues[1]
                    if (candidate.any { it.isDigit() } && !candidate.startsWith("202")) { // Avoid matching copyright years like 2024
                        collectorNumber = candidate
                    }
                }
            }
        }

        // Pattern 3: Fallback standalone 3-uppercase-letter code in the bottom block
        if (setCode == null) {
            val tokenRegex = Regex("""\b([A-Z0-9]{3,4})\b""")
            for (line in lines) {
                if (line.contains("Wizards", ignoreCase = true) || line.contains("Coast", ignoreCase = true)) continue
                for (match in tokenRegex.findAll(line)) {
                    val token = match.groupValues[1]
                    if (token !in nonSetTokens && token.any { it.isLetter() }) {
                        setCode = token
                        break
                    }
                }
                if (setCode != null) break
            }
        }

        return ParsedCardOcr(
            name = null,
            setCode = setCode,
            collectorNumber = collectorNumber,
            language = language,
            isFoil = isFoil,
            rawLines = lines
        )
    }
}