package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Première tâche récurrente et visite des Réglages (docs/spec/premiere-tache.md, issue #98) : depuis « Découvrir Kairos », horloge
 * figée au mardi 29 septembre 2026, interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class RecurrenceCoachUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
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

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "disparition de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.type(label: String, text: String) {
        val field = onNode(hasSetTextAction() and hasText(label)).performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    private fun ComposeUiTest.openHubEntry(entry: String) {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Help").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Help").performClick()
        waitText("Discover Kairos")
        onNodeWithText("Discover Kairos").performClick()
        waitText(entry)
        onNodeWithText(entry).performScrollTo().performClick()
    }

    @Test
    fun theRecurringWalkShowsEachRuleAndCreatesAWeeklyTask() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openHubEntry("Create my first recurring task")
        waitText("Step 1 of 5")
        waitText("What comes back")
        type("Title", "Water the plants")
        onNodeWithText("Next").performClick()
        waitText("The first date")
        assertTrue(onAllNodesWithTag("edit-field-DEADLINE").fetchSemanticsNodes().isNotEmpty())
        onNodeWithText("Next").performClick()
        // La règle : la prochaine échéance de chaque règle, à partir d'aujourd'hui faute d'échéance.
        waitText("The rule")
        waitText("From Tuesday, September 29, 2026, the next deadline would be:")
        waitText("weekdays: Wednesday, September 30, 2026")
        waitText("on day 29 of the month: Thursday, October 29, 2026")
        onNodeWithTag("edit-field-RECURRENCE").performScrollTo().performClick()
        waitText("weekly")
        onNodeWithText("weekly").performClick()
        waitText("weekly: Tuesday, October 6, 2026 (your rule)")
        onNodeWithText("Next").performClick()
        waitText("What repeats")
        onNodeWithText("Next").performClick()
        waitText("Save the recurring task")
        assertEquals(0, repository.snapshot.value.tasks.size)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.isNotEmpty() }
        val task = repository.snapshot.value.tasks.single()
        assertEquals("Water the plants", task.title)
        assertEquals(TaskRecurrence.WEEKLY, task.recurrence)
        // Ce n'est pas l'accompagnement de la première tâche : son invitation n'est pas concernée.
        assertNull(repository.snapshot.value.settings.guide)
    }

    @Test
    fun givingUpTheRecurringWalkWritesNothing() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openHubEntry("Create my first recurring task")
        waitText("What comes back")
        type("Title", "Water the plants")
        onNodeWithText("Cancel").performClick()
        waitGone("What comes back")
        assertEquals(0, services.repository.snapshot.value.tasks.size)
    }

    @Test
    fun theSettingsTourGoesCardByCardAndWritesNothingUntilSave() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openHubEntry("Set up Kairos step by step")
        waitText("Step 1 of 5")
        waitText("Your working day")
        // La catégorie de la carte est ouverte : on peut y changer une valeur pendant la visite.
        type("Start of day (hour, 0-23)", "8")
        onNodeWithText("Next").performClick()
        waitText("Step 2 of 5")
        onNodeWithText("Next").performClick()
        waitText("Several windows and contexts")
        onNodeWithText("Next").performClick()
        waitText("Plan around your best hours")
        onNodeWithText("Next").performClick()
        waitText("Save your settings")
        onNodeWithText("Finish").performClick()
        waitGone("Save your settings")
        // Finir la visite n'enregistre rien : seul « Save » le fait.
        assertEquals(Settings(), repository.snapshot.value.settings)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.workdayStartHour == 8 }
    }
}
