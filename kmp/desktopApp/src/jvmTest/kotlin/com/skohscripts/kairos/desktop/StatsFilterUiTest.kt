package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
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
 * Filtres de l'écran Stats (docs/spec/statistiques.md § Filtres, issue #114) : la période change la fenêtre, le filtre de
 * la vue Jour réduit l'effectif, un filtre sans résultat le dit et se réinitialise ; rien n'est enregistré.
 */
@OptIn(ExperimentalTestApi::class)
class StatsFilterUiTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun task(id: Long, title: String, type: String) = Task(
        id, title, priority = 1, fibonacciPoints = 3, estimatedMinutes = 60, taskType = type, status = TaskStatus.DONE,
        createdAt = Instant.parse("2026-10-01T08:00:00Z"), updatedAt = Instant.parse("2026-10-20T10:00:00Z"),
    )

    private fun session(id: Long, taskId: Long, start: String) =
        Instant.parse(start).let { WorkSession(id, taskId, it, Instant.fromEpochMilliseconds(it.toEpochMilliseconds() + 90 * 60_000), it) }

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(task(1, "Write the spec", "Dev"), task(2, "Weekly sync", "Meeting")),
                workSessions = listOf(session(1, 1, "2026-10-20T09:00:00Z"), session(2, 2, "2026-10-19T14:00:00Z")),
                settings = Settings(taskTypes = "Dev,Meeting"),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    /** Une entrée cliquable (menu, bouton segmenté) : les mêmes mots s'écrivent aussi dans les cartes. */
    private fun ComposeUiTest.clickable(text: String) = onNode(hasText(text) and hasClickAction())

    @Test
    fun thePeriodAndTheFilterRecomputeTheCardsAndNothingIsSaved() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText("task(s) done · 8 wk")
        // Période : 4 semaines.
        clickable("4 wk").performClick()
        waitText("task(s) done · 4 wk")
        assertEquals(0, count("task(s) done · 8 wk"))

        // Filtre de la vue Jour : type « Dev ».
        onNodeWithText("Search / filter").performClick()
        clickable("Type").performScrollTo().performClick()
        clickable("Dev").performClick()
        waitText("Filtered: 1 of 2 tasks")

        // Rien pour ce filtre : on le dit, puis « Reset » rend la vue.
        onNode(hasSetTextAction() and hasText("Search")).performTextInput("zzz")
        waitText("Nothing for this filter. Change it or reset it above.")
        clickable("Reset").performClick()
        waitUntil(timeoutMillis = 15_000) { count("Filtered: 1 of 2 tasks") == 0 && count("Nothing for this filter. Change it or reset it above.") == 0 }
        waitText("task(s) done · 4 wk")
        // Rien n'est écrit : la fenêtre des réglages n'a pas bougé.
        assertEquals(8, services.repository.snapshot.value.settings.statsWindowWeeks)
    }

    @Test
    fun thePeriodRowFitsA360dpWindow() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(360.dp).height(900.dp)) { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } } }
        waitText("task(s) done · 8 wk")
        val row = onNodeWithContentDescription("Period").getUnclippedBoundsInRoot()
        assertTrue(row.left >= 0.dp && row.right <= 360.dp, "rangée de période hors de la fenêtre de 360 dp : $row")
        // « Tout » : depuis la première activité (tâches créées le 1er octobre, semaine du 28 septembre), soit 4 semaines.
        clickable("All").performClick()
        waitText("task(s) done · 4 wk")
    }
}
