package org.arogyasathi.app.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.arogyasathi.app.R
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
    val result: AssistantResult? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Composable
fun AssistantScreen(
    repository: ArogyasathiRepository,
    initialQuery: String? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var queryText by remember { mutableStateOf("") }
    var isSpeaking by remember { mutableStateOf(false) }

    // Chat conversation history state
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = "Namaste Didi! I am Arogyasathi (आरोग्यसाथी), your offline clinical protocol chatbot companion.\n\nAsk me any maternal, newborn, child health, or NCD question in Hindi, English, or Hinglish — or tap an emergency topic below."
            )
        )
    }

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
            speech?.stop()
            speech?.shutdown()
        }
    }

    fun submitQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return

        // 1. Add user message
        messages.add(
            ChatMessage(
                sender = MessageSender.USER,
                text = trimmed
            )
        )
        queryText = ""

        // 2. Query clinical engine (JEV deterministic gate + Laya retrieval)
        val assistantResult = repository.askAssistant(trimmed)

        // 3. Add bot reply
        messages.add(
            ChatMessage(
                sender = MessageSender.ASSISTANT,
                text = assistantResult.answer,
                result = assistantResult
            )
        )

        // Stop previous speech if running
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
        }

        // Scroll to newest message
        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Handle incoming initial queries from Today or Families screen
    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            submitQuery(initialQuery)
        }
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
        }
    }

    val quickTopics = listOf(
        "Sugar patient behosh pada hai",
        "Bachche ko bukhar aur jhatke aa rahe hain",
        "Delivery ke baad heavy bleeding ho rahi hai",
        "6 weeks vaccination tika schedule",
        "3rd ANC checkup kab hota hai",
        "Paracetamol syrup dosage for 2yo"
    )

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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_emergency),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "EMERGENCY HOTLINE 108 / 102",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Direct ambulance & CHC referral dispatch",
                            fontSize = 11.sp,
                            color = Color(0xFFFFCDD2)
                        )
                    }
                }
                Button(
                    onClick = { dialEmergency(context, "108") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "CALL 108",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = EmergencyRed
                    )
                }
            }
        }

        // Quick Suggestion Chips Carousel
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quickTopics) { topic ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, BorderSubtle),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)),
                    onClick = { submitQuery(topic) }
                ) {
                    Text(
                        text = topic,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Divider(color = BorderSubtle, thickness = 0.5.dp)

        // Chat Message Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatBubble(
                    message = message,
                    onPlayTts = { text ->
                        if (isSpeaking) {
                            tts?.stop()
                            isSpeaking = false
                        } else {
                            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "arogyasathi_tts")
                            isSpeaking = true
                        }
                    },
                    onCall108 = { dialEmergency(context, "108") }
                )
            }
        }

        // Bottom Chat Input Bar
        Surface(
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 76.dp) // Room for bottom nav bar
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Speech-to-Text Button
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "बोलें — Ask clinical question (Hindi/English)")
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFFE6F4F1), CircleShape)
                        .semantics { contentDescription = "Voice input in Hindi or English" }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_mic),
                        contentDescription = "Voice input",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text Input Field
                OutlinedTextField(
                    value = queryText,
                    onValueChange = { queryText = it },
                    placeholder = { Text("Ask clinical protocol question...", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = CanvasBackground,
                        unfocusedContainerColor = CanvasBackground,
                        focusedBorderColor = PrimaryTeal,
                        unfocusedBorderColor = BorderSubtle
                    ),
                    maxLines = 3
                )

                // Send Button
                IconButton(
                    onClick = { submitQuery(queryText) },
                    enabled = queryText.trim().isNotEmpty(),
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            if (queryText.trim().isNotEmpty()) PrimaryTeal else Color(0xFFCBD5E1),
                            CircleShape
                        )
                        .semantics { contentDescription = "Send message to Arogyasathi chatbot" }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send),
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onPlayTts: (String) -> Unit,
    onCall108: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val res = message.result

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Bot Avatar
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape),
                color = PrimaryTeal
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_assistant),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (isUser) {
                // User Message Bubble
                Surface(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    color = PrimaryTeal,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            } else {
                // Assistant Message Bubble
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
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Badge Strip
                        if (isEscalation) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = EmergencyRed,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                        text = "IMMEDIATE DANGER SIGN — REFER NOW",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                                        text = "OUT OF SCOPE — DOCTOR REFERRAL",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Clinical Text
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = TextPrimary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Footer with Citation & Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (res != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE6F4F1)
                                ) {
                                    Text(
                                        text = "Citation: ${res.citedSection}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DarkTeal,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Listen via TTS
                                IconButton(
                                    onClick = { onPlayTts(message.text) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_speaker),
                                        contentDescription = "Read aloud",
                                        tint = PrimaryTeal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // If Danger Sign: Immediate 108 Call Button
                                if (isEscalation) {
                                    Button(
                                        onClick = onCall108,
                                        colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Call 108", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Preview(showBackground = true)
@Composable
fun AssistantChatScreenPreview() {
    ArogyasathiTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ChatBubble(
                message = ChatMessage(
                    sender = MessageSender.USER,
                    text = "sugar patient behosh pada hai"
                ),
                onPlayTts = {},
                onCall108 = {}
            )
            Spacer(modifier = Modifier.height(12.dp))
            ChatBubble(
                message = ChatMessage(
                    sender = MessageSender.ASSISTANT,
                    text = "Unconscious patient: refer immediately (call 108). Give nothing by mouth (ESC-6.3, ESC-1.3).",
                    result = AssistantResult(
                        answer = "Unconscious patient: refer immediately (call 108). Give nothing by mouth (ESC-6.3, ESC-1.3).",
                        citedSection = "ESC-6.3",
                        isEscalation = true,
                        isRefusal = false,
                        triageLevel = "EMERGENCY"
                    )
                ),
                onPlayTts = {},
                onCall108 = {}
            )
        }
    }
}
