package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
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
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * « Corriger les estimations… » (docs/spec/statistiques.md, issue #101) : l'aperçu liste « 30 min → 45 min », on décoche une
 * tâche, « Appliquer (1) » écrit l'autre sans toucher sa date de fin, « Undo » rend l'estimation d'avant.
 */
@OptIn(ExperimentalTestApi::class)
class EstimateCorrectionUiTest {
    private val now = Instant.parse("2026-10-20T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val finished = Instant.parse("2026-10-15T16:00:00Z")
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(
                    Task(1, "Write the report", priority = 1, fibonacciPoints = 3, estimatedMinutes = 30, status = TaskStatus.DONE, createdAt = finished, updatedAt = finished),
                    Task(2, "Review the slides", priority = 1, fibonacciPoints = 2, status = TaskStatus.DONE, createdAt = finished, updatedAt = finished),
                ),
                workSessions = listOf(
                    WorkSession(1, 1, Instant.parse("2026-10-15T08:00:00Z"), Instant.parse("2026-10-15T08:45:00Z"), finished),
                    WorkSession(2, 2, Instant.parse("2026-10-15T09:00:00Z"), Instant.parse("2026-10-15T09:20:00Z"), finished),
                ),
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

    @Test
    fun previewUntickApplyAndUndo() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText("Correct the estimates…")
        onNodeWithText("Correct the estimates…").performScrollTo().performClick()
        waitText("30 min → 45 min")
        onNodeWithText("no estimate → 20 min").assertExists()
        // Décocher « Review the slides » : elle garde son absence d'estimation.
        onNodeWithText("Review the slides").performClick()
        waitText("Apply (1)")
        onNodeWithText("Apply (1)").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.single { it.id == 1L }.estimatedMinutes == 45 }
        val tasks = repository.snapshot.value.tasks.associateBy { it.id }
        assertEquals(null, tasks.getValue(2).estimatedMinutes)
        assertEquals(finished, tasks.getValue(1).updatedAt)
        waitText("Undo")
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.tasks.single { it.id == 1L }.estimatedMinutes == 30 }
    }
}
