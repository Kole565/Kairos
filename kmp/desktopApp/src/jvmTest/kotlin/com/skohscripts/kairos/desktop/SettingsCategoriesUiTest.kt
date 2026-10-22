package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Réglages rangés par catégories (docs/spec/reglages.md) : la liste puis une catégorie sur un téléphone, les deux côte
 * à côte en fenêtre large, et un seul formulaire d'une catégorie à l'autre.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsCategoriesUiTest {
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

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "plus de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun ComposeUiTest.rows() = onAllNodesWithTag("settings-category-GENERAL").fetchSemanticsNodes().size

    private fun ComposeUiTest.type(label: String, text: String) {
        val field = onNode(hasSetTextAction() and hasText(label)).performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    @Test
    fun onAPhoneACategoryOpensUnderItsTitleAndBackReturnsToTheList() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(400.dp).height(800.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } } }
        openSettings()
        // La liste d'abord : sept catégories, aucune carte.
        waitText("Day and hours")
        SettingsCategory.entries.forEach { assertEquals(1, onAllNodesWithTag("settings-category-${it.name}").fetchSemanticsNodes().size, it.name) }
        assertEquals(0, count("Working day"))

        // Une catégorie : son titre dans la barre du haut, ses cartes, plus de liste.
        openSettingsCategory(SettingsCategory.DAY)
        waitText("Working day")
        waitText("Hours by day of the week")
        assertEquals(1, count("Day and hours"))
        assertEquals(0, rows())

        // La flèche revient à la liste, puis ferme les Réglages.
        onNodeWithContentDescription("Back").performClick()
        waitGone("Working day")
        waitUntil(timeoutMillis = 15_000) { rows() == 1 }
        onNodeWithContentDescription("Back").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, rows())

        // Rouvrir les Réglages montre la liste, pas la dernière catégorie.
        openSettings()
        waitUntil(timeoutMillis = 15_000) { rows() == 1 }
        assertEquals(0, count("Working day"))
    }

    @Test
    fun aWideWindowShowsTheListAndTheChosenCategorySideBySide() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(1200.dp).height(800.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } } }
        openSettings()
        // « General » d'office, à côté de la liste.
        waitText("Appearance")
        assertEquals(1, rows())
        openSettingsCategory(SettingsCategory.TRACKING)
        waitText("Timer alerts")
        assertEquals(0, count("Appearance"))
        assertEquals(1, rows())
        // La flèche ferme les Réglages d'un coup.
        onNodeWithContentDescription("Back").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, rows())
    }

    @Test
    fun anEditSurvivesSwitchingCategoryAndIsSavedWithTheRest() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { Box(Modifier.width(1200.dp).height(800.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } } }
        openSettings(SettingsCategory.DAY)
        type("Start of day (hour, 0-23)", "8")
        openSettingsCategory(SettingsCategory.TRACKING)
        type("Statistics window (weeks)", "6")
        // Retour dans « Day and hours » : la valeur tapée y est toujours, rien n'est encore enregistré.
        openSettingsCategory(SettingsCategory.DAY)
        onNode(hasSetTextAction() and hasText("Start of day (hour, 0-23)") and hasText("8")).assertExists()
        assertEquals(9, repository.snapshot.value.settings.workdayStartHour)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.workdayStartHour == 8 }
        assertEquals(6, repository.snapshot.value.settings.statsWindowWeeks)
    }
}
