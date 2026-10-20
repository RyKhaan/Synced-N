package com.example.syncedn.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.core.net.toUri
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * High-performance image loading and caching utility for Synced N.
 * Handles both remote Cloudinary HTTPS URLs and local content:// URIs with circular cropping and memory caching.
 */
object ImageLoader {
    private const val TAG = "ImageLoader"

    // Allocate 1/8th of available runtime memory for image cache
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(4096)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Loads a profile photo into an [ImageView].
     * Supports HTTPS URLs, local content URIs, and handles circular cropping.
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

        // 1. Check memory cache
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        placeholderResId?.let { imageView.setImageResource(it) }
        imageView.tag = trimmedUri

        // 2. Fetch asynchronously
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
     * Clears in-memory image cache.
     */
    fun clearCache() {
        memoryCache.evictAll()
    }
}
