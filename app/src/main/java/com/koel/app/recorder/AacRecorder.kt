package com.koel.app.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import android.util.Log
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Reads PCM from an AudioRecord and encodes AAC-LC into an MP4 (.m4a) container.
 * One instance records one call. Not reusable after [stop].
 */
class AacRecorder(
    private val context: Context,
    private val audioSource: Int,
    private val out: FileDescriptor,
) {
    @Volatile private var running = false
    private var thread: Thread? = null
    @Volatile private var error: String? = null
    private var startedAt = 0L
    @Volatile private var peak = 0

    @SuppressLint("MissingPermission", "WrongConstant")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val bufSize = if (minBuf > 0) minBuf * 4 else SAMPLE_RATE * 2
        val record = AudioRecord.Builder()
            .setContext(context)
            .setAudioSource(audioSource)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL_IN)
                    .setEncoding(ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .build()

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize for source $audioSource"
        }

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufSize)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        val muxer = MediaMuxer(out, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        running = true
        startedAt = SystemClock.elapsedRealtime()
        record.startRecording()

        thread = Thread({ loop(record, codec, muxer) }, "koel-rec").also { it.start() }
    }

    private fun loop(record: AudioRecord, codec: MediaCodec, muxer: MediaMuxer) {
        var trackIndex = -1
        var muxerStarted = false
        val bufInfo = MediaCodec.BufferInfo()
        val pcm = ByteArray(2048)
        try {
            while (running) {
                val n = record.read(pcm, 0, pcm.size)
                if (n > 0) {
                    trackPeak(pcm, n)
                    feed(codec, pcm, n, false)
                }
                drain(codec, muxer, bufInfo) { fmt ->
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(fmt)
                        muxer.start()
                        muxerStarted = true
                    }
                    trackIndex
                }.also { muxerStarted = muxerStarted || it }
            }
            // flush encoder
            feed(codec, pcm, 0, true)
            drain(codec, muxer, bufInfo, endOfStream = true) { trackIndex }
        } catch (t: Throwable) {
            Log.e(TAG, "recording loop failed", t)
            error = t.message ?: t.javaClass.simpleName
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun feed(codec: MediaCodec, data: ByteArray, len: Int, eos: Boolean) {
        val idx = codec.dequeueInputBuffer(10_000)
        if (idx < 0) return
        val buf = codec.getInputBuffer(idx) ?: return
        buf.clear()
        if (len > 0) buf.put(data, 0, len)
        val pts = (SystemClock.elapsedRealtime() - startedAt) * 1000
        val flags = if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
        codec.queueInputBuffer(idx, 0, len, pts, flags)
    }

    private inline fun drain(
        codec: MediaCodec,
        muxer: MediaMuxer,
        info: MediaCodec.BufferInfo,
        endOfStream: Boolean = false,
        resolveTrack: (MediaFormat) -> Int,
    ): Boolean {
        var started = false
        while (true) {
            val idx = codec.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    resolveTrack(codec.outputFormat); started = true
                }
                idx < 0 -> return started
                else -> {
                    val encoded: ByteBuffer? = codec.getOutputBuffer(idx)
                    if (encoded != null && info.size > 0 &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        val track = resolveTrack(codec.outputFormat)
                        if (track >= 0) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(track, encoded, info)
                        }
                    }
                    codec.releaseOutputBuffer(idx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return started
                }
            }
        }
    }

    private fun trackPeak(data: ByteArray, len: Int) {
        var i = 0
        var local = peak
        while (i + 1 < len) {
            val sample = (data[i].toInt() and 0xff) or (data[i + 1].toInt() shl 8)
            val a = abs(sample)
            if (a > local) local = a
            i += 32 // sparse sampling is enough for a level meter
        }
        peak = local
    }

    /** Returns "durationMs;peak" on success, or "ERR:<message>". */
    fun stop(): String {
        running = false
        thread?.join(5000)
        val err = error
        if (err != null) return "ERR:$err"
        val duration = SystemClock.elapsedRealtime() - startedAt
        return "$duration;$peak"
    }

    fun isRecording(): Boolean = running

    companion object {
        private const val TAG = "AacRecorder"
        const val SAMPLE_RATE = 48_000
        private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val BIT_RATE = 96_000
    }
}
