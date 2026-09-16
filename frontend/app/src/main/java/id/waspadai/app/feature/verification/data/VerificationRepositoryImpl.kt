package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.data.mapper.MissingNarrativeException
import id.waspadai.app.feature.verification.data.mapper.VerificationMapper
import id.waspadai.app.feature.verification.domain.VerificationRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException

class VerificationRepositoryImpl(
    private val remoteDataSource: VerificationRemoteDataSource,
    private val mapper: VerificationMapper
) : VerificationRepository {
    override suspend fun submitText(text: String): AppResult<VerificationResult> = try {
        AppResult.Success(mapper.map(remoteDataSource.submitText(text)))
    } catch (error: CancellationException) {
        throw error
    } catch (error: VerificationApiException) {
        AppResult.Failure(error.status.toSafeMessage())
    } catch (error: HttpRequestTimeoutException) {
        AppResult.Failure("Pemeriksaan memerlukan waktu terlalu lama. Coba lagi nanti.")
    } catch (error: IOException) {
        AppResult.Failure("Koneksi belum tersedia. Periksa internet lalu coba lagi.")
    } catch (error: MissingNarrativeException) {
        AppResult.Failure("Hasil pemeriksaan belum dapat ditampilkan dengan aman. Coba lagi.")
    } catch (error: Exception) {
        AppResult.Failure("Pemeriksaan belum berhasil. Coba lagi nanti.")
    }
}

private fun HttpStatusCode.toSafeMessage(): String = when (value) {
    413 -> "Ukuran data terlalu besar untuk diperiksa."
    422 -> "Pesan belum memenuhi format pemeriksaan."
    429 -> "Terlalu banyak permintaan. Tunggu sebentar lalu coba lagi."
    in 500..599 -> "Layanan pemeriksaan sedang bermasalah. Coba lagi nanti."
    else -> "Pemeriksaan belum dapat dilakukan. Coba lagi nanti."
}
