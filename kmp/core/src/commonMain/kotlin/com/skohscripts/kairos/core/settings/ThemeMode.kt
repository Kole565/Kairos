package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings

/**
 * Thème clair ou sombre (docs/spec/apparence.md § Thème sombre, issue #72), pur : le thème du système est un paramètre.
 * Trois valeurs en base ([Settings.THEME_MODE_SYSTEM], [Settings.THEME_MODE_LIGHT], [Settings.THEME_MODE_DARK]), deux
 * états à l'écran : l'interrupteur montre le thème résolu et ne stocke jamais une valeur égale au système.
 */
object ThemeMode {
    /** Valeur enregistrée : `light`, `dark`, sinon [Settings.THEME_MODE_SYSTEM] (valeur inconnue = suivre le système). */
    fun normalize(setting: String): String = when (setting.trim().lowercase()) {
        Settings.THEME_MODE_LIGHT -> Settings.THEME_MODE_LIGHT
        Settings.THEME_MODE_DARK -> Settings.THEME_MODE_DARK
        else -> Settings.THEME_MODE_SYSTEM
    }

    /** Le thème affiché est sombre : le choix de l'utilisateur, à défaut celui du système ([systemDark]). */
    fun isDark(setting: String, systemDark: Boolean): Boolean = when (normalize(setting)) {
        Settings.THEME_MODE_DARK -> true
        Settings.THEME_MODE_LIGHT -> false
        else -> systemDark
    }

    /**
     * Valeur que pose l'interrupteur basculé vers [wantDark] : suivre le système s'il donne déjà ce thème (sans quoi le
     * thème serait figé en silence et ne suivrait plus un changement du système), sinon le thème voulu.
     */
    fun toggled(wantDark: Boolean, systemDark: Boolean): String = when {
        wantDark == systemDark -> Settings.THEME_MODE_SYSTEM
        wantDark -> Settings.THEME_MODE_DARK
        else -> Settings.THEME_MODE_LIGHT
    }
}
