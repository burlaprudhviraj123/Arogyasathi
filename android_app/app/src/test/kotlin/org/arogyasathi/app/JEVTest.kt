package org.arogyasathi.app

import org.arogyasathi.app.jev.JEVEscalate
import org.arogyasathi.app.jev.JEVScope
import org.arogyasathi.app.jev.JEVVerify
import org.junit.Assert.*
import org.junit.Test

class JEVTest {

    @Test
    fun testUnconsciousPatientEscalation() {
        val query = "ANM didi phone nahi utha rahi, sugar ke marij behosh pada hai"
        val result = JEVEscalate.evaluate(query)
        assertNotNull("Should detect unconscious danger sign", result)
        assertEquals("ESC-6.3", result?.first)
    }

    @Test
    fun testConvulsionsEscalation() {
        val query = "bachche ko bukhar hai aur jhatke aa rahe hai abhi"
        val result = JEVEscalate.evaluate(query)
        assertNotNull("Should detect convulsions", result)
        assertEquals("ESC-5.1", result?.first)
    }

    @Test
    fun testBenignQueryNoFalseEscalation() {
        val query = "10 mahine ke bachche ko halka bukhar hai, khel raha hai"
        val result = JEVEscalate.evaluate(query)
        assertNull("Benign query must not escalate", result)
    }

    @Test
    fun testOutOfScopeRefusal() {
        val query = "paracetamol syrup kitna dena hai 2 saal ke bachche ko?"
        val result = JEVScope.evaluate(query)
        assertNotNull("Unprescribed medicine should be refused", result)
        assertEquals("SCP-1.2", result?.first)
    }

    @Test
    fun testCitationValidationClamping() {
        val valid = setOf("ANC-1.1", "ANC-1.2", "ESC-2.3")
        val sanitized = JEVVerify.validateCitations(listOf("ANC-1.1", "ANC-1.2", "ESC-2.3"), valid)
        assertEquals(2, sanitized.size)
        assertTrue(sanitized.contains("ANC-1.1"))
        assertTrue(sanitized.contains("ANC-1.2"))
    }
}
