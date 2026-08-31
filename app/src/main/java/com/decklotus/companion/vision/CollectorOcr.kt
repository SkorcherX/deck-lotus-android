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
        val candidateNumbers: List<String> = emptyList(),
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

        // 1. Top Zone (Top 28% of card): Card Title
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
            !line.startsWith("Planeswalker", ignoreCase = true) &&
            !line.startsWith("Battle", ignoreCase = true) &&
            !line.startsWith("Land", ignoreCase = true)
        } ?: allLinesWithBoxes.firstOrNull()?.text

        val cleanName = nameCandidate?.replace(Regex("""[0-9/\{\}]"""), "")?.trim()?.ifBlank { null }

        // 2. Bottom Zone (Bottom 15% of card): Collector Block only
        val collectorLinesWithBoxes = allLinesWithBoxes.filter { item ->
            val top = item.box?.top ?: cardHeight
            top >= cardHeight * 0.84f
        }

        val collectorLines = collectorLinesWithBoxes.map { it.text }
        val effectiveCollectorLines = collectorLines.ifEmpty { 
            allLinesWithBoxes.filter { (it.box?.top ?: 0) >= cardHeight * 0.78f }.map { it.text } 
        }
        val parsedCollector = parseRawCollectorLines(effectiveCollectorLines)

        val hasValidData = cleanName != null || parsedCollector.collectorNumber != null || parsedCollector.setCode != null
        val confidence = when {
            cleanName != null && parsedCollector.collectorNumber != null && parsedCollector.setCode != null -> 0.98f
            cleanName != null && (parsedCollector.collectorNumber != null || parsedCollector.setCode != null) -> 0.85f
            hasValidData -> 0.50f
            else -> 0.0f
        }

        Log.d(TAG, "Parsed Card: Name=\"$cleanName\", Set=\"${parsedCollector.setCode}\", Num=\"${parsedCollector.collectorNumber}\" (Candidates=${parsedCollector.candidateNumbers}), Lang=\"${parsedCollector.language}\"")

        return ParsedCardOcr(
            name = cleanName,
            setCode = parsedCollector.setCode,
            collectorNumber = parsedCollector.collectorNumber,
            candidateNumbers = parsedCollector.candidateNumbers,
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
        var language: String? = null
        var isFoil = false
        val candidateNumbers = mutableListOf<String>()

        if (fullText.contains("★") || fullText.contains("☆") || fullText.contains("*F*", ignoreCase = true) || fullText.contains("(F)", ignoreCase = true)) {
            isFoil = true
        }

        val nonSetTokens = setOf(
            "THE", "AND", "NOT", "FOR", "ALL", "SET", "NEW", "CARD", "MTG", "DECK", "WOTC", "TM", "HAS", "CAN", 
            "YOU", "GET", "ONE", "TWO", "FROM", "THAT", "THIS", "WITH", "HAVE", "DRAW", "EACH", "TURN", "LIFE", 
            "WHEN", "THEN", "LOS", "TEN", "LESS", "MORE", "COST", "COPY", "CAST", "PLAY", "DROP", "GAIN", "DEAL", 
            "TAP", "UNT", "CRE", "SOR", "INS", "ART", "LAN", "PLA"
        )

        // Pattern 1A: Set • Lang with separators (e.g. "SOA • EN", "FDN · EN", "WOE - EN", "MH3/EN", "BLB | EN", "OTJ I EN", "SOS • EN")
        val setLangRegex = Regex("""\b([A-Za-z0-9]{3,4})\s*[\u2022\u2219\u00B7\u25CF\u25AA\.\-\/\\\|I\s]\s*([A-Za-z]{2,3})\b""", RegexOption.IGNORE_CASE)

        // Pattern 1B: Merged Set + Lang without separator (e.g. "SOAEN", "SOSEN", "ECLEN", "SOAENMATTHEW", "SOSENMARIE")
        val mergedSetLangRegex = Regex("""\b([A-Za-z0-9]{3,4})(EN|JP|JA|DE|FR|IT|ES|PT|RU|KO|ZHS|ZHT|CS|CT)\b""", RegexOption.IGNORE_CASE)
        val mergedPrefixRegex = Regex("""\b([A-Za-z0-9]{3,4})(EN|JP|JA|DE|FR|IT|ES|PT|RU|KO|ZHS|ZHT|CS|CT)[A-Za-z]*\b""", RegexOption.IGNORE_CASE)

        // Pattern 2A: High-Confidence Rarity + Collector Number (e.g. "R 0052", "RO052", "M O078", "U 0045", "J O017", "C 0124", "C O017", "L 0282")
        val rarityNumRegex = Regex("""\b(?:R|M|C|U|L|S|T|P|J)\s*([0-9Oo]{1,4}[A-Za-z]?)\b""", RegexOption.IGNORE_CASE)

        // Pattern 2B: Fractional Collector Number (e.g. "0015/0280", "0123/0281", "0052/0281")
        val fractionRegex = Regex("""\b([0-9Oo]{1,4}[A-Za-z]?)\s*\/\s*(\d{2,4})\b""")

        // Pattern 2C: Padded 3/4-digit numbers including OCR letter 'O'/'o' (e.g. "0052", "O078", "O339", "O347", "O324", "0018")
        val paddedNumRegex = Regex("""\b([0-9Oo]{3,4}[A-Za-z]?)\b""")

        // Filter out copyright lines & power/toughness lines
        val filteredLines = lines.filterNot { line ->
            line.contains("Wizards", ignoreCase = true) ||
            line.contains("Coast", ignoreCase = true) ||
            line.contains("TM & ©", ignoreCase = true) ||
            line.contains("©", ignoreCase = true) ||
            line.contains("Illustrated by", ignoreCase = true) ||
            line.matches(Regex("""^\s*\d{1,2}\s*\/\s*\d{1,2}\s*$""")) // Exclude "1/1", "2/2", "3/4" P/T box
        }

        val linesToInspect = filteredLines.ifEmpty { lines }

        // Phase 1: Search for Rarity + Collector Number & Fractions
        for (line in linesToInspect) {
            val clean = line.replace("★", "").replace("☆", "").trim()

            // Check Set • Lang (explicit separator)
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

            // Check Merged Set + Lang (e.g. "SOAEN", "SOSEN", "ECLEN")
            if (setCode == null) {
                val mergedMatch = mergedSetLangRegex.find(clean) ?: mergedPrefixRegex.find(clean)
                if (mergedMatch != null) {
                    val candidateSet = mergedMatch.groupValues[1].uppercase()
                    val candidateLang = mergedMatch.groupValues[2].uppercase()
                    if (candidateSet.length in 3..4 && candidateSet !in nonSetTokens) {
                        setCode = candidateSet
                        language = candidateLang
                    }
                }
            }

            // High-confidence rarity + collector number (e.g. "R 0052", "RO052", "M O078", "J O017" -> captures "0052" and "52")
            val rarityMatch = rarityNumRegex.find(clean)
            if (rarityMatch != null) {
                val rawNum = rarityMatch.groupValues[1].replace('O', '0').replace('o', '0')
                val stripped = rawNum.trimStart('0').ifEmpty { "0" }
                if (rawNum !in candidateNumbers) candidateNumbers.add(rawNum)
                if (stripped !in candidateNumbers) candidateNumbers.add(stripped)
            }

            // Fraction match (e.g. "0015/0280" -> captures "0015" and "15")
            val fracMatch = fractionRegex.find(clean)
            if (fracMatch != null) {
                val rawNum = fracMatch.groupValues[1].replace('O', '0').replace('o', '0')
                val denom = fracMatch.groupValues[2].toIntOrNull() ?: 0
                if (denom >= 30) { // Set denominator must be >= 30, avoiding P/T like 1/1
                    val stripped = rawNum.trimStart('0').ifEmpty { "0" }
                    if (rawNum !in candidateNumbers) candidateNumbers.add(rawNum)
                    if (stripped !in candidateNumbers) candidateNumbers.add(stripped)
                }
            }

            // Padded 3/4-digit numbers (e.g. "0052", "O078", "O339", "O347", "O324")
            val padMatch = paddedNumRegex.find(clean)
            if (padMatch != null) {
                val rawNum = padMatch.groupValues[1].replace('O', '0').replace('o', '0')
                val intVal = rawNum.filter { it.isDigit() }.toIntOrNull() ?: -1
                if (intVal !in 1990..2030) { // Exclude copyright years
                    val stripped = rawNum.trimStart('0').ifEmpty { "0" }
                    if (rawNum !in candidateNumbers) candidateNumbers.add(rawNum)
                    if (stripped !in candidateNumbers) candidateNumbers.add(stripped)
                }
            }
        }

        // Phase 2: Fallback general collector numbers if none found yet
        if (candidateNumbers.isEmpty()) {
            val generalCollectorRegex = Regex("""\b(?:U|R|M|C|L|T|S|P)?\s*([0-9Oo]{1,4}[A-Za-z]?)\b""", RegexOption.IGNORE_CASE)
            for (line in linesToInspect) {
                val clean = line.replace("★", "").replace("☆", "").trim()
                val m = generalCollectorRegex.find(clean)
                if (m != null) {
                    val rawCand = m.groupValues[1].replace('O', '0').replace('o', '0')
                    val intVal = rawCand.filter { it.isDigit() }.toIntOrNull() ?: -1
                    if (intVal !in 1990..2030) { // Ignore copyright years
                        val stripped = rawCand.trimStart('0').ifEmpty { "0" }
                        if (rawCand !in candidateNumbers) candidateNumbers.add(rawCand)
                        if (stripped !in candidateNumbers) candidateNumbers.add(stripped)
                    }
                }
            }
        }

        // Fallback Set Code
        if (setCode == null) {
            val tokenRegex = Regex("""\b([A-Z0-9]{3,4})\b""")
            val nonSetTokens = setOf("THE", "AND", "NOT", "FOR", "ALL", "SET", "NEW", "CARD", "MTG", "DECK", "WOTC", "TM", "HAS", "CAN", "YOU", "GET", "ONE", "TWO")
            for (line in linesToInspect) {
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

        val primaryCollector = candidateNumbers.firstOrNull()

        return ParsedCardOcr(
            name = null,
            setCode = setCode,
            collectorNumber = primaryCollector,
            candidateNumbers = candidateNumbers.distinct(),
            language = language,
            isFoil = isFoil,
            rawLines = lines
        )
    }
}