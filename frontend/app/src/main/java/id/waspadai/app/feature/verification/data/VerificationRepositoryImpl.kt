package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.data.mapper.MissingNarrativeException
import id.waspadai.app.feature.verification.data.mapper.VerificationMapper
import id.waspadai.app.feature.verification.domain.VerificationHistoryDetail
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem
import id.waspadai.app.feature.verification.domain.VerificationConversationDetail
import id.waspadai.app.feature.verification.domain.VerificationConversationSummary
import id.waspadai.app.feature.verification.domain.VerificationConversationTurn
import id.waspadai.app.feature.verification.domain.VerificationRepository
import id.waspadai.app.feature.verification.domain.TextVerificationInput
import id.waspadai.app.feature.verification.domain.ImageVerificationInput
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException

class VerificationRepositoryImpl(
    private val remoteDataSource: VerificationRemoteDataSource,
    private val mapper: VerificationMapper
) : VerificationRepository {
    override suspend fun submitText(input: TextVerificationInput): AppResult<VerificationResult> = try {
        val envelope = remoteDataSource.submitText(
            text = input.text,
            question = input.question,
            sourceUrl = input.sourceUrl,
            senderContext = input.senderContext,
            pageContext = input.pageContext,
            conversationId = input.conversationId,
        )
        AppResult.Success(mapper.map(envelope.result, envelope.history))
    } catch (error: CancellationException) {
        throw error
    } catch (error: MissingAccessTokenException) {
        AppResult.Failure("Sesi Supabase belum tersedia. Login terlebih dahulu sebelum memakai pemeriksaan AI.")
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Pemeriksaan memerlukan waktu terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: MissingNarrativeException) {
        AppResult.Failure("Hasil pemeriksaan belum dapat ditampilkan dengan aman. Coba lagi.")
    } catch (error: Exception) {
        AppResult.Failure("Pemeriksaan belum berhasil. Coba lagi nanti.")
    }

    override suspend fun submitImage(input: ImageVerificationInput): AppResult<VerificationResult> = try {
        val enrichedQuestion = buildImageQuestion(input.question, input.source)
        val envelope = remoteDataSource.submitImage(
            imageBytes = input.imageBytes,
            contentType = input.contentType,
            fileName = input.fileName,
            question = enrichedQuestion,
            conversationId = input.conversationId,
        )
        AppResult.Success(mapper.map(envelope.result, envelope.history))
    } catch (error: CancellationException) {
        throw error
    } catch (error: MissingAccessTokenException) {
        AppResult.Failure("Sesi Supabase belum tersedia. Login terlebih dahulu sebelum memakai pemeriksaan gambar.")
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Pemeriksaan gambar memerlukan waktu terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: MissingNarrativeException) {
        AppResult.Failure("Hasil pemeriksaan gambar belum dapat ditampilkan dengan aman. Coba lagi.")
    } catch (error: Exception) {
        AppResult.Failure("Pemeriksaan gambar belum berhasil. Coba lagi nanti.")
    }

    override suspend fun listHistory(): AppResult<List<VerificationHistoryItem>> = try {
        AppResult.Success(
            remoteDataSource.listHistory().items.map { item ->
                VerificationHistoryItem(
                    caseId = item.caseId,
                    headline = item.headline,
                    verdict = item.verdict,
                    createdAt = item.createdAt
                )
            }
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: MissingAccessTokenException) {
        AppResult.Failure("Sesi Supabase belum tersedia. Login terlebih dahulu sebelum melihat history.")
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Memuat history terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: Exception) {
        AppResult.Failure("History belum dapat dimuat. Coba lagi nanti.")
    }

    override suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail> = try {
        val envelope = remoteDataSource.getHistoryDetail(caseId)
        AppResult.Success(
            VerificationHistoryDetail(
                caseId = envelope.history.caseId,
                inputText = envelope.inputText,
                result = mapper.map(envelope.result, envelope.history)
            )
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: MissingAccessTokenException) {
        AppResult.Failure("Sesi Supabase belum tersedia. Login terlebih dahulu sebelum melihat detail history.")
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Memuat detail history terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: MissingNarrativeException) {
        AppResult.Failure("History belum dapat ditampilkan dengan aman. Coba lagi.")
    } catch (error: Exception) {
        AppResult.Failure("Detail history belum dapat dimuat. Coba lagi nanti.")
    }

    override suspend fun listConversations(): AppResult<List<VerificationConversationSummary>> = try {
        AppResult.Success(
            remoteDataSource.listConversations().items.map { item ->
                VerificationConversationSummary(
                    conversationId = item.conversationId,
                    title = item.title,
                    latestMessagePreview = item.latestMessagePreview,
                    latestMessageRole = item.latestMessageRole,
                    lastVerdict = item.lastVerdict,
                    createdAt = item.createdAt,
                    updatedAt = item.updatedAt,
                )
            }
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: MissingAccessTokenException) {
        AppResult.Failure("Sesi Supabase belum tersedia. Login terlebih dahulu.")
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Memuat percakapan terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: Exception) {
        AppResult.Failure("Percakapan belum dapat dimuat. Coba lagi nanti.")
    }

    override suspend fun getConversationDetail(
        conversationId: String,
    ): AppResult<VerificationConversationDetail> = try {
        val detail = remoteDataSource.getConversationDetail(conversationId)
        AppResult.Success(
            VerificationConversationDetail(
                conversationId = detail.conversationId,
                title = detail.title,
                createdAt = detail.createdAt,
                updatedAt = detail.updatedAt,
                turns = detail.turns.map { turn ->
                    VerificationConversationTurn(
                        caseId = turn.caseId,
                        inputType = turn.inputType,
                        inputText = turn.inputText,
                        createdAt = turn.createdAt,
                        result = mapper.map(turn.result, turn.history),
                    )
                },
            )
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: MissingAccessTokenException) {
        AppResult.Failure("Sesi Supabase belum tersedia. Login terlebih dahulu.")
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Memuat percakapan terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: MissingNarrativeException) {
        AppResult.Failure("Percakapan belum dapat ditampilkan dengan aman.")
    } catch (error: Exception) {
        AppResult.Failure("Detail percakapan belum dapat dimuat. Coba lagi nanti.")
    }
}

private fun buildImageQuestion(
    question: String?,
    source: id.waspadai.app.core.trigger.TriggerSource,
): String? {
    val trimmedQuestion = question?.trim().orEmpty()
    return when {
        source == id.waspadai.app.core.trigger.TriggerSource.FLOATING_OVERLAY && trimmedQuestion.isNotBlank() ->
            "Mode overlay aktif. Sorot area atau elemen visual yang mencurigakan. $trimmedQuestion"
        source == id.waspadai.app.core.trigger.TriggerSource.FLOATING_OVERLAY ->
            "Mode overlay aktif. Sorot area atau elemen visual yang mencurigakan pada gambar ini."
        source == id.waspadai.app.core.trigger.TriggerSource.ASSISTANT && trimmedQuestion.isNotBlank() ->
            "Tangkapan layar dipilih pengguna melalui WaspadAI Assistant. $trimmedQuestion"
        source == id.waspadai.app.core.trigger.TriggerSource.ASSISTANT ->
            "Periksa klaim dan risiko pada area layar yang dipilih pengguna."
        source == id.waspadai.app.core.trigger.TriggerSource.QUICK_SETTINGS && trimmedQuestion.isBlank() ->
            "Periksa klaim dan risiko pada tangkapan layar ini."
        trimmedQuestion.isNotBlank() -> trimmedQuestion
        else -> null
    }
}

private fun VerificationApiException.toSafeMessage(): String {
    val productMessage = error?.message?.takeIf(String::isNotBlank)
    if (productMessage != null) return productMessage
    return when (status.value) {
        401 -> "Sesi tidak valid atau sudah berakhir. Login ulang lalu coba lagi."
        413 -> "Ukuran data terlalu besar untuk diperiksa."
        422 -> "Pesan belum memenuhi format pemeriksaan."
        429 -> "Terlalu banyak permintaan. Tunggu sebentar lalu coba lagi."
        in 500..599 -> "Layanan pemeriksaan sedang bermasalah. Coba lagi nanti."
        else -> "Pemeriksaan belum dapat dilakukan. Coba lagi nanti."
    }
}
