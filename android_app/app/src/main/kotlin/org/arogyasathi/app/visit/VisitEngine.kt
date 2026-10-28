package org.arogyasathi.app.visit

import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.Priority
import org.arogyasathi.app.core.VisitItem
import org.arogyasathi.app.core.VisitStatus
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object VisitEngine {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 26)

    data class VaccineRule(
        val code: String,
        val dueAgeDays: Long,
        val notAfterDays: Long,
        val prevNeeded: String? = null,
        val minGapDays: Long? = null
    )

    private val VACCINE_RULES = listOf(
        VaccineRule("BCG", 0, 365),
        VaccineRule("HepB-0", 0, 0),
        VaccineRule("OPV-0", 0, 15),
        VaccineRule("OPV-1", 42, 1825),
        VaccineRule("OPV-2", 70, 1825, "OPV-1", 28),
        VaccineRule("OPV-3", 98, 1825, "OPV-2", 28),
        VaccineRule("Penta-1", 42, 365),
        VaccineRule("Penta-2", 70, 365, "Penta-1", 28),
        VaccineRule("Penta-3", 98, 365, "Penta-2", 28),
        VaccineRule("RVV-1", 42, 365),
        VaccineRule("RVV-2", 70, 365, "RVV-1", 28),
        VaccineRule("RVV-3", 98, 365, "RVV-2", 28),
        VaccineRule("fIPV-1", 42, 365),
        VaccineRule("fIPV-2", 98, 365, "fIPV-1", 56),
        VaccineRule("fIPV-3", 276, 729, "fIPV-2", 56),
        VaccineRule("PCV-1", 42, 365),
        VaccineRule("PCV-2", 98, 729, "PCV-1", 60),
        VaccineRule("PCV-B", 276, 729, "PCV-2", 60),
        VaccineRule("MR-1", 276, 1825),
        VaccineRule("MR-2", 490, 1825, "MR-1", 28),
        VaccineRule("OPV-B", 490, 1825, "OPV-3", 180),
        VaccineRule("DPT-B1", 490, 2554, "Penta-3", 180),
        VaccineRule("DPT-B2", 1827, 2554, "DPT-B1", null)
    )

    private val ANC_WINDOWS = mapOf(
        1 to Pair(0L, 84L),
        2 to Pair(98L, 182L),
        3 to Pair(196L, 238L),
        4 to Pair(252L, 280L)
    )

    private val HBNC_SCHEDULE = mapOf(
        "institution" to listOf(3, 7, 14, 21, 28, 42),
        "home" to listOf(1, 3, 7, 14, 21, 28, 42)
    )

    fun generateVisits(members: List<Member>, referenceDate: LocalDate = TODAY): List<VisitItem> {
        val visits = mutableListOf<VisitItem>()

        for (m in members) {
            val birthDate = try { LocalDate.parse(m.dob) } catch (e: Exception) { null } ?: continue
            val ageDays = ChronoUnit.DAYS.between(birthDate, referenceDate)

            // Determine high-risk (VIS-5.1)
            var isHighRisk = false

            if (m.isPregnant) {
                val lastHb = m.lastHbGdl
                val isHighBp = m.lastAncBp?.let { isBpHigh(it) } ?: false
                if ((lastHb != null && lastHb < 7.0) || isHighBp) {
                    isHighRisk = true
                }
            }

            if (ageDays in 0..42) {
                isHighRisk = true
            }

            if (ageDays in 184..1824) {
                val lastMuac = m.lastMuacCm
                if ((lastMuac != null && lastMuac < 11.5) || m.bilateralPittingOedema) {
                    isHighRisk = true
                }
            }

            val ncdCond = m.ncdCondition ?: ""
            if (ncdCond.contains("HTN") || ncdCond.contains("DM")) {
                val isHighBp = m.lastBpReading?.let { isBpHigh(it) } ?: false
                if (isHighBp) {
                    isHighRisk = true
                }
            }

            // 1. Pregnant Women Items (VIS-2)
            if (m.isPregnant) {
                val lmp = m.lmpDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
                if (lmp != null) {
                    val k = m.ancVisitDates.size + 1
                    if (k <= 4) {
                        val window = ANC_WINDOWS[k]!!
                        val openDate = lmp.plusDays(window.first)
                        val closeDate = lmp.plusDays(window.second)

                        val st = evaluateStatus(openDate, closeDate, referenceDate)
                        if (st != null) {
                            val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) {
                                Priority.HIGH
                            } else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) {
                                Priority.MEDIUM
                            } else {
                                Priority.LOW
                            }
                            visits.add(
                                VisitItem(
                                    memberId = m.memberId,
                                    memberName = m.name,
                                    householdId = m.householdId,
                                    village = m.village,
                                    dueItem = "ANC-$k",
                                    priority = prio,
                                    status = st,
                                    dueDate = openDate.toString()
                                )
                            )
                        }
                    }

                    // Td in pregnancy (VIS-2.2)
                    if (m.ancVisitDates.isNotEmpty()) {
                        val firstAncDate = try { LocalDate.parse(m.ancVisitDates[0]) } catch (e: Exception) { null }
                        if (firstAncDate != null) {
                            val tdHistory = parseDateMap(m.tdDoses)
                            if (m.tdBoosterEligible) {
                                if ("Td-BST" !in tdHistory) {
                                    val dueDate = firstAncDate
                                    val overdueDate = dueDate.plusDays(28)
                                    val st = evaluateStatus(dueDate, overdueDate, referenceDate)
                                    if (st != null) {
                                        val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) Priority.HIGH else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) Priority.MEDIUM else Priority.LOW
                                        visits.add(
                                            VisitItem(m.memberId, m.name, m.householdId, m.village, "Td-BST", prio, st, dueDate.toString())
                                        )
                                    }
                                }
                            } else {
                                if ("Td-1" !in tdHistory) {
                                    val dueDate = firstAncDate
                                    val overdueDate = dueDate.plusDays(28)
                                    val st = evaluateStatus(dueDate, overdueDate, referenceDate)
                                    if (st != null) {
                                        val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) Priority.HIGH else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) Priority.MEDIUM else Priority.LOW
                                        visits.add(
                                            VisitItem(m.memberId, m.name, m.householdId, m.village, "Td-1", prio, st, dueDate.toString())
                                        )
                                    }
                                } else if ("Td-2" !in tdHistory) {
                                    val td1Date = tdHistory["Td-1"]
                                    if (td1Date != null) {
                                        val dueDate = td1Date.plusDays(28)
                                        val overdueDate = dueDate.plusDays(28)
                                        val st = evaluateStatus(dueDate, overdueDate, referenceDate)
                                        if (st != null) {
                                            val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) Priority.HIGH else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) Priority.MEDIUM else Priority.LOW
                                            visits.add(
                                                VisitItem(m.memberId, m.name, m.householdId, m.village, "Td-2", prio, st, dueDate.toString())
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // High-Risk Pregnancy Follow-up (VIS-2.3)
                if (isHighRisk) {
                    val lastHb = m.lastHbGdl
                    val isHighBp = m.lastAncBp?.let { isBpHigh(it) } ?: false
                    if ((lastHb != null && lastHb < 7.0) || isHighBp) {
                        visits.add(
                            VisitItem(
                                memberId = m.memberId,
                                memberName = m.name,
                                householdId = m.householdId,
                                village = m.village,
                                dueItem = "HRP-followup",
                                priority = Priority.HIGH,
                                status = VisitStatus.DUE_TODAY,
                                dueDate = referenceDate.toString()
                            )
                        )
                    }
                }
            }

            // 2. Newborn HBNC Visits (age <= 42 days, VIS-4)
            if (ageDays in 0..42) {
                val deliv = m.placeOfDelivery?.trim()?.lowercase() ?: "institution"
                val sched = if (deliv == "home") HBNC_SCHEDULE["home"]!! else HBNC_SCHEDULE["institution"]!!
                val doneDays = m.hbncVisitsDone.toSet()

                val pastVisits = sched.filter { birthDate.plusDays(it - 1L) <= referenceDate }
                val futureVisits = sched.filter { birthDate.plusDays(it - 1L) > referenceDate }

                var hbncItem: VisitItem? = null
                if (pastVisits.isNotEmpty()) {
                    val latestPast = pastVisits.last()
                    if (latestPast !in doneDays) {
                        val vDate = birthDate.plusDays(latestPast - 1L)
                        val st = if (vDate == referenceDate) VisitStatus.DUE_TODAY else VisitStatus.OVERDUE
                        val prio = if (isHighRisk) Priority.HIGH else Priority.MEDIUM
                        hbncItem = VisitItem(m.memberId, m.name, m.householdId, m.village, "HBNC-D$latestPast", prio, st, vDate.toString())
                    }
                }
                if (hbncItem == null && futureVisits.isNotEmpty()) {
                    val nextVisit = futureVisits.first()
                    if (nextVisit !in doneDays) {
                        val vDate = birthDate.plusDays(nextVisit - 1L)
                        if (vDate <= referenceDate.plusDays(6)) {
                            hbncItem = VisitItem(m.memberId, m.name, m.householdId, m.village, "HBNC-D$nextVisit", Priority.LOW, VisitStatus.UPCOMING, vDate.toString())
                        }
                    }
                }
                if (hbncItem != null) {
                    visits.add(hbncItem)
                }
            }

            // 3. Children Vaccines (age <= 2554 days, VIS-3)
            if (ageDays in 0..2554) {
                val vacHist = m.childVaccines
                for (rule in VACCINE_RULES) {
                    val codeNorm = rule.code
                    if (codeNorm in vacHist) continue
                    if (rule.prevNeeded != null && rule.prevNeeded !in vacHist) continue
                    if (ageDays > rule.notAfterDays) continue

                    var earliestDate = birthDate.plusDays(rule.dueAgeDays)
                    if (rule.prevNeeded != null && rule.minGapDays != null) {
                        val prevDateStr = vacHist[rule.prevNeeded]
                        val prevDate = prevDateStr?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
                        if (prevDate != null) {
                            val candidate = prevDate.plusDays(rule.minGapDays)
                            if (candidate > earliestDate) earliestDate = candidate
                        }
                    }

                    if (ChronoUnit.DAYS.between(birthDate, earliestDate) > rule.notAfterDays) continue

                    val notAfterDate = birthDate.plusDays(rule.notAfterDays)
                    val candOverdue = earliestDate.plusDays(28)
                    val overdueDate = if (candOverdue < notAfterDate) candOverdue else notAfterDate

                    val st = evaluateStatus(earliestDate, overdueDate, referenceDate)
                    if (st != null) {
                        val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) {
                            Priority.HIGH
                        } else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) {
                            Priority.MEDIUM
                        } else {
                            Priority.LOW
                        }
                        visits.add(
                            VisitItem(m.memberId, m.name, m.householdId, m.village, rule.code, prio, st, earliestDate.toString())
                        )
                    }
                }
            }

            // 4. MUAC Check (178 to 1824 days, VIS-5.2)
            if (ageDays in 178..1824) {
                val lastMuacDate = m.lastMuacDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
                val dueDate = lastMuacDate?.plusDays(30) ?: birthDate.plusDays(184)
                val overdueDate = dueDate.plusDays(28)
                val st = evaluateStatus(dueDate, overdueDate, referenceDate)
                if (st != null) {
                    val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) {
                        Priority.HIGH
                    } else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) {
                        Priority.MEDIUM
                    } else {
                        Priority.LOW
                    }
                    visits.add(
                        VisitItem(m.memberId, m.name, m.householdId, m.village, "MUAC-check", prio, st, dueDate.toString())
                    )
                }
            }

            // 5. NCD Follow-up (VIS-5.3)
            if (ncdCond.contains("HTN") || ncdCond.contains("DM")) {
                if (ncdCond.contains("HTN")) {
                    val lastBpDate = m.lastBpCheckDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
                    val dueDate = lastBpDate?.plusDays(30) ?: referenceDate.minusDays(30)
                    val overdueDate = dueDate.plusDays(28)
                    val st = evaluateStatus(dueDate, overdueDate, referenceDate)
                    if (st != null) {
                        val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) Priority.HIGH else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) Priority.MEDIUM else Priority.LOW
                        visits.add(
                            VisitItem(m.memberId, m.name, m.householdId, m.village, "BP-followup", prio, st, dueDate.toString())
                        )
                    }
                }
                if (ncdCond.contains("DM")) {
                    val lastSugarDate = m.lastSugarCheckDate?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
                    val dueDate = lastSugarDate?.plusDays(30) ?: referenceDate.minusDays(30)
                    val overdueDate = dueDate.plusDays(28)
                    val st = evaluateStatus(dueDate, overdueDate, referenceDate)
                    if (st != null) {
                        val prio = if (isHighRisk && (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE)) Priority.HIGH else if (st == VisitStatus.DUE_TODAY || st == VisitStatus.OVERDUE) Priority.MEDIUM else Priority.LOW
                        visits.add(
                            VisitItem(m.memberId, m.name, m.householdId, m.village, "Sugar-followup", prio, st, dueDate.toString())
                        )
                    }
                }
            }
        }

        return visits.distinctBy { Pair(it.memberId, it.dueItem) }
            .sortedWith(compareBy({ it.priority.level }, { it.dueDate }))
    }

    private fun evaluateStatus(dueDate: LocalDate, overdueAfter: LocalDate, referenceDate: LocalDate): VisitStatus? {
        if (dueDate > referenceDate.plusDays(6)) return null
        if (dueDate > referenceDate) return VisitStatus.UPCOMING
        if (referenceDate > overdueAfter) return VisitStatus.OVERDUE
        return VisitStatus.DUE_TODAY
    }

    private fun isBpHigh(bpStr: String): Boolean {
        val parts = bpStr.split("/")
        if (parts.size == 2) {
            val sys = parts[0].trim().toIntOrNull() ?: 0
            val dia = parts[1].trim().toIntOrNull() ?: 0
            return sys >= 140 || dia >= 90
        }
        return false
    }

    private fun parseDateMap(list: List<String>): Map<String, LocalDate> {
        val out = mutableMapOf<String, LocalDate>()
        for (item in list) {
            val parts = item.split(":")
            if (parts.size == 2) {
                val dt = try { LocalDate.parse(parts[1].trim()) } catch (e: Exception) { null }
                if (dt != null) out[parts[0].trim()] = dt
            }
        }
        return out
    }
}
