package com.decklotus.companion.vision

import android.graphics.Bitmap
import android.graphics.PointF
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
 * Real-time MTG card detector & framing calculator.
 * Snaps bounding borders to the card in the cradle and projects the OCR collector box.
 */
class CardDetector {

    /**
     * Compute clean card quad with exact MTG 63:88 aspect ratio inside the frame.
     */
    fun detectCard(bitmap: Bitmap): DetectedCardQuad {
        val srcW = bitmap.width
        val srcH = bitmap.height

        val step = 4
        val subW = srcW / step
        val subH = srcH / step

        val rowCounts = IntArray(subH)
        val colCounts = IntArray(subW)

        val pixels = IntArray(srcW * srcH)
        bitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)

        for (sy in 0 until subH) {
            val y = sy * step
            val rowOffset = y * srcW
            for (sx in 0 until subW) {
                val x = sx * step
                val c = pixels[rowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                // Fast luma approximation
                val luma = (r * 77 + g * 150 + b * 29) shr 8
                if (luma < 140) {
                    rowCounts[sy]++
                    colCounts[sx]++
                }
            }
        }

        val minCardCols = (subW * 0.30f).toInt()
        val minCardRows = (subH * 0.20f).toInt()

        var firstRow = -1
        var lastRow = -1
        for (sy in 0 until subH) {
            if (rowCounts[sy] >= minCardCols) {
                if (firstRow == -1) firstRow = sy
                lastRow = sy
            }
        }

        var firstCol = -1
        var lastCol = -1
        for (sx in 0 until subW) {
            if (colCounts[sx] >= minCardRows) {
                if (firstCol == -1) firstCol = sx
                lastCol = sx
            }
        }

        val targetAspect = 63.0f / 88.0f
        val normLeft: Float
        val normRight: Float
        val normTop: Float
        val normBottom: Float
        val confidence: Float

        if (firstRow != -1 && lastRow != -1 && firstCol != -1 && lastCol != -1 && (lastRow - firstRow) >= minCardRows) {
            val yMin = (firstRow * step).toFloat()
            val yMax = (lastRow * step).toFloat()
            val xMin = (firstCol * step).toFloat()
            val xMax = (lastCol * step).toFloat()

            val detH = (yMax - yMin).coerceAtLeast(100f)
            val centerX = (xMin + xMax) / 2.0f
            val centerY = (yMin + yMax) / 2.0f

            var cardH = detH
            var cardW = cardH * targetAspect

            val maxAllowedW = srcW * 0.84f
            if (cardW > maxAllowedW) {
                cardW = maxAllowedW
                cardH = cardW / targetAspect
            }

            val left = centerX - cardW / 2.0f
            val right = centerX + cardW / 2.0f
            val top = centerY - cardH / 2.0f
            val bottom = centerY + cardH / 2.0f

            normLeft = (left / srcW).coerceIn(0.0f, 1.0f)
            normRight = (right / srcW).coerceIn(0.0f, 1.0f)
            normTop = (top / srcH).coerceIn(0.0f, 1.0f)
            normBottom = (bottom / srcH).coerceIn(0.0f, 1.0f)
            confidence = 0.98f
        } else {
            // Cradle placement fallback
            var cardH = srcH * 0.44f
            var cardW = cardH * targetAspect
            if (cardW > srcW * 0.82f) {
                cardW = srcW * 0.82f
                cardH = cardW / targetAspect
            }
            val left = (srcW - cardW) / 2.0f
            val top = srcH * 0.26f

            normLeft = left / srcW
            normRight = (left + cardW) / srcW
            normTop = top / srcH
            normBottom = (top + cardH) / srcH
            confidence = 0.80f
        }

        val cardQuad = DetectedCardQuad(
            topLeft = PointF(normLeft, normTop),
            topRight = PointF(normRight, normTop),
            bottomRight = PointF(normRight, normBottom),
            bottomLeft = PointF(normLeft, normBottom),
            confidence = confidence
        )

        return calculateCollectorBox(cardQuad)
    }

    private fun calculateCollectorBox(cardQuad: DetectedCardQuad): DetectedCardQuad {
        val quadPoints = listOf(cardQuad.topLeft, cardQuad.topRight, cardQuad.bottomRight, cardQuad.bottomLeft)
        val sampler = CardGeometry.projectiveMap(quadPoints)

        // Bottom-left collector block in card coordinates: x in [0.03..0.62], y in [0.86..0.98]
        val (p0x, p0y) = sampler(0.03, 0.86)
        val (p1x, p1y) = sampler(0.62, 0.86)
        val (p2x, p2y) = sampler(0.62, 0.98)
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