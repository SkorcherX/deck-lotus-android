package com.decklotus.companion.vision

import android.graphics.Bitmap
import kotlin.math.abs

enum class SettleState {
    WAITING_FOR_CARD,
    CARD_MOVING,
    CARD_SETTLING,
    CARD_SETTLED,
    LOCKED_AFTER_SCAN
}

/**
 * High-speed motion & settle detector for human-fed gravity cradles.
 * Monitors consecutive preview frames at 30+ FPS (<1ms CPU overhead).
 */
class CardSettleDetector(
    private val motionThreshold: Float = 12.0f,
    private val settleThreshold: Float = 4.0f,
    private val settleDurationMs: Long = 100L
) {
    var state: SettleState = SettleState.WAITING_FOR_CARD
        private set

    private var prevSampleGrid: FloatArray? = null
    private var settleStartTime: Long = 0
    val gridSize = 16

    fun processFrame(bitmap: Bitmap, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val currentGrid = sampleLumaGrid(bitmap, gridSize)
        return processLumaGrid(currentGrid, currentTimeMs)
    }

    fun processLumaGrid(currentGrid: FloatArray, currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        val prev = prevSampleGrid
        prevSampleGrid = currentGrid

        if (prev == null) {
            return false
        }

        var totalDiff = 0.0f
        val len = currentGrid.size
        for (i in 0 until len) {
            totalDiff += abs(currentGrid[i] - prev[i])
        }
        val mad = totalDiff / len

        when (state) {
            SettleState.WAITING_FOR_CARD -> {
                if (mad > motionThreshold) {
                    state = SettleState.CARD_MOVING
                } else if (mad <= settleThreshold) {
                    // If card is already in cradle when starting
                    state = SettleState.CARD_SETTLING
                    settleStartTime = currentTimeMs
                }
            }

            SettleState.CARD_MOVING -> {
                if (mad <= settleThreshold) {
                    state = SettleState.CARD_SETTLING
                    settleStartTime = currentTimeMs
                }
            }

            SettleState.CARD_SETTLING -> {
                if (mad > settleThreshold) {
                    state = SettleState.CARD_MOVING
                } else if (currentTimeMs - settleStartTime >= settleDurationMs) {
                    state = SettleState.CARD_SETTLED
                    return true
                }
            }

            SettleState.CARD_SETTLED -> {
                // Wait for capture callback to mark as locked
            }

            SettleState.LOCKED_AFTER_SCAN -> {
                // Require significant motion (new card entered or old card removed) before re-arming
                if (mad > motionThreshold * 1.1f) {
                    state = SettleState.CARD_MOVING
                }
            }
        }

        return false
    }

    fun markCaptured() {
        state = SettleState.LOCKED_AFTER_SCAN
    }

    fun reset() {
        state = SettleState.WAITING_FOR_CARD
        prevSampleGrid = null
        settleStartTime = 0
    }

    private fun sampleLumaGrid(bitmap: Bitmap, size: Int): FloatArray {
        val w = bitmap.width
        val h = bitmap.height
        val grid = FloatArray(size * size)

        val startX = (w * 0.25f).toInt()
        val endX = (w * 0.75f).toInt()
        val startY = (h * 0.20f).toInt()
        val endY = (h * 0.75f).toInt()

        val stepX = (endX - startX) / size
        val stepY = (endY - startY) / size

        var idx = 0
        for (gy in 0 until size) {
            val y = (startY + gy * stepY).coerceIn(0, h - 1)
            for (gx in 0 until size) {
                val x = (startX + gx * stepX).coerceIn(0, w - 1)
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = 0.299f * r + 0.587f * g + 0.114f * b
                grid[idx++] = luma
            }
        }

        return grid
    }
}