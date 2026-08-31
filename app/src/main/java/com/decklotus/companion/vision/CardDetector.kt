package com.decklotus.companion.vision

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DetectedCardQuad(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF,
    val confidence: Float,
    val collectorBox: List<PointF> = emptyList()
)

/**
 * Real-time MTG card detector.
 * Scans preview frames to locate card contours, calculate exact quad corners,
 * and project the OCR target box directly onto the card's collector region.
 */
class CardDetector {

    private var smoothedQuad: DetectedCardQuad? = null
    private val smoothingFactor = 0.35f // Exponential moving average for jitter-free tracking

    /**
     * Detect MTG card boundary from a camera frame bitmap.
     * Returns normalized coordinates [0..1] in screen/preview space.
     */
    fun detectCard(bitmap: Bitmap): DetectedCardQuad? {
        val srcW = bitmap.width
        val srcH = bitmap.height
        if (srcW <= 0 || srcH <= 0) return null

        // Downscale to 180x240 for sub-2ms analysis
        val targetW = 180
        val targetH = (targetW * (srcH.toFloat() / srcW)).roundToInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, false)

        val pixels = IntArray(targetW * targetH)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        // 1. Compute Luma and Gradient Energy map
        val luma = FloatArray(targetW * targetH)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            luma[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }

        // 2. Horizontal & Vertical projection profiles to find card edges
        val edgeThreshold = 18.0f
        val horizEnergy = FloatArray(targetW)
        val vertEnergy = FloatArray(targetH)

        for (y in 1 until targetH - 1) {
            for (x in 1 until targetW - 1) {
                val idx = y * targetW + x
                val gx = abs(luma[idx + 1] - luma[idx - 1])
                val gy = abs(luma[idx + targetW] - luma[idx - targetW])
                val g = gx + gy
                if (g > edgeThreshold) {
                    horizEnergy[x] += g
                    vertEnergy[y] += g
                }
            }
        }

        // Find bounding envelope enclosing significant edge energy
        val minX = findEdgeThreshold(horizEnergy, fromStart = true, minFraction = 0.05f)
        val maxX = findEdgeThreshold(horizEnergy, fromStart = false, minFraction = 0.05f)
        val minY = findEdgeThreshold(vertEnergy, fromStart = true, minFraction = 0.05f)
        val maxY = findEdgeThreshold(vertEnergy, fromStart = false, minFraction = 0.05f)

        if (minX == null || maxX == null || minY == null || maxY == null) {
            smoothedQuad = null
            return null
        }

        val boxW = maxX - minX
        val boxH = maxY - minY

        // Check if detected box matches MTG card proportions (aspect ~ 63/88 = 0.716) and minimum size
        val aspect = boxW.toFloat() / max(1, boxH)
        val minDimension = min(targetW, targetH)
        val isCardLike = boxW >= minDimension * 0.35f && boxH >= minDimension * 0.45f && aspect in 0.50f..0.95f

        if (!isCardLike) {
            smoothedQuad = null
            return null
        }

        // Normalize to [0..1]
        val normLeft = minX.toFloat() / targetW
        val normRight = maxX.toFloat() / targetW
        val normTop = minY.toFloat() / targetH
        val normBottom = maxY.toFloat() / targetH

        val rawQuad = DetectedCardQuad(
            topLeft = PointF(normLeft, normTop),
            topRight = PointF(normRight, normTop),
            bottomRight = PointF(normRight, normBottom),
            bottomLeft = PointF(normLeft, normBottom),
            confidence = 0.90f
        )

        // Apply EMA smoothing to prevent bounding box jitter
        val smoothed = smoothQuad(rawQuad)
        val quadWithCollector = calculateCollectorBox(smoothed)
        smoothedQuad = quadWithCollector
        return quadWithCollector
    }

    private fun findEdgeThreshold(energy: FloatArray, fromStart: Boolean, minFraction: Float): Int? {
        val totalEnergy = energy.sum()
        if (totalEnergy <= 0) return null

        val threshold = totalEnergy * minFraction
        var acc = 0.0f

        val range = if (fromStart) energy.indices else energy.indices.reversed()
        for (i in range) {
            acc += energy[i]
            if (acc >= threshold) return i
        }
        return null
    }

    private fun smoothQuad(current: DetectedCardQuad): DetectedCardQuad {
        val prev = smoothedQuad ?: return current

        fun smoothPoint(pCur: PointF, pPrev: PointF): PointF {
            return PointF(
                pPrev.x + (pCur.x - pPrev.x) * smoothingFactor,
                pPrev.y + (pCur.y - pPrev.y) * smoothingFactor
            )
        }

        return DetectedCardQuad(
            topLeft = smoothPoint(current.topLeft, prev.topLeft),
            topRight = smoothPoint(current.topRight, prev.topRight),
            bottomRight = smoothPoint(current.bottomRight, prev.bottomRight),
            bottomLeft = smoothPoint(current.bottomLeft, prev.bottomLeft),
            confidence = current.confidence
        )
    }

    private fun calculateCollectorBox(cardQuad: DetectedCardQuad): DetectedCardQuad {
        val quadPoints = listOf(cardQuad.topLeft, cardQuad.topRight, cardQuad.bottomRight, cardQuad.bottomLeft)
        val sampler = CardGeometry.projectiveMap(quadPoints)

        // Bottom-left collector block normalized coords in card space: x in [0.03..0.60], y in [0.86..0.98]
        val (p0x, p0y) = sampler(0.03, 0.86)
        val (p1x, p1y) = sampler(0.60, 0.86)
        val (p2x, p2y) = sampler(0.60, 0.98)
        val (p3x, p3y) = sampler(0.03, 0.98)

        val collectorQuad = listOf(
            PointF(p0x.toFloat(), p0y.toFloat()),
            PointF(p1x.toFloat(), p1y.toFloat()),
            PointF(p2x.toFloat(), p2y.toFloat()),
            PointF(p3x.toFloat(), p3y.toFloat())
        )

        return cardQuad.copy(collectorBox = collectorQuad)
    }
}