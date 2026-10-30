package org.arogyasathi.app.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.arogyasathi.app.R
import org.arogyasathi.app.data.ArogyasathiRepository
import kotlin.math.roundToInt

// ── Verified evaluation results from live pipeline run (H5 Dev Split, 40 Qs) ──
private data class EvalScore(val label: String, val value: Float, val detail: String = "")

private val DEV_EVAL_SCORES = listOf(
    EvalScore("Primary Score",          0.9735f, "97.35%"),
    EvalScore("Escalation Recall",      1.0000f, "10 / 10"),
    EvalScore("False Escalation Rate",  0.0000f, "0 false"),
    EvalScore("Refusal Accuracy",       1.0000f, "100%"),
    EvalScore("Citation Accuracy",      1.0000f, "100%"),
    EvalScore("Key Fact Coverage",      0.8235f, "82.35%"),
    EvalScore("Visit List F1",          1.0000f, "100%"),
    EvalScore("Visit Priority Accuracy",1.0000f, "100%"),
)

private data class FootprintMetrics(
    val modelSizeMb: Float,
    val voiceSizeMb: Float,
    val ramMb: Float,
    val latencyMs: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMetricsScreen(
    repository: ArogyasathiRepository,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current

    var footprint by remember { mutableStateOf<FootprintMetrics?>(null) }
    var benchmarkError by remember { mutableStateOf<String?>(null) }

    // Live measurement: model size, RAM, CPU latency
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            try {
                // 1. Model size: protocols.json (BM25 corpus)
                val protocolsBytes = context.assets.openFd("protocols.json").use { it.length }
                val modelSizeMb = protocolsBytes / (1024f * 1024f)

                // 2. Voice model size: en-IN ONNX TTS model
                val voiceBytes = context.assets.openFd("voice-packs/en-IN/model.int8.onnx").use { it.length }
                val voiceSizeMb = voiceBytes / (1024f * 1024f)

                // 3. RAM: process PSS via ActivityManager, fallback to heap
                val ramMb = measureRam(context)

                // 4. CPU latency: run askAssistant 5 times, take median
                val timings = LongArray(5)
                for (i in 0 until 5) {
                    val t0 = System.nanoTime()
                    repository.askAssistant("fever child diarrhoea ors treatment")
                    timings[i] = System.nanoTime() - t0
                }
                timings.sort()
                val medianMs = timings[2] / 1_000_000f // median of 5

                footprint = FootprintMetrics(
                    modelSizeMb = modelSizeMb,
                    voiceSizeMb = voiceSizeMb,
                    ramMb = ramMb,
                    latencyMs = medianMs
                )
            } catch (e: Exception) {
                benchmarkError = e.message ?: "Measurement failed"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "System Performance",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = PrimaryTeal
                        )
                        Text(
                            text = "Live device benchmark + H5 evaluation",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrow_back),
                            contentDescription = "Back",
                            tint = PrimaryTeal
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = CanvasBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Section 1: Live Footprint ─────────────────────────────────
            item {
                SectionHeader(
                    icon = R.drawable.ic_analytics,
                    title = "Device Footprint",
                    subtitle = "Measured live on this device right now"
                )
            }

            item {
                if (footprint == null && benchmarkError == null) {
                    BenchmarkLoadingCard()
                } else if (benchmarkError != null) {
                    ErrorCard(benchmarkError!!)
                } else {
                    footprint?.let { fp ->
                        FootprintCards(fp)
                    }
                }
            }

            // ── Section 2: Escalation Gate ────────────────────────────────
            item {
                SectionHeader(
                    icon = R.drawable.ic_emergency,
                    title = "H5 Evaluation — Dev Split",
                    subtitle = "40 questions • arogyasathi-jev-laya (cpu offline)"
                )
            }

            item {
                EscalationGateBadge()
            }

            // ── Section 3: Score Rows ─────────────────────────────────────
            items(DEV_EVAL_SCORES.size) { i ->
                ScoreRow(DEV_EVAL_SCORES[i])
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                EvalFooter()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun measureRam(context: Context): Float {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        mi.totalPss / 1024f // PSS KB → MB
    } catch (e: Exception) {
        val rt = Runtime.getRuntime()
        (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
    }
}

@Composable
private fun SectionHeader(icon: Int, title: String, subtitle: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = PrimaryTeal.copy(alpha = 0.12f),
            modifier = Modifier.size(34.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = icon),
                    contentDescription = null,
                    tint = PrimaryTeal,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
            Text(subtitle, fontSize = 11.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun BenchmarkLoadingCard() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha"
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(color = PrimaryTeal, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            Text(
                text = "Running benchmark on CPU…",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTeal.copy(alpha = alpha)
            )
            Text(
                text = "Querying BM25 engine × 5 runs to measure median latency",
                fontSize = 11.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFEF2F2),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "⚠ Measurement error: $message",
            fontSize = 12.sp,
            color = EmergencyRed,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun FootprintCards(fp: FootprintMetrics) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FootprintCard(
            modifier = Modifier.weight(1f),
            label = "BM25 Corpus",
            value = "%.2f MB".format(fp.modelSizeMb),
            sub = "protocols.json",
            color = PrimaryTeal
        )
        FootprintCard(
            modifier = Modifier.weight(1f),
            label = "TTS Model",
            value = "%.1f MB".format(fp.voiceSizeMb),
            sub = "en-IN ONNX",
            color = Color(0xFF0891B2)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FootprintCard(
            modifier = Modifier.weight(1f),
            label = "Process RAM",
            value = "%.1f MB".format(fp.ramMb),
            sub = "PSS on device",
            color = Color(0xFF7C3AED)
        )
        FootprintCard(
            modifier = Modifier.weight(1f),
            label = "CPU Latency",
            value = if (fp.latencyMs < 1f) "<1 ms" else "%.1f ms".format(fp.latencyMs),
            sub = "median · 5 runs",
            color = RoutineGreen
        )
    }
}

@Composable
private fun FootprintCard(
    modifier: Modifier,
    label: String,
    value: String,
    sub: String,
    color: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(sub, fontSize = 10.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun EscalationGateBadge() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF0FDF4),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Escalation Gate", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Safety gate for danger-sign detection", fontSize = 11.sp, color = TextSecondary)
            }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = RoutineGreen
            ) {
                Text(
                    text = "  ✓ PASS  ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ScoreRow(score: EvalScore) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(score.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(score.detail, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryTeal)
            }
            Spacer(modifier = Modifier.height(6.dp))
            val barColor = when {
                score.value >= 0.95f -> RoutineGreen
                score.value >= 0.80f -> PrimaryTeal
                score.value == 0.0f -> RoutineGreen  // 0% false escalation = perfect
                else -> Color(0xFFF59E0B)
            }
            // For false escalation rate: 0 is best, so invert bar fill
            val fill = if (score.label == "False Escalation Rate") 1f else score.value
            LinearProgressIndicator(
                progress = { fill },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = barColor,
                trackColor = Color(0xFFE2E8F0)
            )
        }
    }
}

@Composable
private fun EvalFooter() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Model: arogyasathi-jev-laya (cpu offline)",
                fontSize = 11.sp,
                color = TextSecondary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Eval: H5 Dev Split • 40 questions • 0 missed escalations",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Text(
                text = "Device footprint measured live at screen load time",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}
