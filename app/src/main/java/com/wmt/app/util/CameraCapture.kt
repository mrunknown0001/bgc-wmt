package com.wmt.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Helpers for the "capture photo" comment attachment flow: creates the output file for
 * ACTION_IMAGE_CAPTURE, fetches a one-shot location fix, and burns a visible
 * timestamp + coordinates overlay into the JPEG while also writing EXIF date/GPS tags.
 */
object CameraCapture {

    data class PendingPhoto(val file: File, val uri: Uri)

    /** Creates an empty target file in cache and wraps it in a FileProvider URI for TakePicture. */
    fun newPendingPhoto(context: Context): PendingPhoto {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "IMG_$stamp.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return PendingPhoto(file, uri)
    }

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** One-shot location fix (or null when permission is missing / no fix within ~8s). */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return runCatching {
            withTimeoutOrNull(8_000) {
                client.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    CancellationTokenSource().token,
                ).await()
            } ?: client.lastLocation.await()
        }.getOrNull()
    }

    /**
     * Rewrites [file] in place: applies the camera app's EXIF rotation, downscales very large
     * frames, draws a timestamp + coordinates banner in the bottom-left corner, and writes
     * EXIF DateTime/GPS tags so the metadata survives download.
     */
    suspend fun stampPhoto(file: File, location: Location?) = withContext(Dispatchers.IO) {
        val takenAt = Date()
        val rotation = when (
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }

        // Subsample while decoding so a 50–100MP camera frame never sits in memory at full size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = 1
            var edge = maxOf(bounds.outWidth, bounds.outHeight)
            while (edge / 2 >= MAX_LONG_EDGE_PX) {
                inSampleSize *= 2
                edge /= 2
            }
        }
        var bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return@withContext

        // Bake the rotation in so the overlay is upright, and cap resolution to keep uploads small.
        val matrix = Matrix()
        if (rotation != 0f) matrix.postRotate(rotation)
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge > MAX_LONG_EDGE_PX) {
            val scale = MAX_LONG_EDGE_PX.toFloat() / longEdge
            matrix.postScale(scale, scale)
        }
        if (!matrix.isIdentity) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        val stamped = if (bitmap.isMutable && bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }

        drawOverlay(stamped, takenAt, location)
        FileOutputStream(file).use { stamped.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        // Compressing dropped the original EXIF block — write fresh date/GPS tags.
        val exif = ExifInterface(file.absolutePath)
        val exifDate = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(takenAt)
        exif.setAttribute(ExifInterface.TAG_DATETIME, exifDate)
        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, exifDate)
        location?.let { exif.setGpsInfo(it) }
        exif.saveAttributes()
    }

    private fun drawOverlay(bitmap: Bitmap, takenAt: Date, location: Location?) {
        val canvas = Canvas(bitmap)
        val lines = buildList {
            add(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(takenAt))
            location?.let { add(formatCoordinates(it)) }
        }

        val textSize = (bitmap.width * 0.032f).coerceAtLeast(24f)
        val padding = textSize * 0.5f
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setShadowLayer(textSize * 0.08f, 0f, 0f, Color.BLACK)
        }
        val lineHeight = textSize * 1.3f
        val boxWidth = lines.maxOf { textPaint.measureText(it) } + padding * 2
        val boxHeight = lineHeight * lines.size + padding * 1.5f
        val left = padding
        val top = bitmap.height - boxHeight - padding

        canvas.drawRoundRect(
            left, top, left + boxWidth, top + boxHeight,
            textSize * 0.3f, textSize * 0.3f,
            Paint().apply { color = Color.argb(140, 0, 0, 0) },
        )
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, left + padding, top + padding * 0.75f + lineHeight * (index + 1) - textSize * 0.3f, textPaint)
        }
    }

    private fun formatCoordinates(location: Location): String {
        val latDir = if (location.latitude >= 0) "N" else "S"
        val lngDir = if (location.longitude >= 0) "E" else "W"
        val accuracy = if (location.hasAccuracy()) "  ±${location.accuracy.toInt()}m" else ""
        return "%.5f°%s  %.5f°%s%s".format(
            Locale.US, abs(location.latitude), latDir, abs(location.longitude), lngDir, accuracy,
        )
    }

    private const val MAX_LONG_EDGE_PX = 2560
}
