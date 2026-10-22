package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.settings.SettingsCategory
import com.skohscripts.kairos.ui.settings.SettingsScreen
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Largeur des champs des Réglages (docs/spec/densite.md § Réglages) : un champ
 * numérique est borné à 320 dp (son aide suit la même largeur) et ne déborde
 * jamais d'une fenêtre de téléphone (360 dp) ; un champ de texte libre garde
 * la pleine largeur de la carte. Et un enregistrement refusé ramène au champ fautif, dans sa catégorie
 * (docs/spec/reglages.md).
 */
@OptIn(ExperimentalTestApi::class)
class SettingsLayoutUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    /** Les Réglages à [width], ouverts sur [category] (sa ligne de la liste, sur un ou deux volets). */
    private fun ComposeUiTest.settingsAt(width: Dp, category: SettingsCategory = SettingsCategory.DAY) {
        val services = services()
        setContent {
            KairosTheme {
                Box(Modifier.width(width).height(2000.dp)) { SettingsScreen(services) {} }
            }
        }
        openSettingsCategory(category)
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Compose n'offre que `assertWidthIsAtLeast` et `assertWidthIsEqualTo` : la borne haute se lit sur les limites du nœud. */
    private fun SemanticsNodeInteraction.assertWidthIsAtMost(max: Dp) {
        val bounds = getUnclippedBoundsInRoot()
        val width = bounds.right - bounds.left
        assertTrue(width <= max, "largeur $width au-delà de $max")
    }

    /** Lignes de la liste portant [tag] et l'avertissement « A field to fix » (fusionné dans la ligne cliquable, ou dessous). */
    private fun ComposeUiTest.flaggedRows(tag: String): Int {
        val warning = hasContentDescription("A field to fix")
        return onAllNodes(hasTestTag(tag) and (warning or hasAnyDescendant(warning))).fetchSemanticsNodes().size
    }

    private fun ComposeUiTest.field(label: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasText(label))

    @Test
    fun aNumberFieldNeverOverflowsA360dpWindow() = runComposeUiTest {
        settingsAt(360.dp)
        val bounds = field("Default task duration (min)").getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= 0.dp && bounds.right <= 360.dp, "champ numérique hors de la fenêtre de 360 dp : $bounds")
        // Marge d'écran de 16 dp et marge intérieure de carte de 16 dp de chaque côté.
        field("Default task duration (min)").assertWidthIsAtMost(360.dp - 64.dp)
    }

    @Test
    fun aNumberFieldAndItsHelpStopAt320dpInAWideWindow() = runComposeUiTest {
        settingsAt(1000.dp)
        field("Default task duration (min)").assertWidthIsAtMost(320.dp)
        onNodeWithText("Given to a task without an estimate to place it in the day (never saved).").assertWidthIsAtMost(320.dp)
    }

    @Test
    fun aFreeTextFieldKeepsTheFullWidthOfItsCard() = runComposeUiTest {
        settingsAt(1000.dp, SettingsCategory.PLANNING)
        field("Types offered when editing").assertWidthIsAtLeast(400.dp)
    }

    @Test
    fun aRefusedSaveOpensTheCategoryOfTheFlaggedFieldAndScrollsToIt() = runComposeUiTest {
        val services = services()
        // Deux volets, fenêtre basse : le champ fautif (en bas de « Day and hours ») n'est pas à l'écran.
        setContent { KairosTheme { Box(Modifier.width(1000.dp).height(500.dp)) { SettingsScreen(services) {} } } }
        openSettingsCategory(SettingsCategory.DAY)
        field("Extra public holidays").performScrollTo().performTextReplacement("25/12")
        // Passer à une autre catégorie, puis enregistrer : l'écran revient à « Day and hours » et descend vers l'erreur.
        openSettingsCategory(SettingsCategory.GENERAL)
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Appearance").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Unreadable date: “25/12” (format YYYY-MM-DD).").fetchSemanticsNodes().isNotEmpty() }
        waitUntil(timeoutMillis = 15_000) {
            runCatching { field("Extra public holidays").assertIsDisplayed() }.isSuccess
        }
        // La ligne de la catégorie le signale (icône d'avertissement décrite, pas la couleur seule).
        assertEquals(1, flaggedRows("settings-category-DAY"))
        assertEquals(0, flaggedRows("settings-category-GENERAL"))
    }

    @Test
    fun tabLeavesTheMultilineTypesFieldInsteadOfTypingATab() = runComposeUiTest {
        settingsAt(1000.dp, SettingsCategory.PLANNING)
        field("Types offered when editing").performScrollTo().performClick().assertIsFocused()
        field("Types offered when editing").performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        // Le focus passe au champ suivant : sans la règle, Tab restait dans le champ (et y insérait une tabulation).
        field("Types offered when editing").assertIsNotFocused()
    }
}
