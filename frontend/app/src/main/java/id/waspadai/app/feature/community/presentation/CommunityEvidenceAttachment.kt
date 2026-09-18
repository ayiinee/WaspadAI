package id.waspadai.app.feature.community.presentation

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import id.waspadai.app.ui.theme.WaspadAIBlue
import id.waspadai.app.ui.theme.WaspadAIDarkBlue
import id.waspadai.app.ui.theme.WaspadAIMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CommunityEvidenceAttachment(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
)

internal enum class EvidenceKind { Image, Pdf, Other }

internal fun evidenceKind(mimeType: String, displayName: String): EvidenceKind {
    val normalizedMime = mimeType.lowercase()
    val extension = displayName.substringAfterLast('.', "").lowercase()
    return when {
        normalizedMime.startsWith("image/") || extension in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif") -> EvidenceKind.Image
        normalizedMime == "application/pdf" || extension == "pdf" -> EvidenceKind.Pdf
        else -> EvidenceKind.Other
    }
}

internal fun resolveEvidenceAttachment(
    contentResolver: ContentResolver,
    uri: Uri,
    fallbackNumber: Int,
): CommunityEvidenceAttachment {
    var displayName: String? = null
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) displayName = cursor.getString(index)
            }
        }
    }
    val safeName = displayName?.takeIf(String::isNotBlank)
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
        ?: "Bukti $fallbackNumber"
    val extensionMime = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(safeName.substringAfterLast('.', "").lowercase())
    val mimeType = contentResolver.getType(uri)?.takeIf(String::isNotBlank)
        ?: extensionMime
        ?: "application/octet-stream"
    return CommunityEvidenceAttachment(uri, safeName, mimeType)
}

internal fun openEvidenceExternally(context: Context, attachment: CommunityEvidenceAttachment): Boolean {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(attachment.uri, attachment.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        context.startActivity(Intent.createChooser(intent, "Buka ${attachment.displayName}"))
        true
    }.getOrDefault(false)
}

@Composable
internal fun EvidencePreviewDialog(
    attachment: CommunityEvidenceAttachment,
    onDismiss: () -> Unit,
    onOpenFailed: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 12.dp,
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            attachment.displayName,
                            color = WaspadAIDarkBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(attachment.mimeType, color = WaspadAIMuted, fontSize = 11.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Tutup preview")
                    }
                }
                when (evidenceKind(attachment.mimeType, attachment.displayName)) {
                    EvidenceKind.Image -> AsyncImage(
                        model = attachment.uri,
                        contentDescription = "Preview ${attachment.displayName}",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 620.dp).background(Color(0xFFF0F4F7)),
                        contentScale = ContentScale.Fit,
                    )
                    EvidenceKind.Pdf -> PdfEvidencePreview(attachment.uri)
                    EvidenceKind.Other -> OtherEvidencePreview(
                        onOpen = {
                            if (!openEvidenceExternally(context, attachment)) onOpenFailed()
                        },
                    )
                }
            }
        }
    }
}

private sealed interface PdfPreviewState {
    data object Loading : PdfPreviewState
    data class Ready(val bitmap: Bitmap, val pageCount: Int) : PdfPreviewState
    data class Error(val message: String) : PdfPreviewState
}

@Composable
private fun PdfEvidencePreview(uri: Uri) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    val previewState by produceState<PdfPreviewState>(PdfPreviewState.Loading, uri, pageIndex) {
        value = PdfPreviewState.Loading
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        require(renderer.pageCount > 0) { "PDF tidak memiliki halaman." }
                        val safePage = pageIndex.coerceIn(0, renderer.pageCount - 1)
                        renderer.openPage(safePage).use { page ->
                            val targetWidth = 1200
                            val targetHeight = (targetWidth * (page.height.toFloat() / page.width)).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(AndroidColor.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            PdfPreviewState.Ready(bitmap, renderer.pageCount)
                        }
                    }
                } ?: error("File PDF tidak dapat dibaca.")
            }.getOrElse { PdfPreviewState.Error("PDF tidak dapat ditampilkan. File mungkin rusak atau aksesnya sudah berakhir.") }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFFF0F4F7)).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val state = previewState) {
            PdfPreviewState.Loading -> Box(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = WaspadAIBlue) }
            is PdfPreviewState.Error -> Box(
                modifier = Modifier.fillMaxWidth().height(240.dp).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) { Text(state.message, color = WaspadAIDarkBlue, textAlign = TextAlign.Center) }
            is PdfPreviewState.Ready -> {
                androidx.compose.foundation.Image(
                    bitmap = state.bitmap.asImageBitmap(),
                    contentDescription = "Halaman ${pageIndex + 1} dari ${state.pageCount}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { pageIndex-- }, enabled = pageIndex > 0) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Halaman sebelumnya")
                    }
                    Text("Halaman ${pageIndex + 1} / ${state.pageCount}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { pageIndex++ }, enabled = pageIndex < state.pageCount - 1) {
                        Icon(Icons.Rounded.ArrowForward, contentDescription = "Halaman berikutnya")
                    }
                }
            }
        }
    }
}

@Composable
private fun OtherEvidencePreview(onOpen: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = WaspadAIBlue, modifier = Modifier.size(54.dp))
        Text(
            "Format ini dibuka dengan aplikasi yang mendukungnya di perangkat Anda.",
            color = WaspadAIDarkBlue,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = WaspadAIBlue)) {
            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Buka file")
        }
    }
}
