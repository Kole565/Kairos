package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency
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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Cartes du guide qui ouvrent la section précise d'un écran (docs/spec/visite-guidee.md, issue #95) : l'écran défile
 * jusqu'à la section et la met en valeur (« Here »), qui s'efface d'elle-même ; une section absente le dit.
 */
@OptIn(ExperimentalTestApi::class)
class GuideSpotUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(snapshot: KairosSnapshot = KairosSnapshot()): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(snapshot)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun task(id: Long, title: String) =
        Task(id, title, priority = 1, fibonacciPoints = 2, estimatedMinutes = 30, createdAt = now, updatedAt = now)

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    private fun ComposeUiTest.waitGone(text: String) =
        waitUntil(conditionDescription = "disparition de « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }

    /** Aide → « Discover Kairos » → [chapter] → [next] fois « Next » → « Show me the screen ». */
    private fun ComposeUiTest.showCard(chapter: String, next: Int, title: String) {
        onNodeWithContentDescription("Help").performClick()
        onNodeWithText("Discover Kairos").performClick()
        waitText(chapter)
        onNodeWithText(chapter).performClick()
        repeat(next) { onNodeWithText("Next").performClick() }
        waitText(title)
        onNodeWithText("Show me the screen").performClick()
    }

    @Test
    fun learnedDurationsOpensThePlanningCategoryAndMarksItsCard() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // « Follow » : chrono, puis durées apprises.
        showCard("Follow", 1, "Learned durations")
        waitText("Learn durations from past occurrences")
        waitText("Here")
        // La mise en valeur s'efface d'elle-même (3 s).
        waitGone("Here")
    }

    @Test
    fun blockersOpensTheBlockedSectionAndMarksIt() = runComposeUiTest {
        val services = services(
            KairosSnapshot(
                tasks = listOf(task(1, "Get the quote"), task(2, "Sign the contract")),
                dependencies = listOf(TaskDependency(1, taskId = 2, blockerId = 1, createdAt = now)),
            ),
        )
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Les lignes de l'agenda portent l'heure devant le titre.
        waitText("Sign the contract", substring = true)
        // « Organize » : sous-tâches, récurrence, puis bloqueurs.
        showCard("Organize", 2, "Blockers")
        // La section « Blocked », repliée par défaut, est ouverte (son aide s'affiche) et mise en valeur.
        waitText("Waiting for another task to be finished before they can move on.")
        waitText("Here")
    }

    @Test
    fun theDarkThemeCardOpensTheAppearanceCardAndMarksIt() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // « Settings » (3.7.0) : réglages clairs, réglages par catégories, puis thème sombre.
        showCard("Settings", 2, "Dark theme")
        waitText("Follows the system.")
        waitText("Here")
    }

    @Test
    fun theHolidaysCardOpensThePublicHolidaysCardAndMarksIt() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // « Organize » : sous-tâches, récurrence, bloqueurs, créneaux, puis fériés des créneaux « jours ouvrés » (3.7.0).
        showCard("Organize", 4, "Weekday slots skip holidays")
        waitText("Public holidays", substring = true)
        waitText("Here")
    }

    @Test
    fun aMissingSectionIsSaidAndTheScreenStaysAtTheTop() = runComposeUiTest {
        val services = services(KairosSnapshot(tasks = listOf(task(1, "Get the quote"))))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Get the quote", substring = true)
        showCard("Organize", 2, "Blockers")
        waitText("No blocked task right now.")
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Here").fetchSemanticsNodes().isEmpty() }
    }
}
