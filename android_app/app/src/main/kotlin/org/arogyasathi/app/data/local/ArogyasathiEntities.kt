package org.arogyasathi.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.core.Household
import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.PatientConsultation
import org.arogyasathi.app.core.ProtocolSection
import org.arogyasathi.app.core.ReferralSlip
import org.arogyasathi.app.core.SyncItem
import org.arogyasathi.app.core.SyncStatus
import org.arogyasathi.app.core.VisitOutcome

/**
 * Room Entity mirroring the central Neon Postgres `ashas` table and offline `ashas` cache.
 */
@Entity(
    tableName = "ashas",
    indices = [Index(value = ["sub_centre"])]
)
data class AshaEntity(
    @PrimaryKey
    @ColumnInfo(name = "asha_id")
    val ashaId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "phone")
    val phone: String,

    @ColumnInfo(name = "villages_csv")
    val villagesCsv: String,

    @ColumnInfo(name = "sub_centre")
    val subCentre: String,

    @ColumnInfo(name = "parent_phc")
    val parentPhc: String = "Chandrapuri PHC (Sector 1)",

    @ColumnInfo(name = "mo_name")
    val medicalOfficerName: String = "Dr. Rajesh Sharma, MBBS",

    @ColumnInfo(name = "mo_phone")
    val medicalOfficerPhone: String = "+91 94310 82101",

    @ColumnInfo(name = "anm_name")
    val anmName: String = "Anita Sharma",

    @ColumnInfo(name = "anm_phone")
    val anmPhone: String = "+91 98350 41102",

    @ColumnInfo(name = "password")
    val password: String = "Asha@2026"
) {
    fun toDomain(): AshaProfile = AshaProfile(
        ashaId = ashaId,
        name = name,
        phone = phone,
        villages = villagesCsv.split(";").map { it.trim() }.filter { it.isNotEmpty() },
        subCentre = subCentre,
        subCentreId = when (ashaId) {
            "ASHA-RRR5" -> "SC-BWT-101"
            "ASHA-096P" -> "SC-AMP-102"
            "ASHA-YFK5" -> "SC-KSG-103"
            "ASHA-L9R8" -> "SC-DHP-104"
            "ASHA-KQXR" -> "SC-NGK-105"
            else -> "SC-${ashaId.takeLast(4)}-101"
        },
        parentPhc = parentPhc,
        medicalOfficerName = medicalOfficerName,
        medicalOfficerPhone = medicalOfficerPhone,
        anmName = anmName,
        anmPhone = anmPhone,
        password = password
    )

    companion object {
        fun fromDomain(profile: AshaProfile): AshaEntity = AshaEntity(
            ashaId = profile.ashaId,
            name = profile.name,
            phone = profile.phone,
            villagesCsv = profile.villages.joinToString(";"),
            subCentre = profile.subCentre,
            parentPhc = profile.parentPhc,
            medicalOfficerName = profile.medicalOfficerName,
            medicalOfficerPhone = profile.medicalOfficerPhone,
            anmName = profile.anmName,
            anmPhone = profile.anmPhone,
            password = profile.password
        )
    }
}

/**
 * Room Entity mirroring the central Neon Postgres `households` table and local `households` cache.
 */
@Entity(
    tableName = "households",
    indices = [
        Index(value = ["village"]),
        Index(value = ["asha_id"])
    ]
)
data class HouseholdEntity(
    @PrimaryKey
    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "village")
    val village: String,

    @ColumnInfo(name = "asha_id")
    val ashaId: String,

    @ColumnInfo(name = "house_no")
    val houseNo: String,

    @ColumnInfo(name = "head_member_id")
    val headMemberId: String,

    @ColumnInfo(name = "head_name")
    val headName: String,

    @ColumnInfo(name = "members_count")
    val membersCount: Int
) {
    fun toDomain(): Household = Household(
        householdId = householdId,
        village = village,
        ashaId = ashaId,
        houseNo = houseNo,
        headMemberId = headMemberId,
        headName = headName,
        membersCount = membersCount
    )

    companion object {
        fun fromDomain(household: Household): HouseholdEntity = HouseholdEntity(
            householdId = household.householdId,
            village = household.village,
            ashaId = household.ashaId,
            houseNo = household.houseNo,
            headMemberId = household.headMemberId,
            headName = household.headName,
            membersCount = household.membersCount
        )
    }
}

/**
 * Room Entity mirroring the central Neon Postgres `members` table and local `members` cache
 * (2,234 household register beneficiaries across Chandrapuri Block).
 */
