package org.arogyasathi.app.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import kotlinx.coroutines.launch
import org.arogyasathi.app.R
import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.data.ArogyasathiRepository
import org.arogyasathi.app.voice.VoicePackManager
import org.arogyasathi.app.voice.VoicePackState
import org.arogyasathi.app.voice.CatalogueEntry

@Composable
fun SettingsScreen(
    repository: ArogyasathiRepository,
    activeProfile: AshaProfile,
    currentLanguage: AppLanguage,
    onLanguageChanged: (AppLanguage) -> Unit,
    onSwitchProfile: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    var showLanguageDialog by remember { mutableStateOf(false) }

    val voicePackManager = remember(context) { VoicePackManager(context) }
    val packStates by voicePackManager.packStates.collectAsState()
    val catalogueEntries by voicePackManager.catalogue.collectAsState()
    var targetImportLang by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val lang = targetImportLang
        if (uri != null && lang != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        voicePackManager.importPack(lang, stream)
                            .onSuccess { Toast.makeText(context, "Voice pack imported successfully!", Toast.LENGTH_LONG).show() }
                            .onFailure { Toast.makeText(context, "Import failed: ${it.message}", Toast.LENGTH_LONG).show() }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun dial(phone: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${phone.replace(" ", "")}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Section 1: Dynamic Language Selection
        item(key = "language_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strings.languageSectionTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = strings.languageSectionSubtitle,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = RoutineGreen.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = currentLanguage.shortCode,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = RoutineGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Active Language Display Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PrimaryTeal,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_language),
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Column {
                                    Text(
                                        text = "${currentLanguage.nativeName} (${currentLanguage.displayName})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = currentLanguage.subtitle,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = RoutineGreen
                            ) {
                                Text(
                                    text = strings.profileActiveBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Change Language Button
                    Button(
                        onClick = { showLanguageDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_language),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = strings.changeLanguageButton,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section 1.5: Offline Voice Engine & Speech Packs
        item(key = "voice_engine_section") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryTeal.copy(alpha = 0.12f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_mic),
                                        contentDescription = null,
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = strings.voicePacksTitle,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = strings.voicePacksSubtitle,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = strings.voicePacksDesc,
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )

                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)

                    VoicePackManager.SUPPORTED_LANGUAGES.forEach { (langCode, langName) ->
                        val state = packStates[langCode] ?: VoicePackState.NotInstalled
                        val entry = catalogueEntries.find { it.languageTag == langCode }

                        val packSizeDisplay = when {
                            entry != null -> "${entry.bytes / (1024 * 1024)} MB (Archive)"
                            langCode == "te-IN" -> "135 MB (Model)"
                            langCode == "en-IN" -> "44 MB (Model)"
                            else -> "~135 MB (IndicConformer)"
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = langName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "$langCode • $packSizeDisplay",
                                            fontSize = 11.sp,
                                            color = TextSecondary
                                        )
                                    }

                                    // Status Badge
                                    when (state) {
                                        is VoicePackState.Ready -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = RoutineGreen.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = if (state.isBundled) strings.bundledReadyBadge else "${strings.offlineReady} (v${state.version})",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = RoutineGreen,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        is VoicePackState.Downloading -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = PrimaryTeal.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = strings.downloadingBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PrimaryTeal,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        is VoicePackState.Verifying -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    text = strings.verifyingBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        is VoicePackState.Installing -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFDBEAFE)
                                            ) {
                                                Text(
                                                    text = strings.preparingBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF1D4ED8),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        is VoicePackState.UpdateAvailable -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF3E8FF)
                                            ) {
                                                Text(
                                                    text = strings.updateAvailableBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF7E22CE),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        is VoicePackState.Failed -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFFEE2E2)
                                            ) {
                                                Text(
                                                    text = strings.failedBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmergencyRed,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        VoicePackState.NotInstalled -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFF1F5F9)
                                            ) {
                                                Text(
                                                    text = strings.downloadPackButton,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF64748B),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        is VoicePackState.Queued -> {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color(0xFFFEF3C7)
                                            ) {
                                                Text(
                                                    text = strings.downloadingBadge,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFB45309),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // In-progress details
                                when (state) {
                                    is VoicePackState.Downloading -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            LinearProgressIndicator(
                                                progress = { state.progressPercent },
                                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                                color = PrimaryTeal,
                                                trackColor = Color(0xFFE2E8F0)
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${state.downloadedBytes / (1024 * 1024)} MB / ${state.totalBytes / (1024 * 1024)} MB (${(state.progressPercent * 100).toInt()}%)",
                                                    fontSize = 11.sp,
                                                    color = PrimaryTeal,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Cancel",
                                                    fontSize = 11.sp,
                                                    color = EmergencyRed,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.clickable {
                                                        voicePackManager.cancelOperation(langCode)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    is VoicePackState.Verifying -> {
                                        Text(
                                            text = "Verifying cryptographic checksums & manifest...",
                                            fontSize = 11.sp,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                    is VoicePackState.Installing -> {
                                        Text(
                                            text = "Extracting files and running Sherpa health check...",
                                            fontSize = 11.sp,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }
                                    is VoicePackState.Failed -> {
                                        Text(
                                            text = "Error: ${state.reason}",
                                            fontSize = 11.sp,
                                            color = EmergencyRed
                                        )
                                    }
                                    else -> {}
                                }

                                val isEnglish = langCode == "en-IN" || langCode.startsWith("en")

                                // Action Buttons
                                when (state) {
                                    is VoicePackState.Ready -> {
                                        // Already downloaded models: remove import zip; only delete option, and English never has a delete option
                                        if (!isEnglish) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = {
                                                        voicePackManager.deletePack(langCode)
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(strings.deletePackButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    is VoicePackState.NotInstalled -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (entry != null) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            voicePackManager.downloadAndInstall(langCode, entry)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text("${strings.downloadPackButton} (${entry.bytes / (1024 * 1024)} MB)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    targetImportLang = langCode
                                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = if (entry != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                                            ) {
                                                Text(strings.importZipButton, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    is VoicePackState.Failed -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (entry != null) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            voicePackManager.downloadAndInstall(langCode, entry)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(strings.retryDownloadButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    targetImportLang = langCode
                                                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                modifier = if (entry != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                                            ) {
                                                Text(strings.importZipButton, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                    is VoicePackState.UpdateAvailable -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (entry != null) {
                                                Button(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            voicePackManager.downloadAndInstall(langCode, entry)
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7E22CE)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = if (!isEnglish) Modifier.weight(1f) else Modifier.fillMaxWidth()
                                                ) {
                                                    Text("${strings.updatePackButton} (${state.newPackBytes / (1024 * 1024)} MB)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            if (!isEnglish) {
                                                OutlinedButton(
                                                    onClick = {
                                                        voicePackManager.deletePack(langCode)
                                                    },
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = EmergencyRed),
                                                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = if (entry != null) Modifier.weight(1f) else Modifier.fillMaxWidth()
                                                ) {
                                                    Text(strings.deletePackButton, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        // Downloading, Verifying, Installing, Queued
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Health Worker Profile & Coverage Area
        item(key = "profile_section") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.workerProfileSection,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = activeProfile.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "${activeProfile.ashaId} • ${activeProfile.villages.joinToString()}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "PHC: ${activeProfile.parentPhc}",
                                fontSize = 11.sp,
                                color = PrimaryTeal
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = onSwitchProfile,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PrimaryTeal),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = strings.switchProfile,
                                    fontSize = 11.sp,
                                    color = PrimaryTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(
                                onClick = onLogout,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.5f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = strings.logoutButton,
                                    fontSize = 11.sp,
                                    color = EmergencyRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 3: Clinical Architecture & Safety Kernel
        item(key = "architecture_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.clinicalArchitectureSection,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val features = listOf(
                        "JEV Safety Kernel" to "100% deterministic recall on all life-threatening danger signs (ESC-*).",
                        "Laya Multilingual Retrieval" to "Cross-dialect clinical concept expansion across 6 regional languages.",
                        "Zero-Hallucination Extractive QA" to "Factual protocol extraction strictly capped at <= 400 characters.",
                        "100% Offline Primary Execution" to "Runs entirely on-device with zero cloud telemetry or internet dependency."
                    )

                    features.forEach { (title, desc) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check_circle),
                                contentDescription = null,
                                tint = RoutineGreen,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Column {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = desc,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Emergency Helplines
        item(key = "helplines_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.helplineSection,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { dial("108") },
                            colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.ambulance108, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { dial("102") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(strings.ambulance102, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Section 5: Offline Database & Version Info
        item(key = "database_info_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCrisp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = strings.offlineDatabaseSection,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.sqliteInfoDesc,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.aboutSection,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = strings.version,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(60.dp)) }
    }

    // Dynamic Language Selection Dialog
    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_language),
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = strings.selectLanguageTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = strings.selectLanguageDesc,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    AppLanguage.entries.forEach { lang ->
                        val isSelected = lang == currentLanguage
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFE6F4F1) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) PrimaryTeal else BorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    onLanguageChanged(lang)
                                    showLanguageDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) PrimaryTeal else Color(0xFFCBD5E1),
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = lang.shortCode,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = "${lang.nativeName} (${lang.displayName})",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isSelected) PrimaryTeal else TextPrimary
                                        )
                                        Text(
                                            text = lang.subtitle,
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check_circle),
                                        contentDescription = "Selected",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showLanguageDialog = false }
                ) {
                    Text(
                        text = strings.close,
                        color = PrimaryTeal,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}

