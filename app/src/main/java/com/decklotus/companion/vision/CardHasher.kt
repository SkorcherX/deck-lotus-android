package com.decklotus.companion.vision

import android.graphics.Bitmap
import java.math.BigInteger
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 256-bit DCT art hash and 64-bit frame hash.
 * 100% mathematical parity with deck-lotus `src/shared/cardHash.js`.
 */
object CardHasher {
    const val GRID: Int = 32
    const val ART_BLOCK: Int = 16
    const val FRAME_BLOCK: Int = 8

    const val ART_HASH_HEX: Int = (ART_BLOCK * ART_BLOCK) / 4 // 64
    const val FRAME_HASH_HEX: Int = (FRAME_BLOCK * FRAME_BLOCK) / 4 // 16
    const val ART_HASH_BYTES: Int = ART_HASH_HEX / 2 // 32
    const val FRAME_HASH_BYTES: Int = FRAME_HASH_HEX / 2 // 8

    data class RectWindow(val x: Double, val y: Double, val w: Double, val h: Double)

    val ART_WINDOW = RectWindow(0.08, 0.11, 0.84, 0.44)
    val WHOLE_WINDOW = RectWindow(0.0, 0.0, 1.0, 1.0)

    data class CardHashes(val artHash: String, val frameHash: String)

    /**
     * Precomputed cosine table for 32-point DCT-II:
     * table[u * 32 + x] = cos(((2 * x + 1) * u * PI) / (2 * 32))
     */
    private val COS_TABLE: DoubleArray = DoubleArray(GRID * GRID).apply {
        for (u in 0 until GRID) {
            for (x in 0 until GRID) {
                this[u * GRID + x] = cos(((2.0 * x + 1.0) * u * Math.PI) / (2.0 * GRID))
            }
        }
    }

    /**
     * Represents image data with width, height and RGBA / ARGB pixel buffer.
     */
    class ImageBuffer(val width: Int, val height: Int, val getLuma: (x: Int, y: Int) -> Double) {
        companion object {
            fun fromBitmap(bitmap: Bitmap): ImageBuffer {
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                return fromArgbPixels(pixels, w, h)
            }

            fun fromArgbPixels(pixels: IntArray, width: Int, height: Int): ImageBuffer {
                return ImageBuffer(width, height) { x, y ->
                    val c = pixels[y * width + x]
                    val r = (c shr 16) and 0xFF
                    val g = (c shr 8) and 0xFF
                    val b = c and 0xFF
                    // Rec. 601 luma
                    r * 0.299 + g * 0.587 + b * 0.114
                }
            }

            fun fromRgbaBytes(data: ByteArray, width: Int, height: Int): ImageBuffer {
                return ImageBuffer(width, height) { x, y ->
                    val p = (y * width + x) * 4
                    val r = data[p].toInt() and 0xFF
                    val g = data[p + 1].toInt() and 0xFF
                    val b = data[p + 2].toInt() and 0xFF
                    r * 0.299 + g * 0.587 + b * 0.114
                }
            }
        }
    }

    /**
     * Area-average an image region down to a 32x32 grayscale grid.
     */
    fun downsampleToGrid(
        image: ImageBuffer,
        window: RectWindow,
        glareCut: Double? = null
    ): DoubleArray {
        val width = image.width
        val height = image.height

        val x0 = max(0, floor(window.x * width).toInt())
        val y0 = max(0, floor(window.y * height).toInt())
        val x1 = min(width, ceil((window.x + window.w) * width).toInt())
        val y1 = min(height, ceil((window.y + window.h) * height).toInt())

        val boxWidth = max(1, x1 - x0)
        val boxHeight = max(1, y1 - y0)

        val grid = DoubleArray(GRID * GRID)

        for (gy in 0 until GRID) {
            val sy0 = y0 + floor((gy.toDouble() * boxHeight) / GRID).toInt()
            val sy1 = max(sy0 + 1, y0 + floor(((gy + 1.0) * boxHeight) / GRID).toInt())

            for (gx in 0 until GRID) {
                val sx0 = x0 + floor((gx.toDouble() * boxWidth) / GRID).toInt()
                val sx1 = max(sx0 + 1, x0 + floor(((gx + 1.0) * boxWidth) / GRID).toInt())

                var sum = 0.0
                var count = 0
                var allSum = 0.0
                var allCount = 0

                val maxSy = min(sy1, height)
                val maxSx = min(sx1, width)

                for (sy in sy0 until maxSy) {
                    for (sx in sx0 until maxSx) {
                        val luma = image.getLuma(sx, sy)
                        allSum += luma
                        allCount++
                        if (glareCut != null && luma >= glareCut) continue
                        sum += luma
                        count++
                    }
                }

                grid[gy * GRID + gx] = when {
                    count > 0 -> sum / count
                    allCount > 0 -> allSum / allCount
                    else -> 0.0
                }
            }
        }

        return grid
    }

