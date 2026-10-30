package org.arogyasathi.app

import org.arogyasathi.app.ui.AppLanguage
import org.arogyasathi.app.ui.getAppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {

    @Test
    fun testAllLanguagesHaveCompleteTranslations() {
        val allLangs = AppLanguage.entries.toTypedArray()
        assertEquals(6, allLangs.size)

        for (lang in allLangs) {
            val strings = getAppStrings(lang)
            assertNotNull(strings)
            assertEquals(lang, strings.language)

            // Verify non-blank key navigation strings
            assertTrue("App name must not be blank for ${lang.name}", strings.appName.isNotBlank())
            assertTrue("Settings title must not be blank for ${lang.name}", strings.settingsTitle.isNotBlank())
            assertTrue("Settings subtitle must not be blank for ${lang.name}", strings.settingsSubtitle.isNotBlank())
            assertTrue("Change language button must not be blank for ${lang.name}", strings.changeLanguageButton.isNotBlank())
            assertTrue("Select language title must not be blank for ${lang.name}", strings.selectLanguageTitle.isNotBlank())
            assertTrue("Select language desc must not be blank for ${lang.name}", strings.selectLanguageDesc.isNotBlank())
            assertTrue("Today tab label must not be blank for ${lang.name}", strings.tabToday.isNotBlank())
            assertTrue("Families tab label must not be blank for ${lang.name}", strings.tabFamilies.isNotBlank())
            assertTrue("Assistant tab label must not be blank for ${lang.name}", strings.tabAssistant.isNotBlank())
            assertTrue("Records tab label must not be blank for ${lang.name}", strings.tabRecords.isNotBlank())
            assertTrue("Settings tab label must not be blank for ${lang.name}", strings.tabSettings.isNotBlank())
        }
    }

    @Test
    fun testLanguageCodeResolution() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("EN"))
        assertEquals(AppLanguage.TELUGU, AppLanguage.fromCode("te"))
        assertEquals(AppLanguage.HINDI, AppLanguage.fromCode("hi"))
        assertEquals(AppLanguage.TAMIL, AppLanguage.fromCode("ta"))
        assertEquals(AppLanguage.KANNADA, AppLanguage.fromCode("kn"))
        assertEquals(AppLanguage.MALAYALAM, AppLanguage.fromCode("ml"))
        // Fallback
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("unknown_code"))
    }

    @Test
    fun testSpecificNativeNames() {
        assertEquals("తెలుగు", AppLanguage.TELUGU.nativeName)
        assertEquals(true, AppLanguage.HINDI.nativeName.contains("हि"))
        assertEquals("தமிழ்", AppLanguage.TAMIL.nativeName)
        assertEquals("ಕನ್ನಡ", AppLanguage.KANNADA.nativeName)
        assertEquals("മലയാളം", AppLanguage.MALAYALAM.nativeName)
        assertEquals("English", AppLanguage.ENGLISH.nativeName)
    }
}