@Entity(
    tableName = "members",
    foreignKeys = [
        ForeignKey(
            entity = HouseholdEntity::class,
            parentColumns = ["household_id"],
            childColumns = ["household_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["household_id"]),
        Index(value = ["village"]),
        Index(value = ["asha_id"]),
        Index(value = ["is_pregnant"])
    ]
)
data class MemberEntity(
    @PrimaryKey
    @ColumnInfo(name = "member_id")
    val memberId: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "village")
    val village: String,

    @ColumnInfo(name = "asha_id")
    val ashaId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "relation")
    val relation: String,

    @ColumnInfo(name = "sex")
    val sex: String,

    @ColumnInfo(name = "dob")
    val dob: String,

    @ColumnInfo(name = "is_pregnant")
    val isPregnant: Boolean = false,

    @ColumnInfo(name = "lmp_date")
    val lmpDate: String? = null,

    @ColumnInfo(name = "edd")
    val edd: String? = null,

    @ColumnInfo(name = "anc_visit_dates")
    val ancVisitDatesCsv: String? = null,

    @ColumnInfo(name = "last_anc_bp")
    val lastAncBp: String? = null,

    @ColumnInfo(name = "last_hb_gdl")
    val lastHbGdl: Double? = null,

    @ColumnInfo(name = "td_booster_eligible")
    val tdBoosterEligible: Boolean = false,

    @ColumnInfo(name = "td_doses")
    val tdDosesCsv: String? = null,

    @ColumnInfo(name = "place_of_delivery")
    val placeOfDelivery: String? = null,

    @ColumnInfo(name = "birth_weight_kg")
    val birthWeightKg: Double? = null,

    @ColumnInfo(name = "hbnc_visits_done")
    val hbncVisitsDoneCsv: String? = null,

    @ColumnInfo(name = "child_vaccines")
    val childVaccinesCsv: String? = null,

    @ColumnInfo(name = "last_muac_cm")
    val lastMuacCm: Double? = null,

    @ColumnInfo(name = "last_muac_date")
    val lastMuacDate: String? = null,

    @ColumnInfo(name = "bilateral_pitting_oedema")
    val bilateralPittingOedema: Boolean = false,

    @ColumnInfo(name = "ncd_condition")
    val ncdCondition: String? = null,

    @ColumnInfo(name = "last_bp_check_date")
    val lastBpCheckDate: String? = null,

    @ColumnInfo(name = "last_bp_reading")
    val lastBpReading: String? = null,

    @ColumnInfo(name = "last_sugar_check_date")
    val lastSugarCheckDate: String? = null,

    @ColumnInfo(name = "last_rbs_mg_dl")
    val lastRbsMgDl: Double? = null
) {
    fun toDomain(): Member {
        val ancList = ancVisitDatesCsv?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val tdList = tdDosesCsv?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val hbncList = hbncVisitsDoneCsv?.split(";")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val vacMap = mutableMapOf<String, String>()
        childVaccinesCsv?.split(";")?.forEach { entry ->
            val p = entry.split(":")
            if (p.size == 2) {
                vacMap[p[0].trim()] = p[1].trim()
            }
        }
        return Member(
            memberId = memberId,
            householdId = householdId,
            village = village,
            ashaId = ashaId,
            name = name,
            relation = relation,
            sex = sex,
            dob = dob,
            isPregnant = isPregnant,
            lmpDate = lmpDate,
            edd = edd,
            ancVisitDates = ancList,
            lastAncBp = lastAncBp,
            lastHbGdl = lastHbGdl,
            tdBoosterEligible = tdBoosterEligible,
            tdDoses = tdList,
            placeOfDelivery = placeOfDelivery,
            birthWeightKg = birthWeightKg,
            hbncVisitsDone = hbncList,
            childVaccines = vacMap,
            lastMuacCm = lastMuacCm,
            lastMuacDate = lastMuacDate,
            bilateralPittingOedema = bilateralPittingOedema,
            ncdCondition = ncdCondition,
            lastBpCheckDate = lastBpCheckDate,
            lastBpReading = lastBpReading,
            lastSugarCheckDate = lastSugarCheckDate,
            lastRbsMgDl = lastRbsMgDl
        )
    }

    companion object {
        fun fromDomain(member: Member): MemberEntity = MemberEntity(
            memberId = member.memberId,
            householdId = member.householdId,
            village = member.village,
            ashaId = member.ashaId,
            name = member.name,
            relation = member.relation,
            sex = member.sex,
            dob = member.dob,
            isPregnant = member.isPregnant,
            lmpDate = member.lmpDate,
            edd = member.edd,
            ancVisitDatesCsv = member.ancVisitDates.takeIf { it.isNotEmpty() }?.joinToString(";"),
            lastAncBp = member.lastAncBp,
            lastHbGdl = member.lastHbGdl,
            tdBoosterEligible = member.tdBoosterEligible,
            tdDosesCsv = member.tdDoses.takeIf { it.isNotEmpty() }?.joinToString(";"),
            placeOfDelivery = member.placeOfDelivery,
            birthWeightKg = member.birthWeightKg,
            hbncVisitsDoneCsv = member.hbncVisitsDone.takeIf { it.isNotEmpty() }?.joinToString(";"),
            childVaccinesCsv = member.childVaccines.takeIf { it.isNotEmpty() }?.entries?.joinToString(";") { "${it.key}:${it.value}" },
            lastMuacCm = member.lastMuacCm,
            lastMuacDate = member.lastMuacDate,
            bilateralPittingOedema = member.bilateralPittingOedema,
            ncdCondition = member.ncdCondition,
            lastBpCheckDate = member.lastBpCheckDate,
            lastBpReading = member.lastBpReading,
            lastSugarCheckDate = member.lastSugarCheckDate,
            lastRbsMgDl = member.lastRbsMgDl
        )
    }
}

