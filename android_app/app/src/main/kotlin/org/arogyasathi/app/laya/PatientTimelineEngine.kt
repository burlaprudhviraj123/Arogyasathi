package org.arogyasathi.app.laya

import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.PatientConsultation
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Longitudinal Clinical Timeline & Vitals Analysis Engine
 * Extracts and synthesizes patient history, gestational calculations,
 * immunization status, vitals tracking, and longitudinal care gaps
 * relative to the field operating date.
 */
data class PatientClinicalTimeline(
    val member: Member,
    val refDate: String,
    val ageInfo: PatientAgeInfo,
    val ageDisplay: String,
    val lifeStage: LifeStage,
    val gestation: GestationTimeline?,
    val vitals: PatientVitalsAnalysis,
    val immunization: ChildImmunizationStatus?,
    val hbncStatus: HbncPostnatalStatus?,
    val ncdStatus: NcdTrackingStatus?,
    val recentSymptomNotes: List<String>
)

enum class LifeStage {
    NEWBORN,          // 0 - 60 days
    INFANT,           // 2 - 11 months
    TODDLER,          // 12 - 23 months
    CHILD_UNDER_FIVE, // 2 - 5 years
    CHILD_OLDER,      // 6 - 14 years
    ADOLESCENT,       // 15 - 18 years
    ADULT,            // 19 - 49 years
    ELDERLY           // 50+ years
}

data class GestationTimeline(
    val lmpDate: String?,
    val edd: String?,
    val gestationalDays: Int,
    val gestationalWeeks: Int,
    val remainingDaysInWeek: Int,
    val trimester: Int, // 1, 2, 3
    val displayWeeks: String,
    val nextDueAnc: String,
    val completedAncCount: Int,
    val daysUntilEdd: Int
)

data class PatientVitalsAnalysis(
    val bpReading: String?,
    val bpCategory: String, // Normal, Borderline, PreEclampsiaAlert, Crisis
    val isBpElevated: Boolean,
    val hbGdl: Double?,
    val anaemiaSeverity: String, // Normal, Mild, Moderate, Severe
    val isSevereAnaemia: Boolean,
    val muacCm: Double?,
    val muacStatus: String, // Normal, MAM, SAM
    val hasOedema: Boolean,
    val fastBreathingThreshold: Int // breaths per minute
)

data class ChildImmunizationStatus(
    val completedVaccines: Map<String, String>,
    val nextScheduledVaccine: String,
    val isInfantScheduleComplete: Boolean,
    val overdueVaccines: List<String>
)

data class HbncPostnatalStatus(
    val placeOfDelivery: String,
    val birthWeightKg: Double?,
    val isLowBirthWeight: Boolean,
    val isVeryLowBirthWeight: Boolean,
    val completedVisits: List<Int>,
    val nextDueVisitNumber: Int?,
    val totalVisitsRequired: Int
)

data class NcdTrackingStatus(
    val condition: String,
    val lastBpReading: String?,
    val lastBpDate: String?,
    val daysSinceLastBp: Int?,
    val isBpOverdue: Boolean,
    val lastRbsMgDl: Double?,
    val lastRbsDate: String?,
    val isRbsOverdue: Boolean
)

