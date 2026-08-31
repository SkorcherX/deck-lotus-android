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

    /**
     * Crop the collector region (bottom-left) from a rectified card bitmap.
     */
    fun cropCollectorRegion(rectifiedCard: Bitmap): Bitmap {
        val width = rectifiedCard.width
        val height = rectifiedCard.height

        val x0 = max(0, (0.02 * width).roundToInt())
        val y0 = max(0, (0.86 * height).roundToInt())
        val cw = min(width - x0, (0.60 * width).roundToInt())
        val ch = min(height - y0, (0.13 * height).roundToInt())

        return Bitmap.createBitmap(rectifiedCard, x0, y0, max(1, cw), max(1, ch))
    }

    /**
     * Crop the card name/title header region (top) from a rectified card bitmap.
     */
    fun cropTitleRegion(rectifiedCard: Bitmap): Bitmap {
        val width = rectifiedCard.width
        val height = rectifiedCard.height

        val x0 = max(0, (0.05 * width).roundToInt())
        val y0 = max(0, (0.03 * height).roundToInt())
        val cw = min(width - x0, (0.80 * width).roundToInt())
        val ch = min(height - y0, (0.10 * height).roundToInt())

        return Bitmap.createBitmap(rectifiedCard, x0, y0, max(1, cw), max(1, ch))
    }

    /**
     * Run ML Kit Text Recognition asynchronously on the provided bitmap.
     */
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
     * Parse full card OCR by combining title recognition and collector block recognition.
     */
    fun parseFullCardOcr(
        titleText: Text,
        collectorText: Text,
        fullImageText: Text? = null
    ): ParsedCardOcr {
        val titleLines = titleText.textBlocks.flatMap { it.lines.map { l -> l.text.trim() } }.filter { it.isNotBlank() }
        val collectorLines = collectorText.textBlocks.flatMap { it.lines.map { l -> l.text.trim() } }.filter { it.isNotBlank() }
        val allLines = (titleLines + collectorLines + (fullImageText?.textBlocks?.flatMap { it.lines.map { l -> l.text.trim() } } ?: emptyList())).distinct()

        // Extract card name from the title header lines
        val rawName = titleLines.firstOrNull { it.length >= 3 && !it.startsWith("{") && !it.all { c -> c.isDigit() } }
            ?: allLines.firstOrNull { it.length >= 3 && !it.contains("•") && !it.contains("/") && !it.all { c -> c.isDigit() } }

        val cleanName = rawName?.replace(Regex("""[0-9/\{\}]"""), "")?.trim()

        val parsedCollector = parseRawCollectorLines(collectorLines.ifEmpty { allLines })

        return ParsedCardOcr(
            name = cleanName,
            setCode = parsedCollector.setCode,
            collectorNumber = parsedCollector.collectorNumber,
            language = parsedCollector.language,
            isFoil = parsedCollector.isFoil,
            rawLines = allLines,
            confidence = if (cleanName != null && parsedCollector.collectorNumber != null) 0.95f else 0.70f
        )
    }

    /**
     * Parse collector block strings.
     */
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
                continue
            }

            val numMatch = collectorRegex.find(clean)
            if (numMatch != null && collectorNumber == null) {
                val candidate = numMatch.groupValues[1]
                if (candidate.any { it.isDigit() }) {
                    collectorNumber = candidate
                }
            }
        }

        // Combined fallback: e.g. "WOE 0045" or "ECC 0001" or "SOA EN"
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