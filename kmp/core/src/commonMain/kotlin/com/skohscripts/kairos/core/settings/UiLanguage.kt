package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings

/**
 * Langue de l'interface (docs/spec/i18n.md), pure : le système et le réglage sont des paramètres.
 * Deux langues, `fr` et `en` ; toute langue système autre que le français donne l'anglais (repli, issue #70).
 */
object UiLanguage {
    /** `fr` ou `en` : le choix de l'utilisateur ([Settings.language]), ou à défaut la langue [system] (`fr`, `fr-CA`, `de`…). */
    fun resolve(setting: String, system: String): String = when (normalize(setting)) {
        Settings.LANGUAGE_FR -> Settings.LANGUAGE_FR
        Settings.LANGUAGE_EN -> Settings.LANGUAGE_EN
        else -> if (isFrench(system)) Settings.LANGUAGE_FR else Settings.LANGUAGE_EN
    }

    /** La langue [tag] (`fr`, `fr-FR`, `fr_CA`, `FR`) est le français. */
    fun isFrench(tag: String): Boolean = tag.trim().lowercase().let { it == "fr" || it.startsWith("fr-") || it.startsWith("fr_") }

    /** Valeur enregistrée d'un choix : `fr`, `en`, sinon [Settings.LANGUAGE_SYSTEM] (valeur inconnue = suivre le système). */
    fun normalize(setting: String): String = when (setting.trim().lowercase()) {
        Settings.LANGUAGE_FR -> Settings.LANGUAGE_FR
        Settings.LANGUAGE_EN -> Settings.LANGUAGE_EN
        else -> Settings.LANGUAGE_SYSTEM
    }
}
