package com.decklotus.companion.vision

import android.graphics.Bitmap
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Geometric constants and projective warping for card rectification.
 * Parity with deck-lotus `src/shared/cardGeometry.js`.
 */
object CardGeometry {
    /** Magic card standard aspect ratio: 63mm x 88mm */
    const val CARD_ASPECT: Double = 63.0 / 88.0

    /** Height in pixels that a card is rectified to for hashing (matches reference index). */
    const val HASH_HEIGHT: Int = 680

    /** The width corresponding to HASH_HEIGHT. (487 pixels) */
    val HASH_WIDTH: Int = (HASH_HEIGHT * CARD_ASPECT).roundToInt()

    /** Framing ladder probes used during scan capture. */
    val FRAMING_LADDER: FloatArray = floatArrayOf(0.84f, 0.88f, 0.92f, 0.96f, 1.00f)

    /**
     * Map unit square (u, v) in [0..1] onto an arbitrary quadrilateral [p0, p1, p2, p3].
     * p0: Top-Left, p1: Top-Right, p2: Bottom-Right, p3: Bottom-Left.
     */
    fun projectiveMap(quad: List<PointF>): (Double, Double) -> Pair<Double, Double> {
        require(quad.size == 4) { "Quad must have exactly 4 corner points" }
        val p0 = quad[0]
        val p1 = quad[1]
        val p2 = quad[2]
        val p3 = quad[3]

        val dx1 = (p1.x - p2.x).toDouble()
        val dx2 = (p3.x - p2.x).toDouble()
        val dy1 = (p1.y - p2.y).toDouble()
        val dy2 = (p3.y - p2.y).toDouble()
        val sx = (p0.x - p1.x + p2.x - p3.x).toDouble()
        val sy = (p0.y - p1.y + p2.y - p3.y).toDouble()

        val a11: Double
        val a21: Double
        val a31: Double
        val a12: Double
        val a22: Double
        val a32: Double
        val a13: Double
        val a23: Double

        if (abs(sx) < 1e-12 && abs(sy) < 1e-12) {
            // Affine mapping (parallelogram)
            a11 = (p1.x - p0.x).toDouble()
            a21 = (p2.x - p1.x).toDouble()
            a31 = p0.x.toDouble()
            a12 = (p1.y - p0.y).toDouble()
            a22 = (p2.y - p1.y).toDouble()
            a32 = p0.y.toDouble()
            a13 = 0.0
            a23 = 0.0
        } else {
            val den = dx1 * dy2 - dx2 * dy1
            a13 = (sx * dy2 - dx2 * sy) / den
            a23 = (dx1 * sy - sx * dy1) / den
            a11 = (p1.x - p0.x).toDouble() + a13 * p1.x.toDouble()
            a21 = (p3.x - p0.x).toDouble() + a23 * p3.x.toDouble()
            a31 = p0.x.toDouble()
            a12 = (p1.y - p0.y).toDouble() + a13 * p1.y.toDouble()
            a22 = (p3.y - p0.y).toDouble() + a23 * p3.y.toDouble()
            a32 = p0.y.toDouble()
        }

        return { u: Double, v: Double ->
            val denom = a13 * u + a23 * v + 1.0
            val x = (a11 * u + a21 * v + a31) / denom
            val y = (a12 * u + a22 * v + a32) / denom
            Pair(x, y)
        }
    }

    /**
     * Warps a source bitmap into a rectified card of targetWidth x targetHeight (default 487x680).
     */
    fun warpQuad(
        source: Bitmap,
        quad: List<PointF>,
        targetWidth: Int = HASH_WIDTH,
        targetHeight: Int = HASH_HEIGHT
    ): Bitmap {
        val outBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val srcPixels = IntArray(source.width * source.height)
        source.getPixels(srcPixels, 0, source.width, 0, 0, source.width, source.height)

        val outPixels = IntArray(targetWidth * targetHeight)
        val sampler = projectiveMap(quad)

        val srcW = source.width
        val srcH = source.height

        for (dy in 0 until targetHeight) {
            val v = dy.toDouble() / (targetHeight - 1).coerceAtLeast(1)
            for (dx in 0 until targetWidth) {
                val u = dx.toDouble() / (targetWidth - 1).coerceAtLeast(1)
                val (sx, sy) = sampler(u, v)

                val ix = sx.roundToInt().coerceIn(0, srcW - 1)
                val iy = sy.roundToInt().coerceIn(0, srcH - 1)

                outPixels[dy * targetWidth + dx] = srcPixels[iy * srcW + ix]
            }
        }

        outBitmap.setPixels(outPixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)
        return outBitmap
    }
}