package org.arogyasathi.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arogyasathi.app.R
import org.arogyasathi.app.core.AshaProfile
import org.arogyasathi.app.core.Member
import org.arogyasathi.app.core.ReferralSlip
import org.arogyasathi.app.core.maskPhoneNumber
import org.arogyasathi.app.data.ArogyasathiRepository
import org.arogyasathi.app.data.AssistantResult
import org.arogyasathi.app.voice.OfflineVoiceEngine
import org.arogyasathi.app.voice.VoicePackManager
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
    val textTelugu: String = "",
    val textTamil: String = "",
    val textKannada: String = "",
    val textMalayalam: String = "",
    val result: AssistantResult? = null,
    val patientName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun getDisplayText(lang: VoiceLanguage): String {
        if (sender == MessageSender.USER) return text
        if (result != null) {
            return result.getAnswerForLanguage(lang.code)
        }
        return when (lang) {
            VoiceLanguage.HINDI -> if (textHindi.isNotBlank()) textHindi else text
            VoiceLanguage.TELUGU -> if (textTelugu.isNotBlank()) textTelugu else "గమనిక: $text"
            VoiceLanguage.TAMIL -> if (textTamil.isNotBlank()) textTamil else "குறிப்பு: $text"
            VoiceLanguage.KANNADA -> if (textKannada.isNotBlank()) textKannada else "ಸೂಚನೆ: $text"
            VoiceLanguage.MALAYALAM -> if (textMalayalam.isNotBlank()) textMalayalam else "ശ്രദ്ധിക്കുക: $text"
            VoiceLanguage.ENGLISH -> text
        }
    }
}

enum class VoiceLanguage(
    val code: String,
    val nativeLabel: String,
    val shortCode: String,
    val prompt: String,
    val inputPlaceholder: String,
    val offlineVoiceNotice: String
) {
    ENGLISH(
        "en-IN", "English", "EN",
        "Speak clinical query (English)",
        "Ask clinical protocol question...",
        "English offline voice recognition is supported on this device."
    ),
    HINDI(
        "hi-IN", "हिन्दी", "HI",
        "बोलें — नैदानिक प्रश्न पूछें (Hindi)",
        "नैदानिक प्रोटोकॉल प्रश्न पूछें...",
        "हिन्दी ऑफ़लाइन वॉयस पैक सेटिंग्स में डाउनलोड करें।"
    ),
    TELUGU(
        "te-IN", "తెలుగు", "TE",
        "మాట్లాడండి — క్లినికల్ ప్రశ్న (Telugu)",
        "క్లినికల్ ప్రోటోకాల్ ప్రశ్న అడగండి...",
        "తెలుగు ఆఫ్‌లైన్ వాయిస్ ప్యాక్ సెట్టింగ్స్‌లో డౌన్‌లోడ్ చేసుకోండి."
    ),
    TAMIL(
        "ta-IN", "தமிழ்", "TA",
        "பேசுங்கள் — மருத்துவ கேள்வி (Tamil)",
        "மருத்துவ நெறிமுறை கேள்வி கேட்கவும்...",
        "தமிழ் ஆஃப்லைன் குரல் தொகுப்பை அமைப்புகளில் பதிவிறக்கவும்."
    ),
    KANNADA(
        "kn-IN", "ಕನ್ನಡ", "KN",
        "ಮಾತನಾಡಿ — ಕ್ಲಿನಿಕಲ್ ಪ್ರಶ್ನೆ (Kannada)",
        "ಕ್ಲಿನಿಕಲ್ ಪ್ರೋಟೋಕಾಲ್ ಪ್ರಶ್ನೆ ಕೇಳಿ...",
        "ಕನ್ನಡ ಆಫ್‌ಲೈನ್ ಧ್ವನಿ ಪ್ಯಾಕ್ ಸೆಟ್ಟಿಂಗ್‌ಗಳಲ್ಲಿ ಡೌನ್‌ಲೋಡ್ ಮಾಡಿ."
    ),
    MALAYALAM(
        "ml-IN", "മലയാളം", "ML",
        "സംസാരിക്കൂ — ക്ലിനിക്കൽ ചോദ്യം (Malayalam)",
        "ക്ലിനിക്കൽ പ്രോട്ടോക്കോൾ ചോദ്യം ചോദിക്കൂ...",
        "മലയാളം ഓഫ്‌ലൈൻ വോയ്‌സ് പായ്ക്ക് ക്രമീകരണങ്ങളിൽ ഡൗൺലോഡ് ചെയ്യുക."
    )
}

fun AppLanguage.toVoiceLanguage(): VoiceLanguage {
    return when (this) {
        AppLanguage.ENGLISH -> VoiceLanguage.ENGLISH
        AppLanguage.HINDI -> VoiceLanguage.HINDI
        AppLanguage.TELUGU -> VoiceLanguage.TELUGU
        AppLanguage.TAMIL -> VoiceLanguage.TAMIL
        AppLanguage.KANNADA -> VoiceLanguage.KANNADA
        AppLanguage.MALAYALAM -> VoiceLanguage.MALAYALAM
    }
}

