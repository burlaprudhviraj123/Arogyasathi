package org.arogyasathi.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.arogyasathi.app.data.ArogyasathiRepository
import org.arogyasathi.app.data.AssistantResult
import java.util.Locale

@Composable
fun AssistantScreen(
    repository: ArogyasathiRepository,
    initialQuery: String? = null
) {
    val context = LocalContext.current
    var queryText by remember { mutableStateOf(initialQuery ?: "") }
    var result by remember { mutableStateOf<AssistantResult?>(null) }
    var isSpeaking by remember { mutableStateOf(false) }

    // TextToSpeech setup
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

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) {
            queryText = initialQuery
            result = repository.askAssistant(initialQuery)
        }
    }

    fun submitQuery(q: String) {
        val trimmed = q.trim()
        if (trimmed.isNotEmpty()) {
            result = repository.askAssistant(trimmed)
            if (isSpeaking) {
                tts?.stop()
                isSpeaking = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CanvasBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 80.dp) // Leave room for floating bottom nav
    ) {
        // Red Emergency Dial 108 Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = EmergencyRed),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🚨 EMERGENCY AMBULANCE",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Direct hotline to CHC / 108 / 102",
                        fontSize = 12.sp,
                        color = Color(0xFFFFCDD2)
                    )
                }
                Button(
                    onClick = { dialEmergency(context, "108") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "CALL 108",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = EmergencyRed
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Clinical Query Input Field
        OutlinedTextField(
            value = queryText,
            onValueChange = { queryText = it },
            placeholder = { Text("Ask clinical protocol question (Hindi, Hinglish, English)...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceCrisp,
                unfocusedContainerColor = SurfaceCrisp
            ),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { submitQuery(queryText) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Consult Arogyasathi Protocols", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Clinical Questions Chips
        Text(
            text = "Frequently Checked Protocols",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))

        val promptPills = listOf(
            "Bleeding in pregnancy" to "8 mahine ki pregnancy me bleeding ho rahi hai",
            "Child fits / jhatke" to "bachche ko jhatke aa rahe hain aur behosh hai",
            "Baby cold to touch" to "navjaat bachcha chhune pe thanda lag raha hai",
            "Diarrhoea ORS dosage" to "1 saal ke bachche ko dast me kitna ORS dena hai",
            "Pneumonia fast breathing" to "3 mahine ke bachche ki saans 55 per minute hai",
            "High BP crisis" to "BP 185/115 aaya hai kya karein",
            "Paracetamol syrup dose" to "bachche ko bukhar me kaun sa paracetamol syrup du"
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(promptPills) { (label, q) ->
                SuggestionChip(
                    onClick = {
                        queryText = q
                        submitQuery(q)
                    },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = SurfaceCrisp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Result Card
        result?.let { res ->
            val cardBg = when (res.triageLevel) {
                "EMERGENCY" -> Color(0xFFFFEBEE)
                "OUT_OF_SCOPE" -> Color(0xFFFFF8E1)
                else -> SurfaceCrisp
            }
            val accentColor = when (res.triageLevel) {
                "EMERGENCY" -> EmergencyRed
                "OUT_OF_SCOPE" -> HighPriorityOrange
                else -> PrimaryTeal
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Banner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (res.triageLevel) {
                                "EMERGENCY" -> "⚠️ DANGER SIGN — IMMEDIATE REFERRAL"
                                "OUT_OF_SCOPE" -> "🛡️ OUT OF PROTOCOL SCOPE"
                                else -> "📋 VERIFIED CLINICAL PROTOCOL"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = accentColor
                        )

                        // TTS Audio Play/Stop Button
                        IconButton(
                            onClick = {
                                if (isSpeaking) {
                                    tts?.stop()
                                    isSpeaking = false
                                } else {
                                    tts?.speak(res.answer, TextToSpeech.QUEUE_FLUSH, null, "ArogyasathiTTS")
                                    isSpeaking = true
                                }
                            }
                        ) {
                            Text(
                                text = if (isSpeaking) "⏹️ Stop" else "🔊 Listen",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Answer body text
                    Text(
                        text = res.answer,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextPrimary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Citation Chip & Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "Citation: ${res.citedSection}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (res.isEscalation) {
                            Button(
                                onClick = { dialEmergency(context, "108") },
                                colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Call 108 Now", color = Color.White, fontSize = 12.sp)
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
