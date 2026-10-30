package org.arogyasathi.app.ui

import android.content.Intent
import android.net.Uri
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
    var assistantPatient by remember { mutableStateOf<org.arogyasathi.app.core.Member?>(null) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var activeProfile by remember { mutableStateOf(repository.getActiveProfile()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Arogyasathi",
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
                            text = "Frontline Clinical Companion",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                actions = {
                    // Global Emergency SOS Action Button
                    IconButton(
                        onClick = { showEmergencyDialog = true },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_emergency),
                                    contentDescription = "Emergency SOS Contacts",
                                    tint = EmergencyRed,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Minimalist Circular Avatar Button (Standard Material 3 UX)
                    IconButton(
                        onClick = { showProfileDialog = true },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = Color(0xFFE6F4F1),
                            border = BorderStroke(1.5.dp, PrimaryTeal.copy(alpha = 0.35f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = activeProfile.name.firstOrNull()?.uppercase() ?: "A",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryTeal
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
                    activeProfile = activeProfile,
                    onNavigateToAssistant = { prompt, memberId ->
                        assistantPatient = memberId?.let { repository.getMemberById(it) }
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
                        assistantPatient = member
                        assistantQuery = prompt
                        selectedTab = 2 // Switch to Assistant tab
                    }
                )
                2 -> AssistantScreen(
                    repository = repository,
                    initialQuery = assistantQuery,
                    initialPatient = assistantPatient,
                    onClearInitialContext = {
                        assistantQuery = null
                        assistantPatient = null
                    }
                )
                3 -> RecordsScreen(
                    repository = repository
                )
            }
        }
    }

    // Global Emergency SOS Contacts Dialog
    if (showEmergencyDialog) {
        fun dialNumber(number: String) {
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:${number.replace(" ", "")}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        EmergencyContactsDialog(
            activeProfile = activeProfile,
            onCallMo = { dialNumber(activeProfile.medicalOfficerPhone) },
            onCallAnm = { dialNumber(activeProfile.anmPhone) },
            onCall108 = { dialNumber("108") },
            onCall102 = { dialNumber("102") },
            onDismiss = { showEmergencyDialog = false }
        )
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
    val context = LocalContext.current

    fun dialContact(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${number.replace(" ", "")}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
                            text = "Assigned Sub-Centre: ${activeProfile.subCentre}",
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

                // Facility Hierarchy & Clinical Supervision Contacts (Full Numbers Visible Here)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_records),
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Facility & Clinical Hierarchy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DarkTeal
                            )
                        }

                        // Facility info
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Parent Facility: ${activeProfile.parentPhc}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Assigned HWC: ${activeProfile.subCentre}",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                        // Medical Officer In-Charge (Doctor)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeProfile.medicalOfficerRole,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkTeal
                                )
                                Text(
                                    text = activeProfile.medicalOfficerName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = activeProfile.medicalOfficerPhone,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryTeal
                                )
                            }
                            Button(
                                onClick = { dialContact(activeProfile.medicalOfficerPhone) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("CALL MO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Designated Sector ANM
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeProfile.anmRole,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = RoutineGreen
                                )
                                Text(
                                    text = activeProfile.anmName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = activeProfile.anmPhone,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RoutineGreen
                                )
                            }
                            Button(
                                onClick = { dialContact(activeProfile.anmPhone) },
                                colors = ButtonDefaults.buttonColors(containerColor = RoutineGreen),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("CALL ANM", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Emergency Ambulances (108 / 102)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emergency Transport",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmergencyRed
                                )
                                Text(
                                    text = "108 (General) • 102 (Maternal JSSK)",
                                    fontSize = 10.sp,
                                    color = TextSecondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { dialContact("108") },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("108", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { dialContact("102") },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("102", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
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
                                        text = "${profile.name} (${profile.ashaId})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else TextPrimary
                                    )
                                    Text(
                                        text = "${profile.villages.firstOrNull()} • ${profile.parentPhc}",
                                        fontSize = 10.sp,
                                        color = if (isSelected) Color(0xFFE6F4F1) else TextSecondary
                                    )
                                    Text(
                                        text = "MO: ${profile.medicalOfficerName}",
                                        fontSize = 9.sp,
                                        color = if (isSelected) Color(0xFFE6F4F1) else PrimaryTeal
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
