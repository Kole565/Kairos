package com.skohscripts.kairos.core.settings

import com.skohscripts.kairos.core.model.Settings

/**
 * Langue de l'interface (docs/spec/i18n.md), pure : le système et le réglage sont des paramètres.
 * Six langues ([SUPPORTED]) ; toute langue système qui n'en fait pas partie donne l'anglais (repli, issue #70).
 */
object UiLanguage {
    /** Les langues de l'interface, dans l'ordre de la carte Langue des Réglages. */
    val SUPPORTED: List<String> = listOf(
        Settings.LANGUAGE_FR, Settings.LANGUAGE_EN, Settings.LANGUAGE_ES,
        Settings.LANGUAGE_DE, Settings.LANGUAGE_RU, Settings.LANGUAGE_IT,
    )

    /**
     * Une langue de [SUPPORTED] : le choix de l'utilisateur ([Settings.language]), ou à défaut la langue [system]
     * (`fr`, `es-MX`, `de_AT`…) si elle est traduite, sinon l'anglais.
     */
    fun resolve(setting: String, system: String): String {
        val chosen = normalize(setting)
        if (chosen != Settings.LANGUAGE_SYSTEM) return chosen
        return lang(system).takeIf { it in SUPPORTED } ?: Settings.LANGUAGE_EN
    }

    /** La première partie d'une étiquette de langue, en minuscules : `es` pour `es`, `ES`, `es-MX`, `es_AR` (`frr` reste `frr`). */
    fun lang(tag: String): String = tag.trim().lowercase().substringBefore('-').substringBefore('_')

    /** La langue [tag] (`fr`, `fr-FR`, `fr_CA`, `FR`) est le français. */
    fun isFrench(tag: String): Boolean = lang(tag) == Settings.LANGUAGE_FR

    /**
     * Le jour s'écrit avant le mois (« 30/09 ») et la virgule est le séparateur décimal : toutes les langues traduites
     * sauf l'anglais. Une langue inconnue suit l'anglais, comme [resolve].
     */
    fun dayFirst(tag: String): Boolean = lang(tag).let { it in SUPPORTED && it != Settings.LANGUAGE_EN }

    /** Valeur enregistrée d'un choix : une langue de [SUPPORTED], sinon [Settings.LANGUAGE_SYSTEM] (valeur inconnue = suivre le système). */
    fun normalize(setting: String): String = setting.trim().lowercase().takeIf { it in SUPPORTED } ?: Settings.LANGUAGE_SYSTEM
}
