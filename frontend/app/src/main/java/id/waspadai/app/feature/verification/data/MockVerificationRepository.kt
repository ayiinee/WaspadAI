package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.domain.VerificationHistoryDetail
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem
import id.waspadai.app.feature.verification.domain.ImageVerificationInput
import id.waspadai.app.feature.verification.domain.TextVerificationInput
import id.waspadai.app.feature.verification.domain.VerificationConversationDetail
import id.waspadai.app.feature.verification.domain.VerificationConversationAttachment
import id.waspadai.app.feature.verification.domain.VerificationConversationPage
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.feature.verification.domain.VerificationConversationTurn
import id.waspadai.app.feature.verification.domain.VerificationRepository
import java.time.Instant
import kotlinx.coroutines.delay

class MockVerificationRepository : VerificationRepository {
    private val conversations = linkedMapOf<String, VerificationConversationSummary>()
    private val turns = mutableMapOf<String, MutableList<VerificationConversationTurn>>()
    private val attachments = mutableMapOf<Pair<String, String>, ByteArray>()
    private var nextConversationNumber = 1
    private var nextCaseNumber = 1

    override suspend fun submitText(input: TextVerificationInput): AppResult<VerificationResult> {
        delay(700)
        val text = input.text
        val lowerText = text.lowercase()
        val result = when {
            listOf("otp", "pin", "password", "kode verifikasi").any(lowerText::contains) -> {
                VerificationResult(
                    narrative = "Waspada. Jangan pernah mengirim OTP, PIN, kata sandi, atau kode verifikasi kepada siapa pun. Pihak resmi tidak akan meminta data tersebut lewat chat.",
                    riskLevel = RiskLevel.HIGH,
                    reasons = listOf(
                        "Pesan meminta data keamanan akun.",
                        "Permintaan mendesak sering dipakai untuk membuat korban panik."
                    ),
                    recommendedActions = listOf(
                        "Jangan membalas dengan kode apa pun.",
                        "Hubungi layanan resmi melalui kanal yang tepercaya."
                    )
                )
            }
            listOf("hadiah", "menang", "transfer", "rekening", "tautan").any(lowerText::contains) -> {
                VerificationResult(
                    narrative = "Pesan ini perlu diverifikasi. Jangan klik tautan atau mengirim uang sebelum mengecek nomor pengirim dan kanal resmi terkait.",
                    riskLevel = RiskLevel.MEDIUM,
                    reasons = listOf(
                        "Klaim hadiah atau permintaan transfer sering dipakai dalam modus penipuan.",
                        "Sumber pesan belum dapat diverifikasi pada mode simulasi."
                    ),
                    recommendedActions = listOf(
                        "Periksa informasi di situs atau akun resmi.",
                        "Jangan memasukkan data pribadi pada tautan yang dikirim lewat chat."
                    )
                )
            }
            else -> {
                VerificationResult(
                    narrative = "Pesan telah dianalisis dalam mode simulasi. Periksa pengirim, jangan membuka tautan mencurigakan, dan jangan membagikan data pribadi.",
                    riskLevel = RiskLevel.MEDIUM,
                    reasons = listOf(
                        "Identitas pengirim belum diverifikasi.",
                        "Konteks pesan saja belum cukup untuk menyatakan klaim aman."
                    ),
                    recommendedActions = listOf(
                        "Bandingkan informasi dengan kanal resmi.",
                        "Tunda tindakan yang meminta data, uang, atau pemasangan aplikasi."
                    )
                )
            }
        }
        return AppResult.Success(
            persistTurn(
                conversationId = input.conversationId,
                inputType = "TEXT",
                inputText = text,
                result = result.copy(headline = result.narrative.take(80)),
            )
        )
    }

    override suspend fun submitImage(input: ImageVerificationInput): AppResult<VerificationResult> {
        delay(700)
        val modeText = if (input.source == id.waspadai.app.core.trigger.TriggerSource.FLOATING_OVERLAY) {
            " Mode overlay aktif untuk menandai bagian visual yang perlu diperhatikan."
        } else {
            ""
        }
        val result = VerificationResult(
                narrative = "Gambar \"${input.fileName}\" dianalisis dalam mode simulasi.$modeText Periksa sumber asli gambar dan jangan mengikuti instruksi pembayaran, tautan, atau kode yang terlihat mencurigakan.",
                riskLevel = RiskLevel.MEDIUM,
                reasons = listOf(
                    "Validasi gambar live belum aktif pada mode simulasi.",
                    "Konteks visual perlu dibandingkan dengan kanal resmi."
                ),
                recommendedActions = listOf(
                    "Pastikan gambar berasal dari sumber tepercaya.",
                    "Jangan memindai QR atau membuka tautan dari gambar yang belum diverifikasi."
                ),
                headline = "Pemeriksaan gambar ${input.fileName}",
            )
        return AppResult.Success(
            persistTurn(
                conversationId = input.conversationId,
                inputType = "IMAGE",
                inputText = input.question ?: "Gambar dikirim untuk diperiksa.",
                result = result,
                attachmentBytes = input.imageBytes,
                attachmentContentType = input.contentType,
            )
        )
    }

