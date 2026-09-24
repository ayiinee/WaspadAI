package id.waspadai.app.core.model

data class VerificationResult(
    val narrative: String,
    val headline: String = "",
    val riskLevel: RiskLevel,
    val reasons: List<String>,
    val recommendedActions: List<String>,
    val verdict: Verdict = Verdict.UNKNOWN,
    val factualStatus: FactualStatus = FactualStatus.UNKNOWN,
    val evidence: List<VerificationEvidence> = emptyList(),
    val sources: List<VerificationSource> = emptyList(),
    val uncertainty: String = "",
    val requiresHumanReview: Boolean = false,
    val disclaimer: String = "",
    val caseId: String? = null,
    val conversationId: String? = null,
    val communityEligible: Boolean = false,
    val communityState: String = "",
)

enum class RiskLevel(val label: String) {
    CRITICAL("Kritis"),
    HIGH("Tinggi"),
    MEDIUM("Sedang"),
    LOW("Rendah"),
    UNKNOWN("Belum diketahui");

    companion object {
        fun fromWire(value: String?): RiskLevel = when (value?.uppercase()) {
            "CRITICAL" -> CRITICAL
            "HIGH" -> HIGH
            "MEDIUM" -> MEDIUM
            "LOW" -> LOW
            else -> UNKNOWN
        }
    }
}

enum class Verdict(val label: String) {
    SUPPORTED("Didukung bukti"),
    REFUTED("Terbantahkan"),
    MISLEADING("Menyesatkan"),
    PARTLY_TRUE("Sebagian benar"),
    OUTDATED("Kedaluwarsa"),
    UNVERIFIED("Belum terverifikasi"),
    SATIRE("Satire"),
    OPINION("Opini"),
    UNKNOWN("Belum diketahui");

    companion object {
        fun fromWire(value: String?): Verdict = entries.firstOrNull {
            it.name == value?.uppercase()
        } ?: UNKNOWN
    }
}

enum class FactualStatus(val label: String) {
    SUPPORTED("Didukung bukti"),
    REFUTED("Terbantahkan"),
    MISLEADING("Menyesatkan"),
    PARTLY_TRUE("Sebagian benar"),
    OUTDATED("Kedaluwarsa"),
    UNVERIFIED("Belum terverifikasi"),
    SATIRE("Satire"),
    OPINION("Opini"),
    NOT_APPLICABLE("Tidak berlaku"),
    UNKNOWN("Belum diketahui");

    companion object {
        fun fromWire(value: String?): FactualStatus = entries.firstOrNull {
            it.name == value?.uppercase()
        } ?: UNKNOWN
    }
}

data class VerificationEvidence(
    val publisher: String,
    val title: String,
    val url: String,
    val excerpt: String,
    val stance: String,
    val verificationStatus: String,
)

data class VerificationSource(
    val publisher: String,
    val title: String,
    val url: String,
    val publishedAt: String? = null,
)
