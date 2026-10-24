package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamEventKind
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Première tâche d'équipe accompagnée (docs/spec/premiere-tache.md § Première tâche d'équipe, issue #97) : l'entrée du hub
 * « Découvrir Kairos » n'existe qu'avec l'espace Équipe ; chaque étape met en évidence des champs de la vraie fiche ; les
 * félicitations ne viennent qu'une fois la tâche créée ; abandonner n'écrit rien.
 */
@OptIn(ExperimentalTestApi::class)
class TeamTaskCoachUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private val files = object : FileService {
        override suspend fun saveText(suggestedName: String, text: String) = false
        override suspend fun openText(): String? = null
    }

    private fun services(team: Boolean): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        if (team) {
            repository.updateSettings(Settings(team = TeamSettings(enabled = true, name = "Platform", identity = "id-1")))
            repository.createMember("Léa", "Dev", 100, 7.0, false)
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.hasTag(tag: String) = onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.openDiscover() {
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Help").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithContentDescription("Help").performClick()
        onNodeWithText("Discover Kairos").performClick()
        waitText("Create my first task")
    }

    /** Les champs que chaque étape explique, dans l'ordre des sept étapes (balises `edit-field-…` de la fiche). */
    private val fieldsByStep = listOf(
        listOf("TITLE", "DESCRIPTION"),
        listOf("ASSIGNEE"),
        listOf("PRIORITY", "POINTS"),
        listOf("MINUTES", "DEADLINE"),
        listOf("TYPE"),
        listOf("SCHEDULED", "PROJECT", "MANUAL_TIME", "RECURRENCE", "PIN", "SUBTASKS", "BLOCKERS"),
        emptyList(),
    )

    @Test
    fun theWalkCreatesAnAssignedTeamTaskThenCongratulates() = runComposeUiTest {
        val services = services(team = true)
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openDiscover()
        onNodeWithText("Create my first team task").performScrollTo().performClick()
        waitText("New team task")
        onNode(hasSetTextAction() and hasText("Title")).performTextInput("Ship the PDF export")
        for ((index, tags) in fieldsByStep.withIndex()) {
            waitText("Step ${index + 1} of 7")
            tags.forEach { assertTrue(hasTag("edit-field-$it"), "champ « $it » absent de la fiche à l'étape ${index + 1}") }
            if (index == 1) {
                // « Assigné à » : choisir Léa dans le menu.
                onNodeWithTag("edit-field-ASSIGNEE").performClick()
                onAllNodes(hasText("Léa", substring = true) and hasClickAction()).onFirst().performClick()
            }
            if (index < fieldsByStep.lastIndex) onNodeWithText("Next").performClick()
        }
        assertEquals(0, onAllNodesWithText("First team task created").fetchSemanticsNodes().size)
        assertTrue(repository.snapshot.value.tasks.none { it.space == TaskSpace.TEAM })
        onNodeWithText("Save").performClick()
        waitText("First team task created")
        val task = repository.snapshot.value.tasks.single { it.space == TaskSpace.TEAM }
        assertEquals("Ship the PDF export", task.title)
        val lea = repository.snapshot.value.members.single { it.name == "Léa" }
        assertEquals(lea.id, task.assigneeId)
        // Le journal a la création et l'assignation (le dépôt journalise).
        val kinds = repository.snapshot.value.teamEvents.filter { it.taskId == task.id }.map { it.kind }
        assertTrue(TeamEventKind.CREATED in kinds && TeamEventKind.ASSIGNED in kinds, "$kinds")
        waitText("“Ship the PDF export” is “To do” with Léa: Tracking shows its state and load.")
        onNodeWithText("See Tracking").performClick()
        waitText("Tracking")
    }

    @Test
    fun givingUpWritesNothing() = runComposeUiTest {
        val services = services(team = true)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openDiscover()
        onNodeWithText("Create my first team task").performScrollTo().performClick()
        waitText("Step 1 of 7")
        onNode(hasSetTextAction() and hasText("Title")).performTextInput("Never saved")
        onNodeWithText("Cancel").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Step 1 of 7").fetchSemanticsNodes().isEmpty() }
        assertTrue(services.repository.snapshot.value.tasks.none { it.space == TaskSpace.TEAM })
        assertTrue(services.repository.snapshot.value.teamEvents.isEmpty())
    }

    @Test
    fun soloModeHasNoTeamEntry() = runComposeUiTest {
        val services = services(team = false)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openDiscover()
        assertEquals(0, onAllNodesWithText("Create my first team task").fetchSemanticsNodes().size)
    }
}
