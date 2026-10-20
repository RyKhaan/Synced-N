package com.example.syncedn.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.media.ExifInterface
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Lightweight, zero-dependency Cloudinary uploader for Synced N.
 * Performs client-side downscaling and compression before uploading via unsigned multipart POST.
 */
object CloudinaryUploader {
    private const val TAG = "CloudinaryUploader"
    const val CLOUD_NAME = "ffb3fdkm"
    const val UPLOAD_PRESET = "synced_preset"
    private const val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Downscales and compresses the image from the given Uri, then uploads to Cloudinary.
     * Calls [onComplete] on the main thread with the uploaded HTTPS URL on success.
     */
    fun uploadProfileImage(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 512,
        quality: Int = 85,
        onComplete: (Result<String>) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                val compressedBytes = prepareImageBytes(appContext, imageUri, maxDimension, quality)
                if (compressedBytes == null || compressedBytes.isEmpty()) {
                    mainHandler.post {
                        onComplete(Result.failure(IllegalArgumentException("Failed to decode or compress image.")))
                    }
                    return@execute
                }

                val uploadedUrl = executeUpload(compressedBytes)
                mainHandler.post {
                    onComplete(Result.success(uploadedUrl))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloudinary upload failed: ${e.message}", e)
                mainHandler.post {
                    onComplete(Result.failure(e))
                }
            }
        }
    }

    /**
     * Decodes, rotates per EXIF, downscales, and compresses the image into a JPEG byte array.
     */
    fun prepareImageBytes(
        context: Context,
        imageUri: Uri,
        maxDimension: Int = 512,
        quality: Int = 85
    ): ByteArray? {
        val contentResolver = context.contentResolver

        // 1. First decode bounds only
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        } ?: return null

        val origWidth = boundsOptions.outWidth
        val origHeight = boundsOptions.outHeight
        if (origWidth <= 0 || origHeight <= 0) return null

        // 2. Calculate inSampleSize
        var inSampleSize = 1
        var halfWidth = origWidth / 2
        var halfHeight = origHeight / 2
        while ((halfWidth / inSampleSize) >= maxDimension && (halfHeight / inSampleSize) >= maxDimension) {
            inSampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        var decodedBitmap: Bitmap? = contentResolver.openInputStream(imageUri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // 3. Fix EXIF orientation if needed
        try {
            contentResolver.openInputStream(imageUri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                val rotationAngle = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (rotationAngle != 0f) {
                    val matrix = Matrix().apply { postRotate(rotationAngle) }
                    val rotated = Bitmap.createBitmap(
                        decodedBitmap!!, 0, 0,
                        decodedBitmap!!.width, decodedBitmap!!.height,
                        matrix, true
                    )
                    if (rotated != decodedBitmap) {
                        decodedBitmap!!.recycle()
                        decodedBitmap = rotated
                    }
                }
            }
        } catch (_: Exception) {
            // EXIF read error fallback: keep decodedBitmap as-is
        }

        val currentBitmap = decodedBitmap ?: return null

        // 4. Scale down accurately to maxDimension
        val currentWidth = currentBitmap.width
        val currentHeight = currentBitmap.height
        val scaledBitmap: Bitmap = if (currentWidth > maxDimension || currentHeight > maxDimension) {
            val ratio = currentWidth.toFloat() / currentHeight.toFloat()
            val newWidth: Int
            val newHeight: Int
            if (currentWidth >= currentHeight) {
                newWidth = maxDimension
                newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
            }
            val scaled = Bitmap.createScaledBitmap(currentBitmap, newWidth, newHeight, true)
            if (scaled != currentBitmap) {
                currentBitmap.recycle()
            }
            scaled
        } else {
            currentBitmap
        }

        // 5. Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(50, 100), outputStream)
        if (scaledBitmap != currentBitmap) {
            scaledBitmap.recycle()
        }
        return outputStream.toByteArray()
    }

    /**
     * Executes the multipart HTTP POST upload to Cloudinary.
     */
    private fun executeUpload(imageBytes: ByteArray): String {
        val boundary = "==SyncedCloudinaryBoundary${System.currentTimeMillis()}=="
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val url = URL(UPLOAD_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Connection", "Keep-Alive")
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        DataOutputStream(conn.outputStream).use { dos ->
            // Part 1: upload_preset
            dos.writeBytes("$twoHyphens$boundary$lineEnd")
            dos.writeBytes("Content-Disposition: form-data; name=\"upload_preset\"$lineEnd$lineEnd")
            dos.writeBytes(UPLOAD_PRESET)
            dos.writeBytes(lineEnd)

            // Part 2: file
            dos.writeBytes("$twoHyphens$boundary$lineEnd")
            dos.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"avatar.jpg\"$lineEnd")
            dos.writeBytes("Content-Type: image/jpeg$lineEnd$lineEnd")
            dos.write(imageBytes)
            dos.writeBytes(lineEnd)

            // Final boundary
            dos.writeBytes("$twoHyphens$boundary$twoHyphens$lineEnd")
            dos.flush()
        }

        val responseCode = conn.responseCode
        val responseBody = readStream(
            if (responseCode in 200..299) conn.inputStream else conn.errorStream
        )

        if (responseCode !in 200..299) {
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody
            } catch (_: Exception) {
                responseBody
            }
            throw IllegalStateException("Cloudinary upload failed ($responseCode): $errorMsg")
        }

        val json = JSONObject(responseBody)
        val secureUrl = json.optString("secure_url").ifBlank { json.optString("url") }
        if (secureUrl.isBlank()) {
            throw IllegalStateException("Cloudinary returned empty secure_url.")
        }
        return secureUrl
    }

    private fun readStream(stream: InputStream?): String {
        if (stream == null) return ""
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
