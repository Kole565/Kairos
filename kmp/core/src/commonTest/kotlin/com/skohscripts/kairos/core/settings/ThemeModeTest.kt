package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Thème clair ou sombre (docs/spec/apparence.md § Thème sombre, issue #72). */
class ThemeModeTest {
    @Test
    fun followingTheSystemGivesItsThemeAndAChoiceWins() {
        assertTrue(ThemeMode.isDark(Settings.THEME_MODE_SYSTEM, systemDark = true))
        assertFalse(ThemeMode.isDark(Settings.THEME_MODE_SYSTEM, systemDark = false))
        assertTrue(ThemeMode.isDark(Settings.THEME_MODE_DARK, systemDark = false))
        assertFalse(ThemeMode.isDark(Settings.THEME_MODE_LIGHT, systemDark = true))
        // Un choix fait reste quand le système change ensuite.
        assertTrue(ThemeMode.isDark(Settings.THEME_MODE_DARK, systemDark = true))
        assertFalse(ThemeMode.isDark(Settings.THEME_MODE_LIGHT, systemDark = false))
    }

    @Test
    fun theToggleNeverStoresTheSystemTheme() {
        assertEquals(Settings.THEME_MODE_DARK, ThemeMode.toggled(wantDark = true, systemDark = false))
        assertEquals(Settings.THEME_MODE_LIGHT, ThemeMode.toggled(wantDark = false, systemDark = true))
        // Rebasculer vers le thème du système : on le suit à nouveau.
        assertEquals(Settings.THEME_MODE_SYSTEM, ThemeMode.toggled(wantDark = false, systemDark = false))
        assertEquals(Settings.THEME_MODE_SYSTEM, ThemeMode.toggled(wantDark = true, systemDark = true))
    }

    @Test
    fun anUnknownValueFollowsTheSystem() {
        assertEquals(Settings.THEME_MODE_SYSTEM, ThemeMode.normalize("purple"))
        assertEquals(Settings.THEME_MODE_SYSTEM, ThemeMode.normalize(""))
        assertEquals(Settings.THEME_MODE_DARK, ThemeMode.normalize(" Dark "))
        assertEquals(Settings.THEME_MODE_SYSTEM, Settings().themeMode)
        // Un réglage importé illisible ne casse rien : il suit le système.
        assertTrue(ThemeMode.isDark("purple", systemDark = true))
    }

    @Test
    fun theFormNormalisesAndNeverRefusesTheThemeMode() {
        val values = SettingsForm.values(Settings())
        assertEquals(Settings.THEME_MODE_SYSTEM, values["themeMode"])
        assertEquals(Settings.THEME_MODE_DARK, SettingsForm.validate(values + ("themeMode" to "DARK"), Settings()).settings!!.themeMode)
        assertEquals(Settings.THEME_MODE_SYSTEM, SettingsForm.validate(values + ("themeMode" to "sepia"), Settings()).settings!!.themeMode)
    }
}
