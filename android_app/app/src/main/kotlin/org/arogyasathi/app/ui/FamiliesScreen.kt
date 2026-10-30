package org.arogyasathi.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.core.Household
import org.arogyasathi.app.core.Member
import org.arogyasathi.app.data.ArogyasathiRepository

@Composable
fun FamiliesScreen(
    repository: ArogyasathiRepository,
    onConsultMember: (Member) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedVillage by remember { mutableStateOf("All") }
    var households by remember { mutableStateOf<List<Household>>(emptyList()) }
    var expandedHouseholdId by remember { mutableStateOf<String?>(null) }
    var membersMap by remember { mutableStateOf<Map<String, List<Member>>>(emptyMap()) }
    var selectedMemberForVisit by remember { mutableStateOf<Member?>(null) }

    LaunchedEffect(searchQuery, selectedVillage) {
        val list = repository.getHouseholds(searchQuery, selectedVillage)
        households = list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(strings.searchPlaceholder) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCrisp,
                unfocusedContainerColor = SurfaceCrisp
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Village Filter Chips (Scrollable across all villages and beat)
        val villageOptions = remember {
            listOf("All", "My Beat") + repository.getAllVillages()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            villageOptions.forEach { village ->
                FilterChip(
                    selected = selectedVillage == village,
                    onClick = { selectedVillage = village },
                    label = { Text(village, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryTeal,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "${households.size} Households Registered",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(households, key = { it.householdId }) { hh ->
                val isExpanded = expandedHouseholdId == hh.householdId

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isExpanded) {
                                    expandedHouseholdId = null
                                } else {
                                    expandedHouseholdId = hh.householdId
                                    if (!membersMap.containsKey(hh.householdId)) {
                                        val mList = repository.getMembersByHousehold(hh.householdId)
                                        membersMap = membersMap + (hh.householdId to mList)
                                    }
                                }
                            }
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hh.headName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${hh.houseNo} • ${hh.village} • ${hh.householdId}",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE6F4F1)
                            ) {
                                Text(
                                    text = "${hh.membersCount} Members",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = DarkTeal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                HorizontalDivider(color = BorderSubtle)
                                Spacer(modifier = Modifier.height(10.dp))

                                val members = membersMap[hh.householdId] ?: emptyList()
                                if (members.isEmpty()) {
                                    Text(strings.loadingFamilyMembers, fontSize = 13.sp, color = TextSecondary)
                                } else {
                                    members.forEach { m ->
                                        MemberRowItem(
                                            member = m,
                                            onConsult = { onConsultMember(m) },
                                            onRecordVisit = { selectedMemberForVisit = m }
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        selectedMemberForVisit?.let { member ->
            RecordHouseholdVisitDialog(
                member = member,
                onDismiss = { selectedMemberForVisit = null },
                onSave = { dueItem, status, notes ->
                    val ok = repository.recordVisitOutcome(
                        householdId = member.householdId,
                        memberId = member.memberId,
                        memberName = member.name,
                        dueItem = dueItem,
                        status = status,
                        notes = notes
                    )
                    if (ok) {
                        Toast.makeText(context, "Visit recorded for ${member.name} ($dueItem).", Toast.LENGTH_SHORT).show()
                    }
                    selectedMemberForVisit = null
                }
            )
        }
    }
}

@Composable
fun RecordHouseholdVisitDialog(
    member: Member,
    onDismiss: () -> Unit,
    onSave: (dueItem: String, status: String, notes: String) -> Unit
) {
    val strings = LocalAppStrings.current
    val defaultItem = when {
        member.isPregnant -> "ANC Follow-up"
        member.lastMuacCm != null -> "MUAC / Malnutrition"
        member.ncdCondition != null -> "NCD BP/Sugar Follow-up"
        org.arogyasathi.app.laya.PatientAgeCalculator.calculate(member.dob)?.isChildUnderFive == true -> "Immunization Check"
        else -> "Routine Home Visit"
    }
    var selectedItem by remember { mutableStateOf(defaultItem) }
    var selectedStatus by remember { mutableStateOf("COMPLETED") }
    var notesText by remember { mutableStateOf("") }

    val activityOptions = listOf(
        "ANC Follow-up",
        "Immunization Check",
        "HBNC Postnatal Visit",
        "MUAC / Malnutrition",
        "NCD BP/Sugar Follow-up",
        "Routine Home Visit"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.logVisit,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${member.name} • ${member.relation} (${member.village})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryTeal
                )

                Text(strings.selectVisitActivity, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activityOptions.forEach { act ->
                        FilterChip(
                            selected = selectedItem == act,
                            onClick = { selectedItem = act },
                            label = { Text(act, fontSize = 11.sp) }
                        )
                    }
                }

                Text(strings.outcomeStatusLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("COMPLETED", "UNAVAILABLE", "REFUSED").forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.take(4), fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text(strings.clinicalNotesOptional, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedItem, selectedStatus, notesText.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text(strings.saveRecord, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel, color = TextSecondary)
            }
        }
    )
}

@Composable
fun MemberRowItem(
    member: Member,
    onConsult: () -> Unit,
    onRecordVisit: () -> Unit = {}
) {
    val strings = LocalAppStrings.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF9FBFA),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${member.name} (${member.relation})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    val ageDisplay = org.arogyasathi.app.laya.PatientAgeCalculator.calculate(member.dob)?.ageDisplay ?: member.dob
                    Text(
                        text = "Age: $ageDisplay • ${member.sex} (${member.dob})",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onRecordVisit,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(strings.logVisit, fontSize = 12.sp, color = RoutineGreen, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = onConsult,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(strings.checkButton, fontSize = 12.sp, color = PrimaryTeal, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Health Tag Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (member.isPregnant) {
                    TagChip(text = "Pregnant (EDD: ${member.edd ?: "N/A"})", color = Color(0xFFE91E63))
                }
                if (member.lastHbGdl != null && member.lastHbGdl < 7.0) {
                    TagChip(text = "Sev. Anaemia (${member.lastHbGdl}g/dL)", color = EmergencyRed)
                }
                if (member.ncdCondition != null) {
                    TagChip(text = "NCD: ${member.ncdCondition}", color = HighPriorityOrange)
                }
                if (member.lastMuacCm != null && member.lastMuacCm < 11.5) {
                    TagChip(text = "SAM (MUAC ${member.lastMuacCm}cm)", color = EmergencyRed)
                }
                if (member.birthWeightKg != null && member.birthWeightKg < 1.8) {
                    TagChip(text = "LBW (<1.8kg)", color = EmergencyRed)
                }
            }
        }
    }
}

@Composable
fun TagChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun MemberRowPreview() {
    ArogyasathiTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            MemberRowItem(
                member = Member(
                    memberId = "M-TEST01",
                    householdId = "HH-014Q8O",
                    village = "Belwadi Tola",
                    ashaId = "ASHA-RRR5",
                    name = "Jamuna Devi",
                    relation = "wife",
                    sex = "F",
                    dob = "2002-10-31",
                    isPregnant = true,
                    edd = "2026-12-30",
                    lastHbGdl = 6.8
                ),
                onConsult = {}
            )
        }
    }
}
