package com.decklotus.companion.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

/**
 * Ultra-low latency audio cue engine using ToneGenerator.
 * Zero-allocation, zero-decoding overhead for instantaneous physical feed prompts.
 */
class SoundFeedback(private val context: Context) {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (_: Exception) {
            // ToneGenerator unavailable or audio permission denied
        }
    }

    /**
     * Crisp high-pitch chime: card recognized & ingested.
     * User's audio cue to drop the next card.
     */
    fun playSuccessChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        } catch (_: Exception) {}
    }

    /**
     * Double chime: card recognized with ambiguous candidates or foil confirmation.
     */
    fun playReviewChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 120)
        } catch (_: Exception) {}
    }

    /**
     * Subtle low tone: card misaligned or OCR failed.
     */
    fun playErrorTone() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_NACK, 100)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (_: Exception) {}
    }
}