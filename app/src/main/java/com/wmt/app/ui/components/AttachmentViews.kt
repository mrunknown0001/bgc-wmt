package com.wmt.app.ui.components

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.wmt.app.domain.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/** A single comment attachment: image thumbnail (tap → zoomable preview), video tile (tap → in-app player), or file row (tap → PDF preview / download), plus a download button. */
@Composable
fun AttachmentView(attachment: Attachment) {
    val context = LocalContext.current
    var showImage by remember { mutableStateOf(false) }
    var showPdf by remember { mutableStateOf(false) }
    var showVideo by remember { mutableStateOf(false) }
    val isPdf = attachment.fileType == "application/pdf" ||
        attachment.fileName.endsWith(".pdf", ignoreCase = true)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (attachment.isImage) {
            AsyncImage(
                model = attachment.url,
                contentDescription = attachment.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showImage = true },
            )
            Spacer(Modifier.weight(1f))
        } else if (attachment.isVideo) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable { showVideo = true },
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = formatBytes(attachment.fileSizeBytes)
                if (size.isNotEmpty()) {
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Icon(
                imageVector = when {
                    isPdf -> Icons.Default.PictureAsPdf
                    attachment.isSpreadsheet -> Icons.Default.TableChart
                    else -> Icons.Default.AttachFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        when {
                            isPdf -> showPdf = true
                            // Browsers can't render spreadsheets — save them instead.
                            attachment.isSpreadsheet ->
                                downloadAttachment(context, attachment.url, attachment.fileName)
                            else -> openExternally(context, attachment.url)
                        }
                    },
            ) {
                Text(
                    text = attachment.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val size = formatBytes(attachment.fileSizeBytes)
                if (size.isNotEmpty()) {
                    Text(
                        text = size,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        IconButton(onClick = { downloadAttachment(context, attachment.url, attachment.fileName) }) {
            Icon(Icons.Default.Download, contentDescription = "Download")
        }
    }

    if (showImage) {
        ImagePreviewDialog(
            url = attachment.url,
            title = attachment.fileName,
            onDownload = { downloadAttachment(context, attachment.url, attachment.fileName) },
            onDismiss = { showImage = false },
        )
    }
    if (showPdf) {
        PdfPreviewDialog(
            url = attachment.url,
            title = attachment.fileName,
            onDownload = { downloadAttachment(context, attachment.url, attachment.fileName) },
            onDismiss = { showPdf = false },
        )
    }
    if (showVideo) {
        VideoPreviewDialog(
            url = attachment.url,
            title = attachment.fileName,
            onDownload = { downloadAttachment(context, attachment.url, attachment.fileName) },
            onDismiss = { showVideo = false },
        )
    }
}

@Composable
private fun VideoPreviewDialog(
    url: String,
    title: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.parse(url))
                        setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                        setOnPreparedListener { start() }
                        setOnErrorListener { _, _, _ ->
                            Toast.makeText(ctx, "Couldn't play video", Toast.LENGTH_SHORT).show()
                            true
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 16.dp),
            )
            PreviewTopBar(title, onDownload, onDismiss)
        }
    }
}

@Composable
private fun ImagePreviewDialog(
    url: String,
    title: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            AsyncImage(
                model = url,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    )
                    .transformable(transformState),
            )
            PreviewTopBar(title, onDownload, onDismiss)
        }
    }
}

@Composable
private fun PdfPreviewDialog(
    url: String,
    title: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<Bitmap>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(url) {
        runCatching { renderPdf(context, url) }
            .onSuccess { pages = it }
            .onFailure { error = it.message ?: "Couldn't open PDF" }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1C)),
        ) {
            when {
                error != null -> Text(
                    text = error!!,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
                pages == null -> CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 56.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(pages!!) { page ->
                        Image(
                            bitmap = page.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            PreviewTopBar(title, onDownload, onDismiss)
        }
    }
}

@Composable
private fun BoxScope.PreviewTopBar(title: String, onDownload: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
        IconButton(onClick = onDownload) {
            Icon(Icons.Default.Download, contentDescription = "Download", tint = Color.White)
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

/** Downloads the file to the public Downloads folder via the system DownloadManager. */
private fun downloadAttachment(context: Context, url: String, fileName: String) {
    runCatching {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading attachment")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        Toast.makeText(context, "Downloading $fileName", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Download failed: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openExternally(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) {
        size /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(size, units[unit])
}

/** Downloads a PDF to cache and rasterises each page to a bitmap for in-app display. */
private suspend fun renderPdf(context: Context, url: String): List<Bitmap> = withContext(Dispatchers.IO) {
    val file = File(context.cacheDir, "pdfpreview_${url.hashCode()}.pdf")
    URL(url).openStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }

    val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    val renderer = PdfRenderer(descriptor)
    val targetWidth = 1080
    val bitmaps = ArrayList<Bitmap>(renderer.pageCount)
    try {
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val height = (targetWidth.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmaps.add(bitmap)
        }
    } finally {
        renderer.close()
        descriptor.close()
    }
    bitmaps
}
