package id.waspadai.app.feature.verification.data.mapper

import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.core.model.FactualStatus
import id.waspadai.app.core.model.Verdict
import id.waspadai.app.feature.verification.data.dto.AssessmentDimensionsDto
import id.waspadai.app.feature.verification.data.dto.EvidenceDto
import id.waspadai.app.feature.verification.data.dto.NarrativeDto
import id.waspadai.app.feature.verification.data.dto.PresentationDto
import id.waspadai.app.feature.verification.data.dto.RecommendedActionDto
import id.waspadai.app.feature.verification.data.dto.RulebookDto
import id.waspadai.app.feature.verification.data.dto.SourceDto
import id.waspadai.app.feature.verification.data.dto.VerificationResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificationMapperTest {
    private val mapper = VerificationMapper()

    @Test
    fun `maps direct public API narrative without product wrapper`() {
        val result = mapper.map(
            VerificationResponseDto(
                riskLevel = "HIGH",
                why = listOf("Pengirim belum terverifikasi."),
                recommendedActions = listOf(RecommendedActionDto(title = "Jangan kirim OTP.")),
                presentation = PresentationDto(NarrativeDto("Periksa kembali sumber resmi."))
            )
        )

        assertEquals("Periksa kembali sumber resmi.", result.narrative)
        assertEquals(RiskLevel.HIGH, result.riskLevel)
        assertEquals(listOf("Jangan kirim OTP."), result.recommendedActions)
    }

    @Test
    fun `rejects response with missing narrative`() {
        assertThrows(MissingNarrativeException::class.java) {
            mapper.map(VerificationResponseDto())
        }
    }

    @Test
    fun `marks non checkable image response as lightweight result`() {
        val result = mapper.map(
            VerificationResponseDto(
                headline = "Gambar tidak memuat klaim yang bisa diperiksa",
                rulebook = RulebookDto(retrievalMode = "SKIPPED_NON_CHECKABLE_IMAGE"),
                presentation = PresentationDto(
                    NarrativeDto(
                        text = "Gambar ini belum memuat klaim yang bisa diperiksa.",
                        paragraphs = listOf(
                            "Gambar ini belum memuat klaim yang bisa diperiksa.",
                            "Unggah screenshot berita, pesan, poster, dokumen, atau gunakan input teks.",
                        ),
                    )
                ),
            )
        )

        assertTrue(result.isNonCheckableImage)
        assertEquals(
            listOf(
                "Gambar ini belum memuat klaim yang bisa diperiksa.",
                "Unggah screenshot berita, pesan, poster, dokumen, atau gunakan input teks.",
            ),
            result.narrativeParagraphs,
        )
    }

    @Test
    fun `maps complete critical response and ignores unsafe source urls`() {
        val result = mapper.map(
            VerificationResponseDto(
                headline = "Peringatan penipuan",
                verdict = "REFUTED",
                riskLevel = "CRITICAL",
                dimensions = AssessmentDimensionsDto(factualStatus = "MISLEADING"),
                evidence = listOf(
                    EvidenceDto(
                        publisher = "Cek Fakta",
                        title = "Klaim dibantah",
                        url = "https://example.org/fact",
                        excerpt = "Tidak ada program resmi tersebut.",
                        stance = "CONTRADICTS",
                        verificationStatus = "VERIFIED",
                    )
                ),
                sources = listOf(
                    SourceDto("Resmi", "Pengumuman", "https://example.org/source"),
                    SourceDto("Tidak aman", "Lokal", "file:///data/private"),
                ),
                uncertainty = "Identitas pengirim belum diketahui.",
                requiresHumanReview = true,
                disclaimer = "Gunakan sumber resmi.",
                presentation = PresentationDto(NarrativeDto("Jangan ikuti instruksi pengirim.")),
            )
        )

        assertEquals(RiskLevel.CRITICAL, result.riskLevel)
        assertEquals(Verdict.REFUTED, result.verdict)
        assertEquals(FactualStatus.MISLEADING, result.factualStatus)
        assertEquals(1, result.evidence.size)
        assertEquals(1, result.sources.size)
        assertTrue(result.requiresHumanReview)
    }
}
