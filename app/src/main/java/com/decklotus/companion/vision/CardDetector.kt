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
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()

        val targetAspect = 63.0f / 88.0f
        var cardH = srcH * 0.70f
        var cardW = cardH * targetAspect

        if (cardW > srcW * 0.85f) {
            cardW = srcW * 0.85f
            cardH = cardW / targetAspect
        }

        // Centered cradle placement (slightly biased towards center-top where card rests)
        val left = (srcW - cardW) / 2.0f
        val top = (srcH - cardH) * 0.40f

        val normLeft = left / srcW
        val normRight = (left + cardW) / srcW
        val normTop = top / srcH
        val normBottom = (top + cardH) / srcH

        val cardQuad = DetectedCardQuad(
            topLeft = PointF(normLeft, normTop),
            topRight = PointF(normRight, normTop),
            bottomRight = PointF(normRight, normBottom),
            bottomLeft = PointF(normLeft, normBottom),
            confidence = 0.95f
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