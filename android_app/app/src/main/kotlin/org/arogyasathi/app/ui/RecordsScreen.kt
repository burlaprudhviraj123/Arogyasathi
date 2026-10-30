package org.arogyasathi.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.R
import org.arogyasathi.app.core.ProtocolSection
import org.arogyasathi.app.core.SyncItem
import org.arogyasathi.app.core.SyncStatus
import org.arogyasathi.app.core.VisitOutcome
import org.arogyasathi.app.data.ArogyasathiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RecordsScreen(repository: ArogyasathiRepository) {
    val strings = LocalAppStrings.current
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0: Visit History, 1: Sync Outbox, 2: Protocol Corpus
    var completedVisits by remember { mutableStateOf<List<VisitOutcome>>(emptyList()) }
    var syncQueue by remember { mutableStateOf<List<SyncItem>>(emptyList()) }
    var pendingCount by remember { mutableIntStateOf(0) }
    var protocols by remember { mutableStateOf<List<ProtocolSection>>(emptyList()) }
    var protocolSearch by remember { mutableStateOf("") }
    var expandedProtocolId by remember { mutableStateOf<String?>(null) }
    var syncBannerMessage by remember { mutableStateOf<String?>(null) }
    var syncSuccess by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    fun refreshData() {
        completedVisits = repository.getCompletedVisits()
        syncQueue = repository.getSyncQueueItems()
        pendingCount = repository.getPendingSyncCount()
        protocols = repository.getProtocols()
    }

    LaunchedEffect(Unit) {
        refreshData()
        repository.roomDb.syncQueueDao().observeSyncQueue().collect {
            completedVisits = repository.getCompletedVisits()
            syncQueue = repository.getSyncQueueItems()
            pendingCount = repository.getPendingSyncCount()
            protocols = repository.getProtocols()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Sub-tabs Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                label = { Text("Visits (${completedVisits.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryTeal,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                label = { Text("Sync Outbox ($pendingCount)", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryTeal,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                label = { Text("Protocols (${protocols.size})", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PrimaryTeal,
                    selectedLabelColor = Color.White
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (selectedSubTab) {
            0 -> VisitHistoryView(visits = completedVisits)
            1 -> SyncOutboxView(
                syncQueue = syncQueue,
                pendingCount = pendingCount,
                syncBannerMessage = syncBannerMessage,
                syncSuccess = syncSuccess,
                isSyncing = isSyncing,
                onDismissBanner = { syncBannerMessage = null },
                onSyncNow = {
                    isSyncing = true
                    coroutineScope.launch {
                        val res = withContext(Dispatchers.IO) {
                            repository.triggerSync()
                        }
                        syncSuccess = res.success
                        syncBannerMessage = res.message
                        isSyncing = false
                        refreshData()
                    }
                },
                onSimulateSync = {
                    val res = repository.triggerSync(forceOfflineMark = true)
                    syncSuccess = res.success
                    syncBannerMessage = res.message
                    refreshData()
                }
            )
            2 -> ProtocolCorpusView(
                protocols = protocols,
                searchQuery = protocolSearch,
                onSearchChange = { protocolSearch = it },
                expandedId = expandedProtocolId,
                onToggleExpand = { id ->
                    expandedProtocolId = if (expandedProtocolId == id) null else id
                }
            )
        }
    }
}

@Composable
fun VisitHistoryView(visits: List<VisitOutcome>) {
    if (visits.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No visit outcomes recorded yet today.",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Text(
                    text = "Completed visits in the Today tab will appear here.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        }
    } else {
        val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(visits, key = { it.outcomeId }) { v ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = v.memberName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (v.status) {
                                    "COMPLETED" -> Color(0xFFE8F5E9)
                                    "RESCHEDULED" -> Color(0xFFFFF8E1)
                                    else -> Color(0xFFFFEBEE)
                                }
                            ) {
                                Text(
                                    text = v.status,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when (v.status) {
                                        "COMPLETED" -> RoutineGreen
                                        "RESCHEDULED" -> HighPriorityOrange
                                        else -> EmergencyRed
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Item: ${v.dueItem} • ${dateFormat.format(Date(v.timestamp))}",
                            fontSize = 13.sp,
                            color = TextSecondary
                        )

                        if (v.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Notes: ${v.notes}",
                                fontSize = 13.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SyncOutboxView(
    syncQueue: List<SyncItem>,
    pendingCount: Int,
    syncBannerMessage: String? = null,
    syncSuccess: Boolean = false,
    isSyncing: Boolean = false,
    onDismissBanner: () -> Unit = {},
    onSyncNow: () -> Unit,
    onSimulateSync: () -> Unit = {}
) {
    val dateFormat = remember { SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (syncBannerMessage != null) {
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (syncSuccess) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                ),
                border = BorderStroke(1.dp, if (syncSuccess) RoutineGreen.copy(alpha = 0.5f) else HighPriorityOrange.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = if (syncSuccess) R.drawable.ic_check_circle else R.drawable.ic_emergency),
                            contentDescription = null,
                            tint = if (syncSuccess) RoutineGreen else HighPriorityOrange,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp)
                        )
                        Text(
                            text = syncBannerMessage,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            lineHeight = 16.sp
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onDismissBanner() }
                            .padding(2.dp)
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$pendingCount Items In Outbox",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (pendingCount > 0) HighPriorityOrange else RoutineGreen
                    )
                    Text(
                        text = if (pendingCount > 0) "Deferred synchronization ready" else "All local records synchronized",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }

                Button(
                    onClick = onSyncNow,
                    enabled = pendingCount > 0 && !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Syncing...", fontSize = 13.sp, color = Color.White)
                    } else {
                        Text("Sync Now", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(syncQueue, key = { it.id }) { item ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "${item.entityType} • ${item.entityId}",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = dateFormat.format(Date(item.createdAt)),
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (item.status == SyncStatus.SYNCED) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
                        ) {
                            Text(
                                text = item.status.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (item.status == SyncStatus.SYNCED) RoutineGreen else HighPriorityOrange,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProtocolCorpusView(
    protocols: List<ProtocolSection>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    expandedId: String?,
    onToggleExpand: (String) -> Unit
) {
    val filtered = remember(searchQuery, protocols) {
        if (searchQuery.isBlank()) protocols
        else protocols.filter {
            it.sectionId.contains(searchQuery, ignoreCase = true) ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.body.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Filter 107 leaf protocols (ESC, ANC, IMM...)...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCrisp,
                unfocusedContainerColor = SurfaceCrisp
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.sectionId }) { sec ->
                val isExpanded = expandedId == sec.sectionId

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleExpand(sec.sectionId) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${sec.sectionId} — ${sec.title}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (sec.sectionId.startsWith("ESC")) EmergencyRed else DarkTeal,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (isExpanded) "▲" else "▼",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        AnimatedVisibility(visible = isExpanded) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                HorizontalDivider(color = BorderSubtle)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = sec.body,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SyncOutboxPreview() {
    ArogyasathiTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SyncOutboxView(
                syncQueue = listOf(
                    SyncItem(
                        id = 1L,
                        entityType = "VISIT_OUTCOME",
                        entityId = "OUT-001",
                        payloadJson = "{\"member_name\":\"Jamuna Devi\",\"due_item\":\"ANC-2\"}",
                        status = SyncStatus.PENDING,
                        retryCount = 0,
                        createdAt = System.currentTimeMillis()
                    ),
                    SyncItem(
                        id = 2L,
                        entityType = "VISIT_OUTCOME",
                        entityId = "OUT-002",
                        payloadJson = "{\"member_name\":\"Manoj Paswan\",\"due_item\":\"BP-followup\"}",
                        status = SyncStatus.SYNCED,
                        retryCount = 0,
                        createdAt = System.currentTimeMillis() - 3600000
                    )
                ),
                pendingCount = 1,
                onSyncNow = {}
            )
        }
    }
}
