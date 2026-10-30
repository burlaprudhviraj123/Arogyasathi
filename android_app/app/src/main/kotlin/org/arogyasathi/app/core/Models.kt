package org.arogyasathi.app.core

enum class Priority(val level: Int, val label: String) {
    HIGH(1, "High Priority"),
    MEDIUM(2, "Medium Priority"),
    LOW(3, "Routine")
}

enum class VisitStatus {
    OVERDUE,
    DUE_TODAY,
    UPCOMING
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

data class Asha(
    val ashaId: String,
    val ashaName: String,
    val villages: List<String>,
    val phone: String = "+919876543220",
    val subCentre: String = "Chandrapuri CHC"
)

data class AshaProfile(
    val ashaId: String,
    val name: String,
    val phone: String,
    val villages: List<String>,
    val subCentre: String,
    val assignedHouseholdsCount: Int = 100,
    val isVerified: Boolean = true,
    val parentPhc: String = "Chandrapuri Primary Health Centre",
    val medicalOfficerName: String = "Dr. Rajesh Sharma, MBBS",
    val medicalOfficerRole: String = "Medical Officer In-Charge (MO-IC)",
    val medicalOfficerPhone: String = "+91 94310 82101",
    val anmName: String = "Anita Sharma",
    val anmRole: String = "Designated Sector ANM",
    val anmPhone: String = "+91 98350 41102",
    val ambulance108: String = "108",
    val ambulance102: String = "102"
) {
    fun maskedMoPhone(): String = maskPhoneNumber(medicalOfficerPhone)
    fun maskedAnmPhone(): String = maskPhoneNumber(anmPhone)
}

fun maskPhoneNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return if (digits.length >= 10) {
        val ten = digits.takeLast(10)
        val p1 = ten.take(5)
        val p2 = ten.takeLast(2)
        "+91 $p1 •••$p2"
    } else {
        "+91 ••••• •••••"
    }
}

data class Household(
    val householdId: String,
    val village: String,
    val ashaId: String,
    val houseNo: String,
    val headMemberId: String,
    val headName: String,
    val membersCount: Int
)

data class ProtocolSection(
    val sectionId: String,
    val title: String,
    val body: String,
    val category: String,
    val sourceFilename: String = ""
)

data class SafetyResult(
    val isEscalated: Boolean,
    val isRefused: Boolean,
    val citedSectionId: String,
    val answerText: String
)

data class VisitItem(
    val memberId: String,
    val memberName: String,
    val householdId: String,
    val village: String,
    val dueItem: String,
    val priority: Priority,
    val status: VisitStatus,
    val dueDate: String,
    val isDone: Boolean = false
)

data class VisitOutcome(
    val outcomeId: String,
    val householdId: String,
    val memberId: String,
    val memberName: String,
    val dueItem: String,
    val status: String, // COMPLETED, RESCHEDULED, REFUSED
    val notes: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SyncItem(
    val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val payloadJson: String,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

data class Member(
    val memberId: String,
    val householdId: String,
    val village: String,
    val ashaId: String,
    val name: String,
    val relation: String,
    val sex: String,
    val dob: String,
    val isPregnant: Boolean = false,
    val lmpDate: String? = null,
    val edd: String? = null,
    val ancVisitDates: List<String> = emptyList(),
    val lastAncBp: String? = null,
    val lastHbGdl: Double? = null,
    val tdBoosterEligible: Boolean = false,
    val tdDoses: List<String> = emptyList(),
    val placeOfDelivery: String? = null,
    val birthWeightKg: Double? = null,
    val hbncVisitsDone: List<Int> = emptyList(),
    val childVaccines: Map<String, String> = emptyMap(),
    val lastMuacCm: Double? = null,
    val lastMuacDate: String? = null,
    val bilateralPittingOedema: Boolean = false,
    val ncdCondition: String? = null,
    val lastBpCheckDate: String? = null,
    val lastBpReading: String? = null,
    val lastSugarCheckDate: String? = null,
    val lastRbsMgDl: Double? = null
)

data class PatientConsultation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val patientId: String?,
    val patientName: String?,
    val sender: String, // "USER" or "ASSISTANT"
    val queryText: String,
    val answerText: String,
    val answerHindi: String = "",
    val citedSections: List<String> = emptyList(),
    val isEscalation: Boolean = false,
    val isRefusal: Boolean = false,
    val triageLevel: String = "ROUTINE_PROTOCOL",
    val timestamp: Long = System.currentTimeMillis()
)

data class ReferralSlip(
    val id: String = java.util.UUID.randomUUID().toString(),
    val patientId: String,
    val patientName: String,
    val ageGender: String,
    val village: String,
    val householdId: String,
    val suspectedCondition: String,
    val dangerSigns: List<String>,
    val vitalSigns: String,
    val protocolCitation: String,
    val referredFacility: String = "Chandrapuri Community Health Centre (CHC)",
    val immediateActionTaken: String = "108 Emergency Ambulance Dispatched",
    val ashaName: String = "Kalavati Kumari",
    val ashaPhone: String = "+919876543220",
    val medicalOfficerName: String = "Dr. Rajesh Sharma, MBBS",
    val medicalOfficerPhone: String = "+91 94310 82101",
    val timestamp: Long = System.currentTimeMillis()
)
