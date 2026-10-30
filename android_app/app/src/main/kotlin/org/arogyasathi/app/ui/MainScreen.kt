package org.arogyasathi.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.arogyasathi.app.R
import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.core.Priority
import org.arogyasathi.app.data.ArogyasathiRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val repository = remember { ArogyasathiRepository(context) }
    var currentLanguage by remember { mutableStateOf(repository.getAppLanguage()) }
    val strings = getAppStrings(currentLanguage)

    var selectedTab by remember { mutableIntStateOf(0) }
    var assistantQuery by remember { mutableStateOf<String?>(null) }
    var assistantPatient by remember { mutableStateOf<org.arogyasathi.app.core.Member?>(null) }
    var showProfileScreen by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var activeProfile by remember { mutableStateOf(repository.getActiveProfile()) }
    var isLoggedIn by remember { mutableStateOf(repository.isLoggedIn()) }
    var fontScale by remember { mutableFloatStateOf(repository.getFontSizeScale()) }
    var showMetricsScreen by remember { mutableStateOf(false) }

    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity, fontScale) {
        Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScale
        )
    }

    CompositionLocalProvider(
        LocalCurrentLanguage provides currentLanguage,
        LocalAppStrings provides strings,
        LocalDensity provides customDensity
    ) {
        if (!isLoggedIn) {
            LoginScreen(
                repository = repository,
                onLoginSuccess = { profile ->
                    activeProfile = profile
                    isLoggedIn = true
                }
            )
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets.statusBars,
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = strings.appName,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = PrimaryTeal
                                )
                                Text(
                                    text = strings.appSubtitle,
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                        },
                        actions = {
                            // System Metrics Button (opens live benchmark screen)
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { showMetricsScreen = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_analytics),
                                        contentDescription = "System Metrics",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Metrics",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryTeal
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            // Global Emergency SOS Action Button (Shows "SOS", no "108")
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.45f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable { showEmergencyDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = EmergencyRed,
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_emergency),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "SOS",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmergencyRed
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Minimalist Circular Avatar Button (Standard Material 3 UX - Opens Full-Screen Slide Profile)
                            IconButton(
                                onClick = { showProfileScreen = true },
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
                        onTabSelected = { selectedTab = it },
                        todayBadgeCount = remember(selectedTab) {
                            repository.getTodayVisits().count { it.priority == Priority.HIGH }
                        },
                        modifier = Modifier.navigationBarsPadding()
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(CanvasBackground)
                        .padding(paddingValues)
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val direction = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(
                                initialOffsetX = { fullWidth -> direction * (fullWidth / 6) },
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(
                                animationSpec = tween(220, easing = FastOutSlowInEasing)
                            )).togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> -direction * (fullWidth / 8) },
                                    animationSpec = tween(180, easing = FastOutSlowInEasing)
                                ) + fadeOut(
                                    animationSpec = tween(150, easing = FastOutSlowInEasing)
                                )
                            )
                        },
                        label = "tabContentMotion",
                        modifier = Modifier.fillMaxSize()
                    ) { tabIndex ->
                        when (tabIndex) {
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
                                bottomBarPadding = paddingValues.calculateBottomPadding(),
                                onClearInitialContext = {
                                    assistantQuery = null
                                    assistantPatient = null
                                }
                            )
                            3 -> RecordsScreen(
                                repository = repository
                            )
                            4 -> SettingsScreen(
                                repository = repository,
                                activeProfile = activeProfile,
                                currentLanguage = currentLanguage,
                                onLanguageChanged = { newLang ->
                                    repository.setAppLanguage(newLang)
                                    currentLanguage = newLang
                                },
                                onSwitchProfile = { showProfileScreen = true },
                                onLogout = {
                                    repository.logout()
                                    isLoggedIn = false
                                },
                                fontScale = fontScale,
                                onFontScaleChanged = { newScale ->
                                    fontScale = newScale
                                }
                            )
                        }
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

            // Full-Screen Profile Animated from the Slide
            AnimatedVisibility(
                visible = showProfileScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 250)),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 200)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            ) {
                AshaProfileScreen(
                    activeProfile = activeProfile,
                    allProfiles = repository.getAllAshaProfiles(),
                    onSelectProfile = { profile ->
                        repository.setActiveWorker(profile.ashaId)
                        activeProfile = repository.getActiveProfile()
                    },
                    onDismiss = { showProfileScreen = false },
                    onLogout = {
                        repository.logout()
                        showProfileScreen = false
                        isLoggedIn = false
                    }
                )
            }

            // System Metrics screen (slide in from right)
            AnimatedVisibility(
                visible = showMetricsScreen,
                enter = slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 250)),
                exit = slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 200)),
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
            ) {
                SystemMetricsScreen(
                    repository = repository,
                    onDismiss = { showMetricsScreen = false }
                )
            }
        }
        }
    }
}

@Deprecated("Replaced with AshaProfileScreen for full-screen slide animation presentation")
@Composable
fun AshaProfileDialog(
    activeProfile: AshaProfile,
    allProfiles: List<AshaProfile>,
    onSelectProfile: (AshaProfile) -> Unit,
    onDismiss: () -> Unit
) {
    AshaProfileScreen(
        activeProfile = activeProfile,
        allProfiles = allProfiles,
        onSelectProfile = onSelectProfile,
        onDismiss = onDismiss
    )
}

