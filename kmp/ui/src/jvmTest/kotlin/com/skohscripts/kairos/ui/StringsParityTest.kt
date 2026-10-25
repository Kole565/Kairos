package com.skohscripts.kairos.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Chaque langue a exactement les mêmes clés que l'anglais, sans valeur vide
 * (docs/spec/i18n.md § Invariants) : une chaîne ajoutée dans une langue
 * l'est aussi dans toutes les autres.
 */
class StringsParityTest {
    private val resources = File("src/commonMain/composeResources")
    private val stringPattern = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    /** Les langues traduites, face à l'anglais (`values`, langue de repli). */
    private val translated = listOf("values-fr", "values-es", "values-de", "values-ru", "values-it")
    private val all = listOf("values") + translated

    private fun strings(folder: String): Map<String, String> =
        stringPattern.findAll(File(resources, "$folder/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].trim() }

    @Test
    fun everyLanguageHasTheSameKeysAsEnglish() {
        val en = strings("values")
        assertTrue(en.isNotEmpty())
        for (folder in translated) assertEquals(en.keys.sorted(), strings(folder).keys.sorted(), folder)
    }

    @Test
    fun noEmptyTranslation() {
        for (folder in all) {
            val empty = strings(folder).filterValues { it.isEmpty() }.keys
            assertTrue(empty.isEmpty(), "$folder : chaînes vides $empty")
        }
    }

    @Test
    fun noAndroidStyleEscapes() {
        // Les ressources Compose n'interprètent pas `\'` ni `\"` : ils s'afficheraient
        // tels quels (« Aujourd\'hui »). Apostrophe typographique ’ à la place.
        for (folder in all) {
            val escaped = strings(folder).filterValues { "\\'" in it || "\\\"" in it }.keys
            assertTrue(escaped.isEmpty(), "$folder : échappements Android $escaped")
        }
    }

    @Test
    fun placeholdersMatchAcrossLanguages() {
        val placeholder = Regex("""%\d+\$[sd]""")
        val en = strings("values")
        for (folder in translated) {
            for ((key, value) in strings(folder)) {
                assertEquals(
                    placeholder.findAll(en.getValue(key)).map { it.value }.toSet(),
                    placeholder.findAll(value).map { it.value }.toSet(),
                    "$folder $key",
                )
            }
        }
    }

    @Test
    fun frenchPunctuationKeepsItsNonBreakingSpace() {
        // « … » et l'espace avant : ; ? ! sont insécables (docs/spec/i18n.md) : sinon un retour à la ligne laisse « » »
        // ou « : » seul en tête de ligne.
        val loose = Regex("""« | [:;?!»]""")
        val bad = strings("values-fr").filterValues { loose.containsMatchIn(it) }.keys
        assertTrue(bad.isEmpty(), "espaces sécables avant la ponctuation : $bad")
    }

    @Test
    fun translatedTextsUseTheTypographicApostrophe() {
        // Une apostrophe droite « ' » en italien (« l'attività ») ou en français serait la seule de l'interface.
        for (folder in all) {
            val straight = strings(folder).filterValues { '\'' in it }.keys
            assertTrue(straight.isEmpty(), "$folder : apostrophe droite $straight")
        }
    }

    @Test
    fun languageNamesStayInTheirOwnLanguage() {
        // Chaque langue s'écrit sous son propre nom dans toutes les langues (docs/spec/i18n.md § Carte Langue).
        val names = mapOf("language_fr" to "Français", "language_en" to "English", "language_es" to "Español",
            "language_de" to "Deutsch", "language_ru" to "Русский", "language_it" to "Italiano")
        for (folder in all) for ((key, name) in names) assertEquals(name, strings(folder).getValue(key), "$folder $key")
    }
}
