package org.arogyasathi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.R
import org.arogyasathi.app.core.Priority
import org.arogyasathi.app.core.VisitItem
import org.arogyasathi.app.core.VisitStatus
import org.arogyasathi.app.data.ArogyasathiRepository

@Composable
fun TodayScreen(
    repository: ArogyasathiRepository,
    activeProfile: org.arogyasathi.app.core.AshaProfile = repository.getActiveProfile(),
    onNavigateToAssistant: (String, String?) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isOnline by remember {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val active = cm?.activeNetwork
        val caps = if (active != null) cm.getNetworkCapabilities(active) else null
        mutableStateOf(caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }

    DisposableEffect(context) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                isOnline = true
            }
            override fun onLost(network: android.net.Network) {
                isOnline = false
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm?.registerNetworkCallback(request, callback)
        } catch (_: Exception) {}

        onDispose {
            try {
                cm?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {}
        }
    }

    var selectedVillage by remember { mutableStateOf("All") }
    var selectedPriority by remember { mutableStateOf<Priority?>(null) }
    var visitList by remember { mutableStateOf<List<VisitItem>>(emptyList()) }
    var completedCount by remember { mutableIntStateOf(0) }
    var showOutcomeDialog by remember { mutableStateOf<VisitItem?>(null) }

    fun refreshVisits() {
        visitList = repository.getTodayVisits(selectedVillage, selectedPriority)
        completedCount = repository.getCompletedVisits().size
    }

    LaunchedEffect(selectedVillage, selectedPriority, activeProfile.ashaId) {
        refreshVisits()
    }

    val totalPlanned = visitList.size + completedCount
    val urgentCount = visitList.count { it.priority == Priority.HIGH }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp)
    ) {
        // ASHA Header Card (Scrolls with list)
        item(key = "asha_header_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = activeProfile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${activeProfile.ashaId} • Chandrapuri Block",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFF1F5F9),
                        modifier = Modifier.padding(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) RoutineGreen else Color(0xFF64748B))
                            )
                            Text(
                                text = if (isOnline) "Online" else "Offline",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isOnline) RoutineGreen else Color(0xFF475569)
                            )
                        }
                    }
                }
            }
        }

        // 3 Metrics Counter Row (Scrolls with list)
        item(key = "metrics_row") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricCard(
                    label = "Planned",
                    count = totalPlanned,
                    color = PrimaryTeal,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Urgent",
                    count = urgentCount,
                    color = EmergencyRed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Completed",
                    count = completedCount,
                    color = RoutineGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Village Filter Chips (Scrolls with list)
        item(key = "village_filters") {
            val villageOptions = remember(activeProfile) {
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
        }

        // Priority Filter Chips (Scrolls with list)
        item(key = "priority_filters") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = selectedPriority == null,
                    onClick = { selectedPriority = null },
                    label = { Text("All Priorities", fontSize = 12.sp) }
                )
                FilterChip(
                    selected = selectedPriority == Priority.HIGH,
                    onClick = { selectedPriority = Priority.HIGH },
                    label = { Text("Urgent", fontSize = 12.sp, color = if (selectedPriority == Priority.HIGH) Color.White else EmergencyRed) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmergencyRed,
                        selectedLabelColor = Color.White
                    )
                )
                FilterChip(
                    selected = selectedPriority == Priority.MEDIUM,
                    onClick = { selectedPriority = Priority.MEDIUM },
                    label = { Text("Medium", fontSize = 12.sp) }
                )
            }
        }

        // Visit List Cards or Empty State
        if (visitList.isEmpty()) {
            item(key = "empty_visits_state") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = RoutineGreen,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "All prioritized visits complete",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = RoutineGreen
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "New visit schedules will populate automatically for tomorrow.",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        } else {
            items(visitList, key = { "${it.memberId}_${it.dueItem}" }) { item ->
                VisitCard(
                    item = item,
                    onCompleteClick = { showOutcomeDialog = item },
                    onConsultProtocolClick = {
                        val prompt = when {
                            item.dueItem.startsWith("ANC") -> "ANC checkup protocol for ${item.dueItem}"
                            item.dueItem.startsWith("HBNC") -> "HBNC neonatal visit guidelines ${item.dueItem}"
                            item.dueItem == "MUAC-check" -> "MUAC screening SAM MAM referral"
                            item.dueItem == "BP-followup" -> "Hypertension BP screening ASHA referral"
                            item.dueItem == "Sugar-followup" -> "Diabetes blood sugar screening guidelines"
                            item.dueItem == "HRP-followup" -> "High risk pregnancy severe anaemia pre-eclampsia"
                            else -> "Vaccine protocol for ${item.dueItem}"
                        }
                        onNavigateToAssistant(prompt, item.memberId)
                    }
                )
            }
        }
    }

    // Visit Outcome Completion Dialog
    showOutcomeDialog?.let { item ->
        VisitOutcomeDialog(
            item = item,
            onDismiss = { showOutcomeDialog = null },
            onSave = { status, notes ->
                repository.recordVisitOutcome(
                    householdId = item.householdId,
                    memberId = item.memberId,
                    memberName = item.memberName,
                    dueItem = item.dueItem,
                    status = status,
                    notes = notes
                )
                showOutcomeDialog = null
                refreshVisits()
            }
        )
    }
}

