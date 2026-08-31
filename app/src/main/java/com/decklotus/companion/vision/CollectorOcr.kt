package com.decklotus.companion.vision

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Optical character recognition for MTG card title and collector block.
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
     * Parse full card OCR directly from full rectified card text recognition.
     */
    fun parseFromVisionText(visionText: Text): ParsedCardOcr {
        val allLines = visionText.textBlocks.flatMap { it.lines.map { l -> l.text.trim() } }.filter { it.isNotBlank() }

        if (allLines.isEmpty()) {
            return ParsedCardOcr()
        }

        // 1. Find title: Top-most text block (excluding pure numbers/symbols)
        val nameCandidate = allLines.firstOrNull { line ->
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
        }

        val cleanName = nameCandidate?.replace(Regex("""[0-9/\{\}]"""), "")?.trim()?.ifBlank { null }

        // 2. Find collector block (bottom lines)
        val parsedCollector = parseRawCollectorLines(allLines)

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
            rawLines = allLines,
            confidence = confidence
        )
    }

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

        val setLangRegex = Regex("""([A-Za-z0-9]{3,5})\s*[\u2022\u2219\u00B7\.\-\/]\s*([A-Za-z]{2,3})""", RegexOption.IGNORE_CASE)
        val collectorRegex = Regex("""\b(?:U|R|M|C|L|T|S)?\s*(\d{1,4}[A-Za-z]?)(?:\s*\/\s*\d{1,4})?\b""", RegexOption.IGNORE_CASE)

        for (line in lines) {
            val clean = line.replace("★", "").replace("☆", "").trim()

            val setMatch = setLangRegex.find(clean)
            if (setMatch != null && setCode == null) {
                setCode = setMatch.groupValues[1].uppercase()
                language = setMatch.groupValues[2].uppercase()
            }

            val numMatch = collectorRegex.find(clean)
            if (numMatch != null && collectorNumber == null) {
                val candidate = numMatch.groupValues[1]
                if (candidate.any { it.isDigit() }) {
                    collectorNumber = candidate
                }
            }
        }

        if (setCode == null || collectorNumber == null) {
            val combinedRegex = Regex("""\b([A-Za-z0-9]{3,5})\s+([A-Za-z0-9★†-]*\d[A-Za-z0-9★†-]*)\b""")
            for (line in lines) {
                val match = combinedRegex.find(line)
                if (match != null) {
                    if (setCode == null) setCode = match.groupValues[1].uppercase()
                    if (collectorNumber == null) collectorNumber = match.groupValues[2].replace("★", "").trim()
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