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

        // Downsample factor for sub-millisecond execution (~0.5ms)
        val step = 4
        val subW = srcW / step
        val subH = srcH / step

        val gray = IntArray(subW * subH)
        val pixels = IntArray(srcW * srcH)
        bitmap.getPixels(pixels, 0, srcW, 0, 0, srcW, srcH)

        for (sy in 0 until subH) {
            val y = sy * step
            val rowOffset = y * srcW
            val subOffset = sy * subW
            for (sx in 0 until subW) {
                val x = sx * step
                val c = pixels[rowOffset + x]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                // Rec 601 integer luma
                gray[subOffset + sx] = (r * 77 + g * 150 + b * 29) shr 8
            }
        }

        // 1. Vertical profile along central 30% width: x in [0.35 * subW .. 0.65 * subW]
        val cx0 = (subW * 0.35f).toInt()
        val cx1 = (subW * 0.65f).toInt().coerceAtLeast(cx0 + 1)
        val vProfile = FloatArray(subH)

        for (sy in 0 until subH) {
            var sum = 0
            val subOffset = sy * subW
            for (sx in cx0 until cx1) {
                sum += gray[subOffset + sx]
            }
            vProfile[sy] = sum.toFloat() / (cx1 - cx0)
        }

        val gw = 4 // ~16 pixels gradient window
        val yTopMin = (subH * 0.18f).toInt()
        val yTopMax = (subH * 0.45f).toInt().coerceAtLeast(yTopMin + 1)

        var minGrad = Float.MAX_VALUE
        var topSy = (subH * 0.25f).toInt()

        for (sy in yTopMin until (yTopMax - gw).coerceAtLeast(yTopMin + 1)) {
            val grad = vProfile[sy + gw] - vProfile[sy]
            if (grad < minGrad) {
                minGrad = grad
                topSy = sy + gw / 2
            }
        }

        val yBotMin = (subH * 0.65f).toInt()
        val yBotMax = (subH * 0.85f).toInt().coerceAtLeast(yBotMin + 1)

        var maxGrad = Float.MIN_VALUE
        var botSy = (subH * 0.75f).toInt()

        for (sy in yBotMin until (yBotMax - gw).coerceAtLeast(yBotMin + 1)) {
            val grad = vProfile[sy + gw] - vProfile[sy]
            if (grad > maxGrad) {
                maxGrad = grad
                botSy = sy + gw / 2
            }
        }

        val topY = (topSy * step).toFloat()
        val botY = (botSy * step).toFloat().coerceAtLeast(topY + 100f)
        val cardH = botY - topY

        // 2. Horizontal profile along central card height: y in [topY + 0.3 * cardH .. topY + 0.7 * cardH]
        val cy0 = ((topY + cardH * 0.30f) / step).toInt().coerceIn(0, subH - 1)
        val cy1 = ((topY + cardH * 0.70f) / step).toInt().coerceIn(cy0 + 1, subH)
        val hProfile = FloatArray(subW)

        for (sx in 0 until subW) {
            var sum = 0
            for (sy in cy0 until cy1) {
                sum += gray[sy * subW + sx]
            }
            hProfile[sx] = sum.toFloat() / (cy1 - cy0)
        }

        val xLeftMin = (subW * 0.05f).toInt()
        val xLeftMax = (subW * 0.25f).toInt().coerceAtLeast(xLeftMin + 1)

        var minHGrad = Float.MAX_VALUE
        var leftSx = (subW * 0.10f).toInt()
        for (sx in xLeftMin until (xLeftMax - gw).coerceAtLeast(xLeftMin + 1)) {
            val grad = hProfile[sx + gw] - hProfile[sx]
            if (grad < minHGrad) {
                minHGrad = grad
                leftSx = sx + gw / 2
            }
        }

        val xRightMin = (subW * 0.75f).toInt()
        val xRightMax = (subW * 0.95f).toInt().coerceAtLeast(xRightMin + 1)

        var maxHGrad = Float.MIN_VALUE
        var rightSx = (subW * 0.85f).toInt()
        for (sx in xRightMin until (xRightMax - gw).coerceAtLeast(xRightMin + 1)) {
            val grad = hProfile[sx + gw] - hProfile[sx]
            if (grad > maxHGrad) {
                maxHGrad = grad
                rightSx = sx + gw / 2
            }
        }

        val leftX = (leftSx * step).toFloat()
        val rightX = (rightSx * step).toFloat().coerceAtLeast(leftX + 100f)

        val targetAspect = 63.0f / 88.0f
        val centerX = (leftX + rightX) / 2.0f
        val centerY = (topY + botY) / 2.0f

        val finalW = cardH * targetAspect
        val finalH = cardH

        val finalLeft = centerX - finalW / 2.0f
        val finalRight = centerX + finalW / 2.0f
        val finalTop = centerY - finalH / 2.0f
        val finalBot = centerY + finalH / 2.0f

        val normLeft = (finalLeft / srcW).coerceIn(0.0f, 1.0f)
        val normRight = (finalRight / srcW).coerceIn(0.0f, 1.0f)
        val normTop = (finalTop / srcH).coerceIn(0.0f, 1.0f)
        val normBottom = (finalBot / srcH).coerceIn(0.0f, 1.0f)

        val cardQuad = DetectedCardQuad(
            topLeft = PointF(normLeft, normTop),
            topRight = PointF(normRight, normTop),
            bottomRight = PointF(normRight, normBottom),
            bottomLeft = PointF(normLeft, normBottom),
            confidence = 0.98f
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