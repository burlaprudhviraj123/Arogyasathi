package org.arogyasathi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    var searchQuery by remember { mutableStateOf("") }
    var selectedVillage by remember { mutableStateOf("All") }
    var households by remember { mutableStateOf<List<Household>>(emptyList()) }
    var expandedHouseholdId by remember { mutableStateOf<String?>(null) }
    var membersMap by remember { mutableStateOf<Map<String, List<Member>>>(emptyMap()) }

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
            placeholder = { Text("Search by family head, house #, or ID...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCrisp,
                unfocusedContainerColor = SurfaceCrisp
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Village Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Belwadi Tola", "Amrapur", "Kesarganj").forEach { village ->
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
                                    Text("Loading family members...", fontSize = 13.sp, color = TextSecondary)
                                } else {
                                    members.forEach { m ->
                                        MemberRowItem(
                                            member = m,
                                            onConsult = { onConsultMember(m) }
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
    }
}

@Composable
fun MemberRowItem(
    member: Member,
    onConsult: () -> Unit
) {
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
                Column {
                    Text(
                        text = "${member.name} (${member.relation})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "Sex: ${member.sex} • DOB: ${member.dob}",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                TextButton(
                    onClick = onConsult,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Check", fontSize = 12.sp, color = PrimaryTeal)
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