object PatientTimelineEngine {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun analyze(
        member: Member,
        refDate: String = "2026-10-26",
        recentHistory: List<PatientConsultation> = emptyList()
    ): PatientClinicalTimeline {
        val ageInfo = PatientAgeCalculator.calculate(member.dob, refDate) ?: PatientAgeInfo(
            years = 25, months = 0, days = 0, totalMonths = 300, totalDays = 9125,
            ageDisplay = "Adult", isChildUnderTwo = false, isChildTwoToFive = false,
            isChildUnderFive = false, isInfantUnderOne = false, isUnderSixMonths = false,
            isNewbornUnderTwoMonths = false, isAdult = true
        )

        val lifeStage = when {
            ageInfo.totalDays <= 60 -> LifeStage.NEWBORN
            ageInfo.totalMonths < 12 -> LifeStage.INFANT
            ageInfo.totalMonths < 24 -> LifeStage.TODDLER
            ageInfo.totalMonths <= 60 -> LifeStage.CHILD_UNDER_FIVE
            ageInfo.years < 15 -> LifeStage.CHILD_OLDER
            ageInfo.years < 19 -> LifeStage.ADOLESCENT
            ageInfo.years < 50 -> LifeStage.ADULT
            else -> LifeStage.ELDERLY
        }

        // 1. Gestational Timeline
        val gestation = if (member.isPregnant) {
            calculateGestation(member.lmpDate, member.edd, member.ancVisitDates, refDate)
        } else null

        // 2. Vitals Analysis
        val vitals = analyzeVitals(member, ageInfo, lifeStage)

        // 3. Child Immunization
        val immunization = if (ageInfo.isChildUnderFive) {
            analyzeChildImmunization(member, ageInfo)
        } else null

        // 4. HBNC Postnatal
        val hbncStatus = if (ageInfo.totalDays <= 90 || member.hbncVisitsDone.isNotEmpty()) {
            analyzeHbnc(member)
        } else null

        // 5. NCD Tracking
        val ncdStatus = if (member.ncdCondition != null || ageInfo.years >= 30) {
            analyzeNcd(member, refDate)
        } else null

        // 6. Recent Symptoms from Consultations
        val symptomNotes = recentHistory.mapNotNull {
            val q = it.queryText.lowercase()
            when {
                q.contains("fever") || q.contains("bukhar") -> "Fever reported recently"
                q.contains("vomit") || q.contains("ulti") -> "Vomiting reported recently"
                q.contains("cough") || q.contains("khansi") -> "Cough reported recently"
                q.contains("dast") || q.contains("motion") -> "Diarrhoea reported recently"
                q.contains("headache") || q.contains("sar dard") -> "Severe headache reported recently"
                else -> null
            }
        }.distinct()

        return PatientClinicalTimeline(
            member = member,
            refDate = refDate,
            ageInfo = ageInfo,
            ageDisplay = ageInfo.ageDisplay,
            lifeStage = lifeStage,
            gestation = gestation,
            vitals = vitals,
            immunization = immunization,
            hbncStatus = hbncStatus,
            ncdStatus = ncdStatus,
            recentSymptomNotes = symptomNotes
        )
    }

    private fun calculateGestation(
        lmp: String?,
        edd: String?,
        ancVisits: List<String>,
        refDate: String
    ): GestationTimeline {
        var gestationDays = 140 // default 20 weeks if missing
        var daysUntilEdd = 140

        if (!edd.isNullOrBlank()) {
            val daysDiff = diffInDays(refDate, edd)
            daysUntilEdd = daysDiff
            gestationDays = (280 - daysDiff).coerceIn(0, 310)
        } else if (!lmp.isNullOrBlank()) {
            val daysDiff = diffInDays(lmp, refDate)
            gestationDays = daysDiff.coerceIn(0, 310)
            daysUntilEdd = 280 - gestationDays
        }

        val weeks = gestationDays / 7
        val remDays = gestationDays % 7
        val trimester = when {
            weeks <= 12 -> 1
            weeks <= 27 -> 2
            else -> 3
        }

        val nextDueAnc = when {
            weeks < 12 -> "ANC-1 (within 12w: registration, BP, Hb, urine check)"
            weeks < 26 -> "ANC-2 (14-26w: BP, weight, abdominal check, start IFA & Calcium)"
            weeks < 34 -> "ANC-3 (28-34w: BP, repeat Hb, fetal heart & growth check)"
            else -> "ANC-4 (36-40w: delivery plan, 102/108 ambulance coordination)"
        }

        val display = "$weeks weeks${if (remDays > 0) " $remDays days" else ""} (${trimester}nd Trimester)"

        return GestationTimeline(
            lmpDate = lmp,
            edd = edd,
            gestationalDays = gestationDays,
            gestationalWeeks = weeks,
            remainingDaysInWeek = remDays,
            trimester = trimester,
            displayWeeks = display,
            nextDueAnc = nextDueAnc,
            completedAncCount = ancVisits.size,
            daysUntilEdd = daysUntilEdd
        )
    }

