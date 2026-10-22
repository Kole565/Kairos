package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.team.TeamMember
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * « Déplacer » depuis la vue Semaine (docs/spec/vue-semaine.md, issue #106) : toucher une tâche ouvre le menu des jours,
 * choisir un jour la programme sans toucher à l'échéance, « Annuler » rend la date d'avant. Horloge figée au mardi
 * 29 septembre 2026 ; vendredi 2 octobre déclaré férié, samedi 3 jour libre.
 */
@OptIn(ExperimentalTestApi::class)
class WeekMoveUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private val wednesday = LocalDate(2026, 9, 30)

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(
                    task(1, "Quarterly review"),
                    // Tâche d'équipe assignée à « moi » : visible dans la semaine, sans menu.
                    task(2, "Team job", TaskSpace.TEAM, assignee = 10),
                ),
                members = listOf(TeamMember(10, "uid-10", "Me", "", 100, 7.0, isSelf = true, archived = false, createdAt = now, updatedAt = now)),
                settings = Settings(
                    extraHolidays = "2026-10-02",
                    schedule = ScheduleSettings(mapOf(5 to DaySchedule(free = true))),
                    team = TeamSettings(enabled = true, identity = "id-1"),
                ),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun task(id: Long, title: String, space: TaskSpace = TaskSpace.PERSONAL, assignee: Long? = null) = Task(
        id, title, priority = 1, fibonacciPoints = 3, estimatedMinutes = 30, deadline = wednesday, space = space, assigneeId = assignee,
        createdAt = now, updatedAt = now,
    )

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Une entrée du menu : les en-têtes des cartes portent les mêmes « Thu 10/1 », mais ne se touchent pas. */
    private fun ComposeUiTest.menuItem(text: String) = onNode(hasText(text) and hasClickAction())

    private fun ComposeUiTest.menuItems(text: String) = onAllNodes(hasText(text) and hasClickAction()).fetchSemanticsNodes().size

    private fun ComposeUiTest.waitMenu() =
        waitUntil(conditionDescription = "menu « Déplacer »", timeoutMillis = 15_000) { menuItems("Choose a date…") == 1 }

    private fun ComposeUiTest.openWeek(width: Int) {
        setContent { Box(Modifier.width(width.dp).height(1200.dp)) { KairosApp(Platform.DESKTOP, Destination.WEEK, TeamDestination.BOARD) { services } } }
        waitText("Quarterly review")
    }

    private lateinit var services: AppServices

    @Test
    fun aTaskIsMovedToAnotherDayOfTheWeekThenTheMoveIsUndone() = runComposeUiTest {
        services = services()
        val repository = services.repository
        openWeek(1200)
        onNodeWithText("Quarterly review").performScrollTo().performClick()

        // Les jours à partir d'aujourd'hui, sans celui de la tâche ; férié et jour libre signalés, mais choisissables.
        waitMenu()
        assertEquals(0, menuItems("Mon 9/28"))
        assertEquals(0, menuItems("Wed 9/30"))
        menuItem("Tue 9/29").assertExists()
        menuItem("Thu 10/1").assertExists()
        menuItem("Fri 10/2 · public holiday").assertExists()
        menuItem("Sat 10/3 · day off").assertExists()

        menuItem("Thu 10/1").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.single { it.id == 1L }.scheduledDate != null }
        val moved = repository.snapshot.value.tasks.single { it.id == 1L }
        assertEquals(LocalDate(2026, 10, 1), moved.scheduledDate)
        assertEquals(wednesday, moved.deadline)
        // Une seule ligne : au jeudi, « Programmée », avec le rappel de l'échéance passée (contour d'avertissement).
        waitText("Scheduled")
        assertEquals(1, count("Quarterly review"))
        onNodeWithText("due Sep 30").assertExists()

        waitText("Undo")
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.single { it.id == 1L }.scheduledDate == null }
        waitUntil(timeoutMillis = 15_000) { count("Scheduled") == 0 }
    }

    @Test
    fun aTeamTaskHasNoMenuAndTheLineIsATouchTarget() = runComposeUiTest {
        services = services()
        openWeek(1200)
        waitText("Team job")
        assertEquals(0, onAllNodes(hasText("Team job") and hasClickAction()).fetchSemanticsNodes().size)
        // La ligne d'une tâche personnelle se touche d'un coup : 48 dp au moins.
        onNode(hasText("Quarterly review") and hasClickAction()).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun theMenuWorksAt360dp() = runComposeUiTest {
        services = services()
        openWeek(360)
        val line = onNode(hasText("Quarterly review") and hasClickAction()).performScrollTo()
        val bounds = line.getUnclippedBoundsInRoot()
        assertTrue(bounds.left >= 0.dp && bounds.right <= 360.dp, "ligne hors de la fenêtre de 360 dp : $bounds")
        line.performClick()
        waitMenu()
        val item = menuItem("Fri 10/2 · public holiday").getUnclippedBoundsInRoot()
        assertTrue(item.left >= 0.dp && item.right <= 360.dp, "menu hors de la fenêtre de 360 dp : $item")
        menuItem("Fri 10/2 · public holiday").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.tasks.single { it.id == 1L }.scheduledDate == LocalDate(2026, 10, 2) }
    }
}
