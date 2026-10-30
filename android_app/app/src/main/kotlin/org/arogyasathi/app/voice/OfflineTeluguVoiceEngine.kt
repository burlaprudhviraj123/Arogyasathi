package org.arogyasathi.app.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * File-backed, fully local Telugu ASR. The pack is valid only when all three
 * files from the exporter are present in files/voice-packs/te-IN.
 */
class OfflineTeluguVoiceEngine(private val context: Context) : AutoCloseable {
    companion object {
        const val SAMPLE_RATE = 16_000
        private const val PACK_LANGUAGE = "te-IN"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val recording = AtomicBoolean(false)
    private val samples = ArrayList<ShortArray>()
    private var recorder: AudioRecord? = null
    private var recognizer: OfflineRecognizer? = null

    private val packDirectory: File
        get() = File(context.filesDir, "voice-packs/$PACK_LANGUAGE")

    /** Copies the APK-bundled Telugu pack once into private storage for ORT. */
    fun installBundledPackIfNeeded() {
        if (isInstalled()) return
        val destination = packDirectory
        destination.mkdirs()
        listOf("model.int8.onnx", "tokens.txt", "pack.json").forEach { name ->
            val target = File(destination, name)
            val temporary = File(destination, "$name.partial")
            context.assets.open("voice-packs/$PACK_LANGUAGE/$name").use { input ->
                FileOutputStream(temporary).use { output -> input.copyTo(output) }
            }
            check(temporary.renameTo(target)) { "Could not install bundled Telugu voice pack." }
        }
        check(isInstalled()) { "Bundled Telugu voice pack is incomplete." }
    }

    fun isInstalled(): Boolean = listOf("model.int8.onnx", "tokens.txt", "pack.json")
        .all { File(packDirectory, it).isFile }

    /**
     * Starts capturing 16 kHz mono PCM. Call [stop] to decode the capture. No
     * speech-recognition intent, network request, or system language service is used.
     */
    fun start(onError: (String) -> Unit) {
        if (!isInstalled()) {
            onError("Telugu offline voice pack is not installed.")
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
            recorder?.release()
            recorder = null
            mainHandler.post { onError(error.message ?: "Could not start the microphone.") }
        }
    }

    fun stop(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val activeRecorder = recorder ?: return
        if (!recording.compareAndSet(true, false)) return
        executor.execute {
            try {
                activeRecorder.stop()
                activeRecorder.release()
                recorder = null
                val chunks = synchronized(samples) { samples.toList() }
                val audio = FloatArray(chunks.sumOf { it.size })
                var index = 0
                chunks.forEach { chunk -> chunk.forEach { sample -> audio[index++] = sample / 32768f } }
                val localRecognizer = recognizer ?: createRecognizer().also { recognizer = it }
                val stream = localRecognizer.createStream()
                stream.acceptWaveform(audio, SAMPLE_RATE)
                localRecognizer.decode(stream)
                val text = localRecognizer.getResult(stream).text.trim()
                stream.release()
                mainHandler.post { onResult(text) }
            } catch (error: Throwable) {
                mainHandler.post { onError(error.message ?: "Offline Telugu recognition failed.") }
            }
        }
    }

    private fun createRecognizer(): OfflineRecognizer {
        val model = File(packDirectory, "model.int8.onnx")
        val tokens = File(packDirectory, "tokens.txt")
        return OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                modelConfig = OfflineModelConfig(
                    nemo = OfflineNemoEncDecCtcModelConfig(model = model.absolutePath),
                    tokens = tokens.absolutePath,
                    numThreads = 2,
                    provider = "cpu",
                ),
            )
        )
    }

    override fun close() {
        recording.set(false)
        recorder?.runCatching { stop(); release() }
        recorder = null
        recognizer?.release()
        recognizer = null
        executor.shutdownNow()
    }
}
