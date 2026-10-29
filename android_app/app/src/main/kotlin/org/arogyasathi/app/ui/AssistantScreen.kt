package org.arogyasathi.app.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.arogyasathi.app.R
import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.ReferralSlip
import org.arogyasathi.app.data.ArogyasathiRepository
import org.arogyasathi.app.data.AssistantResult
import java.util.Locale
import java.util.UUID

enum class MessageSender {
    USER,
    ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val textHindi: String = "",
    val result: AssistantResult? = null,
    val patientName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class VoiceLanguage(
    val code: String,
    val nativeLabel: String,
    val shortCode: String,
    val prompt: String
) {
    ENGLISH("en-IN", "English", "EN", "Speak clinical query (English)"),
    HINDI("hi-IN", "हिन्दी", "HI", "बोलें — नैदानिक प्रश्न पूछें (Hindi)"),
    TELUGU("te-IN", "తెలుగు", "TE", "మాట్లాడండి — క్లినికల్ ప్రశ్న (Telugu)"),
    TAMIL("ta-IN", "தமிழ்", "TA", "பேசுங்கள் — மருத்துவ கேள்வி (Tamil)"),
    KANNADA("kn-IN", "ಕನ್ನಡ", "KN", "ಮಾತನಾಡಿ — ಕ್ಲಿನಿಕಲ್ ಪ್ರಶ್ನೆ (Kannada)"),
    MALAYALAM("ml-IN", "മലയാളം", "ML", "സംസാരിക്കൂ — ക്ലിനിക്കൽ ചോദ്യം (Malayalam)")
}

@Composable
fun AssistantScreen(
    repository: ArogyasathiRepository,
    initialQuery: String? = null,
    initialPatient: Member? = null,
    onClearInitialContext: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activePatient by remember { mutableStateOf<Member?>(initialPatient) }
    var selectedLanguage by remember { mutableStateOf(VoiceLanguage.ENGLISH) }
    val isHindiMode = selectedLanguage == VoiceLanguage.HINDI
    var showPatientPicker by remember { mutableStateOf(false) }
    var activeReferralSlip by remember { mutableStateOf<ReferralSlip?>(null) }
    var showOfflineVoiceGuide by remember { mutableStateOf(false) }

    var queryText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }

    val messages = remember { mutableStateListOf<ChatMessage>() }

