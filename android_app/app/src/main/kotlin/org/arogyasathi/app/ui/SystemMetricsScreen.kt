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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private data class DeviceMetrics(
    val modelSizeMb: Float,
    val voiceSizeMb: Float,
    val ramMb: Float,
    val latencyMs: Float
)

/** Read an asset's true byte count — works even when Android has compressed it in the APK. */
private fun assetSizeMb(context: Context, path: String): Float {
    var bytes = 0L
    val buf = ByteArray(8192)
    context.assets.open(path).use { stream ->
        var n: Int
        while (stream.read(buf).also { n = it } != -1) bytes += n
    }
    return bytes / (1024f * 1024f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemMetricsScreen(
    repository: ArogyasathiRepository,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current

    var metrics by remember { mutableStateOf<DeviceMetrics?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            try {
                // 1. BM25 corpus size (protocols.json — compressed in APK, read via stream)
                val modelSizeMb = assetSizeMb(context, "protocols.json")

                // 2. TTS ONNX model size
                val voiceSizeMb = assetSizeMb(context, "voice-packs/en-IN/model.int8.onnx")

                // 3. Process RAM via PSS (most accurate; fallback to heap diff)
                val ramMb = try {
                    val mi = Debug.MemoryInfo()
                    Debug.getMemoryInfo(mi)
                    mi.totalPss / 1024f
                } catch (e: Exception) {
                    val rt = Runtime.getRuntime()
                    (rt.totalMemory() - rt.freeMemory()) / (1024f * 1024f)
                }

                // 4. CPU latency: 5 warm runs, take median
                val timings = LongArray(5)
                for (i in 0 until 5) {
                    val t0 = System.nanoTime()
                    repository.askAssistant("fever child diarrhoea ors treatment")
                    timings[i] = System.nanoTime() - t0
                }
                timings.sort()
                val latencyMs = timings[2] / 1_000_000f

                metrics = DeviceMetrics(modelSizeMb, voiceSizeMb, ramMb, latencyMs)
            } catch (e: Exception) {
                error = e.message ?: "Measurement failed"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Device Metrics",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = PrimaryTeal
                        )
                        Text(
                            "Live measurements on this device",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                error != null -> ErrorCard(error!!)
                metrics == null -> LoadingCard()
                else -> {
                    val m = metrics!!
                    MetricCard(
                        label = "BM25 Corpus",
                        value = "%.2f MB".format(m.modelSizeMb),
                        sub = "protocols.json — knowledge index",
                        color = PrimaryTeal,
                        icon = R.drawable.ic_analytics
                    )
                    MetricCard(
                        label = "TTS Voice Model",
                        value = "%.1f MB".format(m.voiceSizeMb),
                        sub = "en-IN ONNX int8 — speech synthesis",
                        color = Color(0xFF0891B2),
                        icon = R.drawable.ic_analytics
                    )
                    MetricCard(
                        label = "Process RAM (PSS)",
                        value = "%.1f MB".format(m.ramMb),
                        sub = "Proportional Set Size — all loaded libs",
                        color = Color(0xFF7C3AED),
                        icon = R.drawable.ic_analytics
                    )
                    MetricCard(
                        label = "CPU Response Time",
                        value = if (m.latencyMs < 1f) "<1 ms" else "%.1f ms".format(m.latencyMs),
                        sub = "BM25 query · median of 5 runs · no GPU",
                        color = RoutineGreen,
                        icon = R.drawable.ic_analytics
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingCard() {
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
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(color = PrimaryTeal, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
            Text(
                "Running benchmark…",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTeal.copy(alpha = alpha)
            )
            Text(
                "Reading asset sizes and running BM25 query × 5 to measure CPU latency",
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
            "⚠ $message",
            fontSize = 12.sp,
            color = EmergencyRed,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, sub: String, color: Color, icon: Int) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = color)
                Text(sub, fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}
