package id.waspadai.app.core.model

data class VerificationResult(
    val narrative: String,
    val headline: String = "",
    val riskLevel: RiskLevel,
    val reasons: List<String>,
    val recommendedActions: List<String>,
    val caseId: String? = null,
    val conversationId: String? = null,
    val communityEligible: Boolean = false,
    val communityState: String = "",
)

enum class RiskLevel(val label: String) {
    HIGH("Tinggi"),
    MEDIUM("Sedang"),
    LOW("Rendah"),
    UNKNOWN("Belum diketahui");

    companion object {
        fun fromWire(value: String?): RiskLevel = when (value?.uppercase()) {
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> UNKNOWN
        }
    }
}