    // TextToSpeech setup for Hindi/English voice guidance
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var speech: TextToSpeech? = null
        speech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                speech?.language = Locale.forLanguageTag("hi-IN")
            }
        }
        tts = speech
        onDispose {
            speech.stop()
            speech.shutdown()
        }
    }

    // Function to load conversation history from SQLite for active patient or general
    fun reloadMessages(patient: Member?) {
        messages.clear()
        val history = repository.getPatientChatHistory(patient?.memberId)
        if (history.isNotEmpty()) {
            history.forEach { item ->
                messages.add(
                    ChatMessage(
                        id = item.id,
                        sender = if (item.sender == "USER") MessageSender.USER else MessageSender.ASSISTANT,
                        text = if (item.sender == "USER") item.queryText else item.answerText,
                        textHindi = item.answerHindi,
                        result = if (item.sender == "ASSISTANT") {
                            AssistantResult(
                                answer = item.answerText,
                                citedSection = item.citedSections.firstOrNull() ?: "SCP-1.1",
                                isEscalation = item.isEscalation,
                                isRefusal = item.isRefusal,
                                triageLevel = item.triageLevel,
                                answerHindi = item.answerHindi,
                                patientContext = item.patientName?.let { "Patient: $it" }
                            )
                        } else null,
                        patientName = item.patientName,
                        timestamp = item.timestamp
                    )
                )
            }
        } else {
            val welcomeText = if (patient != null) {
                "Namaste Didi! Clinical consultation opened for ${patient.name} (${patient.village}).\n\n" +
                when {
                    patient.isPregnant -> "Gestation Status: Pregnant (EDD: ${patient.edd ?: "N/A"}). Last BP: ${patient.lastAncBp ?: "Normal"}, Hb: ${patient.lastHbGdl ?: "Normal"}.\nAsk any ANC guidance, medicine questions, or danger signs."
                    patient.ncdCondition != null -> "NCD Profile: ${patient.ncdCondition}. Last BP: ${patient.lastBpReading ?: "N/A"}.\nAsk any NCD follow-up or lifestyle advice."
                    else -> "Family Profile: ${patient.relation} in ${patient.village}.\nAsk protocol guidance or immunization schedules."
                }
            } else {
                "Namaste Didi! I am Arogyasathi (आरोग्यसाथी), your offline clinical protocol companion.\n\nAsk any maternal, newborn, child health, or NCD question in Hindi, English, or Hinglish — or select a patient to view history."
            }

            val welcomeHindi = if (patient != null) {
                "नमस्ते दीदी! ${patient.name} (${patient.village}) के लिए क्लिनिकल परामर्श खुला है। कृपया कोई भी प्रोटोकॉल प्रश्न पूछें।"
            } else {
                "नमस्ते दीदी! मैं आरोग्यसाथी हूँ। मातृ, नवजात, बाल स्वास्थ्य या गैर-संचारी रोग (NCD) पर कोई भी सवाल पूछें।"
            }

            messages.add(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = welcomeText,
                    textHindi = welcomeHindi,
                    patientName = patient?.name
                )
            )
        }
    }

    fun submitQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return

        // Resolve patient: if query explicitly mentions a registered patient, switch context to that patient
        val mentionedPatient = repository.resolveMemberFromQuery(trimmed)
        val p = if (mentionedPatient != null) {
            if (activePatient != mentionedPatient) {
                activePatient = mentionedPatient
            }
            mentionedPatient
        } else {
            activePatient
        }

        // 1. Add user message
        messages.add(
            ChatMessage(
                sender = MessageSender.USER,
                text = trimmed,
                patientName = p?.name
            )
        )
        queryText = ""

        // 2. Query clinical engine (JEV deterministic gate + Laya retrieval + patient context)
        val assistantResult = repository.askAssistant(trimmed, p)

        // 3. Save into SQLite patient history
        repository.savePatientChatMessage(
            patientId = p?.memberId,
            patientName = p?.name,
            sender = "USER",
            queryText = trimmed,
            answerText = "",
            answerHindi = "",
            citedSections = emptyList(),
            isEscalation = false,
            isRefusal = false,
            triageLevel = "QUERY"
        )

        repository.savePatientChatMessage(
            patientId = p?.memberId,
            patientName = p?.name,
            sender = "ASSISTANT",
            queryText = trimmed,
            answerText = assistantResult.answer,
            answerHindi = assistantResult.answerHindi,
            citedSections = listOf(assistantResult.citedSection),
            isEscalation = assistantResult.isEscalation,
            isRefusal = assistantResult.isRefusal,
            triageLevel = assistantResult.triageLevel
        )

        // 4. Add bot reply
        messages.add(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = assistantResult.answer,
                textHindi = assistantResult.answerHindi,
                result = assistantResult,
                patientName = p?.name
            )
        )

        // Stop previous speech if running
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        }

        // Scroll to newest message
        coroutineScope.launch {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.size - 1)
            }
        }
    }

    // React to initial patient and query changes from navigation
    LaunchedEffect(initialPatient, initialQuery) {
        if (initialPatient != null && initialPatient != activePatient) {
            activePatient = initialPatient
            reloadMessages(initialPatient)
        }
        if (!initialQuery.isNullOrBlank()) {
            submitQuery(initialQuery)
            onClearInitialContext()
        }
    }

    // Initial load
    LaunchedEffect(activePatient) {
        reloadMessages(activePatient)
    }

    // Speech-to-Text Voice Input Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                submitQuery(spoken)
            }
        } else if (res.resultCode == Activity.RESULT_CANCELED && selectedLanguage != VoiceLanguage.ENGLISH) {
            // When cancelled or offline speech model is not installed for this language, open guidance
            showOfflineVoiceGuide = true
        }
    }

    // Dynamic suggested topics based on active patient context
    val quickTopics = when {
        activePatient?.isPregnant == true -> listOf(
            "Bleeding in pregnancy danger signs",
            "Pre-eclampsia high BP and severe headache",
            "Daily IFA and Calcium tablet schedule",
            "3rd ANC checkup window weeks"
        )
        activePatient?.ncdCondition != null -> listOf(
            "BP 140/90 hypertension management",
            "Random blood sugar >=200 guidelines",
            "Salt reduction and exercise counselling"
        )
        activePatient != null && org.arogyasathi.app.laya.PatientAgeCalculator.calculate(activePatient!!.dob)?.isChildUnderFive == true -> listOf(
            "Motions avthunnai (ORS and Zinc dose)",
            "Fast breathing and chest indrawing",
            "Next due vaccine schedule",
            "MUAC malnutrition screening"
        )
        activePatient != null && org.arogyasathi.app.laya.PatientAgeCalculator.calculate(activePatient!!.dob)?.isNewbornUnderTwoMonths == true -> listOf(
            "Baby unable to suck or feed",
            "Baby cold to touch hypothermia",
            "Cord care and delayed bathing",
            "HBNC home visit schedule"
        )
        else -> listOf(
            "Diarrhoea ORS and Zinc dose",
            "6 weeks vaccination tika schedule",
            "3rd ANC checkup kab hota hai",
            "Sugar patient behosh pada hai",
            "Bachche ko bukhar aur jhatke aa rahe hain",
            "Delivery ke baad heavy bleeding ho rahi hai"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // Top 108 Emergency Banner
        Surface(
            color = EmergencyRed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_emergency),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = "EMERGENCY HOTLINE 108 / 102",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            text = "One-tap ambulance & CHC referral dispatch",
                            fontSize = 10.sp,
                            color = Color(0xFFFFCDD2)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { dialEmergency(context, "108") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "CALL 108",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = EmergencyRed
                        )
                    }
                }
            }
        }

        // Active Patient Context Bar / Selector
        Surface(
            color = Color.White,
            border = BorderStroke(0.5.dp, BorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (activePatient != null) {
                    val p = activePatient!!
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryTeal,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_person),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = p.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = DarkTeal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = when {
                                p.isPregnant -> "Pregnant (EDD ${p.edd ?: "TBD"}) • ${p.village}"
                                p.ncdCondition != null -> "${p.ncdCondition} • ${p.village}"
                                else -> "${p.relation} • ${p.village}"
                            }
                            Text(
                                text = meta,
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showPatientPicker = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Switch", fontSize = 11.sp, color = PrimaryTeal, fontWeight = FontWeight.SemiBold)
                        }
                        IconButton(
                            onClick = {
                                activePatient = null
                                reloadMessages(null)
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Clear patient",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                } else {
                    // No patient selected - prompt to select
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPatientPicker = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_person),
                                contentDescription = null,
                                tint = PrimaryTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "General Mode — Tap to link patient & view history",
                                fontSize = 12.sp,
                                color = DarkTeal,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFE6F4F1),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Select Patient",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTeal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Multilingual Voice & Clinical Language Selector Strip
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(VoiceLanguage.values()) { lang ->
                val isSelected = selectedLanguage == lang
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) PrimaryTeal else Color(0xFFE6F4F1),
                    border = BorderStroke(1.dp, if (isSelected) PrimaryTeal else Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedLanguage = lang }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = lang.nativeLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else PrimaryTeal
                        )
                        Text(
                            text = lang.shortCode,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else DarkTeal
                        )
                    }
                }
            }

            // Offline Voice Pack Guide button
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF3C7),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showOfflineVoiceGuide = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_info),
                            contentDescription = "Offline Voice Setup Guide",
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Offline Voice Setup",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }
        }

        // Quick Question Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickTopics) { topic ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                    onClick = { submitQuery(topic) }
                ) {
                    Text(
                        text = topic,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // Chat Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    isHindiMode = isHindiMode,
                    activePatient = activePatient,
                    onPlayTts = { textToSpeak ->
                        if (isSpeaking) {
                            tts?.stop()
                            isSpeaking = false
                        } else {
                            val lang = when (selectedLanguage) {
                                VoiceLanguage.HINDI -> Locale.forLanguageTag("hi-IN")
                                VoiceLanguage.TELUGU -> Locale.forLanguageTag("te-IN")
                                VoiceLanguage.TAMIL -> Locale.forLanguageTag("ta-IN")
                                VoiceLanguage.KANNADA -> Locale.forLanguageTag("kn-IN")
                                VoiceLanguage.MALAYALAM -> Locale.forLanguageTag("ml-IN")
                                VoiceLanguage.ENGLISH -> Locale.forLanguageTag("en-IN")
                            }
                            tts?.language = lang
                            tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "arogyasathi_tts")
                            isSpeaking = true
                        }
                    },
                    onCall108 = { dialEmergency(context, "108") },
                    onOpenReferralSlip = { slip ->
                        activeReferralSlip = slip
                    }
                )
            }
        }

        // Bottom Chat Input Bar (Sleek Floating Pill - Zero Awkward Padding)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.35f)),
            shadowElevation = 4.dp,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Speech-to-Text Button
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLanguage.code)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, selectedLanguage.prompt)
                                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice input not supported on this device. Please type your query.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0xFFE6F4F1), CircleShape)
                        .semantics { contentDescription = "Voice input in Hindi or English" }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Voice input",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Text Input Field
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = {
                        Text(
                            text = if (activePatient != null) "Ask question for ${activePatient!!.name}..." else "Ask clinical protocol question...",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CanvasBackground,
                        unfocusedContainerColor = CanvasBackground,
                        focusedBorderColor = PrimaryTeal,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    maxLines = 2
                )

                // Send Button
                IconButton(
                    onClick = { submitQuery(queryText) },
                    enabled = queryText.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (queryText.trim().isNotEmpty()) PrimaryTeal else Color(0xFFCBD5E1),
                            CircleShape
                        )
                        .semantics { contentDescription = "Send message" }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send),
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // Patient Picker Dialog
    if (showPatientPicker) {
        PatientPickerDialog(
            repository = repository,
            onSelectPatient = { selected ->
                activePatient = selected
                showPatientPicker = false
                reloadMessages(selected)
            },
            onDismiss = { showPatientPicker = false }
        )
    }

    // Structured Referral Slip Dialog
    activeReferralSlip?.let { slip ->
        ReferralSlipDialog(
            slip = slip,
            onDismiss = { activeReferralSlip = null },
            onCall108 = { dialEmergency(context, "108") },
            onSaveToOutbox = {
                val result = repository.saveReferralSlip(slip)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                activeReferralSlip = null
            }
        )
    }

    // Offline Voice Setup Dialog
    if (showOfflineVoiceGuide) {
        AlertDialog(
            onDismissRequest = { showOfflineVoiceGuide = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info),
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Offline Voice Setup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Android includes English offline voice by default. For Telugu, Hindi, Tamil, Kannada, or Malayalam to work 100% offline with zero network:",
                        fontSize = 13.sp,
                        color = TextPrimary,
                        lineHeight = 18.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "1. Connect device to Wi-Fi / mobile data once",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "2. Open phone Settings > Google > Voice",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "3. Tap 'Offline speech recognition' > ALL tab",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = "4. Download your language pack (~25 MB)",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }

                    Text(
                        text = "Once downloaded, voice input operates completely offline anywhere in the field. You can also type queries directly in Telugu/Indic scripts or tap suggestion chips anytime.",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            val settingsIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)
                            context.startActivity(settingsIntent)
                        } catch (e: Exception) {
                            try {
                                val localeIntent = Intent(Settings.ACTION_LOCALE_SETTINGS)
                                context.startActivity(localeIntent)
                            } catch (e2: Exception) {
                                Toast.makeText(context, "Please open phone Settings > Voice to configure offline speech.", Toast.LENGTH_LONG).show()
                            }
                        }
                        showOfflineVoiceGuide = false
                    }
                ) {
                    Text("Open Voice Settings", fontWeight = FontWeight.Bold, color = PrimaryTeal)
                }
            },
            dismissButton = {
                TextButton(onClick = { showOfflineVoiceGuide = false }) {
                    Text("Dismiss", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    isHindiMode: Boolean,
    activePatient: Member?,
    onPlayTts: (String) -> Unit,
    onCall108: () -> Unit,
    onOpenReferralSlip: (ReferralSlip) -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val res = message.result

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape),
                color = PrimaryTeal
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_assistant),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = PrimaryTeal,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            } else {
                val isEscalation = res?.isEscalation == true
                val isRefusal = res?.isRefusal == true

                val bubbleBg = when {
                    isEscalation -> Color(0xFFFEF2F2)
                    isRefusal -> Color(0xFFFFFBEB)
                    else -> Color.White
                }
                val bubbleBorder = when {
                    isEscalation -> EmergencyRed.copy(alpha = 0.5f)
                    isRefusal -> HighPriorityOrange.copy(alpha = 0.5f)
                    else -> BorderSubtle
                }

                Card(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = bubbleBg),
                    border = BorderStroke(1.dp, bubbleBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Badge Strip
                        if (isEscalation) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmergencyRed,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_emergency),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "EMERGENCY DANGER SIGN — REFER NOW",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else if (isRefusal) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = HighPriorityOrange,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_shield),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "OUT OF SCOPE — MO REFERRAL",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Clinical Text (Formatted with Patient Badge, Step Cards, & Danger Alerts)
                        val displayText = if (isHindiMode && message.textHindi.isNotBlank()) {
                            message.textHindi
                        } else {
                            message.text
                        }

                        FormattedClinicalGuidance(
                            text = displayText,
                            isEscalation = isEscalation,
                            isRefusal = isRefusal
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Footer with Citation & Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (res != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE6F4F1),
                                    border = BorderStroke(0.5.dp, PrimaryTeal.copy(alpha = 0.3f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_records),
                                            contentDescription = null,
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Protocol: ${res.citedSection}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkTeal
                                        )
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Listen via TTS
                                IconButton(
                                    onClick = { onPlayTts(displayText) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_speaker),
                                        contentDescription = "Read aloud",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Structured Referral Slip Button for emergencies
                                if (isEscalation) {
                                    Button(
                                        onClick = {
                                            val slip = ReferralSlip(
                                                patientId = activePatient?.memberId ?: "M-EMERGENCY",
                                                patientName = activePatient?.name ?: message.patientName ?: "Emergency Patient",
                                                ageGender = if (activePatient != null) {
                                                    val age = org.arogyasathi.app.laya.PatientAgeCalculator.calculate(activePatient.dob)?.ageDisplay ?: activePatient.dob
                                                    "$age / ${if (activePatient.sex == "M") "Male" else "Female"}"
                                                } else "Adult / Female",
                                                village = activePatient?.village ?: "Chandrapuri Block",
                                                householdId = activePatient?.householdId ?: "HH-EMERGENCY",
                                                suspectedCondition = "Acute Danger Sign",
                                                dangerSigns = listOf(res.citedSection),
                                                vitalSigns = when {
                                                    activePatient?.isPregnant == true -> "BP: ${activePatient.lastAncBp ?: "Check Urgently"}, Hb: ${activePatient.lastHbGdl ?: "Check"} g/dL"
                                                    activePatient?.lastBpReading != null -> "BP: ${activePatient.lastBpReading}"
                                                    activePatient?.lastMuacCm != null -> "MUAC: ${activePatient.lastMuacCm} cm"
                                                    else -> "Critical Triage"
                                                },
                                                protocolCitation = res.citedSection
                                            )
                                            onOpenReferralSlip(slip)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkTeal),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("Referral Slip", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = onCall108,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Text("108", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
fun PatientPickerDialog(
    repository: ArogyasathiRepository,
    onSelectPatient: (Member) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val allMembers = remember { repository.getAllMembers() }

    val filtered = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            allMembers.take(40)
        } else {
            val q = searchQuery.trim().lowercase()
            allMembers.filter {
                it.name.lowercase().contains(q) ||
                it.village.lowercase().contains(q) ||
                it.householdId.lowercase().contains(q)
            }.take(40)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Select Patient for Clinical Chat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Links chat history and tailors protocol answers to this person",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search name, village or ID...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered) { member ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CanvasBackground,
                            border = BorderStroke(0.5.dp, BorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectPatient(member) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = member.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextPrimary
                                    )
                                    val tag = when {
                                        member.isPregnant -> "Pregnant (EDD ${member.edd ?: "TBD"})"
                                        member.ncdCondition != null -> "NCD: ${member.ncdCondition}"
                                        else -> "${member.relation} • ${member.sex}"
                                    }
                                    Text(
                                        text = "$tag • ${member.village}",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                if (member.isPregnant) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFFFEBEE)
                                    ) {
                                        Text(
                                            text = "ANC",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmergencyRed,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
fun ReferralSlipDialog(
    slip: ReferralSlip,
    onDismiss: () -> Unit,
    onCall108: () -> Unit,
    onSaveToOutbox: () -> Unit
) {
    val context = LocalContext.current

    val formattedSms = """
AROGYASATHI EMERGENCY REFERRAL SLIP
Patient: ${slip.patientName} (${slip.ageGender})
Village: ${slip.village} | HH: ${slip.householdId}
Danger Sign: ${slip.suspectedCondition}
Protocol Citation: ${slip.protocolCitation}
Vitals / Findings: ${slip.vitalSigns}
Action Taken: ${slip.immediateActionTaken}
Referred To: ${slip.referredFacility}
ASHA Worker: ${slip.ashaName} (${slip.ashaPhone})
Date/Time: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(java.util.Date(slip.timestamp))}
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = EmergencyRed,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_emergency),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Clinical Referral Slip",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Grounded in GoI Leaf Protocols",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = slip.patientName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = EmergencyRed
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Village: ${slip.village} • Household: ${slip.householdId}",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Identified Danger Sign: ${slip.suspectedCondition}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmergencyRed
                        )
                        Text(
                            text = "Protocol Citation: ${slip.protocolCitation}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTeal
                        )
                        Text(
                            text = "Vitals / Observations: ${slip.vitalSigns}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Referred Facility: ${slip.referredFacility}",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Attending ASHA: ${slip.ashaName} (${slip.ashaPhone})",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Arogyasathi Referral Slip", formattedSms)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Referral slip copied to clipboard for SMS/WhatsApp", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Copy SMS", fontSize = 11.sp)
                }

                Button(
                    onClick = onSaveToOutbox,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkTeal),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Save Outbox", fontSize = 11.sp)
                }

                Button(
                    onClick = onCall108,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Call 108", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextSecondary)
            }
        }
    )
}

@Composable
fun FormattedClinicalGuidance(
    text: String,
    isEscalation: Boolean,
    isRefusal: Boolean
) {
    if (isEscalation) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val parts = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
            parts.forEachIndexed { idx, p ->
                if (idx == 0) {
                    Text(
                        text = p.trim(),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmergencyRed
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmergencyRed,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                        ) {}
                        Text(
                            text = p.trim(),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
        return
    }

    if (isRefusal) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = text.trim(),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        return
    }

    // Check for numbered points like "1. ", "2. ", "3. ", etc.
    val hasNumberedSteps = text.contains(Regex("\\b[1-9]\\.\\s+"))
    if (hasNumberedSteps) {
        val parts = text.split(Regex("\\s*(?=[1-9]\\.\\s+)")).map { it.trim() }.filter { it.isNotBlank() }
        val header = if (parts.firstOrNull()?.matches(Regex("^[1-9]\\..*")) == false) parts.first() else null
        val items = if (header != null) parts.drop(1) else parts

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Patient / Context Header Badge
            if (header != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE6F4F1),
                    border = BorderStroke(0.5.dp, PrimaryTeal.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = null,
                            tint = PrimaryTeal,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = header.removeSuffix(":").trim(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTeal
                        )
                    }
                }
            }

            // Step Items
            items.forEach { item ->
                val numMatch = Regex("^([1-9])\\.\\s*(.*)").find(item)
                val stepNum = numMatch?.groupValues?.getOrNull(1) ?: "•"
                val rawBody = numMatch?.groupValues?.getOrNull(2) ?: item

                // Check if this step is a Danger Sign alert
                val isDangerStep = rawBody.contains("Danger sign", ignoreCase = true) ||
                        rawBody.contains("खतरे के लक्षण") ||
                        (rawBody.contains("refer", ignoreCase = true) && rawBody.contains("immediately", ignoreCase = true))

                // Split title and content if colon is present (e.g. "ORS: Give 100 to 200 ml...")
                val colonIdx = rawBody.indexOf(':')
                val stepTitle = if (colonIdx in 1..25) rawBody.substring(0, colonIdx).trim() else null
                val stepContent = if (stepTitle != null) rawBody.substring(colonIdx + 1).trim() else rawBody

                if (isDangerStep) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = EmergencyRed,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "!",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (stepTitle != null) {
                                    Text(
                                        text = stepTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmergencyRed
                                    )
                                }
                                Text(
                                    text = stepContent,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(0.5.dp, BorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PrimaryTeal,
                                modifier = Modifier.size(18.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = stepNum,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                if (stepTitle != null) {
                                    Text(
                                        text = stepTitle,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkTeal
                                    )
                                }
                                Text(
                                    text = stepContent,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Break sentences into comfortable reading paragraphs if multiple exist
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.size > 1 && !text.contains('\n')) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sentences.forEach { s ->
                    Text(
                        text = s.trim(),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = TextPrimary
                    )
                }
            }
        } else {
            Text(
                text = text.trim(),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = TextPrimary
            )
        }
    }
}

private fun dialEmergency(context: Context, number: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