    private fun analyzeVitals(
        member: Member,
        ageInfo: PatientAgeInfo,
        lifeStage: LifeStage
    ): PatientVitalsAnalysis {
        val bp = member.lastAncBp ?: member.lastBpReading
        var bpCategory = "Normal"
        var isBpElevated = false

        if (bp != null) {
            val parts = bp.split("/").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size == 2) {
                val sys = parts[0]
                val dia = parts[1]
                when {
                    sys >= 180 || dia >= 110 -> {
                        bpCategory = "Hypertensive Crisis (>=180/110) — Immediate CHC Referral"
                        isBpElevated = true
                    }
                    sys >= 140 || dia >= 90 -> {
                        bpCategory = if (member.isPregnant) "Pre-eclampsia Alert (>=140/90) — Urgent Hospital Referral" else "Stage 2 Hypertension (>=140/90)"
                        isBpElevated = true
                    }
                    sys >= 130 || dia >= 85 -> {
                        bpCategory = if (member.isPregnant) "Borderline Gestational BP (130-139/85-89)" else "Stage 1 Hypertension"
                        isBpElevated = true
                    }
                    else -> bpCategory = "Normal (<130/85)"
                }
            }
        }

        val hb = member.lastHbGdl
        val anaemiaSeverity = when {
            hb == null -> "Not Recorded"
            hb < 7.0 -> "Severe Anaemia (<7.0 g/dL) — Immediate Hospital Referral"
            hb < 10.0 -> "Moderate Anaemia (7.0-9.9 g/dL) — 2 IFA Tablets Daily"
            hb < 11.0 -> "Mild Anaemia (10.0-10.9 g/dL)"
            else -> "Normal (>=11.0 g/dL)"
        }
        val isSevereAnaemia = hb != null && hb < 7.0

        val muac = member.lastMuacCm
        val muacStatus = when {
            member.bilateralPittingOedema -> "SAM (Bilateral Pitting Oedema) — Immediate NRC Referral"
            muac == null -> "Not Recorded"
            muac < 11.5 -> "SAM Red Band (<11.5 cm) — Immediate NRC Referral"
            muac < 12.5 -> "MAM Yellow Band (11.5-12.4 cm) — Supplementary Feeding"
            else -> "Normal Green Band (>=12.5 cm)"
        }

        val fastBreathingCutoff = when {
            lifeStage == LifeStage.NEWBORN -> 60
            lifeStage == LifeStage.INFANT -> 50
            lifeStage == LifeStage.TODDLER || lifeStage == LifeStage.CHILD_UNDER_FIVE -> 40
            else -> 30
        }

