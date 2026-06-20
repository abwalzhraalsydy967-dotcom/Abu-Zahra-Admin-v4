package com.abuzahra.admin.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * ImageLoader — loads image thumbnails from the server's authenticated
 * `/api/files/{fileId}` endpoint and decodes them into small bitmaps.
 *
 * Because the Admin-App talks to the server via Bearer-token auth, image
 * bytes cannot be loaded by Glide/Coil via plain HTTP URL — we need to
 * attach the `Authorization: Bearer …` header. This helper does that
 * with OkHttp, then decodes the bytes to a small bitmap using
 * `inSampleSize` for memory efficiency.
 *
 * A small in-memory LruCache (keyed by `fileId:size`) avoids re-fetching
 * the same thumbnail when the RecyclerView rebinds the same item.
 */
object ImageLoader {

    private const val TAG = "ImageLoader"

    /**
     * Memory cache for decoded thumbnails. Key is `fileId:sizePx` so the
     * same file can be requested at multiple target sizes (e.g. 80 for
     * the list and 240 for the grid) without collisions.
     */
    private val cache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
        object : LruCache<String, Bitmap>(maxKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                value.byteCount / 1024
        }
    }

    /**
     * Single shared OkHttp client with the same SSL/timeouts as the rest
     * of the app. We don't go through Retrofit because the URL is dynamic.
     */
    private fun buildClient(token: String): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .apply {
                attachTrustAll(this)
                if (token.isNotBlank()) {
                    addInterceptor { chain ->
                        val req = chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("Accept", "image/*")
                            .build()
                        chain.proceed(req)
                    }
                }
            }
            .build()
    }

    /**
     * Fetches `/api/files/{fileId}` with the Bearer token and decodes a
     * small bitmap suitable for showing as a thumbnail.
     *
     * @param serverUrl The base URL of the server, e.g. `https://host/`.
     * @param token     The Bearer token from Preferences.
     * @param fileId    The server-side file id (RemoteFile.id).
     * @param size      Target edge size in pixels (default 80). Used to
     *                  compute `inSampleSize` and to key the cache.
     * @return The decoded bitmap, or `null` on failure / non-image.
     */
    suspend fun loadFileThumbnail(
        serverUrl: String,
        token: String,
        fileId: String,
        size: Int = 80
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext null
        val cacheKey = "$fileId:$size"
        cache.get(cacheKey)?.let { return@withContext it }

        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val url = "${base}api/files/$fileId"
        val client = buildClient(token)

        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Thumbnail fetch failed: HTTP ${resp.code} for $fileId")
                    return@withContext null
                }
                val bytes = resp.body?.bytes() ?: return@withContext null
                val bmp = decodeSampledBitmap(bytes, size, size) ?: return@withContext null
                cache.put(cacheKey, bmp)
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFileThumbnail failed for $fileId", e)
            null
        }
    }

    /**
     * Decode a full-size bitmap (used by ImageViewerActivity for the
     * fullscreen image). Cached by `fileId:full`.
     */
    suspend fun loadFileFull(
        serverUrl: String,
        token: String,
        fileId: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext null
        val cacheKey = "$fileId:full"
        cache.get(cacheKey)?.let { return@withContext it }

        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val url = "${base}api/files/$fileId"
        val client = buildClient(token)

        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                // Decode bounds first to decide sample size for very
                // large images so we don't OOM.
                val bmp = decodeSampledBitmap(bytes, 2048, 2048) ?: return@withContext null
                cache.put(cacheKey, bmp)
                bmp
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFileFull failed for $fileId", e)
            null
        }
    }

    /**
     * Decode bytes to a Bitmap with `inSampleSize` computed from the
     * bitmap's actual dimensions to fit inside (reqW, reqH).
     */
    private fun decodeSampledBitmap(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        var sample = 1
        var halfH = opts.outHeight / 2
        var halfW = opts.outWidth / 2
        while (halfH / sample >= reqH && halfW / sample >= reqW) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565  // half memory vs. ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    /**
     * Stream a file to a local cache path (used by VideoViewerActivity and
     * AudioPlayerDialogFragment — they need a file path or URL readable by
     * VideoView/MediaPlayer, neither of which can attach headers).
     */
    suspend fun downloadToCache(
        serverUrl: String,
        token: String,
        fileId: String,
        destFile: java.io.File
    ): Boolean = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext false
        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val url = "${base}api/files/$fileId"
        val client = buildClient(token)
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext false
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "downloadToCache failed for $fileId", e)
            false
        }
    }

    /**
     * Fetch a video file from the server (via the same authenticated
     * `/api/files/{fileId}` endpoint) and extract a single frame to use
     * as a thumbnail.
     *
     * Because [MediaMetadataRetriever] cannot attach HTTP headers and our
     * endpoint requires a Bearer token, we first stream the file to a
     * cache file (reusing it across calls), then point the retriever at
     * the local path. The extracted frame is downscaled to ~[size]px on
     * its longest edge and stored in the in-memory [cache] so subsequent
     * binds of the same RecyclerView item don't re-decode it.
     *
     * To avoid downloading very large videos on a flaky network we bail
     * out if the file is bigger than [MAX_VIDEO_BYTES_FOR_THUMB].
     *
     * @param cacheDir writable directory to store the downloaded video
     *                 (typically `context.cacheDir`). The file is reused
     *                 across calls so subsequent binds of the same item
     *                 become cheap local reads.
     * @return the thumbnail bitmap, or `null` if the download failed,
     *         the file was too large, or no frame could be extracted.
     */
    suspend fun loadVideoThumbnail(
        serverUrl: String,
        token: String,
        fileId: String,
        cacheDir: File,
        size: Int = 160,
        knownSize: Long = 0L
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (fileId.isBlank()) return@withContext null
        val cacheKey = "$fileId:video:$size"
        cache.get(cacheKey)?.let { return@withContext it }

        // Skip very large videos — downloading them just for a thumbnail
        // is wasteful and may OOM or time out.
        if (knownSize > MAX_VIDEO_BYTES_FOR_THUMB) {
            Log.w(TAG, "loadVideoThumbnail: skipping $fileId, size $knownSize > $MAX_VIDEO_BYTES_FOR_THUMB")
            return@withContext null
        }

        val base = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
        val url = "${base}api/files/$fileId"
        val client = buildClient(token)

        // Per-fileId cache file inside the supplied cache dir. Reusing it
        // across calls turns repeated binds into cheap local reads.
        val thumbDir = File(cacheDir, "video_thumbs").apply { if (!exists()) mkdirs() }
        val videoFile = File(thumbDir, "vthumb_${fileId}.bin")

        try {
            if (!videoFile.exists() || videoFile.length() == 0L) {
                val req = Request.Builder().url(url).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        Log.w(TAG, "Video thumb fetch failed: HTTP ${resp.code} for $fileId")
                        return@withContext null
                    }
                    videoFile.outputStream().use { output ->
                        resp.body?.byteStream()?.use { input -> input.copyTo(output) }
                    }
                }
                // Re-check size after download — bail out if it blew the limit.
                if (videoFile.length() > MAX_VIDEO_BYTES_FOR_THUMB) {
                    Log.w(TAG, "Video too large after download: ${videoFile.length()}")
                    videoFile.delete()
                    return@withContext null
                }
            }

            val frame = extractVideoFrame(videoFile, size) ?: return@withContext null
            cache.put(cacheKey, frame)
            frame
        } catch (e: Exception) {
            Log.e(TAG, "loadVideoThumbnail failed for $fileId", e)
            null
        }
    }

    /**
     * Use [MediaMetadataRetriever] to grab a frame near the start of the
     * video file (1 second in, or at 0 if the video is shorter) and scale
     * it down to ~[size]px on its longest edge.
     */
    private fun extractVideoFrame(videoFile: File, size: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            // Try 1s in (skips potential black fade-in); fall back to 0.
            val bmp = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            scaleToFit(bmp, size)
        } catch (e: Exception) {
            Log.w(TAG, "extractVideoFrame failed", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /** Scale a bitmap so its longest edge is ≤ [size], preserving aspect. */
    private fun scaleToFit(src: Bitmap, size: Int): Bitmap {
        val w = src.width
        val h = src.height
        if (w <= size && h <= size) return src
        val scale = size.toFloat() / maxOf(w, h)
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    /** Drop everything from the in-memory cache. */
    fun evictAll() = cache.evictAll()

    private const val MAX_VIDEO_BYTES_FOR_THUMB = 25L * 1024 * 1024  // 25 MB
}

/**
 * Attach a permissive TrustManager to an OkHttp Builder. The Admin-App
 * talks to a self-signed server in dev, so we replicate the trust-all
 * behavior used in [com.abuzahra.admin.data.api.ApiClient].
 */
private fun attachTrustAll(builder: OkHttpClient.Builder) {
    try {
        val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
            object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) {}
                override fun checkServerTrusted(
                    chain: Array<out java.security.cert.X509Certificate>?,
                    authType: String?
                ) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, java.security.SecureRandom())
        }
        builder.sslSocketFactory(
            sslContext.socketFactory,
            trustAllCerts[0] as javax.net.ssl.X509TrustManager
        )
        builder.hostnameVerifier { _, _ -> true }
    } catch (e: Exception) {
        Log.w("ImageLoader", "Failed to attach trust-all SSL", e)
    }
}
