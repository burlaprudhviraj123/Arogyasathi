package org.arogyasathi.app.ui

import androidx.compose.runtime.compositionLocalOf

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val shortCode: String,
    val subtitle: String
) {
    ENGLISH("en", "English", "English", "EN", "Standard Indian English"),
    TELUGU("te", "Telugu", "తెలుగు", "TE", "ఆంధ్రప్రదేశ్ & తెలంగాణ"),
    HINDI("hi", "Hindi", "हिन्दी", "HI", "उत्तर एवं मध्य भारत"),
    TAMIL("ta", "Tamil", "தமிழ்", "TA", "தமிழ்நாடு"),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ", "KN", "ಕರ್ನಾಟಕ"),
    MALAYALAM("ml", "Malayalam", "മലയാളം", "ML", "കേരളം");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

val LocalAppStrings = compositionLocalOf { getAppStrings(AppLanguage.ENGLISH) }
val LocalCurrentLanguage = compositionLocalOf { AppLanguage.ENGLISH }