@Composable
fun AssistantScreen(
    repository: ArogyasathiRepository,
    initialQuery: String? = null,
    initialPatient: Member? = null,
    onClearInitialContext: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentAppLang = LocalCurrentLanguage.current
    val strings = LocalAppStrings.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var activePatient by remember { mutableStateOf<Member?>(initialPatient) }
    var selectedLanguage by remember(currentAppLang) { mutableStateOf(currentAppLang.toVoiceLanguage()) }
    val isHindiMode = selectedLanguage == VoiceLanguage.HINDI
    var showPatientPicker by remember { mutableStateOf(false) }
    var activeReferralSlip by remember { mutableStateOf<ReferralSlip?>(null) }
    var showVoiceConsultationDialog by remember { mutableStateOf(false) }
    var autoPlayTts by remember { mutableStateOf(true) }

    var queryText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }
    var isOfflineRecording by remember { mutableStateOf(false) }
    val micTransition = rememberInfiniteTransition(label = "offlineMic")
    val micScale by micTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isOfflineRecording) 1.16f else 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "offlineMicScale",
    )
    val voicePackManager = remember(context) { VoicePackManager(context) }
    val offlineVoiceEngine = remember(context, voicePackManager) { OfflineVoiceEngine(context, voicePackManager) }
    DisposableEffect(offlineVoiceEngine) { onDispose { offlineVoiceEngine.close() } }
    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val langTag = selectedLanguage.code
        if (granted && voicePackManager.isPackReady(langTag)) {
            offlineVoiceEngine.startRecording(
                languageTag = langTag,
                onError = { message ->
                    isOfflineRecording = false
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            )
            isOfflineRecording = true
        } else if (!granted) {
            Toast.makeText(context, "Microphone permission is required for offline voice input.", Toast.LENGTH_SHORT).show()
        }
    }
    val activeProfile = remember(repository) { repository.getActiveProfile() }

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

            val welcomeTelugu = if (patient != null) {
                "నమస్తే అక్క! ${patient.name} (${patient.village}) కోసం క్లినికల్ సంప్రదింపు ప్రారంభించబడింది. దయచేసి ఏదైనా ప్రోటోకాల్ ప్రశ్న అడగండి."
            } else {
                "నమస్తే అక్క! నేను ఆరోగ్యసాథి, మీ ఆఫ్‌లైన్ క్లినికల్ ప్రోటోకాల్ సహచరుడిని. మాతా, శిశు లేదా ఎన్‌సీడీ గురించి ఏదైనా ప్రశ్న అడగండి."
            }

            val welcomeTamil = if (patient != null) {
                "வணக்கம் சகோதரி! ${patient.name} (${patient.village}) மருத்துவ ஆலோசனை தொடங்கப்பட்டது. ஏதேனும் நெறிமுறை கேள்வி கேட்கவும்."
            } else {
                "வணக்கம் சகோதரி! நான் ஆரோக்கியசாதி, உங்கள் ஆஃப்லைன் மருத்துவ நெறிமுறை துணைவன்."
            }

            val welcomeKannada = if (patient != null) {
                "ನಮಸ್ಕಾರ ಅಕ್ಕ! ${patient.name} (${patient.village}) ರವರ ಕ್ಲಿನಿಕಲ್ ಸಮಾಲೋಚನೆ ತೆರೆದಿದೆ. ಯಾವುದೇ ಪ್ರೋಟೋಕಾಲ್ ಪ್ರಶ್ನೆ ಕೇಳಿ."
            } else {
                "ನಮಸ್ಕಾರ ಅಕ್ಕ! ನಾನು ಆರೋಗ್ಯಸಾಥಿ, ನಿಮ್ಮ ಆಫ್‌ಲೈನ್ ಕ್ಲಿನಿಕಲ್ ಪ್ರೋಟೋಕಾಲ್ ಸಹಾಯಕ."
            }

            val welcomeMalayalam = if (patient != null) {
                "നമസ്കാരം ചേച്ചി! ${patient.name} (${patient.village}) ക്ലിനിക്കൽ കൺസൾട്ടേഷൻ ആരംഭിച്ചു. ചോദ്യങ്ങൾ ചോദിക്കാം."
            } else {
                "നമസ്കാരം ചേച്ചി! ഞാൻ ആരോഗ്യസാഥി, നിങ്ങളുടെ ഓഫ്‌ലൈൻ ക്ലിനിക്കൽ പ്രോട്ടോക്കോൾ കൂട്ടുകാരൻ."
            }

            messages.add(
                ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = welcomeText,
                    textHindi = welcomeHindi,
                    textTelugu = welcomeTelugu,
                    textTamil = welcomeTamil,
                    textKannada = welcomeKannada,
                    textMalayalam = welcomeMalayalam,
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
                textTelugu = assistantResult.answerTelugu,
                textTamil = assistantResult.answerTamil,
                textKannada = assistantResult.answerKannada,
                textMalayalam = assistantResult.answerMalayalam,
                result = assistantResult,
                patientName = p?.name
            )
        )

        // Stop previous speech if running
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        }

        if (autoPlayTts) {
            val textToSpeak = when (selectedLanguage) {
                VoiceLanguage.TELUGU -> assistantResult.answerTelugu ?: assistantResult.answer
                VoiceLanguage.HINDI -> assistantResult.answerHindi ?: assistantResult.answer
                VoiceLanguage.TAMIL -> assistantResult.answerTamil ?: assistantResult.answer
                VoiceLanguage.KANNADA -> assistantResult.answerKannada ?: assistantResult.answer
                VoiceLanguage.MALAYALAM -> assistantResult.answerMalayalam ?: assistantResult.answer
                VoiceLanguage.ENGLISH -> assistantResult.answer
            }
            val langLocale = when (selectedLanguage) {
                VoiceLanguage.TELUGU -> java.util.Locale.forLanguageTag("te-IN")
                VoiceLanguage.HINDI -> java.util.Locale.forLanguageTag("hi-IN")
                VoiceLanguage.TAMIL -> java.util.Locale.forLanguageTag("ta-IN")
                VoiceLanguage.KANNADA -> java.util.Locale.forLanguageTag("kn-IN")
                VoiceLanguage.MALAYALAM -> java.util.Locale.forLanguageTag("ml-IN")
                VoiceLanguage.ENGLISH -> java.util.Locale.forLanguageTag("en-IN")
            }
            try {
                tts?.language = langLocale
                tts?.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "arogyasathi_tts")
                isSpeaking = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

    // Dynamic suggested topics based on active patient context
    val quickTopics = when {
        activePatient?.isPregnant == true -> when (selectedLanguage) {
            VoiceLanguage.TELUGU -> listOf(
                "గర్భధారణలో రక్తస్రావం ప్రమాద సంకేతాలు",
                "ప్రీ-ఎక్లాంప్సియా అధిక రక్తపోటు మరియు తీవ్ర తలనొప్పి",
                "రోజువారీ ఐఎఫ్‌ఏ మరియు కాల్షియం మాత్రల షెడ్యూల్",
                "3వ ఏఎన్‌సీ తనిఖీ సమయం (వారాలు)"
            )
            VoiceLanguage.HINDI -> listOf(
                "गर्भावस्था में रक्तस्राव खतरे के संकेत",
                "प्री-एक्लेमप्सिया उच्च रक्तचाप और गंभीर सिरदर्द",
                "दैनिक आईएफए और कैल्शियम गोली की समय-सारणी",
                "तीसरी एएनसी जांच की समय-सीमा"
            )
            VoiceLanguage.TAMIL -> listOf(
                "கர்ப்பகாலத்தில் ரத்தப்போக்கு ஆபத்து அறிகுறிகள்",
                "உயர் ரத்த அழுத்தம் மற்றும் கடுமையான தலைவலி",
                "தினசரி இரும்பு மற்றும் கால்சியம் மாத்திரைகள்",
                "3வது ANC பரிசோதனை காலம்"
            )
            VoiceLanguage.KANNADA -> listOf(
                "ಗರ್ಭಾವಸ್ಥೆಯಲ್ಲಿ ರಕ್ತಸ್ರಾವ ಅಪಾಯದ ಚಿಹ್ನೆಗಳು",
                "ಅಧಿಕ ರಕ್ತದೊತ್ತಡ ಮತ್ತು ತೀವ್ರ ತಲೆನೋವು",
                "ದೈನಂದಿನ ಐಎಫ್‌ಎ ಮತ್ತು ಕ್ಯಾಲ್ಸಿಯಂ ಮಾತ್ರೆಗಳು",
                "3ನೇ ಎಎನ್‌ಸಿ ತಪಾಸಣೆ ಸಮಯ"
            )
            VoiceLanguage.MALAYALAM -> listOf(
                "ഗർഭകാലത്തെ രക്തസ്രാവം അപകട ലക്ഷണങ്ങൾ",
                "ഉയർന്ന രക്തസമ്മർദ്ദവും കഠിനമായ തലവേദനയും",
                "ദിവസേനയുള്ള അയൺ, കാൽസ്യം ഗുളികകൾ",
                "മൂന്നാമത്തെ ANC പരിശോധനാ സമയം"
            )
            VoiceLanguage.ENGLISH -> listOf(
                "Bleeding in pregnancy danger signs",
                "Pre-eclampsia high BP and severe headache",
                "Daily IFA and Calcium tablet schedule",
                "3rd ANC checkup window weeks"
            )
        }
        activePatient?.ncdCondition != null -> when (selectedLanguage) {
            VoiceLanguage.TELUGU -> listOf(
                "బీపీ 140/90 రక్తపోటు నిర్వహణ మార్గదర్శకాలు",
                "రక్తంలో చక్కెర (షుగర్) >=200 మార్గదర్శకాలు",
                "ఉప్పు తగ్గించడం మరియు వ్యాయామ సలహాలు"
            )
            VoiceLanguage.HINDI -> listOf(
                "बीपी 140/90 उच्च रक्तचाप प्रबंधन",
                "रैंडम ब्लड शुगर >=200 दिशा-निर्देश",
                "नमक कम करने और व्यायाम की सलाह"
            )
            VoiceLanguage.TAMIL -> listOf(
                "ரத்த அழுத்தம் 140/90 மேலாண்மை நெறிமுறை",
                "சர்க்கரை அளவு >=200 வழிகாட்டுதல்கள்",
                "உப்பு குறைப்பு மற்றும் உடற்பயிற்சி ஆலோசனைகள்"
            )
            VoiceLanguage.KANNADA -> listOf(
                "ಬಿಪಿ 140/90 ರಕ್ತದೊತ್ತಡ ನಿರ್ವಹಣಾ ಮಾರ್ಗಸೂಚಿಗಳು",
                "ರಕ್ತದ ಸಕ್ಕರೆ ಪ್ರಮಾಣ >=200 ಮಾರ್ಗಸೂಚಿಗಳು",
                "ಉಪ್ಪು ಕಡಿಮೆ ಮಾಡುವುದು ಮತ್ತು ವ್ಯಾಯಾಮ ಸಲಹೆ"
            )
            VoiceLanguage.MALAYALAM -> listOf(
                "ബിപി 140/90 രക്താതിമർദ്ദ നിയന്ത്രണം",
                "ബ്ലഡ് ഷുഗർ >=200 മാർഗ്ഗനിർദ്ദേശങ്ങൾ",
                "ഉപ്പ് കുറയ്ക്കലും വ്യായാമ നിർദ്ദേശങ്ങളും"
            )
            VoiceLanguage.ENGLISH -> listOf(
                "BP 140/90 hypertension management",
                "Random blood sugar >=200 guidelines",
                "Salt reduction and exercise counselling"
            )
        }
        activePatient != null && org.arogyasathi.app.laya.PatientAgeCalculator.calculate(activePatient!!.dob)?.isChildUnderFive == true -> when (selectedLanguage) {
            VoiceLanguage.TELUGU -> listOf(
                "విరేచనాలకు ఓఆర్ఎస్ మరియు జింక్ మోతాదు",
                "వేగంగా శ్వాస మరియు ఛాతీ లోపలికి లాగడం",
                "రాబోయే టీకాల షెడ్యూల్",
                "MUAC పోషకాహార లోప తనిఖీ"
            )
            VoiceLanguage.HINDI -> listOf(
                "दस्त में ओआरएस और जिंक की खुराक",
                "तेज सांस और छाती धंसना (निमोनिया)",
                "अगला टीकाकरण शेड्यूल",
                "MUAC कुपोषण जांच"
            )
            VoiceLanguage.TAMIL -> listOf(
                "வயிற்றுப்போக்கு ORS மற்றும் ஜிங்க் அளவு",
                "வேகமான மூச்சு மற்றும் நிமோனியா",
                "தடுப்பூசி அட்டவணை",
                "MUAC ஊட்டச்சத்து குறைபாடு பரிசோதனை"
            )
            VoiceLanguage.KANNADA -> listOf(
                "ಭೇದಿಗೆ ಓಆರ್‌ಎಸ್ ಮತ್ತು ಜಿಂಕ್ ಪ್ರಮಾಣ",
                "ವೇಗದ ಉಸಿರಾಟ ಮತ್ತು ನ್ಯುಮೋನಿಯಾ",
                "ಮುಂದಿನ ಲಸಿಕೆ ವೇಳಾಪಟ್ಟಿ",
                "MUAC ಅಪೌಷ್ಟಿಕತೆ ಪರೀಕ್ಷೆ"
            )
            VoiceLanguage.MALAYALAM -> listOf(
                "വയറിളക്കത്തിന് ഒആർഎസും സിങ്കും",
                "വേഗതയേറിയ ശ്വാസോച്ഛ്വാസം ന്യുമോണിയ",
                "വാക്സിനേഷൻ ഷെഡ്യൂൾ",
                "MUAC പോഷകാഹാര പരിശോധന"
            )
            VoiceLanguage.ENGLISH -> listOf(
                "Diarrhoea ORS and Zinc dose",
                "Fast breathing and chest indrawing",
                "Next due vaccine schedule",
                "MUAC malnutrition screening"
            )
        }
        activePatient != null && org.arogyasathi.app.laya.PatientAgeCalculator.calculate(activePatient!!.dob)?.isNewbornUnderTwoMonths == true -> when (selectedLanguage) {
            VoiceLanguage.TELUGU -> listOf(
                "శిశువు పాలు తాగలేకపోతోంది",
                "శిశువు శరీరం చల్లబడటం (హైపోథర్మియా)",
                "బొడ్డు సంరక్షణ మరియు స్నానం వాయిదా",
                "HBNC గృహ సందర్శన షెడ్యూల్"
            )
            VoiceLanguage.HINDI -> listOf(
                "शिशु दूध नहीं पी पा रहा है",
                "शिशु का शरीर ठंडा पड़ना (हाइपोथर्मिया)",
                "नाल की देखभाल और नहलाने में देरी",
                "HBNC गृह भ्रमण समय-सारणी"
            )
            VoiceLanguage.TAMIL -> listOf(
                "குழந்தை பால் குடிக்க முடியவில்லை",
                "குழந்தை உடல் குளிர்ச்சியடைதல் (ஹைபோதெர்மியா)",
                "தொப்புள் கொடி பராமரிப்பு மற்றும் குளிப்பாட்டுதல்",
                "HBNC இல்ல வருகை அட்டவணை"
            )
            VoiceLanguage.KANNADA -> listOf(
                "ಮಗು ಹಾಲು ಕುಡಿಯಲು ಅಸಮರ್ಥತೆ",
                "ಮಗುವಿನ ದೇಹ ತಣ್ಣಗಾಗುವುದು (ಹೈಪೋಥರ್ಮಿಯಾ)",
                "ಹೊಕ್ಕುಳಬಳ್ಳಿ ಆರೈಕೆ ಮತ್ತು ಸ್ನಾನ ಮುಂದೂಡುವುದು",
                "HBNC ಮನೆ ಭೇಟಿ ವೇಳಾಪಟ್ಟಿ"
            )
            VoiceLanguage.MALAYALAM -> listOf(
                "കുഞ്ഞിന് പാൽ കുടിക്കാൻ കഴിയുന്നില്ല",
                "കുഞ്ഞിന്റെ ശരീരം തണുത്തുപോകൽ (ഹൈപ്പോതെർമിയ)",
                "പൊക്കിൾക്കൊടി പരിചരണവും കുളി മാറ്റിവയ്ക്കലും",
                "HBNC ഗൃഹസന്ദർശന ഷെഡ്യൂൾ"
            )
            VoiceLanguage.ENGLISH -> listOf(
                "Baby unable to suck or feed",
                "Baby cold to touch hypothermia",
                "Cord care and delayed bathing",
                "HBNC home visit schedule"
            )
        }
        else -> when (selectedLanguage) {
            VoiceLanguage.HINDI -> listOf(
                "दस्त में ओआरएस और जिंक की खुराक",
                "तेज सांस और छाती धंसना (निमोनिया)",
                "गर्भावस्था में 4 एएनसी जांच की समय-सारणी",
                "बीपी 140/90 और नमक कम करने की सलाह",
                "डिलीवरी के बाद तेज रक्तस्राव (108)"
            )
            VoiceLanguage.TELUGU -> listOf(
                "విరేచనాలకు ఓఆర్ఎస్ మరియు జింక్ మోతాదు",
                "వేగంగా శ్వాస మరియు ఛాతీ లోపలికి లాగడం",
                "గర్భధారణలో 4 ఏఎన్‌సీ తనిఖీలు",
                "బీపీ 140/90 రక్తపోటు నియంత్రణ",
                "ప్రసవం తర్వాత తీవ్ర రక్తస్రావం (108)"
            )
            VoiceLanguage.TAMIL -> listOf(
                "வயிற்றுப்போக்கு ORS மற்றும் ஜிங்க் அளவு",
                "வேகமான மூச்சு மற்றும் நிமோனியா",
                "கர்ப்பகால 4 பரிசோதனைகள் (ANC)",
                "உயர் ரத்த அழுத்தம் 140/90 கட்டுப்பாடு"
            )
            VoiceLanguage.KANNADA -> listOf(
                "ಭೇದಿಗೆ ಓಆರ್‌ಎಸ್ ಮತ್ತು ಜಿಂಕ್ ಪ್ರಮಾಣ",
                "ವೇಗದ ಉಸಿರಾಟ ಮತ್ತು ನ್ಯುಮೋನಿಯಾ",
                "ಗರ್ಭಾವಸ್ಥೆಯಲ್ಲಿ 4 ಎಎನ್‌ಸಿ ತಪಾಸಣೆಗಳು",
                "ಬಿಪಿ 140/90 ರಕ್ತದೊತ್ತಡ ನಿಯಂತ್ರಣ"
            )
            VoiceLanguage.MALAYALAM -> listOf(
                "വയറിളക്കത്തിന് ഒആർഎസും സിങ്കും",
                "വേഗതയേറിയ ശ്വാസോച്ഛ്വാസം ന്യുമോണിയ",
                "ഗർഭകാലത്തെ 4 ANC പരിശോധനകൾ",
                "രക്താതിമർദ്ദം 140/90 നിയന്ത്രണം"
            )
            VoiceLanguage.ENGLISH -> listOf(
                "Diarrhoea ORS and Zinc dose",
                "Fast breathing and pneumonia thresholds",
                "Mandatory 4 ANC checkup timeline",
                "Hypertension BP 140/90 guidelines",
                "Severe bleeding after delivery (108)"
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
    ) {
        // Compact Clinical Header: Patient Context + Emergency SOS Speed Dial
        Surface(
            color = SurfaceCrisp,
            border = BorderStroke(0.5.dp, BorderSubtle),
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Patient Context Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (activePatient != null) Color(0xFFE6F4F1) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (activePatient != null) PrimaryTeal.copy(alpha = 0.4f) else BorderSubtle),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { showPatientPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (activePatient != null) PrimaryTeal else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_person),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                        Text(
                            text = if (activePatient != null) "${activePatient!!.name} • ${activePatient!!.village}" else strings.generalConsultation,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activePatient != null) DarkTeal else TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activePatient != null) {
                            IconButton(
                                onClick = {
                                    activePatient = null
                                    reloadMessages(null)
                                },
                                modifier = Modifier.size(16.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear patient",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
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
                    selectedLanguage = selectedLanguage,
                    isHindiMode = isHindiMode,
                    activePatient = activePatient,
                    activeProfile = activeProfile,
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
                    onCallMo = { dialEmergency(context, activeProfile.medicalOfficerPhone) },
                    onCallAnm = { dialEmergency(context, activeProfile.anmPhone) },
                    onCall108 = { dialEmergency(context, "108") },
                    onOpenReferralSlip = { slip ->
                        activeReferralSlip = slip
                    }
                )
            }
        }

        // Quick Clinical Suggestion Chips (Anchored ergonomically directly above input bar)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickTopics) { topic ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SurfaceCrisp,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 1.dp,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                    onClick = { submitQuery(topic) }
                ) {
                    Text(
                        text = topic,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Bottom Chat Input Bar (Rich Floating Capsule with Tactile Action Twins)
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Seamless Text Input Field inside the single bar
                BasicTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp),
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Normal
                    ),
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (queryText.trim().isNotEmpty()) {
                            submitQuery(queryText)
                        }
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (queryText.isEmpty()) {
                                Text(
                                    text = if (activePatient != null) "${strings.askQuestionForPrefix} ${activePatient!!.name}..." else selectedLanguage.inputPlaceholder,
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                // Uniform Microphone Button (Rich Medical Teal Glow)
                Surface(
                    shape = CircleShape,
                    shadowElevation = 1.5.dp,
                    border = BorderStroke(1.dp, Color(0xFF99F6E4)),
                    modifier = Modifier
                        .size(38.dp)
                        .scale(micScale)
                        .clip(CircleShape)
                        .clickable {
                            val langTag = selectedLanguage.code
                            if (voicePackManager.isPackReady(langTag)) {
                                if (isOfflineRecording) {
                                    isOfflineRecording = false
                                    offlineVoiceEngine.stopRecording(
                                        languageTag = langTag,
                                        onResult = { spoken ->
                                            if (spoken.isNotBlank()) queryText = spoken
                                            else Toast.makeText(context, "No speech detected. Please try again.", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() },
                                    )
                                } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    offlineVoiceEngine.startRecording(
                                        languageTag = langTag,
                                        onError = { message ->
                                            isOfflineRecording = false
                                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                    isOfflineRecording = true
                                } else {
                                    microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    "Offline voice pack for ${selectedLanguage.nativeLabel} is not installed. Download or import it in Settings.",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                        .semantics {
                            contentDescription = if (isOfflineRecording) {
                                "Recording ${selectedLanguage.nativeLabel} voice. Tap to stop."
                            } else {
                                "Start ${selectedLanguage.nativeLabel} offline voice input"
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isOfflineRecording) {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFFEE2E2), Color(0xFFFECACA))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFF0FDFA), Color(0xFFCCFBF1))
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_mic),
                            contentDescription = "Voice consultation",
                            tint = if (isOfflineRecording) EmergencyRed else Color(0xFF0F766E),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Uniform Send Button (Rich Vibrant Action Gradient)
                val isSendEnabled = queryText.trim().isNotEmpty()
                Surface(
                    shape = CircleShape,
                    shadowElevation = if (isSendEnabled) 3.dp else 0.dp,
                    border = BorderStroke(
                        1.dp,
                        if (isSendEnabled) Color(0xFF0F766E).copy(alpha = 0.8f) else Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = isSendEnabled) { submitQuery(queryText) }
                        .semantics { contentDescription = "Send message" }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSendEnabled) {
                                    Brush.verticalGradient(listOf(Color(0xFF14B8A6), Color(0xFF0F766E)))
                                } else {
                                    Brush.verticalGradient(listOf(Color(0xFFF8FAFC), Color(0xFFE2E8F0)))
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_send),
                            contentDescription = "Send",
                            tint = if (isSendEnabled) Color.White else Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                    }
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
            onCallMo = { dialEmergency(context, slip.medicalOfficerPhone) },
            onCall108 = { dialEmergency(context, "108") },
            onSaveToOutbox = {
                val result = repository.saveReferralSlip(slip)
                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                activeReferralSlip = null
            }
        )
    }

    // In-App Offline Voice Consultation Dialog
    if (showVoiceConsultationDialog) {
        VoiceConsultationDialog(
            repository = repository,
            selectedLanguage = selectedLanguage,
            autoPlayTts = autoPlayTts,
            onToggleAutoPlayTts = { autoPlayTts = !autoPlayTts },
            onDismiss = { showVoiceConsultationDialog = false },
            onSelectSpokenQuery = { spoken ->
                showVoiceConsultationDialog = false
                submitQuery(spoken)
            }
        )
    }
}

@Composable
fun SoundWaveVisualizer(
    modifier: Modifier = Modifier,
    isPulsing: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val h1 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 30f,
        animationSpec = infiniteRepeatable(tween(350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 20f, targetValue = 42f,
        animationSpec = infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 14f, targetValue = 46f,
        animationSpec = infiniteRepeatable(tween(280, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h3"
    )
    val h4 by infiniteTransition.animateFloat(
        initialValue = 24f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h4"
    )
    val h5 by infiniteTransition.animateFloat(
        initialValue = 10f, targetValue = 36f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "h5"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val heights = if (isPulsing) listOf(h1, h2, h3, h4, h5) else listOf(12f, 18f, 22f, 16f, 10f)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PrimaryTeal)
            )
        }
    }
}

@Composable
fun VoiceConsultationDialog(
    repository: ArogyasathiRepository,
    selectedLanguage: VoiceLanguage,
    autoPlayTts: Boolean,
    onToggleAutoPlayTts: () -> Unit,
    onDismiss: () -> Unit,
    onSelectSpokenQuery: (String) -> Unit
) {
    val context = LocalContext.current
    val isModelDownloaded = true
    var isListening by remember { mutableStateOf(false) }
    var spokenInputText by remember { mutableStateOf("") }
    var statusMessage by remember {
        mutableStateOf(
            if (isModelDownloaded) "తెలుగు ప్యాక్ APKలో ఉంది — ప్రధాన మైక్రోఫోన్‌ను ఉపయోగించండి"
            else "తెలుగు ఆఫ్‌లైన్ ప్యాక్ మాత్రమే ఈ APKలో ఉంది"
        )
    }

    val voiceQueries = when (selectedLanguage) {
        VoiceLanguage.TELUGU -> listOf(
            Triple("గర్భధారణలో రక్తస్రావం అవుతోంది", "అత్యవసర ప్రమాద సంకేతం (Bleeding in pregnancy)", EmergencyRed),
            Triple("శిశువుకు పాలు తాగడం రావడం లేదు", "నవజాత శిశువు అత్యవసరం (Inability to feed)", EmergencyRed),
            Triple("బీపీ 160/100 తీవ్ర తలనొప్పి", "ప్రీ-ఎక్లాంప్సియా హైపర్టెన్షన్ (Severe Pre-eclampsia)", EmergencyRed),
            Triple("విరేచనాలకు ఓఆర్ఎస్ మరియు జింక్ మోతాదు ఎంత", "పిల్లల విరేచనాల చికిత్స (Diarrhoea ORS/Zinc)", PrimaryTeal),
            Triple("3వ త్రైమాసికంలో ఏఎన్‌సీ తనిఖీలు ఎప్పుడు చేయాలి", "గర్భధారణ ప్రోటోకాల్ (ANC Schedule)", PrimaryTeal),
            Triple("సిజేరియన్ సర్జరీ ఆపరేషన్ ఖర్చు ఎంత?", "పరిధి దాటిన ప్రశ్న (Caesarean surgery referral)", Color(0xFF64748B))
        )
        VoiceLanguage.HINDI -> listOf(
            Triple("तीसरी तिमाही में तेज रक्तस्राव हो रहा है", "आपातकालीन खतरा (3rd trimester bleeding)", EmergencyRed),
            Triple("बच्चे को तेज बुखार और दौरे पड़ रहे हैं", "नवजात शिशु आपातकाल (Convulsions/fits)", EmergencyRed),
            Triple("गर्भवती का बीपी 160/100 और तेज सिरदर्द", "प्री-एक्लेम्पसिया (Severe pre-eclampsia)", EmergencyRed),
            Triple("दस्त में ओआरएस और जिंक की सही खुराक", "बाल स्वास्थ्य प्रोटोकॉल (ORS & Zinc dose)", PrimaryTeal),
            Triple("गर्भावस्था में 4 एएनसी जांच की समय-सारणी", "मातृ प्रोटोकॉल (ANC Schedule)", PrimaryTeal),
            Triple("कैंसर की कीमोथेरेपी दवाएं", "परिधि से बाहर (Chemotherapy referral)", Color(0xFF64748B))
        )
        VoiceLanguage.TAMIL -> listOf(
            Triple("கர்ப்ப காலத்தில் அதிக ரத்தப்போக்கு", "அவசர ஆபத்து (Pregnancy bleeding)", EmergencyRed),
            Triple("குழந்தை பால் குடிக்கவில்லை வலிப்பு", "குழந்தை அவசரம் (Inability to feed)", EmergencyRed),
            Triple("வயிற்றுப்போக்கு ORS மற்றும் ஜிங்க் அளவு", "மருத்துவ நெறிமுறை (ORS Zinc dose)", PrimaryTeal),
            Triple("அறுவை சிகிச்சை சிசேரியன்", "வரம்பிற்கு அப்பாற்பட்டது (Surgery)", Color(0xFF64748B))
        )
        VoiceLanguage.KANNADA -> listOf(
            Triple("ಗರ್ಭಿಣಿಗೆ ತೀವ್ರ ರಕ್ತಸ್ರಾವ", "ತುರ್ತು ಅಪಾಯ (Pregnancy bleeding)", EmergencyRed),
            Triple("ಮಗುವಿಗೆ ಎದೆಹಾಲು ಕುಡಿಯಲು ಆಗುತ್ತಿಲ್ಲ", "ನವಜಾತ ಶಿಶು ತುರ್ತು (Inability to feed)", EmergencyRed),
            Triple("ಭೇದಿಗೆ ಓಆರ್‌ಎಸ್ మరియు జింಕ್ ప్రమాಣ", "ಕ್ಲಿನಿಕಲ್ ಪ್ರೋಟೋಕಾಲ್ (ORS Zinc dose)", PrimaryTeal),
            Triple("ಶಸ್ತ್ರಚಿಕಿತ್ಸೆ ಸಿಜೇರಿಯನ್", "ವ್ಯಾಪ್ತಿಯ ಹೊರಗೆ (Surgery referral)", Color(0xFF64748B))
        )
        VoiceLanguage.MALAYALAM -> listOf(
            Triple("ഗർഭാവസ്ഥയിൽ കഠിനമായ രക്തസ്രാവം", "അടിയന്തര അപകടം (Pregnancy bleeding)", EmergencyRed),
            Triple("കുഞ്ഞിന് മുലപ്പാൽ കുടിക്കാൻ കഴിയുന്നില്ല", "നവജാത ശിശു അടിയന്തരം (Inability to feed)", EmergencyRed),
            Triple("വയറിളക്കത്തിന് ഒആർഎസും സിങ്കും അളവ്", "പ്രോട്ടോക്കോൾ (ORS Zinc dose)", PrimaryTeal),
            Triple("ശസ്ത്രക്രിയ സിസേറിയൻ", "പരിധിക്ക് പുറത്ത് (Surgery referral)", Color(0xFF64748B))
        )
        VoiceLanguage.ENGLISH -> listOf(
            Triple("Vaginal bleeding in third trimester", "Emergency Danger Sign (ESC-2.1)", EmergencyRed),
            Triple("Newborn unable to suck or feed with chest indrawing", "Severe Newborn Illness (ESC-1.1)", EmergencyRed),
            Triple("Severe headache blurred vision and BP 160/110", "Pre-eclampsia Crisis (ESC-2.3)", EmergencyRed),
            Triple("ORS and Zinc dosage for child diarrhoea", "Protocol Dosage (SEC-4.2)", PrimaryTeal),
            Triple("Schedule for 4 ANC visits during pregnancy", "Maternal Protocol (SEC-1.3)", PrimaryTeal),
            Triple("Caesarean surgery and cancer chemotherapy", "Out-of-Scope Procedure (SCP-1.2)", Color(0xFF64748B))
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            text = "Arogyasathi Voice Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isModelDownloaded) "${selectedLanguage.nativeLabel} (${selectedLanguage.code}) • Offline Model Ready"
                            else "${selectedLanguage.nativeLabel} (${selectedLanguage.code}) • Model Not Downloaded",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isModelDownloaded) PrimaryTeal else Color(0xFFD97706)
                        )
                    }
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // In-App Soundwave & Recording Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isListening) EmergencyRed.copy(alpha = 0.5f) else Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SoundWaveVisualizer(modifier = Modifier.height(42.dp), isPulsing = isListening)

                        Text(
                            text = statusMessage,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isListening) EmergencyRed else TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Big In-App Record Button
                        Button(
                            onClick = {
                                isListening = false
                                statusMessage = "ఈ పాత డైలాగ్‌లో వాయిస్ ఇన్‌పుట్ లేదు. దాన్ని మూసి ప్రధాన తెలుగు మైక్రోఫోన్‌ను ఉపయోగించండి."
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListening) EmergencyRed else PrimaryTeal
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_mic),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isListening) "వింటున్నాము... (Listening...)" else "మైక్రోఫోన్ ప్రారంభించండి (Tap to Speak)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        // Live Speech Preview Box
                        if (spokenInputText.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, PrimaryTeal.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
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
                                            text = "గుర్తించిన మాటలు (Transcribed Speech):",
                                            fontSize = 9.sp,
                                            color = TextSecondary
                                        )
                                        Text(
                                            text = spokenInputText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                    }
                                    Button(
                                        onClick = { onSelectSpokenQuery(spokenInputText) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("సమర్పించండి", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // TTS Auto Read-out Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleAutoPlayTts() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_speaker),
                            contentDescription = null,
                            tint = if (autoPlayTts) RoutineGreen else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Auto-speak answer out loud (TTS)",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                    }
                    Switch(
                        checked = autoPlayTts,
                        onCheckedChange = { onToggleAutoPlayTts() },
                        modifier = Modifier.height(20.dp)
                    )
                }

                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                // Quick Spoken Queries List
                Text(
                    text = "లేదా క్లినికల్ ప్రశ్నపై నొక్కండి (${selectedLanguage.nativeLabel}):",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    voiceQueries.forEach { (queryText, categoryDesc, badgeColor) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFAFAFA),
                            border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelectSpokenQuery(queryText) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = queryText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = categoryDesc,
                                        fontSize = 9.sp,
                                        color = badgeColor
                                    )
                                }
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_speaker),
                                    contentDescription = "Speak",
                                    tint = PrimaryTeal,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (spokenInputText.isNotEmpty()) {
                TextButton(onClick = { onSelectSpokenQuery(spokenInputText) }) {
                    Text("సమర్పించండి (Submit)", fontWeight = FontWeight.Bold, color = PrimaryTeal)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "రద్దు (Close)", fontSize = 12.sp, color = TextSecondary)
            }
        }
    )
}
@Composable
fun ChatBubble(
    message: ChatMessage,
    selectedLanguage: VoiceLanguage = VoiceLanguage.ENGLISH,
    isHindiMode: Boolean = selectedLanguage == VoiceLanguage.HINDI,
    activePatient: Member?,
    activeProfile: AshaProfile,
    onPlayTts: (String) -> Unit,
    onCallMo: () -> Unit = {},
    onCallAnm: () -> Unit = {},
    onCall108: () -> Unit,
    onOpenReferralSlip: (ReferralSlip) -> Unit
) {
    val strings = LocalAppStrings.current
    val isUser = message.sender == MessageSender.USER
    val res = message.result
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                color = PrimaryTeal
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_assistant),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
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
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            } else {
                val isEscalation = res?.isEscalation == true
                val isRefusal = res?.isRefusal == true
                val priorityColor = when {
                    isEscalation -> EmergencyRed
                    isRefusal -> HighPriorityOrange
                    else -> PrimaryTeal
                }

                Surface(
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = SurfaceCrisp,
                    border = BorderStroke(1.dp, BorderSubtle),
                    shadowElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        // Left Priority Triage Strip
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight()
                                .background(priorityColor)
                        )

                        Column(modifier = Modifier.weight(1f).padding(12.dp)) {
                            // Header Row: Triage Pill + Citation + Speaker
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Triage Status Pill
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = priorityColor.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (isEscalation) R.drawable.ic_emergency else if (isRefusal) R.drawable.ic_shield else R.drawable.ic_check_circle
                                            ),
                                            contentDescription = null,
                                            tint = priorityColor,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = when {
                                                isEscalation -> strings.dangerSignAlert
                                                isRefusal -> strings.doctorReferralAlert
                                                else -> strings.clinicalProtocolAlert
                                            },
                                            color = priorityColor,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    if (res != null) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFF1F5F9)
                                        ) {
                                            Text(
                                                text = res.citedSection,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TextSecondary,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    // Audio TTS Button
                                    IconButton(
                                        onClick = { onPlayTts(message.getDisplayText(selectedLanguage)) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_speaker),
                                            contentDescription = "Read aloud",
                                            tint = PrimaryTeal,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Clinical Text
                            val displayText = message.getDisplayText(selectedLanguage)
                            FormattedClinicalGuidance(
                                text = displayText,
                                isEscalation = isEscalation,
                                isRefusal = isRefusal
                            )

                            // Action Footer (Compact single row)
                            if (isEscalation) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = onCall108,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(painterResource(R.drawable.ic_call), null, tint = Color.White, modifier = Modifier.size(11.dp))
                                            Text("108", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = onCallMo,
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(painterResource(R.drawable.ic_call), null, tint = Color.White, modifier = Modifier.size(11.dp))
                                            Text(strings.profileCallMo, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

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
                                                dangerSigns = listOf(res?.citedSection ?: "ESC-2.1"),
                                                vitalSigns = when {
                                                    activePatient?.isPregnant == true -> "BP: ${activePatient.lastAncBp ?: "Check Urgently"}, Hb: ${activePatient.lastHbGdl ?: "Check"} g/dL"
                                                    activePatient?.lastBpReading != null -> "BP: ${activePatient.lastBpReading}"
                                                    activePatient?.lastMuacCm != null -> "MUAC: ${activePatient.lastMuacCm} cm"
                                                    else -> "Critical Triage"
                                                },
                                                protocolCitation = res?.citedSection ?: "ESC-2.1",
                                                referredFacility = activeProfile.parentPhc,
                                                immediateActionTaken = "Medical Officer Alerted & 108 Emergency Ambulance Dispatched",
                                                ashaName = activeProfile.name,
                                                ashaPhone = activeProfile.phone,
                                                medicalOfficerName = activeProfile.medicalOfficerName,
                                                medicalOfficerPhone = activeProfile.medicalOfficerPhone
                                            )
                                            onOpenReferralSlip(slip)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkTeal),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1.2f).height(30.dp)
                                    ) {
                                        Text(strings.referralSlipButton, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (isRefusal) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = onCallMo,
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(painterResource(R.drawable.ic_call), null, tint = Color.White, modifier = Modifier.size(11.dp))
                                            Text(strings.profileCallMo, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Button(
                                        onClick = onCallAnm,
                                        colors = ButtonDefaults.buttonColors(containerColor = RoutineGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Icon(painterResource(R.drawable.ic_call), null, tint = Color.White, modifier = Modifier.size(11.dp))
                                            Text(strings.profileCallAnm, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Arogyasathi Protocol Guidance", displayText)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Copied protocol guidance to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                        modifier = Modifier.height(24.dp)
                                    ) {
                                        Text(strings.copyGuidance, fontSize = 10.sp, color = PrimaryTeal, fontWeight = FontWeight.SemiBold)
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
fun EmergencyContactsDialog(
    activeProfile: AshaProfile,
    onCallMo: () -> Unit,
    onCallAnm: () -> Unit,
    onCall108: () -> Unit,
    onCall102: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
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
                    modifier = Modifier.size(34.dp)
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
                        text = strings.emergencyDialogTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = strings.emergencyDialogSubtitle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // MO In-Charge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.moInChargeTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryTeal)
                            Text(activeProfile.medicalOfficerName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(activeProfile.parentPhc, fontSize = 11.sp, color = TextSecondary)
                        }
                        Button(
                            onClick = onCallMo,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(strings.profileCallMo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Sector ANM
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.anmTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = RoutineGreen)
                            Text(activeProfile.anmName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(activeProfile.subCentre, fontSize = 11.sp, color = TextSecondary)
                        }
                        Button(
                            onClick = onCallAnm,
                            colors = ButtonDefaults.buttonColors(containerColor = RoutineGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(strings.profileCallAnm, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // National Emergency 108 / 102
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(strings.nationalAmbulancesTitle, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = EmergencyRed)
                            Text("108 (Emergency) • 102 (JSSK)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(strings.tollFreeHelplineDesc, fontSize = 11.sp, color = TextSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Button(
                                onClick = onCall108,
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("108", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onCall102,
                                colors = ButtonDefaults.buttonColors(containerColor = HighPriorityOrange),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("102", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close, color = TextSecondary)
            }
        }
    )
}

@Composable
fun PatientPickerDialog(
    repository: ArogyasathiRepository,
    onSelectPatient: (Member) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
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
                    text = strings.selectPatientTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = strings.selectPatientSubtitle,
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
                    placeholder = { Text(strings.searchPatientPlaceholder, fontSize = 12.sp) },
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
                Text(strings.cancel, color = TextSecondary)
            }
        }
    )
}

@Composable
fun ReferralSlipDialog(
    slip: ReferralSlip,
    onDismiss: () -> Unit,
    onCallMo: () -> Unit = {},
    onCall108: () -> Unit,
    onSaveToOutbox: () -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    val formattedSms = """
AROGYASATHI EMERGENCY REFERRAL SLIP
Patient: ${slip.patientName} (${slip.ageGender})
Village: ${slip.village} | HH: ${slip.householdId}
Danger Sign: ${slip.suspectedCondition}
Protocol Citation: ${slip.protocolCitation}
Vitals / Findings: ${slip.vitalSigns}
Action Taken: ${slip.immediateActionTaken}
Referred Facility: ${slip.referredFacility}
Medical Officer: ${slip.medicalOfficerName} (${slip.medicalOfficerPhone})
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
                        text = strings.referralSlipTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = strings.referralSlipSubtitle,
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
                            text = "${strings.village}: ${slip.village} • Household: ${slip.householdId}",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${strings.dangerSignLabel} ${slip.suspectedCondition}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = EmergencyRed
                        )
                        Text(
                            text = "${strings.protocolCitationLabel} ${slip.protocolCitation}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTeal
                        )
                        Text(
                            text = "${strings.vitalsObservationsLabel} ${slip.vitalSigns}",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${strings.referredFacilityLabel} ${slip.referredFacility}",
                            fontSize = 11.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "${strings.attendingMoLabel} ${slip.medicalOfficerName} (${maskPhoneNumber(slip.medicalOfficerPhone)})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTeal
                        )
                        Text(
                            text = "${strings.attendingAshaLabel} ${slip.ashaName} (${slip.ashaPhone})",
                            fontSize = 10.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onCallMo,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(strings.profileCallMo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onCall108,
                    colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("108", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Arogyasathi Referral Slip", formattedSms)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Referral slip copied to clipboard for SMS/WhatsApp", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkTeal),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(strings.copyButton, fontSize = 10.sp)
                }

                Button(
                    onClick = onSaveToOutbox,
                    colors = ButtonDefaults.buttonColors(containerColor = RoutineGreen),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(strings.outboxButton, fontSize = 10.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.close, color = TextSecondary)
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
