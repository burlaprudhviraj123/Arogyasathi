package org.arogyasathi.app.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Universal offline voice recognition engine powered by sherpa-onnx and AudioRecord.
 *
 * Dynamically binds to any verified voice pack managed by [VoicePackManager]
 * (e.g. te-IN, en-IN, hi-IN, etc.). Captures raw 16 kHz mono 16-bit PCM and transcribes
 * locally on-device with zero network access, zero Google Speech services, and zero RecognizerIntent.
 */
class OfflineVoiceEngine(
    private val context: Context,
    private val voicePackManager: VoicePackManager = VoicePackManager(context),
) : AutoCloseable {

    companion object {
        const val SAMPLE_RATE = 16_000
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recording = AtomicBoolean(false)
    private val samples = ArrayList<ShortArray>()
    private var recorder: AudioRecord? = null

    // Cache initialized recognizers per languageTag to avoid re-creation latency
    private val recognizerCache = ConcurrentHashMap<String, OfflineRecognizer>()

    fun isLanguageReady(languageTag: String): Boolean {
        return voicePackManager.isPackReady(languageTag)
    }

    /**
     * Starts capturing 16 kHz mono PCM from the device microphone.
     */
    fun startRecording(
        languageTag: String,
        onError: (String) -> Unit,
    ) {
        if (!isLanguageReady(languageTag)) {
            onError("Offline voice pack for '$languageTag' is not installed or verified.")
            return
        }
        if (!recording.compareAndSet(false, true)) return

        try {
            val minSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            require(minSize > 0) { "This device cannot record 16 kHz mono PCM." }
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minSize * 2, SAMPLE_RATE / 2),
            ).also {
                check(it.state == AudioRecord.STATE_INITIALIZED) { "Microphone initialization failed." }
                it.startRecording()
            }

            synchronized(samples) { samples.clear() }
            executor.execute {
                val buffer = ShortArray(1_600)
                while (recording.get()) {
                    val read = recorder?.read(buffer, 0, buffer.size) ?: break
                    if (read > 0) synchronized(samples) { samples += buffer.copyOf(read) }
                }
            }
        } catch (error: Throwable) {
            recording.set(false)
            recorder?.runCatching { stop(); release() }
            recorder = null
            mainHandler.post { onError(error.message ?: "Could not start microphone recording.") }
        }
    }

    /**
     * Stops capture, performs on-device sherpa-onnx inference, and invokes [onResult] with transcription.
     */
    fun stopRecording(
        languageTag: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val activeRecorder = recorder ?: return
        if (!recording.compareAndSet(true, false)) return

        executor.execute {
            try {
                activeRecorder.stop()
                activeRecorder.release()
                recorder = null

                val chunks = synchronized(samples) { samples.toList() }
                if (chunks.isEmpty()) {
                    mainHandler.post { onResult("") }
                    return@execute
                }

                val audio = FloatArray(chunks.sumOf { it.size })
                var index = 0
                chunks.forEach { chunk -> chunk.forEach { sample -> audio[index++] = sample / 32768f } }

                val recognizer = getOrCreateRecognizer(languageTag)
                val stream = recognizer.createStream()
                stream.acceptWaveform(audio, SAMPLE_RATE)
                recognizer.decode(stream)
                val text = recognizer.getResult(stream).text.trim()
                stream.release()

                mainHandler.post { onResult(text) }
            } catch (error: Throwable) {
                mainHandler.post { onError(error.message ?: "Offline speech recognition failed.") }
            }
        }
    }

    private fun getOrCreateRecognizer(languageTag: String): OfflineRecognizer {
        return recognizerCache.computeIfAbsent(languageTag) {
            val packDir = voicePackManager.getActivePackDirectory(languageTag)
                ?: throw IllegalStateException("No active verified voice pack found for $languageTag")

            val modelFile = File(packDir, "model.int8.onnx")
            val tokensFile = File(packDir, "tokens.txt")

            OfflineRecognizer(
                config = OfflineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OfflineModelConfig(
                        nemo = OfflineNemoEncDecCtcModelConfig(model = modelFile.absolutePath),
                        tokens = tokensFile.absolutePath,
                        numThreads = 2,
                        provider = "cpu",
                    ),
                )
            )
        }
    }

    override fun close() {
        recording.set(false)
        recorder?.runCatching { stop(); release() }
        recorder = null
        recognizerCache.values.forEach { it.release() }
        recognizerCache.clear()
        executor.shutdownNow()
    }
}

