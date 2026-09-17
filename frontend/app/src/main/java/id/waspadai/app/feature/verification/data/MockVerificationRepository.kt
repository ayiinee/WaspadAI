package id.waspadai.app.feature.verification.data

import id.waspadai.app.core.common.AppResult
import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.VerificationResult
import id.waspadai.app.feature.verification.domain.VerificationHistoryDetail
import id.waspadai.app.feature.verification.domain.VerificationHistoryItem
import id.waspadai.app.feature.verification.domain.VerificationRepository
import kotlinx.coroutines.delay

class MockVerificationRepository : VerificationRepository {
    override suspend fun submitText(text: String): AppResult<VerificationResult> {
        delay(700)
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
        return AppResult.Success(result)
    }

    override suspend fun submitImage(
        imageBytes: ByteArray,
        contentType: String,
        fileName: String,
        question: String?,
        overlayModeEnabled: Boolean,
    ): AppResult<VerificationResult> {
        delay(700)
        val modeText = if (overlayModeEnabled) {
            " Mode overlay aktif untuk menandai bagian visual yang perlu diperhatikan."
        } else {
            ""
        }
        return AppResult.Success(
            VerificationResult(
                narrative = "Gambar \"$fileName\" dianalisis dalam mode simulasi.$modeText Periksa sumber asli gambar dan jangan mengikuti instruksi pembayaran, tautan, atau kode yang terlihat mencurigakan.",
                riskLevel = RiskLevel.MEDIUM,
                reasons = listOf(
                    "Validasi gambar live belum aktif pada mode simulasi.",
                    "Konteks visual perlu dibandingkan dengan kanal resmi."
                ),
                recommendedActions = listOf(
                    "Pastikan gambar berasal dari sumber tepercaya.",
                    "Jangan memindai QR atau membuka tautan dari gambar yang belum diverifikasi."
                )
            )
        )
    }

    override suspend fun listHistory(): AppResult<List<VerificationHistoryItem>> =
        AppResult.Success(emptyList())

    override suspend fun getHistoryDetail(caseId: String): AppResult<VerificationHistoryDetail> =
        AppResult.Failure("History hanya tersedia saat backend aktif.")
}
