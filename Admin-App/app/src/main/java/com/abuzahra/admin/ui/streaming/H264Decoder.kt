package com.abuzahra.admin.ui.streaming

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * H264 (AVC) hardware decoder that renders decoded frames onto a [Surface].
 *
 * The device's `ScreenStreamService` / `CameraStreamService` encode each
 * captured frame with MediaCodec and ship the resulting NAL units (base64)
 * to the server via `/ws/stream`. The admin viewer WebSocket receives those
 * frames in real time and feeds them here for decoding.
 *
 * Threading: MediaCodec is not thread-safe, so every input/output operation
 * is posted to a dedicated [HandlerThread]. Callers can invoke [feedFrame]
 * from any thread (including OkHttp's WebSocket worker thread).
 *
 * Codec config (SPS/PPS): the device sends config frames inline with the
 * bitstream (BUFFER_FLAG_CODEC_CONFIG). MediaCodec extracts them from the
 * stream automatically — we don't need to pre-configure the SPS/PPS by
 * hand. We just need to call `configure` with a placeholder MediaFormat so
 * the decoder knows the mime type; the real width/height arrive with the
 * first SPS NAL unit.
 */
class H264Decoder {

    private val tag = "H264Decoder"

    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var configured = false
    private var released = false

    /** Dedicated thread for all MediaCodec calls (codec is not thread-safe). */
    private val codecThread: HandlerThread by lazy {
        HandlerThread("h264-decoder").apply { start() }
    }
    private val codecHandler: Handler by lazy { Handler(codecThread.looper) }

    /** Cached output format (width × height) — reported via [onFormatChanged]. */
    @Volatile var videoWidth: Int = 0
        private set
    @Volatile var videoHeight: Int = 0
        private set

    /** Optional callback invoked on the main thread when the decoder reports a new size. */
    var onFormatChanged: ((width: Int, height: Int) -> Unit)? = null

    /** Optional callback invoked on the main thread whenever a frame is rendered. */
    var onFrameRendered: (() -> Unit)? = null

    /**
     * Configure and start the decoder. Must be called before [feedFrame].
     *
     * We create the MediaFormat with a placeholder size (320×240). The actual
     * width/height are extracted from the SPS NAL unit when the first
     * config frame arrives — MediaCodec fires INFO_OUTPUT_FORMAT_CHANGED
     * at that point and we update [videoWidth] / [videoHeight].
     */
    fun setup(targetSurface: Surface) {
        if (configured) return
        surface = targetSurface
        codecHandler.post {
            try {
                val format = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC,
                    /* placeholder — updated when first SPS arrives */ 320,
                    240
                ).apply {
                    // Request low-latency decoding if supported (Android 11+).
                    // Older devices simply ignore the key.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    }
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 512 * 1024)
                }
                val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                c.configure(format, targetSurface, null, 0)
                c.start()
                codec = c
                configured = true
                Log.i(tag, "Decoder configured and started")
            } catch (e: Exception) {
                Log.e(tag, "Failed to configure decoder", e)
                cleanupCodec()
            }
        }
    }

    /**
     * Feed one encoded frame (one or more NAL units, with start codes) to the
     * decoder. Safe to call from any thread — work is posted to [codecHandler].
     *
     * @param data       raw H264 NAL bytes (NOT base64 — caller must decode first)
     * @param ptsUs      presentation timestamp in microseconds (from the device)
     * @param isKeyframe true if the device flagged this as an I-frame. Passed
     *                   as a hint; the decoder itself decides via NAL type.
     */
    fun feedFrame(data: ByteArray, ptsUs: Long, isKeyframe: Boolean) {
        if (released || !configured) return
        val c = codec ?: return
        codecHandler.post {
            if (released || c !== codec) return@post
            try {
                val inputIndex = c.dequeueInputBuffer(INPUT_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buf: ByteBuffer? = c.getInputBuffer(inputIndex)
                    if (buf != null) {
                        buf.clear()
                        if (data.size <= buf.capacity()) {
                            buf.put(data)
                            c.queueInputBuffer(inputIndex, 0, data.size, ptsUs, 0)
                        } else {
                            // Frame too large for the input buffer — skip it.
                            c.queueInputBuffer(inputIndex, 0, 0, ptsUs, 0)
                            Log.w(tag, "Frame too large (${data.size}B) — skipped")
                        }
                    } else {
                        c.queueInputBuffer(inputIndex, 0, 0, ptsUs, 0)
                    }
                }
                drainOutput(c)
            } catch (e: Exception) {
                Log.e(tag, "feedFrame error", e)
            }
        }
    }

    /** Pull every available decoded frame from the codec and render it. */
    private fun drainOutput(c: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIndex = c.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val fmt = c.outputFormat
                    val w = fmt.getInteger(MediaFormat.KEY_WIDTH)
                    val h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                    videoWidth = w
                    videoHeight = h
                    Log.i(tag, "Output format changed: ${w}x$h")
                    val cb = onFormatChanged
                    if (cb != null) {
                        Handler(Looper.getMainLooper()).post { cb(w, h) }
                    }
                }
                outIndex >= 0 -> {
                    // render=true → push the decoded frame to the Surface.
                    c.releaseOutputBuffer(outIndex, true)
                    val cb = onFrameRendered
                    if (cb != null) {
                        Handler(Looper.getMainLooper()).post { cb() }
                    }
                }
            }
        }
    }

    /**
     * Send an end-of-stream marker and release all codec resources. After
     * release, the instance cannot be reused — create a new one instead.
     */
    fun release() {
        if (released) return
        released = true
        codecHandler.post {
            cleanupCodec()
            try { codecThread.quitSafely() } catch (_: Exception) {}
        }
    }

    private fun cleanupCodec() {
        configured = false
        try {
            codec?.let { c ->
                try { c.stop() } catch (_: Exception) {}
                c.release()
            }
        } catch (_: Exception) {}
        codec = null
        surface = null
    }

    companion object {
        private const val INPUT_TIMEOUT_US = 10_000L   // 10 ms
        private const val OUTPUT_TIMEOUT_US = 0L        // non-blocking drain
    }
}
