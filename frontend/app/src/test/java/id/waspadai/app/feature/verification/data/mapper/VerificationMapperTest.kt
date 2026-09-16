package id.waspadai.app.feature.verification.data.mapper

import id.waspadai.app.core.model.RiskLevel
import id.waspadai.app.feature.verification.data.dto.NarrativeDto
import id.waspadai.app.feature.verification.data.dto.PresentationDto
import id.waspadai.app.feature.verification.data.dto.RecommendedActionDto
import id.waspadai.app.feature.verification.data.dto.VerificationResponseDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
}
