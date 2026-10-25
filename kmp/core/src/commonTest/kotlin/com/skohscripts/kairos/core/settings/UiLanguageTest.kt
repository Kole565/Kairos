package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Langue de l'interface : choix de l'utilisateur, sinon système avec repli anglais (docs/spec/i18n.md, issue #70). */
class UiLanguageTest {
    @Test
    fun theSystemFollowsATranslatedLanguageAndFallsBackToEnglish() {
        for (system in listOf("fr", "FR", "fr-FR", "fr_CA")) assertEquals("fr", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
        for (system in listOf("es", "ES", "es-MX", "es_AR")) assertEquals("es", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
        for (system in listOf("de", "de-AT", "de_CH")) assertEquals("de", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
        for (system in listOf("ru", "ru-RU")) assertEquals("ru", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
        for (system in listOf("it", "it-CH")) assertEquals("it", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
        for (system in listOf("en", "en-GB", "pt", "zh-Hans", "ar", "", "frr", "deu")) assertEquals("en", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
    }

    @Test
    fun aChosenLanguageIgnoresTheSystem() {
        assertEquals("en", UiLanguage.resolve("en", "fr"))
        assertEquals("fr", UiLanguage.resolve("fr", "ru"))
        assertEquals("it", UiLanguage.resolve(" IT ", "de"))
    }

    @Test
    fun anUnknownSettingFollowsTheSystem() {
        assertEquals("fr", UiLanguage.resolve("klingon", "fr"))
        assertEquals("ru", UiLanguage.resolve("klingon", "ru"))
        assertEquals("en", UiLanguage.resolve("klingon", "pt"))
        assertEquals(Settings.LANGUAGE_SYSTEM, UiLanguage.normalize("klingon"))
        assertEquals(Settings.LANGUAGE_SYSTEM, UiLanguage.normalize("pt"))
        for (language in UiLanguage.SUPPORTED) assertEquals(language, UiLanguage.normalize(language))
    }

    @Test
    fun onlyFrenchTagsAreFrench() {
        assertTrue(UiLanguage.isFrench("fr"))
        assertFalse(UiLanguage.isFrench("frr"))
        assertFalse(UiLanguage.isFrench("en"))
    }

    @Test
    fun dayFirstAndDecimalCommaHoldForEveryTranslatedLanguageButEnglish() {
        for (tag in listOf("fr", "es-MX", "de", "ru", "it")) assertTrue(UiLanguage.dayFirst(tag), tag)
        for (tag in listOf("en", "en-US", "pt", "")) assertFalse(UiLanguage.dayFirst(tag), tag)
    }
}
