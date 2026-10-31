package com.example.syncedn.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
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
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * High-performance image loading, downsampling, and multi-tier caching utility for Synced N.
 * Features in-memory LruCache, synchronous local disk cache for instant avatar loads,
 * intelligent downsampling (avoids high-res memory bloat and scroll stutter), and async download pipeline.
 */
object ImageLoader {
    private const val TAG = "ImageLoader"

    // Allocate 1/8th of available runtime memory for memory cache (typically 24MB-32MB)
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(4096)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val executor = Executors.newFixedThreadPool(4)
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 10 MB safety cap

    /**
     * Upgrades insecure http:// remote image URLs to https:// to protect against MITM tampering.
     */
    private val appAssetCache = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()

    /**
     * Loads and caches an app UI drawable/asset bitmap across the entire process with smart downsampling
     * so huge PNGs (like 2048x2048 team_brand_logo or 1024x1024 team_header_badge) are decoded once at optimal
     * display size instead of stalling page navigation.
     */
    fun getAppAssetBitmap(
        context: Context,
        name: String,
        fallbackSupplier: () -> Bitmap
    ): Bitmap {
        appAssetCache[name]?.takeIf { !it.isRecycled }?.let { return it }

        val targetMaxDim = when (name) {
            "team_brand_logo", "team_header_badge", "team_badge", "google_mark", "profile_photo" -> 256
            "worship_hero_bg", "team_sunset", "hero_container", "welcome_artwork" -> 720
            "landing_background" -> 1080
            else -> 720
        }

        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        val decoded: Bitmap? = if (resId != 0) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, resId, bounds)
                val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, targetMaxDim)
                val opts = BitmapFactory.Options().apply {
                    inScaled = false
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeResource(context.resources, resId, opts)
            } catch (_: Throwable) {
                null
            }
        } else {
            null
        }

        val raw = decoded ?: fallbackSupplier()
        val maxSide = Math.max(raw.width, raw.height)
        val finalBitmap = if (maxSide > targetMaxDim && targetMaxDim > 0) {
            val scale = targetMaxDim.toFloat() / maxSide.toFloat()
            val w = (raw.width * scale).toInt().coerceAtLeast(1)
            val h = (raw.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(raw, w, h, true)
        } else {
            raw
        }

        appAssetCache[name] = finalBitmap
        return finalBitmap
    }

    /**
     * Pre-warms frequently used navigation header and hero assets on a background thread.
     */
    fun prewarmAppAssets(context: Context) {
        val appCtx = context.applicationContext ?: context
        executor.execute {
            val commonAssets = listOf(
                "team_brand_logo",
                "team_header_badge",
                "worship_hero_bg",
                "team_sunset",
                "profile_photo"
            )
            for (asset in commonAssets) {
                if (!appAssetCache.containsKey(asset)) {
                    try {
                        val resId = appCtx.resources.getIdentifier(asset, "drawable", appCtx.packageName)
                        if (resId != 0) {
                            getAppAssetBitmap(appCtx, asset) {
                                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                            }
                        }
                    } catch (_: Throwable) {}
                }
            }
        }
    }

    fun normalizeSecureImageUrl(url: String): String {
        val clean = url.trim()
        return if (clean.startsWith("http://", ignoreCase = true)) {
            "https://" + clean.substring(7)
        } else {
            clean
        }
    }

    /**
     * Calculates the optimal power-of-two inSampleSize so decoding avoids allocating
     * excessive memory for high-resolution camera photos.
     */
    fun calculateInSampleSize(origWidth: Int, origHeight: Int, maxDimension: Int): Int {
        var inSampleSize = 1
        val maxSide = Math.max(origWidth, origHeight)
        while ((maxSide / (inSampleSize * 2)) >= maxDimension) {
            inSampleSize *= 2
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * Decodes a byte array into a downsampled, correctly oriented, high-performance Bitmap.
     * Prevents multi-megabyte texture upload bottlenecks and UI scroll stutter.
     */
    fun decodeSampledBitmap(data: ByteArray, maxDimension: Int = 800): Bitmap? {
        if (data.isEmpty()) return null

        // 1. Decode bounds only
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, boundsOptions)
        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // 2. Decode with optimal inSampleSize
        val sampleSize = calculateInSampleSize(origWidth, origHeight, maxDimension)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var decoded = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions) ?: return null

        // 3. Fix EXIF orientation if present
        try {
            ByteArrayInputStream(data).use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val matrix = Matrix()
                when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                    ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                }
                if (!matrix.isIdentity) {
                    val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    if (rotated != decoded) {
                        decoded.recycle()
                        decoded = rotated
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Downscale smoothly if still exceeding target dimension
        val currentMax = Math.max(decoded.width, decoded.height)
        if (currentMax > maxDimension) {
            val scale = maxDimension.toFloat() / currentMax.toFloat()
            val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
            val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
            if (scaled != decoded) {
                decoded.recycle()
                decoded = scaled
            }
        }
        return decoded
    }

    private fun getCacheFile(context: Context, key: String, circular: Boolean): File {
        val dir = File(context.cacheDir, "avatar_cache").apply { if (!exists()) mkdirs() }
        val ext = if (circular) ".png" else ".jpg"
        val safeName = key.hashCode().toString() + "_" + key.takeLast(16).replace(Regex("[^a-zA-Z0-9]"), "") + ext
        return File(dir, safeName)
    }

    private fun saveToDisk(file: File, bitmap: Bitmap, circular: Boolean) {
        try {
            FileOutputStream(file).use { out ->
                if (circular) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
        } catch (_: Exception) {}
    }

    private fun loadFromDisk(file: File, maxDimension: Int = 800): Bitmap? {
        return try {
            if (!file.exists() || file.length() <= 0) return null
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)
            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            val sampleSize = calculateInSampleSize(origWidth, origHeight, maxDimension)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
            val maxSide = Math.max(decoded.width, decoded.height)
            if (maxSide > maxDimension) {
                val scale = maxDimension.toFloat() / maxSide.toFloat()
                val targetW = (decoded.width * scale).toInt().coerceAtLeast(1)
                val targetH = (decoded.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(decoded, targetW, targetH, true)
                if (scaled != decoded) {
                    decoded.recycle()
                    scaled
                } else decoded
            } else decoded
        } catch (_: Exception) { null }
    }

    /**
     * Synchronously retrieves a cached profile bitmap from memory or fast local disk cache.
     * Returns null if not cached yet. Allows views to render immediately without flickering.
     */
    fun getCachedProfileBitmap(context: Context, uriString: String?, circular: Boolean = true, maxDimension: Int = 320): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val trimmedUri = uriString.trim()
        val cacheKey = if (circular) "circle_$trimmedUri" else "media_$trimmedUri"

        // 1. Memory cache check (0ms instant hit)
        val memBitmap = memoryCache.get(cacheKey)
        if (memBitmap != null && !memBitmap.isRecycled) {
            return memBitmap
        }

        // 2. Fast disk cache check
        val diskFile = getCacheFile(context, cacheKey, circular)
        val diskBitmap = loadFromDisk(diskFile, maxDimension)
        if (diskBitmap != null && !diskBitmap.isRecycled) {
            memoryCache.put(cacheKey, diskBitmap)
            return diskBitmap
        }

        return null
    }

    /**
     * Pre-seeds both memory and disk caches with a known bitmap.
     * Downsamples the bitmap to [maxDimension] to prevent excessive memory usage.
     */
    fun preseedCache(
        context: Context,
        uriString: String,
        bitmap: Bitmap,
        circular: Boolean = true,
        maxDimension: Int = if (circular) 320 else 800
    ) {
        val trimmedUri = uriString.trim()
        val cacheKey = if (circular) "circle_$trimmedUri" else "media_$trimmedUri"
        val maxSide = Math.max(bitmap.width, bitmap.height)
        val sizedBitmap = if (maxSide > maxDimension) {
            val scale = maxDimension.toFloat() / maxSide.toFloat()
            val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else bitmap

        val finalBitmap = if (circular) getCircularBitmap(sizedBitmap) else sizedBitmap
        memoryCache.put(cacheKey, finalBitmap)
        executor.execute {
            saveToDisk(getCacheFile(context, cacheKey, circular), finalBitmap, circular)
        }
    }

    /**
     * Loads a photo into an [ImageView].
     * Renders synchronously if in memory cache; otherwise streams/decodes in background.
     * Downsamples to [maxDimension] to guarantee smooth 60fps scrolling and avoid GPU lag.
     */
    fun loadProfilePhoto(
        imageView: ImageView,
        uriString: String?,
        placeholderResId: Int? = null,
        circular: Boolean = true,
        maxDimension: Int = if (circular) 320 else 800
    ) {
        if (uriString.isNullOrBlank()) {
            placeholderResId?.let { imageView.setImageResource(it) }
            return
        }

        val trimmedUri = uriString.trim()
        val cacheKey = if (circular) "circle_$trimmedUri" else "media_$trimmedUri"

        // 1. Check memory cache (0ms instant hit)
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null && !cachedBitmap.isRecycled) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // 2. Fallback placeholder while fetching asynchronously
        placeholderResId?.let { imageView.setImageResource(it) }
        imageView.tag = trimmedUri

        // 3. Fetch asynchronously without blocking UI main thread
        executor.execute {
            val diskFile = getCacheFile(imageView.context, cacheKey, circular)
            var bitmap: Bitmap? = loadFromDisk(diskFile, maxDimension)

            if (bitmap == null) {
                bitmap = try {
                    when {
                        trimmedUri.startsWith("http://", ignoreCase = true) ||
                        trimmedUri.startsWith("https://", ignoreCase = true) -> {
                            downloadBitmap(normalizeSecureImageUrl(trimmedUri), maxDimension)
                        }
                        else -> {
                            loadLocalBitmap(imageView.context, trimmedUri.toUri(), maxDimension)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load image $trimmedUri: ${e.message}")
                    null
                }

                if (bitmap != null) {
                    val finalBitmap = if (circular) getCircularBitmap(bitmap) else bitmap
                    saveToDisk(diskFile, finalBitmap, circular)
                    bitmap = finalBitmap
                }
            }

            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                val displayBitmap = bitmap
                mainHandler.post {
                    if (imageView.tag == trimmedUri) {
                        imageView.setImageBitmap(displayBitmap)
                    }
                }
            }
        }
    }

    /**
     * Downloads an image over HTTP/HTTPS with downsampling.
     */
    private fun downloadBitmap(urlStr: String, maxDimension: Int): Bitmap? {
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
                val bytes = conn.inputStream.use { it.readBytes() }
                decodeSampledBitmap(bytes, maxDimension)
            } else {
                Log.w(TAG, "HTTP error $responseCode downloading $urlStr")
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Loads a local image from content:// or file:// URI with downsampling.
     */
    private fun loadLocalBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: if (uri.scheme == "file" || uri.path?.startsWith("/") == true) {
                    File(uri.path ?: "").takeIf { it.exists() }?.readBytes()
                } else null
            if (bytes != null && bytes.isNotEmpty()) {
                decodeSampledBitmap(bytes, maxDimension)
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load local bitmap: $uri", e)
            null
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