    /**
     * Separable 2-D DCT-II (rows then columns).
     */
    fun dct2d(grid: DoubleArray): DoubleArray {
        val rows = DoubleArray(GRID * GRID)

        for (y in 0 until GRID) {
            val yOffset = y * GRID
            for (u in 0 until GRID) {
                var sum = 0.0
                val uOffset = u * GRID
                for (x in 0 until GRID) {
                    sum += grid[yOffset + x] * COS_TABLE[uOffset + x]
                }
                rows[yOffset + u] = sum
            }
        }

        val out = DoubleArray(GRID * GRID)

        for (u in 0 until GRID) {
            for (v in 0 until GRID) {
                var sum = 0.0
                val vOffset = v * GRID
                for (y in 0 until GRID) {
                    sum += rows[y * GRID + u] * COS_TABLE[vOffset + y]
                }
                out[v * GRID + u] = sum
            }
        }

        return out
    }

    /**
     * Sign the low-frequency block against its own median (excluding the DC coefficient at [0,0]).
     */
    fun signBlock(coefficients: DoubleArray, block: Int): BigInteger {
        val values = ArrayList<Double>((block * block) - 1)
        for (v in 0 until block) {
            for (u in 0 until block) {
                if (u == 0 && v == 0) continue
                values.add(coefficients[v * GRID + u])
            }
        }

        val sorted = values.sorted()
        val mid = sorted.size / 2
        val median = if (sorted.size % 2 != 0) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }

        var bits = BigInteger.ZERO
        for (value in values) {
            bits = bits.shiftLeft(1)
            if (value > median) {
                bits = bits.or(BigInteger.ONE)
            }
        }

        // Single trailing zero so it lands on a whole number of bytes (64 bits / 256 bits)
        return bits.shiftLeft(1)
    }

    fun toHex(bits: BigInteger, width: Int): String {
        return bits.toString(16).padStart(width, '0').lowercase()
    }

    fun fromHex(hex: String): BigInteger {
        val clean = if (hex.startsWith("0x", ignoreCase = true)) hex.substring(2) else hex
        require(clean.isNotEmpty() && clean.length <= 64) { "Invalid hex hash: $hex" }
        return BigInteger(clean, 16)
    }

    /**
     * Compute Hamming distance (differing bits) between two BigInteger hashes.
     */
    fun hammingDistance(a: BigInteger, b: BigInteger): Int {
        return a.xor(b).bitCount()
    }

    fun hammingDistanceHex(aHex: String, bHex: String): Int {
        return hammingDistance(fromHex(aHex), fromHex(bHex))
    }

    /**
     * Primary entry point: hashes a full rectified card bitmap.
     */
    fun hashRectified(image: ImageBuffer): CardHashes {
        val artGrid = downsampleToGrid(image, ART_WINDOW)
        val frameGrid = downsampleToGrid(image, WHOLE_WINDOW)

        val artDct = dct2d(artGrid)
        val frameDct = dct2d(frameGrid)

        val artBits = signBlock(artDct, ART_BLOCK)
        val frameBits = signBlock(frameDct, FRAME_BLOCK)

        return CardHashes(
            artHash = toHex(artBits, ART_HASH_HEX),
            frameHash = toHex(frameBits, FRAME_HASH_HEX)
        )
    }

    fun hashRectified(bitmap: Bitmap): CardHashes {
        return hashRectified(ImageBuffer.fromBitmap(bitmap))
    }
}