/**
 * Room Entity mirroring the central Neon Postgres `protocols` table (107 official leaf sections).
 */
@Entity(
    tableName = "protocols",
    indices = [Index(value = ["category"])]
)
data class ProtocolSectionEntity(
    @PrimaryKey
    @ColumnInfo(name = "section_id")
    val sectionId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String,

    @ColumnInfo(name = "category")
    val category: String
) {
    fun toDomain(): ProtocolSection = ProtocolSection(
        sectionId = sectionId,
        title = title,
        body = body,
        category = category
    )

    companion object {
        fun fromDomain(protocol: ProtocolSection): ProtocolSectionEntity = ProtocolSectionEntity(
            sectionId = protocol.sectionId,
            title = protocol.title,
            body = protocol.body,
            category = protocol.category
        )
    }
}

/**
 * Room Entity mirroring the central Neon Postgres `visit_outcomes` table.
 */
@Entity(
    tableName = "visit_outcomes",
    indices = [
        Index(value = ["member_id"]),
        Index(value = ["household_id"])
    ]
)
data class VisitOutcomeEntity(
    @PrimaryKey
    @ColumnInfo(name = "outcome_id")
    val outcomeId: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "member_id")
    val memberId: String,

    @ColumnInfo(name = "member_name")
    val memberName: String,

    @ColumnInfo(name = "due_item")
    val dueItem: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "notes")
    val notes: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    fun toDomain(): VisitOutcome = VisitOutcome(
        outcomeId = outcomeId,
        householdId = householdId,
        memberId = memberId,
        memberName = memberName,
        dueItem = dueItem,
        status = status,
        notes = notes,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(outcome: VisitOutcome): VisitOutcomeEntity = VisitOutcomeEntity(
            outcomeId = outcome.outcomeId,
            householdId = outcome.householdId,
            memberId = outcome.memberId,
            memberName = outcome.memberName,
            dueItem = outcome.dueItem,
            status = outcome.status,
            notes = outcome.notes,
            timestamp = outcome.timestamp
        )
    }
}

/**
 * Room Entity mirroring the central Neon Postgres `danger_escalations` / local `referral_slips` table.
 */
