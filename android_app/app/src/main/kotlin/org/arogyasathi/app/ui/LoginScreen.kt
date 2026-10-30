package org.arogyasathi.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.R
import org.arogyasathi.app.core.AshaAuthResult
import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.data.ArogyasathiRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    repository: ArogyasathiRepository,
    onLoginSuccess: (AshaProfile) -> Unit
) {
    var currentLanguage by remember { mutableStateOf(repository.getAppLanguage()) }
    val strings = getAppStrings(currentLanguage)
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    var phoneNumber by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPhoneFocused by remember { mutableStateOf(false) }
    var isPinFocused by remember { mutableStateOf(false) }

    var showForgotPinDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val allProfiles = remember { repository.getAllAshaProfiles() }

    fun performLogin(phoneToUse: String, pinToUse: String) {
        errorMessage = null
        val cleanPhone = phoneToUse.replace(Regex("[^0-9]"), "")
        if (cleanPhone.length < 10) {
            errorMessage = strings.loginErrorInvalid
            return
        }
        if (pinToUse.trim().isEmpty()) {
            errorMessage = strings.loginErrorEmptyPassword
            return
        }

        when (val authResult = repository.loginWithPhoneAndPin(cleanPhone, pinToUse, rememberMe)) {
            is AshaAuthResult.Success -> {
                onLoginSuccess(authResult.profile)
            }
            is AshaAuthResult.InvalidPassword -> {
                errorMessage = "${strings.loginErrorInvalidPassword} (${authResult.parentPhc})"
            }
            is AshaAuthResult.UserNotFound -> {
                errorMessage = strings.loginErrorNotFound
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF042F2E),
                        Color(0xFF0D5E56),
                        Color(0xFF0F766E),
                        Color(0xFF14B8A6)
                    )
                )
            )
    ) {
        // Decorative background floating circles for depth
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = (-60).dp, y = (-40).dp)
                .background(Color.White.copy(alpha = 0.04f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = 60.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar: App Identity & Language Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shield),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = strings.appName.uppercase(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "राष्ट्रीय स्वास्थ्य मिशन",
                            fontSize = 9.sp,
                            color = Color(0xFFCCFBF1)
                        )
                    }
                }

                // Language Switcher Chip
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showLanguageDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_language),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = currentLanguage.nativeName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "▼",
                            fontSize = 9.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bold Headline & Description (as seen in mockup top banner)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = strings.loginHeadline,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 30.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.loginSubtitle,
                    fontSize = 12.sp,
                    color = Color(0xFFCCFBF1),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Sleek White Curved Bottom Sheet (Curving up with Rounded Top Corners)
            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .imePadding()
                ) {
                    // Sheet Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = strings.loginTitle,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enter registered mobile & security PIN",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }

                        // Verified Green Shield
                        Surface(
                            shape = CircleShape,
                            color = RoutineGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check_circle),
                                    contentDescription = null,
                                    tint = RoutineGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Field 1: Mobile Number
                    Text(
                        text = strings.loginPhoneLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(
                            width = if (isPhoneFocused) 1.5.dp else 1.dp,
                            color = if (isPhoneFocused) PrimaryTeal else BorderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_call),
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "+91",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(BorderSubtle)
                            )
                            BasicTextField(
                                value = phoneNumber,
                                onValueChange = { input ->
                                    val digitsOnly = input.filter { it.isDigit() }
                                    if (digitsOnly.length <= 10) {
                                        phoneNumber = digitsOnly
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isPhoneFocused = it.isFocused },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Next
                                ),
                                decorationBox = { innerTextField ->
                                    if (phoneNumber.isEmpty()) {
                                        Text(
                                            text = strings.loginPhonePlaceholder,
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Field 2: Password / Security PIN
                    Text(
                        text = strings.loginPasswordLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(
                            width = if (isPinFocused) 1.5.dp else 1.dp,
                            color = if (isPinFocused) PrimaryTeal else BorderSubtle
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_shield),
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(18.dp)
                            )
                            BasicTextField(
                                value = pin,
                                onValueChange = { pin = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { isPinFocused = it.isFocused },
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        focusManager.clearFocus()
                                        performLogin(phoneNumber, pin)
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (pin.isEmpty()) {
                                        Text(
                                            text = strings.loginPasswordPlaceholder,
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                }
                            )
                            Text(
                                text = if (passwordVisible) "HIDE" else "SHOW",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTeal,
                                modifier = Modifier
                                    .clickable { passwordVisible = !passwordVisible }
                                    .padding(4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Remember Me & Forgot Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { rememberMe = !rememberMe }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = rememberMe,
                                onCheckedChange = { rememberMe = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PrimaryTeal,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strings.loginRememberMe,
                                fontSize = 12.sp,
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Text(
                            text = strings.loginForgotPassword,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTeal,
                            modifier = Modifier
                                .clickable { showForgotPinDialog = true }
                                .padding(vertical = 4.dp)
                        )
                    }

                    // Error banner
                    AnimatedVisibility(
                        visible = errorMessage != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        errorMessage?.let { error ->
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = error,
                                    fontSize = 12.sp,
                                    color = EmergencyRed,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Main Login Button (Pill shaped, matching reference design)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            performLogin(phoneNumber, pin)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = strings.loginButton,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Divider with "Or Quick ASHA Access"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFE2E8F0),
                            thickness = 1.dp
                        )
                        Text(
                            text = "  ${strings.loginQuickAccess}  ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        HorizontalDivider(
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFE2E8F0),
                            thickness = 1.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick ASHA Profile Selector Pills
                    Text(
                        text = "Tap any registered ASHA profile below to auto-fill & login instantly:",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allProfiles, key = { it.ashaId }) { profile ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.25f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val clean = profile.phone.replace("+91", "").replace(" ", "").trim()
                                        phoneNumber = clean
                                        pin = profile.password
                                        performLogin(clean, profile.password)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = PrimaryTeal,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = profile.name.firstOrNull()?.uppercase() ?: "A",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = profile.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "${profile.villages.firstOrNull() ?: ""} • ${profile.ashaId}",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // MoHFW Official Trust Footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🇮🇳 100% Offline Operational • National Health Mission",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Forgot PIN Dialog (Official PHC Administration Credential Support)
    if (showForgotPinDialog) {
        val context = LocalContext.current
        val activeProfile = repository.findAshaByPhone(phoneNumber) ?: allProfiles.first()

        AlertDialog(
            onDismissRequest = { showForgotPinDialog = false },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_shield),
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = strings.forgotPinDialogTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = strings.forgotPinDialogDesc,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        lineHeight = 18.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_shield),
                                    contentDescription = null,
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = activeProfile.parentPhc,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryTeal
                                )
                            }

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                            Column {
                                Text(
                                    text = "${strings.moInChargeTitle}:",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = activeProfile.medicalOfficerName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = activeProfile.medicalOfficerPhone,
                                    fontSize = 12.sp,
                                    color = RoutineGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                            Column {
                                Text(
                                    text = "${strings.anmTitle}:",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                                Text(
                                    text = activeProfile.anmName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = activeProfile.anmPhone,
                                    fontSize = 12.sp,
                                    color = RoutineGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Direct Contact Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val clean = activeProfile.medicalOfficerPhone.replace(" ", "")
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_call),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.call,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val clean = activeProfile.anmPhone.replace(" ", "")
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$clean"))
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, PrimaryTeal),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "ANM",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTeal
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showForgotPinDialog = false }) {
                    Text(strings.close, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Language Selector Dialog
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
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = strings.selectLanguageDesc,
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    AppLanguage.entries.forEach { lang ->
                        val isSelected = lang == currentLanguage
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PrimaryTeal.copy(alpha = 0.12f) else Color(0xFFF8FAFC),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) PrimaryTeal else BorderSubtle
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    repository.setAppLanguage(lang)
                                    currentLanguage = lang
                                    showLanguageDialog = false
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = lang.nativeName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) PrimaryTeal else TextPrimary
                                    )
                                    Text(
                                        text = lang.displayName,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check_circle),
                                        contentDescription = null,
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
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.close, color = PrimaryTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
