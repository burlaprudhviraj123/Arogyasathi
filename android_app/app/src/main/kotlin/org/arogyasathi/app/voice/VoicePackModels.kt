package org.arogyasathi.app.voice

/**
 * Lifecycle states of an offline voice pack in Arogyasathi.
 *
 * State is derived strictly from verified filesystem state and recognizer initialization;
 * it is never persisted as a bare Boolean in SharedPreferences.
 */
sealed class VoicePackState {
    object NotInstalled : VoicePackState()
    data class Queued(val languageTag: String) : VoicePackState()
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val progressPercent: Float,
    ) : VoicePackState()
    object Verifying : VoicePackState()
    object Installing : VoicePackState()
    data class Ready(
        val version: String,
        val modelBytes: Long,
        val isBundled: Boolean = false,
    ) : VoicePackState()
    data class UpdateAvailable(
        val currentVersion: String,
        val newVersion: String,
        val newPackBytes: Long,
    ) : VoicePackState()
    data class Failed(
        val reason: String,
        val canRetry: Boolean = true,
    ) : VoicePackState()
}

/**
 * Entry from the cryptographically signed remote catalogue.
 */
data class CatalogueEntry(
    val packId: String,
    val languageTag: String,
    val displayName: String,
    val version: String,
    val downloadUrl: String,
    val bytes: Long,
    val sha256: String,
    val minAppVersionCode: Int,
    val runtime: String,
)

/**
 * File declaration inside a Schema v1 pack.json manifest.
 */
data class ManifestFileEntry(
    val path: String,
    val bytes: Long,
    val sha256: String,
)

/**
 * Schema v1 manifest contained inside every voice pack archive.
 */
data class VoicePackManifest(
    val schemaVersion: Int,
    val packId: String,
    val languageTag: String,
    val displayName: String,
    val runtimeEngine: String,
    val modelType: String,
    val sampleRateHz: Int,
    val featureDimension: Int,
    val minimumRuntimeVersion: String,
    val source: String,
    val sourceCheckpointSha256: String,
    val version: String,
    val files: List<ManifestFileEntry>,
    val archiveSha256: String?,
    val createdAt: String,
)

/**
 * Local installation record written atomically upon successful verification and staging.
 */
data class InstallationRecord(
    val packId: String,
    val languageTag: String,
    val version: String,
    val modelSha256: String,
    val modelBytes: Long,
    val tokensSha256: String,
    val tokensBytes: Long,
    val archiveSha256: String,
    val installedAtTimestampMs: Long,
    val runtimeEngine: String = "sherpa-onnx-2.0.1",
)
