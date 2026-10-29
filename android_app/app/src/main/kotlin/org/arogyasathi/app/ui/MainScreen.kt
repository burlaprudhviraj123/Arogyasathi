package org.arogyasathi.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.R
import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.data.ArogyasathiRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { ArogyasathiRepository(context) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var assistantQuery by remember { mutableStateOf<String?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var activeProfile by remember { mutableStateOf(repository.getActiveProfile()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "आरोग्यसाथी",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp,
                                color = PrimaryTeal
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = RoutineGreen.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "OFFLINE READY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoutineGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Arogyasathi Clinical Companion",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    // Top Right Clickable ASHA Profile Chip
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFE6F4F1),
                        border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showProfileDialog = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = PrimaryTeal
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_person),
                                        contentDescription = "ASHA Profile",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = activeProfile.name.split(" ").firstOrNull() ?: activeProfile.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTeal
                                )
                                Text(
                                    text = activeProfile.phone,
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            FloatingGlassBottomBar(
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CanvasBackground)
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> TodayScreen(
                    repository = repository,
                    onNavigateToAssistant = { prompt ->
                        assistantQuery = prompt
                        selectedTab = 2 // Switch to Assistant tab
                    }
                )
                1 -> FamiliesScreen(
                    repository = repository,
                    onConsultMember = { member ->
                        val prompt = when {
                            member.isPregnant -> "High risk pregnancy follow-up for ${member.name} EDD ${member.edd ?: "unknown"}"
                            member.ncdCondition != null -> "NCD management protocol for ${member.ncdCondition}"
                            else -> "Clinical protocol guidance for ${member.name}"
                        }
                        assistantQuery = prompt
                        selectedTab = 2 // Switch to Assistant tab
                    }
                )
                2 -> AssistantScreen(
                    repository = repository,
                    initialQuery = assistantQuery
                )
                3 -> RecordsScreen(
                    repository = repository
                )
            }
        }
    }

    // ASHA Profile Dialog
    if (showProfileDialog) {
        AshaProfileDialog(
            activeProfile = activeProfile,
            allProfiles = repository.getAllAshaProfiles(),
            onSelectProfile = { profile ->
                repository.setActiveWorker(profile.ashaId)
                activeProfile = repository.getActiveProfile()
            },
            onDismiss = { showProfileDialog = false }
        )
    }
}

@Composable
fun AshaProfileDialog(
    activeProfile: AshaProfile,
    allProfiles: List<AshaProfile>,
    onSelectProfile: (AshaProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PrimaryTeal,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "ASHA Worker Profile",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Phone Number + OTP Authentication",
                        fontSize = 11.sp,
                        color = RoutineGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Active Profile Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE6F4F1),
                    border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = activeProfile.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = DarkTeal
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Registered Mobile: ",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = activeProfile.phone,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PrimaryTeal
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Assigned Beat: ${activeProfile.villages.joinToString(", ")}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Sub-Centre / Facility: ${activeProfile.subCentre}",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Worker Identifier: ${activeProfile.ashaId}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }

                // IVR Call Auto-Auth Explanation
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Phone Call (IVR) Auto-Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "When calling from ${activeProfile.phone}, the system verifies your caller ID automatically. If calling from another phone, you are prompted to enter this 10-digit number.",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 15.sp
                        )
                    }
                }

                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                // Beat Switcher (For evaluators and multi-village testing)
                Text(
                    text = "Switch Active ASHA Beat:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allProfiles) { profile ->
                        val isSelected = profile.ashaId == activeProfile.ashaId
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) PrimaryTeal else Color.White,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryTeal else BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectProfile(profile) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = profile.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextPrimary
                                    )
                                    Text(
                                        text = "${profile.phone} • ${profile.villages.firstOrNull()}",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color(0xFFE6F4F1) else TextSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check_circle),
                                        contentDescription = "Selected",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", fontSize = 13.sp)
            }
        }
    )
}
