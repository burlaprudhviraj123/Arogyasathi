package org.arogyasathi.app

import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.Priority
import org.arogyasathi.app.core.VisitStatus
import org.arogyasathi.app.visit.VisitEngine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VisitEngineTest {

    @Test
    fun testHighRiskPregnancyElevation() {
        val member = Member(
            memberId = "M-TEST01",
            householdId = "HH-TEST01",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Test Mother",
            relation = "wife",
            sex = "F",
            dob = "2000-01-01",
            isPregnant = true,
            lmpDate = "2026-03-25",
            lastHbGdl = 6.5 // Severe Anaemia < 7.0
        )

        val visits = VisitEngine.generateVisits(listOf(member))
        val hrpVisit = visits.find { it.dueItem == "HRP-followup" }
        assertNotNull("Should generate HRP follow-up for severe anaemia", hrpVisit)
        assertEquals(Priority.HIGH, hrpVisit?.priority)
    }

    @Test
    fun testSamChildElevation() {
        val member = Member(
            memberId = "M-TEST02",
            householdId = "HH-TEST02",
            village = "Amrapur",
            ashaId = "ASHA-096P",
            name = "Test Child",
            relation = "son",
            sex = "M",
            dob = "2024-05-01", // 29 months old
            lastMuacCm = 11.2 // SAM < 11.5
        )

        val visits = VisitEngine.generateVisits(listOf(member))
        val muacVisit = visits.find { it.dueItem == "MUAC-check" }
        assertNotNull("Should generate MUAC check for child", muacVisit)
        assertEquals(Priority.HIGH, muacVisit?.priority)
    }

    @Test
    fun testNeonateHbncVisits() {
        // Born 10 days before TODAY (2026-10-16 vs TODAY 2026-10-26)
        val baby = Member(
            memberId = "M-TEST03",
            householdId = "HH-TEST03",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Baby Kumar",
            relation = "son",
            sex = "M",
            dob = "2026-10-16",
            placeOfDelivery = "institution",
            hbncVisitsDone = listOf(3)
        )

        val visits = VisitEngine.generateVisits(listOf(baby))
        val hbncVisit = visits.find { it.dueItem.startsWith("HBNC") }
        assertNotNull("Should generate HBNC visit for neonate", hbncVisit)
        assertEquals("HBNC-D7", hbncVisit?.dueItem)
        assertEquals(Priority.HIGH, hbncVisit?.priority) // Age <= 42 days is High Risk
    }

    @Test
    fun testHypertensiveCrisisElevation() {
        val elder = Member(
            memberId = "M-TEST04",
            householdId = "HH-TEST04",
            village = "Kesarganj",
            ashaId = "ASHA-YFK5",
            name = "Elder Lal",
            relation = "head",
            sex = "M",
            dob = "1965-01-01",
            ncdCondition = "HTN",
            lastBpReading = "186/112",
            lastBpCheckDate = "2026-09-10"
        )

        val visits = VisitEngine.generateVisits(listOf(elder))
        val bpVisit = visits.find { it.dueItem == "BP-followup" }
        assertNotNull("Should generate BP followup", bpVisit)
        assertEquals(Priority.HIGH, bpVisit?.priority)
    }

    @Test
    fun testPrioritySortingOrder() {
        val p1 = Member(
            memberId = "M-P1",
            householdId = "HH-1",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "High Risk",
            relation = "wife",
            sex = "F",
            dob = "1998-01-01",
            isPregnant = true,
            lmpDate = "2026-03-25",
            lastHbGdl = 6.2
        )
        val p2 = Member(
            memberId = "M-P2",
            householdId = "HH-2",
            village = "Belwadi Tola",
            ashaId = "ASHA-RRR5",
            name = "Routine Child",
            relation = "son",
            sex = "M",
            dob = "2024-01-01",
            lastMuacCm = 13.5,
            lastMuacDate = "2026-09-20"
        )

        val visits = VisitEngine.generateVisits(listOf(p2, p1))
        assertTrue("Visits must not be empty", visits.isNotEmpty())
        assertEquals(Priority.HIGH, visits.first().priority)
    }
}
