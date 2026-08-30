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
 * Optical character recognition for MTG collector block (bottom-left of modern cards).
 * Uses Google ML Kit Text Recognition v2 (accelerated by Tensor G5 NPU on Pixel 10 Pro).
 */
object CollectorOcr {

    /**
     * Normalized coordinates for bottom-left collector block.
     */
    data class OcrCropBounds(
        val x: Double = 0.02,
        val y: Double = 0.88,
        val w: Double = 0.50,
        val h: Double = 0.11
    )

    data class ParsedCollector(
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
     * Crop the collector region from a rectified card bitmap.
     */
    fun cropCollectorRegion(rectifiedCard: Bitmap, bounds: OcrCropBounds = OcrCropBounds()): Bitmap {
        val width = rectifiedCard.width
        val height = rectifiedCard.height

        val x0 = max(0, (bounds.x * width).roundToInt())
        val y0 = max(0, (bounds.y * height).roundToInt())
        val cw = min(width - x0, (bounds.w * width).roundToInt())
        val ch = min(height - y0, (bounds.h * height).roundToInt())

        return Bitmap.createBitmap(rectifiedCard, x0, y0, max(1, cw), max(1, ch))
    }

    /**
     * Run ML Kit Text Recognition asynchronously on the provided bitmap crop.
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
     * Parse collector information from recognized text blocks.
     */
    fun parseOcrResult(visionText: Text): ParsedCollector {
        val rawLines = visionText.textBlocks.flatMap { block ->
            block.lines.map { it.text.trim() }
        }.filter { it.isNotEmpty() }

        return parseRawLines(rawLines)
    }

    /**
     * Pure regex parsing of OCR output lines.
     * Modern card format:
     *   Line 1: "ECC • EN" or "MH3 • EN" or "FDN • EN" or "2X2 • EN"
     *   Line 2: "0001" or "0123/0387" or "0015 ★"
     */
    fun parseRawLines(lines: List<String>): ParsedCollector {
        if (lines.isEmpty()) return ParsedCollector()

        val fullText = lines.joinToString("\n")
        var setCode: String? = null
        var collectorNumber: String? = null
        var language: String? = null
        var isFoil = false

        // Detect foil star or symbol (★, †, *F*, (F))
        if (fullText.contains("★") || fullText.contains("☆") || fullText.contains("*F*", ignoreCase = true) || fullText.contains("(F)", ignoreCase = true)) {
            isFoil = true
        }

        // Pattern 1: Set and Lang separated by bullet or space/dot
        // e.g. "ECC • EN", "MH3.EN", "FDN - EN", "BLB EN", "2X2/EN"
        val setLangRegex = Regex("""([A-Za-z0-9]{3,5})\s*[\u2022\u2219\u00B7\.\-\/]\s*([A-Za-z]{2,3})""", RegexOption.IGNORE_CASE)

        // Pattern 2: Collector number (e.g. "0001", "0123/0387", "123a", "0052b", "245★")
        val collectorRegex = Regex("""\b(\d{1,4}[A-Za-z]?)(?:\s*\/\s*\d{1,4})?\b""")

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
                // Ensure it is not mistaken for a 4-letter set code
                val candidate = numMatch.groupValues[1]
                if (candidate.any { it.isDigit() }) {
                    collectorNumber = candidate
                }
            }
        }

        // Fallback: If line contains both set and number like "ECC 0001" or "FDN 123"
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

        // Compute approximate confidence based on how many fields were resolved
        val confidence = when {
            setCode != null && collectorNumber != null && language != null -> 0.95f
            setCode != null && collectorNumber != null -> 0.90f
            setCode != null || collectorNumber != null -> 0.60f
            else -> 0.10f
        }

        return ParsedCollector(
            setCode = setCode,
            collectorNumber = collectorNumber,
            language = language,
            isFoil = isFoil,
            rawLines = lines,
            confidence = confidence
        )
    }
}