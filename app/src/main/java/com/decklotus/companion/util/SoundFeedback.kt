package com.decklotus.companion.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * Ultra-low latency synthesized audio cue engine.
 * Pure sine wave oscillators exactly matching the deck-lotus webscanner cues:
 * - Confident / Settled: 1320 Hz pure sine (E6) with exponential decay (130ms)
 * - Review / Ambiguous: 440 Hz pure sine (A4) with exponential decay (130ms)
 * - Miss / Error: 220 Hz pure sine (A3) with exponential decay (110ms)
 */
class SoundFeedback(private val context: Context) {

    private val sampleRate = 44100

    private val confidentTrack: AudioTrack by lazy {
        createSineTrack(frequency = 1320.0, durationMs = 130)
    }

    private val reviewTrack: AudioTrack by lazy {
        createSineTrack(frequency = 440.0, durationMs = 130)
    }

    private val errorTrack: AudioTrack by lazy {
        createSineTrack(frequency = 220.0, durationMs = 110)
    }

    private fun createSineTrack(frequency: Double, durationMs: Int): AudioTrack {
        val numSamples = (sampleRate * durationMs / 1000.0).toInt()
        val buffer = ShortArray(numSamples)
        val attackSamples = (sampleRate * 0.01).toInt() // 10ms attack
        val decaySamples = numSamples - attackSamples

        for (i in 0 until numSamples) {
            val angle = 2.0 * Math.PI * i * frequency / sampleRate
            val rawSine = sin(angle)

            val envelope = when {
                i < attackSamples -> (i.toDouble() / attackSamples)
                else -> Math.exp(-3.5 * (i - attackSamples).toDouble() / decaySamples)
            }

            buffer[i] = (rawSine * envelope * Short.MAX_VALUE * 0.85).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        return track
    }

    /**
     * Confident match chime: 1320 Hz pure tone (E6).
     * Audio signal that the card is fully identified and confident to feed next.
     */
    fun playSuccessChime() {
        playTrack(confidentTrack)
    }

    /**
     * Review match chime: 440 Hz pure tone (A4).
     * Audio signal that the card is identified but held for printing review.
     */
    fun playReviewChime() {
        playTrack(reviewTrack)
    }

    /**
     * Error / miss tone: 220 Hz low tone (A3).
     * Audio signal that the card could not be recognized.
     */
    fun playErrorTone() {
        playTrack(errorTrack)
    }

    private fun playTrack(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop()
            }
            track.reloadStaticData()
            track.play()
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            confidentTrack.release()
            reviewTrack.release()
            errorTrack.release()
        } catch (_: Exception) {}
    }
}