    override suspend fun listHistory(): AppResult<List<VerificationHistoryItem>> =
        AppResult.Success(emptyList())

    override suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail> =
        AppResult.Failure("History hanya tersedia saat backend aktif.")

    override suspend fun listConversations(): AppResult<List<VerificationConversationSummary>> =
        AppResult.Success(sortedConversations())

    override suspend fun listConversationPage(
        cursor: String?,
    ): AppResult<VerificationConversationPage> {
        val start = cursor?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        val all = sortedConversations()
        val pageSize = 20
        val page = all.drop(start).take(pageSize)
        val next = (start + page.size).takeIf { it < all.size }?.toString()
        return AppResult.Success(VerificationConversationPage(page, next))
    }

    override suspend fun renameConversation(
        conversationId: String,
        title: String,
    ): AppResult<VerificationConversationSummary> {
        val current = conversations[conversationId]
            ?: return AppResult.Failure("Percakapan tidak ditemukan.")
        val updated = current.copy(title = title.trim().take(80), updatedAt = Instant.now().toString())
        conversations[conversationId] = updated
        return AppResult.Success(updated)
    }

    override suspend fun deleteConversation(conversationId: String): AppResult<Unit> {
        if (conversations.remove(conversationId) == null) {
            return AppResult.Failure("Percakapan tidak ditemukan.")
        }
        turns.remove(conversationId)
        attachments.keys.removeAll { it.first == conversationId }
        return AppResult.Success(Unit)
    }

    override suspend fun loadConversationAttachment(
        conversationId: String,
        caseId: String,
    ): AppResult<ByteArray> = attachments[conversationId to caseId]
        ?.let { AppResult.Success(it) }
        ?: AppResult.Failure("Lampiran tidak ditemukan.")

    override suspend fun getConversationDetail(
        conversationId: String,
    ): AppResult<VerificationConversationDetail> {
        val summary = conversations[conversationId]
            ?: return AppResult.Failure("Percakapan tidak ditemukan.")
        return AppResult.Success(
            VerificationConversationDetail(
                conversationId = conversationId,
                title = summary.title,
                createdAt = summary.createdAt,
                updatedAt = summary.updatedAt,
                turns = turns[conversationId].orEmpty(),
            )
        )
    }

    private fun persistTurn(
        conversationId: String?,
        inputType: String,
        inputText: String,
        result: VerificationResult,
        attachmentBytes: ByteArray? = null,
        attachmentContentType: String? = null,
    ): VerificationResult {
        val resolvedConversationId = conversationId ?: "mock-conversation-${nextConversationNumber++}"
        val caseId = "mock-case-${nextCaseNumber++}"
        val now = Instant.now().toString()
        val storedResult = result.copy(caseId = caseId, conversationId = resolvedConversationId)
        val existing = conversations[resolvedConversationId]
        conversations[resolvedConversationId] = VerificationConversationSummary(
            conversationId = resolvedConversationId,
            title = existing?.title ?: conversationTitle(inputText),
            latestMessagePreview = result.narrative.take(2000),
            latestMessageRole = "ASSISTANT",
            lastVerdict = result.riskLevel.name,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        turns.getOrPut(resolvedConversationId, ::mutableListOf).add(
            VerificationConversationTurn(
                caseId = caseId,
                inputType = inputType,
                inputText = inputText,
                createdAt = now,
                result = storedResult,
                attachment = if (inputType == "IMAGE") {
                    VerificationConversationAttachment(
                        available = attachmentBytes != null,
                        contentType = attachmentContentType,
                        sizeBytes = attachmentBytes?.size?.toLong(),
                    )
                } else {
                    null
                },
            )
        )
        if (attachmentBytes != null) {
            attachments[resolvedConversationId to caseId] = attachmentBytes
        }
        return storedResult
    }

    private fun sortedConversations(): List<VerificationConversationSummary> =
        conversations.values.sortedByDescending { it.updatedAt }

    private fun conversationTitle(input: String): String = input
        .trim()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .take(7)
        .joinToString(" ")
        .take(80)
        .ifBlank { "Pemeriksaan gambar" }

}
