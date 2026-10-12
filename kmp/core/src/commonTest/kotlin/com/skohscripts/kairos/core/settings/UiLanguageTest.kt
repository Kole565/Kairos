package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Langue de l'interface : choix de l'utilisateur, sinon système avec repli anglais (docs/spec/i18n.md, issue #70). */
class UiLanguageTest {
    @Test
    fun theSystemFollowsFrenchAndFallsBackToEnglish() {
        for (system in listOf("fr", "FR", "fr-FR", "fr_CA")) assertEquals("fr", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
        for (system in listOf("en", "en-GB", "ru", "de", "es", "zh-Hans", "", "frr")) assertEquals("en", UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, system), system)
    }

    @Test
    fun aChosenLanguageIgnoresTheSystem() {
        assertEquals("en", UiLanguage.resolve("en", "fr"))
        assertEquals("fr", UiLanguage.resolve("fr", "ru"))
    }

    @Test
    fun anUnknownSettingFollowsTheSystem() {
        assertEquals("fr", UiLanguage.resolve("klingon", "fr"))
        assertEquals("en", UiLanguage.resolve("klingon", "ru"))
        assertEquals(Settings.LANGUAGE_SYSTEM, UiLanguage.normalize("klingon"))
    }

    @Test
    fun onlyFrenchTagsAreFrench() {
        assertTrue(UiLanguage.isFrench("fr"))
        assertFalse(UiLanguage.isFrench("frr"))
        assertFalse(UiLanguage.isFrench("en"))
    }
}