@Composable
fun MetricCard(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val bgTint = when (color) {
        EmergencyRed -> Color(0xFFFEF2F2)
        RoutineGreen -> Color(0xFFECFDF5)
        else -> Color(0xFFE6F4F1)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = bgTint,
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f)),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = color
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun VisitCard(
    item: VisitItem,
    onCompleteClick: () -> Unit,
    onConsultProtocolClick: () -> Unit
) {
    val priorityColor = when (item.priority) {
        Priority.HIGH -> EmergencyRed
        Priority.MEDIUM -> HighPriorityOrange
        Priority.LOW -> RoutineGreen
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCrisp,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left Priority Accent Strip
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(priorityColor)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = priorityColor.copy(alpha = 0.12f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = item.memberName.firstOrNull()?.uppercase() ?: "P",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = priorityColor
                                )
                            }
                        }
                        Column {
                            Text(
                                text = item.memberName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${item.householdId} • ${item.village}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    // Priority Pill
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = priorityColor.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, priorityColor.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = when (item.priority) {
                                Priority.HIGH -> "URGENT"
                                Priority.MEDIUM -> "MEDIUM"
                                Priority.LOW -> "ROUTINE"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Due clinical task tag
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE6F4F1),
                        border = BorderStroke(0.5.dp, PrimaryTeal.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "Due: ${item.dueItem}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTeal,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (item.status == VisitStatus.OVERDUE) Color(0xFFFEF2F2) else Color(0xFFFFFBEB)
                    ) {
                        Text(
                            text = if (item.status == VisitStatus.OVERDUE) "Overdue" else "Due Today",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (item.status == VisitStatus.OVERDUE) EmergencyRed else HighPriorityOrange,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onConsultProtocolClick,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = PrimaryTeal
                        ),
                        border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .semantics { contentDescription = "Consult protocol for ${item.dueItem}" }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_assistant),
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("Protocols", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onCompleteClick,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .semantics { contentDescription = "Record completion for ${item.memberName}'s ${item.dueItem} visit" }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("Complete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VisitOutcomeDialog(
    item: VisitItem,
    onDismiss: () -> Unit,
    onSave: (status: String, notes: String) -> Unit
) {
    var selectedStatus by remember { mutableStateOf("COMPLETED") }
    var notesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Record Visit Outcome",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${item.memberName} — ${item.dueItem}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = PrimaryTeal
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Outcome Status:", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("COMPLETED", "RESCHEDULED", "REFUSED").forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.lowercase().replaceFirstChar { it.uppercase() }, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryTeal,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Field Notes / Observations") },
                    placeholder = { Text("e.g. BP 120/80 checked, IFA distributed, no danger signs.") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedStatus, notesText) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Save to Outbox", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun VisitCardPreview() {
    ArogyasathiTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            VisitCard(
                item = VisitItem(
                    memberId = "M-TEST01",
                    memberName = "Jamuna Devi",
                    householdId = "HH-014Q8O",
                    village = "Belwadi Tola",
                    dueItem = "ANC-2",
                    priority = Priority.HIGH,
                    status = VisitStatus.DUE_TODAY,
                    dueDate = "2026-10-26"
                ),
                onCompleteClick = {},
                onConsultProtocolClick = {}
            )
        }
    }
}