        return PatientVitalsAnalysis(
            bpReading = bp,
            bpCategory = bpCategory,
            isBpElevated = isBpElevated,
            hbGdl = hb,
            anaemiaSeverity = anaemiaSeverity,
            isSevereAnaemia = isSevereAnaemia,
            muacCm = muac,
            muacStatus = muacStatus,
            hasOedema = member.bilateralPittingOedema,
            fastBreathingThreshold = fastBreathingCutoff
        )
    }

    private fun analyzeChildImmunization(member: Member, ageInfo: PatientAgeInfo): ChildImmunizationStatus {
        val vacs = member.childVaccines
        val totalMonths = ageInfo.totalMonths

        val nextVac = when {
            totalMonths < 2 -> "6 Weeks: Pentavalent-1, OPV-1, Rotavirus-1, fIPV-1, PCV-1"
            totalMonths < 3 -> "10 Weeks: Pentavalent-2, OPV-2, Rotavirus-2"
            totalMonths < 4 -> "14 Weeks: Pentavalent-3, OPV-3, Rotavirus-3, fIPV-2, PCV-2"
            totalMonths < 12 -> "9 Months: MR-1, PCV-Booster, fIPV-3, Vitamin-A (1st Dose)"
            totalMonths < 24 -> "16-24 Months: DPT-Booster 1, MR-2, OPV-Booster, Vitamin-A (2nd Dose)"
            else -> "5-6 Years: DPT-Booster 2"
        }

        val overdue = mutableListOf<String>()
        if (totalMonths >= 3 && !hasVaccine(vacs, "penta-1", "pentavalent-1")) overdue.add("Pentavalent-1")
        if (totalMonths >= 5 && !hasVaccine(vacs, "penta-3", "pentavalent-3")) overdue.add("Pentavalent-3")
        if (totalMonths >= 12 && !hasVaccine(vacs, "mr-1", "mr1")) overdue.add("MR-1")
        if (totalMonths >= 25 && !hasVaccine(vacs, "dpt-b1", "dptb1")) overdue.add("DPT-Booster 1")

        return ChildImmunizationStatus(
            completedVaccines = vacs,
            nextScheduledVaccine = nextVac,
            isInfantScheduleComplete = totalMonths >= 12 && hasVaccine(vacs, "mr-1", "mr1"),
            overdueVaccines = overdue
        )
    }

    private fun hasVaccine(vacs: Map<String, String>, vararg aliases: String): Boolean {
        val keys = vacs.keys.map { it.lowercase().trim() }
        return aliases.any { alias -> keys.any { it.contains(alias) } }
    }

    private fun analyzeHbnc(member: Member): HbncPostnatalStatus {
        val pod = member.placeOfDelivery ?: "institution"
        val totalReq = if (pod.equals("home", ignoreCase = true)) 7 else 6
        val completed = member.hbncVisitsDone.sorted()
        val nextVisit = (1..totalReq).firstOrNull { it !in completed }

        val bw = member.birthWeightKg
        val isVeryLow = bw != null && bw < 1.8
        val isLow = bw != null && bw < 2.5

        return HbncPostnatalStatus(
            placeOfDelivery = pod,
            birthWeightKg = bw,
            isLowBirthWeight = isLow,
            isVeryLowBirthWeight = isVeryLow,
            completedVisits = completed,
            nextDueVisitNumber = nextVisit,
            totalVisitsRequired = totalReq
        )
    }

    private fun analyzeNcd(member: Member, refDate: String): NcdTrackingStatus {
        val condition = member.ncdCondition ?: "Hypertension Screening"
        val bpDate = member.lastBpCheckDate
        val daysSinceBp = if (!bpDate.isNullOrBlank()) diffInDays(bpDate, refDate) else null
        val isBpOverdue = daysSinceBp != null && daysSinceBp > 30

        val rbsDate = member.lastSugarCheckDate
        val daysSinceRbs = if (!rbsDate.isNullOrBlank()) diffInDays(rbsDate, refDate) else null
        val isRbsOverdue = daysSinceRbs != null && daysSinceRbs > 30

        return NcdTrackingStatus(
            condition = condition,
            lastBpReading = member.lastBpReading,
            lastBpDate = bpDate,
            daysSinceLastBp = daysSinceBp,
            isBpOverdue = isBpOverdue,
            lastRbsMgDl = member.lastRbsMgDl,
            lastRbsDate = rbsDate,
            isRbsOverdue = isRbsOverdue
        )
    }

    private fun diffInDays(fromStr: String, toStr: String): Int {
        return try {
            val d1 = dateFormat.parse(fromStr)
            val d2 = dateFormat.parse(toStr)
            if (d1 != null && d2 != null) {
                ((d2.time - d1.time) / (1000 * 60 * 60 * 24)).toInt()
            } else 0
        } catch (e: Exception) {
            0
        }
    }
}
