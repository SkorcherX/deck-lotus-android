package com.decklotus.companion

import com.decklotus.companion.vision.CardHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import kotlin.math.exp

class CardHasherTest {

    private class SeededRandom(seed: Long) {
        private var state = seed and 0xFFFFFFFFL
        fun next(): Double {
            state = (state * 1664525L + 1013904223L) and 0xFFFFFFFFL
            return state.toDouble() / 0x100000000.toDouble()
        }
    }

    private data class SyntheticBlob(val cx: Double, val cy: Double, val r: Double, val tone: Double)

    private fun syntheticCard(seed: Long, width: Int = 244, height: Int = 340): CardHasher.ImageBuffer {
        val random = SeededRandom(seed)
        val data = ByteArray(width * height * 4)

        val blobs = List(6) {
            SyntheticBlob(
                cx = random.next() * width,
                cy = random.next() * height,
                r = 20.0 + random.next() * 80.0,
                tone = random.next() * 255.0
            )
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val border = x < 8 || y < 8 || x >= width - 8 || y >= height - 8
                var value = if (border) 20.0 else 128.0

                for (blob in blobs) {
                    val dx = x - blob.cx
                    val dy = y - blob.cy
                    val falloff = exp(-(dx * dx + dy * dy) / (2.0 * blob.r * blob.r))
                    value += (blob.tone - 128.0) * falloff
                }

                value += (random.next() - 0.5) * 6.0
                val clamped = value.coerceIn(0.0, 255.0).toInt().toByte()

                val p = (y * width + x) * 4
                data[p] = clamped
                data[p + 1] = clamped
                data[p + 2] = clamped
                data[p + 3] = 255.toByte()
            }
        }

        return CardHasher.ImageBuffer.fromRgbaBytes(data, width, height)
    }

    private fun distance(aHex: String, bHex: String): Double {
        return CardHasher.hammingDistanceHex(aHex, bHex).toDouble() / (aHex.length * 4.0)
    }

    @Test
    fun testHexRoundTrips() {
        for (value in listOf(
            BigInteger.ZERO,
            BigInteger.ONE,
            BigInteger("00ff00ff00ff00ff", 16),
            (BigInteger.ONE.shiftLeft(63)).or(BigInteger.ONE)
        )) {
            val hex = CardHasher.toHex(value, 16)
            assertEquals(16, hex.length)
            assertEquals(value, CardHasher.fromHex(hex))
        }

        val artHex = CardHasher.toHex(BigInteger.ONE, CardHasher.ART_HASH_HEX)
        assertEquals(CardHasher.ART_HASH_HEX, artHex.length)
    }

    @Test
    fun testHammingDistance() {
        assertEquals(0, CardHasher.hammingDistance(BigInteger.ZERO, BigInteger.ZERO))
        assertEquals(2, CardHasher.hammingDistance(BigInteger.valueOf(0b1011), BigInteger.valueOf(0b0001)))
        assertEquals(64, CardHasher.hammingDistance(BigInteger.ZERO, BigInteger.valueOf(1).shiftLeft(64).subtract(BigInteger.ONE)))
    }

    @Test
    fun testSameCardHashesIdentically() {
        val a = CardHasher.hashRectified(syntheticCard(1))
        val b = CardHasher.hashRectified(syntheticCard(1))

        assertEquals(a.artHash, b.artHash)
        assertEquals(a.frameHash, b.frameHash)
        assertEquals(CardHasher.ART_HASH_HEX, a.artHash.length)
        assertEquals(CardHasher.FRAME_HASH_HEX, a.frameHash.length)
    }

    @Test
    fun testDifferentCardsAreFarApart() {
        val hashes = (1L..8L).map { seed -> CardHasher.hashRectified(syntheticCard(seed)) }

        for (i in hashes.indices) {
            for (j in (i + 1) until hashes.size) {
                val artDist = distance(hashes[i].artHash, hashes[j].artHash)
                val frameDist = distance(hashes[i].frameHash, hashes[j].frameHash)

                assertTrue("Cards $i and $j art distance ($artDist) should be >= 0.25", artDist >= 0.25)
                assertTrue("Cards $i and $j frame distance ($frameDist) should be >= 0.25", frameDist >= 0.25)
            }
        }
    }
}