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
    val isVerified: Boolean = true
)

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
    val category: String
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

