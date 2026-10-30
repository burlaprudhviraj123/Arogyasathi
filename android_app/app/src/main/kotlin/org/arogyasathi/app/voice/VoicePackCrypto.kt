package org.arogyasathi.app.voice

import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/**
 * Cryptographic utilities for Arogyasathi voice pack integrity and catalogue verification.
 *
 * Uses ECDSA NIST P-256 (SHA256withECDSA) with X.509 SubjectPublicKeyInfo format,
 * running directly on standard Android JCA primitives without external dependencies.
 */
object VoicePackCrypto {

    /**
     * Authoritative offline-held public key embedded in the app.
     * Corresponds to keys/catalogue_private_key.pem.
     */
    const val DEFAULT_TRUSTED_PUBLIC_KEY_B64: String =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8HCaz0+N+y6G4PYuI9ke4pjUeEqtN75yhgCzOQJIJBBs4oZMW9rNNxufe7woehUAjuLVGuYohLn3TfB3tRnrFA=="

    const val EXPECTED_ALGORITHM = "SHA256withECDSA"

    /**
     * Decodes Base64 safely on both Android devices and standard JVM testing environments.
     */
    fun decodeBase64(input: String): ByteArray {
        val clean = input.replace("\n", "").replace("\r", "").replace(" ", "").trim()
        return try {
            java.util.Base64.getDecoder().decode(clean)
        } catch (e: Throwable) {
            android.util.Base64.decode(clean, android.util.Base64.DEFAULT)
        }
    }

    /**
     * Decodes a Base64 X.509 DER public key into a JCA [PublicKey].
     */
    fun decodePublicKey(base64PublicKey: String): PublicKey {
        val keyBytes = decodeBase64(base64PublicKey)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(spec)
    }

    /**
     * Verifies the cryptographic signature of a catalogue envelope.
     *
     * @param envelopeJson Raw JSON string of the catalogue envelope containing "payload", "signature", and "algorithm".
     * @param trustedPublicKeyB64 Base64 X.509 DER encoded public key.
     * @return Result containing the verified payload string, or failure if tampered / invalid.
     */
    fun verifyCatalogueEnvelope(
        envelopeJson: String,
        trustedPublicKeyB64: String = DEFAULT_TRUSTED_PUBLIC_KEY_B64,
    ): Result<String> {
        return runCatching {
            val root = JSONObject(envelopeJson)
            val algorithm = root.optString("algorithm", "")
            if (algorithm != EXPECTED_ALGORITHM) {
                throw SecurityException("Untrusted signature algorithm: '$algorithm'. Expected '$EXPECTED_ALGORITHM'.")
            }

            val payload = root.getString("payload")
            val signatureB64 = root.getString("signature")

            val publicKey = decodePublicKey(trustedPublicKeyB64)
            val signatureBytes = decodeBase64(signatureB64)

            val verifier = Signature.getInstance(EXPECTED_ALGORITHM)
            verifier.initVerify(publicKey)
            verifier.update(payload.toByteArray(Charsets.UTF_8))

            val isValid = verifier.verify(signatureBytes)
            if (!isValid) {
                throw SecurityException("Catalogue cryptographic signature verification FAILED. Payload may be tampered.")
            }
            payload
        }
    }

    /**
     * Computes the lowercase hexadecimal SHA-256 hash of a file.
     */
    fun computeSha256(file: File): String {
        return FileInputStream(file).use { computeSha256(it) }
    }

    /**
     * Computes the lowercase hexadecimal SHA-256 hash of an InputStream.
     */
    fun computeSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
