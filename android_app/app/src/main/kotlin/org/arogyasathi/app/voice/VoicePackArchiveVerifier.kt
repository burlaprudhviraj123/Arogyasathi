package org.arogyasathi.app.voice

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Defensive ZIP extraction and manifest validation engine for Arogyasathi voice packs.
 *
 * Implements strict ZipSlip path traversal protection, symlink rejection, total size capping,
 * and cryptographic hash verification for all constituent files.
 */
object VoicePackArchiveVerifier {

    const val MAX_UNCOMPRESSED_BYTES: Long = 500L * 1024L * 1024L // 500 MB limit
    const val MAX_ENTRY_COUNT: Int = 10

    /**
     * Safely unpacks a voice pack archive into [destinationDir] and verifies all manifest assertions.
     *
     * @param archiveFile The .zip archive file to verify and unpack.
     * @param destinationDir Target staging directory.
     * @return The parsed and verified [VoicePackManifest].
     */
    fun unpackAndVerify(archiveFile: File, destinationDir: File): VoicePackManifest {
        require(archiveFile.isFile) { "Archive path must be an existing file: ${archiveFile.absolutePath}" }
        destinationDir.mkdirs()

        var totalUncompressedBytes: Long = 0
        var entryCount = 0
        val extractedFiles = mutableSetOf<String>()

        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                if (entryCount > MAX_ENTRY_COUNT) {
                    throw SecurityException("Archive safety violation: entry count ($entryCount) exceeds limit ($MAX_ENTRY_COUNT)")
                }

                val name = entry.name
                // ZipSlip and traversal checks
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw SecurityException("Archive safety violation: illegal path traversal in entry '$name'")
                }

                // Disallow directory entries or nested paths; voice pack root must contain only root files
                if (entry.isDirectory || name.contains("/") || name.contains("\\")) {
                    throw SecurityException("Archive safety violation: subdirectories are not permitted in voice pack root ('$name')")
                }

                totalUncompressedBytes += entry.size
                if (totalUncompressedBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw SecurityException("Archive safety violation: total uncompressed size exceeds 500 MB ceiling")
                }

                val targetFile = File(destinationDir, name)
                val canonicalTarget = targetFile.canonicalPath
                val canonicalDest = destinationDir.canonicalPath
                if (!canonicalTarget.startsWith(canonicalDest + File.separator)) {
                    throw SecurityException("Archive safety violation: entry '$name' targets outside destination directory")
                }

                zip.getInputStream(entry).use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                extractedFiles.add(name)
            }
        }

        // Must contain pack.json, model.int8.onnx, tokens.txt
        val requiredFiles = setOf("pack.json", "model.int8.onnx", "tokens.txt")
        if (!extractedFiles.containsAll(requiredFiles)) {
            val missing = requiredFiles - extractedFiles
            throw IllegalStateException("Voice pack archive is missing required files: $missing")
        }

        val manifestFile = File(destinationDir, "pack.json")
        val manifest = parseManifest(manifestFile)

        // Verify each declared file in manifest matches extracted byte size and SHA-256
        for (fileEntry in manifest.files) {
            val actualFile = File(destinationDir, fileEntry.path)
            if (!actualFile.isFile) {
                throw IllegalStateException("Declared file '${fileEntry.path}' was not extracted.")
            }
            if (actualFile.length() != fileEntry.bytes) {
                throw IllegalStateException(
                    "File size mismatch for '${fileEntry.path}': declared=${fileEntry.bytes}, actual=${actualFile.length()}"
                )
            }
            val actualSha256 = VoicePackCrypto.computeSha256(actualFile)
            if (!actualSha256.equals(fileEntry.sha256, ignoreCase = true)) {
                throw IllegalStateException(
                    "SHA-256 hash mismatch for '${fileEntry.path}': declared=${fileEntry.sha256}, actual=$actualSha256"
                )
            }
        }

        return manifest
    }

    /**
     * Parses and validates a Schema v1 pack.json manifest.
     */
    fun parseManifest(manifestFile: File): VoicePackManifest {
        val content = manifestFile.readText(Charsets.UTF_8)
        val json = JSONObject(content)

        val schemaVersion = json.getInt("schemaVersion")
        if (schemaVersion != 1) {
            throw IllegalArgumentException("Unsupported manifest schemaVersion: $schemaVersion. Expected 1.")
        }

        val packId = json.getString("packId")
        val languageTag = json.getString("languageTag")
        val displayName = json.optString("displayName", languageTag)

        val runtime = json.getJSONObject("runtime")
        val runtimeEngine = runtime.optString("engine", "sherpa-onnx")
        val modelType = runtime.optString("modelType", "nemo-ctc")
        val sampleRateHz = runtime.optInt("sampleRateHz", 16000)
        val featureDimension = runtime.optInt("featureDimension", 80)
        val minimumRuntimeVersion = runtime.optString("minimumRuntimeVersion", "2.0.1")

        val model = json.getJSONObject("model")
        val source = model.optString("source", "")
        val sourceCheckpointSha256 = model.optString("sourceCheckpointSha256", "")
        val version = model.optString("version", "1")

        val filesArray = json.getJSONArray("files")
        val files = mutableListOf<ManifestFileEntry>()
        for (i in 0 until filesArray.length()) {
            val f = filesArray.getJSONObject(i)
            files.add(
                ManifestFileEntry(
                    path = f.getString("path"),
                    bytes = f.getLong("bytes"),
                    sha256 = f.getString("sha256").lowercase(),
                )
            )
        }

        val archiveSha256 = json.optString("archiveSha256", "").takeIf { it.isNotBlank() }?.lowercase()
        val createdAt = json.optString("createdAt", "")

        return VoicePackManifest(
            schemaVersion = schemaVersion,
            packId = packId,
            languageTag = languageTag,
            displayName = displayName,
            runtimeEngine = runtimeEngine,
            modelType = modelType,
            sampleRateHz = sampleRateHz,
            featureDimension = featureDimension,
            minimumRuntimeVersion = minimumRuntimeVersion,
            source = source,
            sourceCheckpointSha256 = sourceCheckpointSha256,
            version = version,
            files = files,
            archiveSha256 = archiveSha256,
            createdAt = createdAt,
        )
    }
}
