package com.wmt.app.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wmt.app.util.ImageTranscoder
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns content URIs into multipart parts for the endpoints that accept uploads --
 * task comments and approval requests and their comments. Shared so the streaming and
 * transcoding rules stay identical wherever a file is sent.
 */
@Singleton
class AttachmentPartFactory @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Wraps a content URI in a multipart part named [fieldName] (a Laravel array field).
     * Gallery images the backend would reject (HEIC/HEIF, oversized) are transcoded to
     * JPEG first; everything else streams straight from the ContentResolver so large
     * files (videos can be up to 50MB) never sit fully in memory.
     */
    fun filePart(uriString: String, fieldName: String = ATTACHMENTS_FIELD): MultipartBody.Part? {
        val uri = Uri.parse(uriString)
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: "application/octet-stream"
        val name = displayName(uri) ?: "attachment"
        // Probe once so unreadable URIs are skipped instead of failing mid-request.
        resolver.openInputStream(uri)?.close() ?: return null

        ImageTranscoder.transcodeIfNeeded(context, uri, mime, fileSize(uri))?.let { jpeg ->
            val jpegName = name.substringBeforeLast('.') + ".jpg"
            return MultipartBody.Part.createFormData(
                fieldName,
                jpegName,
                jpeg.asRequestBody("image/jpeg".toMediaTypeOrNull()),
            )
        }

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = mime.toMediaTypeOrNull()
            override fun contentLength(): Long = fileSize(uri) ?: -1L
            // One-shot keeps HttpLoggingInterceptor from buffering the whole file to log it.
            override fun isOneShot(): Boolean = true
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
            }
        }
        return MultipartBody.Part.createFormData(fieldName, name, requestBody)
    }

    /** Maps URIs to parts, dropping any that cannot be read. */
    fun fileParts(
        uriStrings: List<String>,
        fieldName: String = ATTACHMENTS_FIELD,
    ): List<MultipartBody.Part> = uriStrings.mapNotNull { filePart(it, fieldName) }

    private fun fileSize(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    companion object {
        const val ATTACHMENTS_FIELD = "attachments[]"

        /** A plain text field in a multipart body. */
        fun textPart(value: String): RequestBody =
            value.toRequestBody("text/plain".toMediaTypeOrNull())
    }
}
