package com.decklotus.companion.matcher

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class HashMatchCandidate(
    val rowId: Int,
    val artDistance: Int,
    val frameDistance: Int?,
    val confidence: Float
)

/**
 * High-speed in-memory Hamming distance matcher for 112,815 MTG card art & frame hashes.
 * Search time across all 112,815 cards is ~3-5ms on Tensor G5.
 */
class CardHashMatcher {

    private var rowCount: Int = 0
    private var hashes: IntArray = IntArray(0) // Flat array: 10 ints per row (8 art, 2 frame)

    val isLoaded: Boolean
        get() = rowCount > 0

    fun getLoadedCount(): Int = rowCount

    suspend fun loadFromAssets(context: Context, assetName: String = "card-hashes.bin") = withContext(Dispatchers.IO) {
        if (isLoaded) return@withContext

        val start = System.currentTimeMillis()
        val customFile = java.io.File(context.filesDir, assetName)
        if (customFile.exists() && customFile.length() > 0) {
            Log.d("CardHashMatcher", "Loading hashes from internal storage: ${customFile.absolutePath} (${customFile.length()} bytes)")
            java.io.FileInputStream(customFile).use { stream ->
                loadFromStream(stream)
            }
        } else {
            Log.d("CardHashMatcher", "Loading hashes from APK assets: $assetName")
            context.assets.open(assetName).use { stream ->
                loadFromStream(stream)
            }
        }
        val elapsed = System.currentTimeMillis() - start
        Log.d("CardHashMatcher", "Loaded $rowCount card hashes in ${elapsed}ms")
    }

    suspend fun reload(context: Context, assetName: String = "card-hashes.bin") = withContext(Dispatchers.IO) {
        rowCount = 0
        hashes = IntArray(0)
        loadFromAssets(context, assetName)
    }

    fun loadFromStream(stream: InputStream) {
        val headerBytes = ByteArray(16)
        var read = stream.read(headerBytes)
        if (read < 16) throw IllegalArgumentException("Truncated hash header")

        val headerBuffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.BIG_ENDIAN)
        val magic = headerBuffer.int
        if (magic != 0x444c4348) throw IllegalArgumentException("Invalid magic header: $magic")

        val version = headerBuffer.short
        val artBytes = headerBuffer.get().toInt() and 0xFF
        val frameBytes = headerBuffer.get().toInt() and 0xFF
        val count = headerBuffer.int

        val artWords = artBytes / 4 // 8
        val frameWords = frameBytes / 4 // 2
        val wordsPerRow = artWords + frameWords // 10

        rowCount = count
        hashes = IntArray(count * wordsPerRow)

        val rowBuffer = ByteArray(56 * 4096) // Read in batches of 4096 rows
        var rowIdx = 0

        while (rowIdx < count) {
            val rowsToRead = kotlin.math.min(4096, count - rowIdx)
            val bytesToRead = rowsToRead * 56
            var totalRead = 0
            while (totalRead < bytesToRead) {
                val r = stream.read(rowBuffer, totalRead, bytesToRead - totalRead)
                if (r < 0) break
                totalRead += r
            }

            val buf = ByteBuffer.wrap(rowBuffer, 0, totalRead).order(ByteOrder.BIG_ENDIAN)
            for (i in 0 until rowsToRead) {
                buf.position(i * 56 + 16) // Skip 16 bytes UUID to reach Art Hash
                val base = (rowIdx + i) * wordsPerRow

                for (w in 0 until artWords) {
                    hashes[base + w] = buf.int
                }
                for (w in 0 until frameWords) {
                    hashes[base + artWords + w] = buf.int
                }
            }

            rowIdx += rowsToRead
        }
    }

    /**
     * Compute the exact 256-bit Art Hamming distance for a specific row ID in ~50 nanoseconds.
     */
    fun getArtDistance(rowId: Int, probeArtHex: String): Int {
        if (rowId < 0 || rowId >= rowCount || probeArtHex.isBlank()) return 256
        val probeArt = hexToInts(probeArtHex, 8)
        val base = rowId * 10
        val hashData = hashes

        var dist = 0
        dist += Integer.bitCount(probeArt[0] xor hashData[base])
        dist += Integer.bitCount(probeArt[1] xor hashData[base + 1])
        dist += Integer.bitCount(probeArt[2] xor hashData[base + 2])
        dist += Integer.bitCount(probeArt[3] xor hashData[base + 3])
        dist += Integer.bitCount(probeArt[4] xor hashData[base + 4])
        dist += Integer.bitCount(probeArt[5] xor hashData[base + 5])
        dist += Integer.bitCount(probeArt[6] xor hashData[base + 6])
        dist += Integer.bitCount(probeArt[7] xor hashData[base + 7])
        return dist
    }

    fun match(
        artHashHex: String,
        frameHashHex: String? = null,
        maxDistance: Int = 77, // 30% of 256 bits
        limit: Int = 30
    ): List<HashMatchCandidate> {
        if (rowCount == 0) return emptyList()

        val probeArt = hexToInts(artHashHex, 8)
        val probeFrame = if (frameHashHex != null) hexToInts(frameHashHex, 2) else null

        val matches = mutableListOf<HashMatchCandidate>()
        val totalRows = rowCount
        val hashData = hashes

        for (row in 0 until totalRows) {
            val base = row * 10

            var dist = 0
            dist += Integer.bitCount(probeArt[0] xor hashData[base])
            dist += Integer.bitCount(probeArt[1] xor hashData[base + 1])
            dist += Integer.bitCount(probeArt[2] xor hashData[base + 2])
            dist += Integer.bitCount(probeArt[3] xor hashData[base + 3])
            dist += Integer.bitCount(probeArt[4] xor hashData[base + 4])
            dist += Integer.bitCount(probeArt[5] xor hashData[base + 5])
            dist += Integer.bitCount(probeArt[6] xor hashData[base + 6])
            dist += Integer.bitCount(probeArt[7] xor hashData[base + 7])

            if (dist <= maxDistance) {
                var frameDist: Int? = null
                if (probeFrame != null) {
                    var fd = 0
                    fd += Integer.bitCount(probeFrame[0] xor hashData[base + 8])
                    fd += Integer.bitCount(probeFrame[1] xor hashData[base + 9])
                    frameDist = fd
                }

                val confidence = kotlin.math.max(0.0f, kotlin.math.min(1.0f, 1.0f - (dist.toFloat() / 256.0f / 0.30f)))
                matches.add(HashMatchCandidate(row, dist, frameDist, confidence))
            }
        }

        // Sort by art distance, then frame distance
        matches.sortWith { a, b ->
            val d = a.artDistance.compareTo(b.artDistance)
            if (d != 0) d else (a.frameDistance ?: 0).compareTo(b.frameDistance ?: 0)
        }

        return if (matches.size > limit) matches.subList(0, limit) else matches
    }

    private fun hexToInts(hex: String, count: Int): IntArray {
        val clean = hex.trim()
        val result = IntArray(count)
        for (i in 0 until count) {
            val start = i * 8
            val end = kotlin.math.min(clean.length, start + 8)
            if (start < clean.length) {
                result[i] = clean.substring(start, end).toLong(16).toInt()
            }
        }
        return result
    }
}