package com.koel.app.recorder

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Real-time, dependency-free cleanup for call audio, applied to 16-bit mono PCM in place.
 * Mirrors the ffmpeg chain we validated: high-pass -> low-pass -> dynamic auto-leveling.
 *
 *  - High-pass @ 120 Hz   removes rumble/handling noise and DC offset
 *  - Low-pass  @ 7500 Hz  tames hiss above the voice band the network carries
 *  - AGC (auto gain)      pulls both sides toward a consistent loudness, with a limiter
 *
 * One instance per recording; [process] is called on each captured buffer in order.
 */
class AudioEnhancer(sampleRate: Int) {

    private val hpf = Biquad.highPass(sampleRate, 120.0, 0.707)
    private val lpf = Biquad.lowPass(sampleRate, 7500.0, 0.707)

    // AGC state
    private var gain = 1.0
    private val targetRms = dbToLinear(-18.0) * Short.MAX_VALUE   // ~4125
    private val maxGain = 8.0
    private val minGain = 0.5
    // Per-buffer smoothing: move quickly when too quiet, slowly when backing off.
    private val attack = 0.30   // toward louder
    private val release = 0.05  // toward quieter
    private val limitCeil = Short.MAX_VALUE * 0.98

    /** Processes [len] bytes (16-bit little-endian) of [buf] in place. */
    fun process(buf: ByteArray, len: Int) {
        if (len < 2) return
        // 1) filter + measure RMS of the filtered signal
        var sumSq = 0.0
        var i = 0
        val n = len and 1.inv()
        while (i < n) {
            var s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toDouble()
            s = hpf.step(s)
            s = lpf.step(s)
            // stash filtered sample back (pre-gain); re-read in the gain pass
            val v = s.toInt().coerceIn(-32768, 32767)
            buf[i] = (v and 0xff).toByte()
            buf[i + 1] = ((v shr 8) and 0xff).toByte()
            sumSq += s * s
            i += 2
        }
        val samples = n / 2
        val rms = if (samples > 0) sqrt(sumSq / samples) else 0.0

        // 2) update smoothed gain toward the level that would hit targetRms
        if (rms > 1.0) {
            val desired = (targetRms / rms).coerceIn(minGain, maxGain)
            val a = if (desired > gain) attack else release
            gain += (desired - gain) * a
        }

        // 3) apply gain with a soft ceiling limiter
        i = 0
        while (i < n) {
            val raw = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toDouble()
            var v = raw * gain
            if (v > limitCeil) v = limitCeil
            if (v < -limitCeil) v = -limitCeil
            val iv = v.toInt().coerceIn(-32768, 32767)
            buf[i] = (iv and 0xff).toByte()
            buf[i + 1] = ((iv shr 8) and 0xff).toByte()
            i += 2
        }
    }

    private fun dbToLinear(db: Double) = Math.pow(10.0, db / 20.0)

    /** Minimal direct-form-1 biquad (RBJ cookbook). */
    private class Biquad(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double,
    ) {
        private var x1 = 0.0; private var x2 = 0.0
        private var y1 = 0.0; private var y2 = 0.0

        fun step(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x
            y2 = y1; y1 = y
            return y
        }

        companion object {
            fun highPass(sr: Int, freq: Double, q: Double): Biquad {
                val w0 = 2 * PI * freq / sr
                val cosw = cos(w0); val alpha = sin(w0) / (2 * q)
                val a0 = 1 + alpha
                return Biquad(
                    b0 = ((1 + cosw) / 2) / a0,
                    b1 = (-(1 + cosw)) / a0,
                    b2 = ((1 + cosw) / 2) / a0,
                    a1 = (-2 * cosw) / a0,
                    a2 = (1 - alpha) / a0,
                )
            }

            fun lowPass(sr: Int, freq: Double, q: Double): Biquad {
                val w0 = 2 * PI * freq / sr
                val cosw = cos(w0); val alpha = sin(w0) / (2 * q)
                val a0 = 1 + alpha
                return Biquad(
                    b0 = ((1 - cosw) / 2) / a0,
                    b1 = (1 - cosw) / a0,
                    b2 = ((1 - cosw) / 2) / a0,
                    a1 = (-2 * cosw) / a0,
                    a2 = (1 - alpha) / a0,
                )
            }
        }
    }
}
