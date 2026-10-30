package org.arogyasathi.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom

/**
 * Security manager for AES-256 database encryption key management.
 * Generates and securely persists a device-bound 256-bit AES master key
 * used by SQLCipher to encrypt patient health records at rest.
 */
object DatabaseSecurityManager {
    private const val PREFS_NAME = "arogyasathi_security_store"
    private const val KEY_DB_PASSPHRASE = "db_aes_256_passphrase_key"

    @Synchronized
    fun getOrGeneratePassphrase(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }

        // Generate cryptographically secure 256-bit (32 bytes) random AES key
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        val encoded = Base64.encodeToString(randomBytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
        return encoded
    }
}
