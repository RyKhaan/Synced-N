package com.example.syncedn.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.core.net.toUri
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * High-performance image loading and multi-tier caching utility for Synced N.
 * Features an in-memory LruCache, synchronous local disk cache to eliminate UI flicker ("pitik"),
 * and background download pipeline.
 */
object ImageLoader {
    private const val TAG = "ImageLoader"

    // Allocate 1/8th of available runtime memory for memory cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(4096)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun getCacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "avatar_cache").apply { if (!exists()) mkdirs() }
        val safeName = key.hashCode().toString() + "_" + key.takeLast(16).replace(Regex("[^a-zA-Z0-9]"), "") + ".png"
        return File(dir, safeName)
    }

    private fun saveToDisk(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (_: Exception) {}
    }

    private fun loadFromDisk(file: File): Bitmap? {
        return try {
            if (file.exists() && file.length() > 0) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * Synchronously retrieves a cached profile bitmap from memory or fast local disk cache.
     * Returns null if not cached yet. Allows views to render immediately without flickering.
     */
    fun getCachedProfileBitmap(context: Context, uriString: String?, circular: Boolean = true): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val trimmedUri = uriString.trim()
        val cacheKey = if (circular) "circle_$trimmedUri" else trimmedUri

        // 1. Memory cache check
        val memBitmap = memoryCache.get(cacheKey)
        if (memBitmap != null && !memBitmap.isRecycled) {
            return memBitmap
        }

        // 2. Disk cache check
        val diskFile = getCacheFile(context, cacheKey)
        val diskBitmap = loadFromDisk(diskFile)
        if (diskBitmap != null && !diskBitmap.isRecycled) {
            memoryCache.put(cacheKey, diskBitmap)
            return diskBitmap
        }

        return null
    }

    /**
     * Pre-seeds both memory and disk caches with a known bitmap.
     */
    fun preseedCache(context: Context, uriString: String, bitmap: Bitmap, circular: Boolean = true) {
        val trimmedUri = uriString.trim()
        val cacheKey = if (circular) "circle_$trimmedUri" else trimmedUri
        val finalBitmap = if (circular) getCircularBitmap(bitmap) else bitmap
        memoryCache.put(cacheKey, finalBitmap)
        executor.execute {
            saveToDisk(getCacheFile(context, cacheKey), finalBitmap)
        }
    }

    /**
     * Loads a profile photo into an [ImageView].
     * Renders synchronously if in memory or on disk; otherwise loads in background.
     */
    fun loadProfilePhoto(
        imageView: ImageView,
        uriString: String?,
        placeholderResId: Int? = null,
        circular: Boolean = true
    ) {
        if (uriString.isNullOrBlank()) {
            placeholderResId?.let { imageView.setImageResource(it) }
            return
        }

        val trimmedUri = uriString.trim()
        val cacheKey = if (circular) "circle_$trimmedUri" else trimmedUri

        // 1. Check memory cache (0ms)
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 2. Check disk cache (<1ms synchronous read)
        val diskFile = getCacheFile(imageView.context, cacheKey)
        val diskBitmap = loadFromDisk(diskFile)
        if (diskBitmap != null && !diskBitmap.isRecycled) {
            memoryCache.put(cacheKey, diskBitmap)
            imageView.setImageBitmap(diskBitmap)
            return
        }

        // 3. Fallback placeholder while fetching
        placeholderResId?.let { imageView.setImageResource(it) }
        imageView.tag = trimmedUri

        // 4. Fetch asynchronously
        executor.execute {
            val bitmap = try {
                when {
                    trimmedUri.startsWith("http://", ignoreCase = true) ||
                    trimmedUri.startsWith("https://", ignoreCase = true) -> {
                        downloadBitmap(trimmedUri)
                    }
                    else -> {
                        loadLocalBitmap(imageView.context, trimmedUri.toUri())
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load image $trimmedUri: ${e.message}")
                null
            }

            if (bitmap != null) {
                val finalBitmap = if (circular) getCircularBitmap(bitmap) else bitmap
                memoryCache.put(cacheKey, finalBitmap)
                saveToDisk(diskFile, finalBitmap)

                mainHandler.post {
                    if (imageView.tag == trimmedUri) {
                        imageView.setImageBitmap(finalBitmap)
                    }
                }
            }
        }
    }

    /**
     * Downloads an image over HTTP/HTTPS with timeout safeguards.
     */
    private fun downloadBitmap(urlStr: String): Bitmap? {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            doInput = true
            useCaches = true
        }

        return try {
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
                Log.w(TAG, "HTTP error $responseCode downloading $urlStr")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Loads a local image from content:// or file:// URI.
     */
    private fun loadLocalBitmap(context: Context, uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }

    /**
     * Transforms any bitmap into a circular cropped bitmap.
     */
    fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val minDim = Math.min(width, height)

        val output = Bitmap.createBitmap(minDim, minDim, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }

        val left = (width - minDim) / 2
        val top = (height - minDim) / 2
        val srcRect = Rect(left, top, left + minDim, top + minDim)
        val dstRect = Rect(0, 0, minDim, minDim)

        val radius = minDim / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)

        return output
    }

    /**
     * Clears in-memory and disk image cache.
     */
    fun clearCache(context: Context? = null) {
        memoryCache.evictAll()
        if (context != null) {
            try {
                File(context.cacheDir, "avatar_cache").deleteRecursively()
            } catch (_: Exception) {}
        }
    }
}
