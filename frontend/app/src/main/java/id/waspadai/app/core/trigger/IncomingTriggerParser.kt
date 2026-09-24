package id.waspadai.app.core.trigger

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IncomingTriggerParser(private val contentResolver: ContentResolver) {
    suspend fun parse(intent: Intent): Result<PendingVerificationTrigger> = withContext(Dispatchers.IO) {
        runCatching {
            require(intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) {
                "Intent share tidak didukung."
            }
            val contexts = mutableListOf<CapturedContext>()
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
                ?.toString()
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { text ->
                    contexts += CapturedContext.Text(
                        text = text.take(MAX_TEXT_LENGTH),
                        source = TriggerSource.SHARE_SHEET,
                    )
                }
            sharedUris(intent).take(MAX_ATTACHMENTS).forEach { uri ->
                contexts += readImage(uri)
            }
            require(contexts.isNotEmpty()) { "Tidak ada teks atau gambar yang dapat diperiksa." }
            PendingVerificationTrigger(contexts)
        }
    }

    @Suppress("DEPRECATION")
    private fun sharedUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
        Intent.ACTION_SEND_MULTIPLE ->
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }

    private fun readImage(uri: Uri): CapturedContext.Image {
        val contentType = contentResolver.getType(uri).orEmpty().lowercase().let {
            if (it == "image/jpg") "image/jpeg" else it
        }
        require(contentType in SUPPORTED_IMAGE_TYPES) { "Format gambar share tidak didukung." }
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            val output = java.io.ByteArrayOutputStream()
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size() <= MAX_IMAGE_BYTES) { "Ukuran gambar melebihi 8 MB." }
            }
            output.toByteArray()
        } ?: error("Gambar share tidak dapat dibaca.")
        require(bytes.isNotEmpty()) { "Gambar share kosong." }
        require(detectSupportedImageType(bytes) == contentType) {
            "Isi gambar tidak sesuai dengan tipe file yang dibagikan."
        }
        return CapturedContext.Image(
            imageBytes = bytes,
            contentType = contentType,
            fileName = displayName(uri),
            source = TriggerSource.SHARE_SHEET,
        )
    }

    private fun displayName(uri: Uri): String {
        val queried = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null else cursor.getString(0)
            }
        return queried
            ?.substringAfterLast('/')
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.take(120)
            ?.takeIf(String::isNotBlank)
            ?: "shared-image"
    }

    private companion object {
        const val MAX_ATTACHMENTS = 5
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_TEXT_LENGTH = 25_000
        val SUPPORTED_IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

internal fun detectSupportedImageType(bytes: ByteArray): String? = when {
    bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() &&
        bytes[4] == 0x0D.toByte() && bytes[5] == 0x0A.toByte() &&
        bytes[6] == 0x1A.toByte() && bytes[7] == 0x0A.toByte() -> "image/png"
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() &&
        bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
    bytes.size >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
        bytes.copyOfRange(8, 12).decodeToString() == "WEBP" -> "image/webp"
    else -> null
}
