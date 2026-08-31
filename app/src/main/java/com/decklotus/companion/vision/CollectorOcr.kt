package com.decklotus.companion.vision

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optical character recognition with spatial zone filtering for MTG card title and collector block.
 * Uses Google ML Kit Text Recognition v2 (Tensor G5 NPU on Pixel 10 Pro).
 */
object CollectorOcr {

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
     * Parse full card OCR with strict spatial zone filtering.
     * Prevents card rules text (e.g. "turn", "opponent") from polluting set codes and numbers.
     */
    fun parseFromVisionText(visionText: Text): ParsedCardOcr {
        val allLinesWithBoxes = visionText.textBlocks.flatMap { block ->
            block.lines.map { line ->
                LineWithBox(line.text.trim(), line.boundingBox)
            }
        }.filter { it.text.isNotBlank() }

        if (allLinesWithBoxes.isEmpty()) {
            return ParsedCardOcr()
        }

        // Estimate reference card height from bounding boxes
        val maxBottom = allLinesWithBoxes.mapNotNull { it.box?.bottom }.maxOrNull() ?: 680
        val cardHeight = if (maxBottom > 100) maxBottom else 680

        // 1. Top Zone (Top 22% of card): Card Title
        val titleLines = allLinesWithBoxes.filter { item ->
            val top = item.box?.top ?: 0
            top < cardHeight * 0.25f
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
            !line.startsWith("Artifact", ignoreCase = true)
        } ?: allLinesWithBoxes.firstOrNull()?.text

        val cleanName = nameCandidate?.replace(Regex("""[0-9/\{\}]"""), "")?.trim()?.ifBlank { null }

        // 2. Bottom Zone (Bottom 18% of card): Collector Block ONLY
        val collectorLines = allLinesWithBoxes.filter { item ->
            val top = item.box?.top ?: cardHeight
            top >= cardHeight * 0.80f
        }.map { it.text }

        val parsedCollector = parseRawCollectorLines(collectorLines)

        val hasValidData = cleanName != null || parsedCollector.collectorNumber != null || parsedCollector.setCode != null
        val confidence = when {
            cleanName != null && parsedCollector.collectorNumber != null -> 0.95f
            cleanName != null || parsedCollector.collectorNumber != null -> 0.75f
            hasValidData -> 0.40f
            else -> 0.0f
        }

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

        // Standard MTG set line: e.g. "SOA • EN", "WOE • EN", "FDN • EN", "MH3-EN"
        val setLangRegex = Regex("""\b([A-Za-z0-9]{3,4})\s*[\u2022\u2219\u00B7\.\-\/]\s*([A-Za-z]{2,3})\b""", RegexOption.IGNORE_CASE)
        // Standard collector line: e.g. "U 0045", "0045", "0045/0281", "R 0124"
        val collectorRegex = Regex("""\b(?:U|R|M|C|L|T|S|P)?\s*(\d{1,4}[A-Za-z]?)(?:\s*\/\s*\d{1,4})?\b""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val clean = line.replace("★", "").replace("☆", "").trim()

            val setMatch = setLangRegex.find(clean)
            if (setMatch != null && setCode == null) {
                val candidateSet = setMatch.groupValues[1].uppercase()
                // Avoid matching short non-set words
                if (candidateSet.length in 3..4 && candidateSet.all { it.isLetterOrDigit() }) {
                    setCode = candidateSet
                    language = setMatch.groupValues[2].uppercase()
                }
            }

            val numMatch = collectorRegex.find(clean)
            if (numMatch != null && collectorNumber == null) {
                val candidate = numMatch.groupValues[1]
                if (candidate.any { it.isDigit() }) {
                    collectorNumber = candidate
                }
            }
        }

        // Fallback search for 3-letter set codes in the collector block lines
        if (setCode == null) {
            val setTokenRegex = Regex("""\b([A-Z]{3})\b""")
            for (line in lines) {
                if (line.contains("Wizards", ignoreCase = true) || line.contains("Coast", ignoreCase = true)) continue
                val match = setTokenRegex.find(line)
                if (match != null) {
                    val token = match.groupValues[1]
                    if (token !in setOf("THE", "AND", "NOT", "FOR", "ALL", "SET", "NEW")) {
                        setCode = token
                        break
                    }
                }
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