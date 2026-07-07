package com.wmt.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Converts gallery picks the backend would reject into uploadable JPEGs:
 * HEIC/HEIF photos (the default on many phones, not on the server's allowlist)
 * and images over the server's size limit are re-encoded; JPEG/PNG/WebP/GIF
 * within the limit pass through untouched.
 */
object ImageTranscoder {

    private const val MAX_LONG_EDGE_PX = 2560
    private const val JPEG_QUALITY = 88

    /** Images at/under this size in an accepted format skip transcoding (server default cap is 10MB). */
    private const val PASSTHROUGH_MAX_BYTES = 9L * 1024 * 1024

    private val PASSTHROUGH_MIMES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    /**
     * Returns a temp JPEG file to upload instead of [uri], or null when the file
     * should be uploaded as-is (non-image, or already an accepted format within limits).
     */
    fun transcodeIfNeeded(context: Context, uri: Uri, mime: String, sizeBytes: Long?): File? {
        if (!mime.startsWith("image/")) return null
        val withinLimit = sizeBytes != null && sizeBytes <= PASSTHROUGH_MAX_BYTES
        if (mime in PASSTHROUGH_MIMES && withinLimit) return null

        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            var edge = maxOf(bounds.outWidth, bounds.outHeight)
            while (edge / 2 >= MAX_LONG_EDGE_PX) {
                inSampleSize *= 2
                edge /= 2
            }
        }
        var bitmap = resolver.openInputStream(uri)
            ?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: return null

        // Bake in the EXIF orientation — re-encoding drops the tag, which would
        // otherwise leave portrait shots lying on their side.
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use { stream ->
                when (
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        val longEdge = maxOf(bitmap.width, bitmap.height)
        val matrix = Matrix()
        if (rotation != 0f) matrix.postRotate(rotation)
        if (longEdge > MAX_LONG_EDGE_PX) {
            val scale = MAX_LONG_EDGE_PX.toFloat() / longEdge
            matrix.postScale(scale, scale)
        }
        if (!matrix.isIdentity) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }

        val dir = File(context.cacheDir, "uploads").apply { mkdirs() }
        val file = File(dir, "upload_${System.nanoTime()}.jpg")
        return runCatching {
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            file
        }.getOrElse {
            file.delete()
            null
        }
    }
}
