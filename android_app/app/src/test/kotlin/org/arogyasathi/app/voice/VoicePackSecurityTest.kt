package org.arogyasathi.app.voice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoicePackSecurityTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testComputeSha256MatchesStandardKnownHash() {
        // SHA-256("test") = 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
        val stream = ByteArrayInputStream("test".toByteArray(Charsets.UTF_8))
        val hash = VoicePackCrypto.computeSha256(stream)
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", hash)
    }

    @Test
    fun testCatalogueVerificationPassesWithValidSignature() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()

        val pubKeyB64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)
        val payload = """{"schemaVersion":1,"packs":[{"packId":"test-pack","languageTag":"te-IN","version":"1.0.0"}]}"""

        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        val sigB64 = java.util.Base64.getEncoder().encodeToString(signer.sign())

        val envelope = JSONObject().apply {
            put("payload", payload)
            put("signature", sigB64)
            put("algorithm", "SHA256withECDSA")
            put("keyId", "test-key")
        }.toString()

        val verified = VoicePackCrypto.verifyCatalogueEnvelope(envelope, pubKeyB64)
        assertTrue(verified.isSuccess)
        assertEquals(payload, verified.getOrNull())
    }

    @Test
    fun testCatalogueVerificationFailsOnTamperedPayload() {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()
        val pubKeyB64 = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded)

        val payload = """{"schemaVersion":1,"packs":[{"packId":"legit"}]}"""
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        val sigB64 = java.util.Base64.getEncoder().encodeToString(signer.sign())

        // Attacker tampers payload
        val tamperedPayload = """{"schemaVersion":1,"packs":[{"packId":"malicious"}]}"""
        val envelope = JSONObject().apply {
            put("payload", tamperedPayload)
            put("signature", sigB64)
            put("algorithm", "SHA256withECDSA")
        }.toString()

        val verified = VoicePackCrypto.verifyCatalogueEnvelope(envelope, pubKeyB64)
        assertTrue(verified.isFailure)
        assertTrue(verified.exceptionOrNull() is SecurityException)
    }

    @Test
    fun testCatalogueVerificationFailsOnUntrustedAlgorithm() {
        val envelope = JSONObject().apply {
            put("payload", "{}")
            put("signature", "invalid")
            put("algorithm", "RSAwithSHA256")
        }.toString()

        val result = VoicePackCrypto.verifyCatalogueEnvelope(envelope)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Untrusted signature algorithm") == true)
    }

    @Test
    fun testZipSlipPathTraversalIsRejected() {
        val zipFile = tempFolder.newFile("evil_slip.zip")
        val stagingDir = tempFolder.newFolder("staging")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../evil.txt"))
            zos.write("malicious".toByteArray())
            zos.closeEntry()
        }

        try {
            VoicePackArchiveVerifier.unpackAndVerify(zipFile, stagingDir)
            fail("Expected SecurityException for ZipSlip path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("illegal path traversal") == true)
        }
    }

    @Test
    fun testSubdirectoriesInArchiveAreRejected() {
        val zipFile = tempFolder.newFile("nested.zip")
        val stagingDir = tempFolder.newFolder("staging_nested")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("sub/nested.txt"))
            zos.write("nested".toByteArray())
            zos.closeEntry()
        }

        try {
            VoicePackArchiveVerifier.unpackAndVerify(zipFile, stagingDir)
            fail("Expected SecurityException for subdirectories")
        } catch (e: SecurityException) {
            assertTrue(e.message?.contains("subdirectories are not permitted") == true)
        }
    }

    @Test
    fun testMissingRequiredFilesIsRejected() {
        val zipFile = tempFolder.newFile("incomplete.zip")
        val stagingDir = tempFolder.newFolder("staging_incomplete")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("pack.json"))
            zos.write("{}".toByteArray())
            zos.closeEntry()
        }

        try {
            VoicePackArchiveVerifier.unpackAndVerify(zipFile, stagingDir)
            fail("Expected IllegalStateException for missing files")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("missing required files") == true)
        }
    }

    @Test
    fun testValidArchiveWithManifestUnpacksAndVerifies() {
        val zipFile = tempFolder.newFile("valid_pack.zip")
        val stagingDir = tempFolder.newFolder("staging_valid")

        val modelContent = "MODEL_INT8_BYTES".toByteArray(Charsets.UTF_8)
        val tokensContent = "<blk>\na\nb\n".toByteArray(Charsets.UTF_8)

        val modelSha = VoicePackCrypto.computeSha256(ByteArrayInputStream(modelContent))
        val tokensSha = VoicePackCrypto.computeSha256(ByteArrayInputStream(tokensContent))

        val manifestJson = """
        {
          "schemaVersion": 1,
          "packId": "test-te-ctc",
          "languageTag": "te-IN",
          "displayName": "Telugu",
          "runtime": {
            "engine": "sherpa-onnx",
            "modelType": "nemo-ctc",
            "sampleRateHz": 16000,
            "featureDimension": 80,
            "minimumRuntimeVersion": "2.0.1"
          },
          "model": {
            "source": "AI4Bharat IndicConformer",
            "sourceCheckpointSha256": "fake-src-sha",
            "version": 1
          },
          "files": [
            {
              "path": "model.int8.onnx",
              "bytes": ${modelContent.size},
              "sha256": "$modelSha"
            },
            {
              "path": "tokens.txt",
              "bytes": ${tokensContent.size},
              "sha256": "$tokensSha"
            }
          ],
          "archiveSha256": "fake-archive-sha",
          "createdAt": "2026-10-31T00:00:00Z"
        }
        """.trimIndent()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("pack.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("model.int8.onnx"))
            zos.write(modelContent)
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("tokens.txt"))
            zos.write(tokensContent)
            zos.closeEntry()
        }

        val manifest = VoicePackArchiveVerifier.unpackAndVerify(zipFile, stagingDir)
        assertNotNull(manifest)
        assertEquals("test-te-ctc", manifest.packId)
        assertEquals("te-IN", manifest.languageTag)
        assertEquals(2, manifest.files.size)

        // Verify unpacked files exist
        assertTrue(File(stagingDir, "model.int8.onnx").isFile)
        assertTrue(File(stagingDir, "tokens.txt").isFile)
        assertTrue(File(stagingDir, "pack.json").isFile)
    }
}
