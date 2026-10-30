package org.arogyasathi.app

import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.core.Household
import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.PatientConsultation
import org.arogyasathi.app.core.ProtocolSection
import org.arogyasathi.app.core.ReferralSlip
import org.arogyasathi.app.core.SyncStatus
import org.arogyasathi.app.core.VisitOutcome
import org.arogyasathi.app.data.local.AshaEntity
import org.arogyasathi.app.data.local.DangerEscalationEntity
import org.arogyasathi.app.data.local.HouseholdEntity
import org.arogyasathi.app.data.local.MemberEntity
import org.arogyasathi.app.data.local.PatientChatEntity
import org.arogyasathi.app.data.local.ProtocolSectionEntity
import org.arogyasathi.app.data.local.SyncQueueEntity
import org.arogyasathi.app.data.local.VisitOutcomeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying the Android Room Database Schema (`@Entity` <-> Domain mapping)
 * and Neon Postgres schema alignment for offline-first operation.
 */
class RoomNeonSchemaTest {

    @Test
    fun testAshaEntityRoundTrip() {
        val profile = AshaProfile(
            ashaId = "ASHA-RRR5",
            name = "Kalavati Kumari",
            phone = "+91 98765 43220",
            villages = listOf("Belwadi Tola", "Amrapur"),
            subCentre = "Belwadi Tola Ayushman Arogya Mandir (HWC)"
        )
        val entity = AshaEntity.fromDomain(profile)
        assertEquals("Belwadi Tola;Amrapur", entity.villagesCsv)

        val restored = entity.toDomain()
        assertEquals(profile.ashaId, restored.ashaId)
        assertEquals(profile.name, restored.name)
        assertEquals(listOf("Belwadi Tola", "Amrapur"), restored.villages)
        assertEquals("Asha@2026", entity.password)
        assertEquals("Asha@2026", restored.password)
    }

    @Test
    fun testAshaCustomPasswordRoundTrip() {
        val profile = AshaProfile(
            ashaId = "ASHA-RRR5",
            name = "Kalavati Kumari",
            phone = "+91 98765 43220",
            villages = listOf("Belwadi Tola"),
            subCentre = "Belwadi Tola HWC",
            password = "SecretKalavati#2026"
        )
        val entity = AshaEntity.fromDomain(profile)
        assertEquals("SecretKalavati#2026", entity.password)

        val restored = entity.toDomain()
        assertEquals("SecretKalavati#2026", restored.password)
    }

    @Test
    fun testHouseholdAndMemberEntityRoundTrip() {
        val hh = Household(
            householdId = "HH-014Q8O",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            houseNo = "Ward 4, Near Handpump",
            headMemberId = "M-YXYWBY6B",
            headName = "Jamuna Devi",
            membersCount = 5
        )
        val hhEntity = HouseholdEntity.fromDomain(hh)
        assertEquals(hh, hhEntity.toDomain())

        val member = Member(
            memberId = "M-YXYWBY6B",
            householdId = "HH-014Q8O",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Jamuna Devi",
            relation = "wife",
            sex = "F",
            dob = "2002-10-31",
            isPregnant = true,
            lmpDate = "2026-03-25",
            edd = "2026-12-30",
            ancVisitDates = listOf("2026-06-05", "2026-09-22"),
            lastAncBp = "144/92",
            lastHbGdl = 6.8,
            childVaccines = mapOf("BCG" to "2026-01-10", "OPV-0" to "2026-01-10")
        )
        val memberEntity = MemberEntity.fromDomain(member)
        val restored = memberEntity.toDomain()
        assertEquals(member.memberId, restored.memberId)
        assertTrue(restored.isPregnant)
        assertEquals(listOf("2026-06-05", "2026-09-22"), restored.ancVisitDates)
        assertEquals(6.8, restored.lastHbGdl ?: 0.0, 0.001)
        assertEquals("2026-01-10", restored.childVaccines["BCG"])
    }

    @Test
    fun testProtocolVisitOutcomeAndOutboxRoundTrip() {
        val protocol = ProtocolSection(
            sectionId = "CHD-1.2",
            title = "How much ORS to give",
            body = "Up to 2 years: 50 to 100 ml after each loose stool.",
            category = "CHD"
        )
        val protoEntity = ProtocolSectionEntity.fromDomain(protocol)
        assertEquals(protocol, protoEntity.toDomain())

        val outcome = VisitOutcome(
            outcomeId = "OUT-101",
            householdId = "HH-014Q8O",
            memberId = "M-YXYWBY6B",
            memberName = "Jamuna Devi",
            dueItem = "ANC-3",
            status = "COMPLETED",
            notes = "BP checked and IFA given",
            timestamp = 1730280000000L
        )
        assertEquals(outcome, VisitOutcomeEntity.fromDomain(outcome).toDomain())

        val syncEntity = SyncQueueEntity(
            id = 42L,
            entityType = "VISIT_OUTCOME",
            entityId = "OUT-101",
            payloadJson = "{\"outcome_id\":\"OUT-101\"}",
            status = "PENDING",
            retryCount = 0,
            createdAt = 1730280000000L
        )
        val syncItem = syncEntity.toDomain()
        assertEquals(SyncStatus.PENDING, syncItem.status)
        assertEquals("OUT-101", syncItem.entityId)
    }

    @Test
    fun testDangerEscalationAndPatientChatRoundTrip() {
        val slip = ReferralSlip(
            id = "ESC-SLIP-1",
            patientId = "M-YXYWBY6B",
            patientName = "Jamuna Devi",
            ageGender = "24Y / F",
            village = "Belwadi Tola",
            householdId = "HH-014Q8O",
            suspectedCondition = "Severe Pre-Eclampsia",
            dangerSigns = listOf("Severe headache", "Blurred vision"),
            vitalSigns = "BP 160/110",
            protocolCitation = "ESC-2.3"
        )
        val slipRestored = DangerEscalationEntity.fromDomain(slip).toDomain()
        assertEquals(slip.id, slipRestored.id)
        assertEquals(listOf("Severe headache", "Blurred vision"), slipRestored.dangerSigns)

        val chat = PatientConsultation(
            id = "MSG-1",
            patientId = "M-YXYWBY6B",
            patientName = "Jamuna Devi",
            sender = "ASSISTANT",
            queryText = "Severe headache in pregnancy",
            answerText = "Refer immediately to hospital (ESC-2.3).",
            citedSections = listOf("ESC-2.3", "ESC-1.1"),
            isEscalation = true,
            isRefusal = false,
            triageLevel = "EMERGENCY"
        )
        val chatRestored = PatientChatEntity.fromDomain(chat).toDomain()
        assertEquals(listOf("ESC-2.3", "ESC-1.1"), chatRestored.citedSections)
        assertTrue(chatRestored.isEscalation)
    }
}