@Entity(
    tableName = "referral_slips",
    indices = [
        Index(value = ["patient_id"]),
        Index(value = ["household_id"])
    ]
)
data class DangerEscalationEntity(
    @PrimaryKey
    @ColumnInfo(name = "slip_id")
    val id: String,

    @ColumnInfo(name = "patient_id")
    val patientId: String,

    @ColumnInfo(name = "patient_name")
    val patientName: String,

    @ColumnInfo(name = "age_gender")
    val ageGender: String,

    @ColumnInfo(name = "village")
    val village: String,

    @ColumnInfo(name = "household_id")
    val householdId: String,

    @ColumnInfo(name = "suspected_condition")
    val suspectedCondition: String,

    @ColumnInfo(name = "danger_signs_csv")
    val dangerSignsCsv: String,

    @ColumnInfo(name = "vital_signs")
    val vitalSigns: String,

    @ColumnInfo(name = "protocol_citation")
    val protocolCitation: String,

    @ColumnInfo(name = "referred_facility")
    val referredFacility: String,

    @ColumnInfo(name = "immediate_action_taken")
    val immediateActionTaken: String,

    @ColumnInfo(name = "asha_name")
    val ashaName: String,

    @ColumnInfo(name = "asha_phone")
    val ashaPhone: String,

    @ColumnInfo(name = "mo_name")
    val medicalOfficerName: String = "Dr. Rajesh Sharma, MBBS",

    @ColumnInfo(name = "mo_phone")
    val medicalOfficerPhone: String = "+91 94310 82101",

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    fun toDomain(): ReferralSlip = ReferralSlip(
        id = id,
        patientId = patientId,
        patientName = patientName,
        ageGender = ageGender,
        village = village,
        householdId = householdId,
        suspectedCondition = suspectedCondition,
        dangerSigns = dangerSignsCsv.split(";").map { it.trim() }.filter { it.isNotEmpty() },
        vitalSigns = vitalSigns,
        protocolCitation = protocolCitation,
        referredFacility = referredFacility,
        immediateActionTaken = immediateActionTaken,
        ashaName = ashaName,
        ashaPhone = ashaPhone,
        medicalOfficerName = medicalOfficerName,
        medicalOfficerPhone = medicalOfficerPhone,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(slip: ReferralSlip): DangerEscalationEntity = DangerEscalationEntity(
            id = slip.id,
            patientId = slip.patientId,
            patientName = slip.patientName,
            ageGender = slip.ageGender,
            village = slip.village,
            householdId = slip.householdId,
            suspectedCondition = slip.suspectedCondition,
            dangerSignsCsv = slip.dangerSigns.joinToString(";"),
            vitalSigns = slip.vitalSigns,
            protocolCitation = slip.protocolCitation,
            referredFacility = slip.referredFacility,
            immediateActionTaken = slip.immediateActionTaken,
            ashaName = slip.ashaName,
            ashaPhone = slip.ashaPhone,
            medicalOfficerName = slip.medicalOfficerName,
            medicalOfficerPhone = slip.medicalOfficerPhone,
            timestamp = slip.timestamp
        )
    }
}

/**
 * Room Entity for offline-first transactional outbox synchronization (`sync_queue`).
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["status"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "entity_type")
    val entityType: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "status")
    val status: String,

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): SyncItem = SyncItem(
        id = id,
        entityType = entityType,
        entityId = entityId,
        payloadJson = payloadJson,
        status = runCatching { SyncStatus.valueOf(status) }.getOrDefault(SyncStatus.PENDING),
        retryCount = retryCount,
        createdAt = createdAt
    )
}

/**
 * Room Entity for longitudinal per-patient offline clinical consultation history (`patient_chat_history`).
 */
@Entity(
    tableName = "patient_chat_history",
    indices = [Index(value = ["patient_id", "timestamp"])]
)
data class PatientChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "patient_id")
    val patientId: String?,

    @ColumnInfo(name = "patient_name")
    val patientName: String?,

    @ColumnInfo(name = "sender")
    val sender: String,

    @ColumnInfo(name = "query_text")
    val queryText: String,

    @ColumnInfo(name = "answer_text")
    val answerText: String,

    @ColumnInfo(name = "answer_hindi")
    val answerHindi: String,

    @ColumnInfo(name = "cited_sections")
    val citedSectionsCsv: String,

    @ColumnInfo(name = "is_escalation")
    val isEscalation: Boolean,

    @ColumnInfo(name = "is_refusal")
    val isRefusal: Boolean,

    @ColumnInfo(name = "triage_level")
    val triageLevel: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
) {
    fun toDomain(): PatientConsultation = PatientConsultation(
        id = id,
        patientId = patientId,
        patientName = patientName,
        sender = sender,
        queryText = queryText,
        answerText = answerText,
        answerHindi = answerHindi,
        citedSections = citedSectionsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        isEscalation = isEscalation,
        isRefusal = isRefusal,
        triageLevel = triageLevel,
        timestamp = timestamp
    )

    companion object {
        fun fromDomain(consultation: PatientConsultation): PatientChatEntity = PatientChatEntity(
            id = consultation.id,
            patientId = consultation.patientId,
            patientName = consultation.patientName,
            sender = consultation.sender,
            queryText = consultation.queryText,
            answerText = consultation.answerText,
            answerHindi = consultation.answerHindi,
            citedSectionsCsv = consultation.citedSections.joinToString(","),
            isEscalation = consultation.isEscalation,
            isRefusal = consultation.isRefusal,
            triageLevel = consultation.triageLevel,
            timestamp = consultation.timestamp
        )
    }
}
