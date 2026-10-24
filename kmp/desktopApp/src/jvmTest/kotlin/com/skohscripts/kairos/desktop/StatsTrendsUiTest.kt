package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Évolutions des statistiques (docs/spec/statistiques.md § Évolutions, issue #112) : le résumé écrit de chaque graphique,
 * lu aussi par le lecteur d'écran, l'effectif et « not reliable ». Horloge figée au jeudi 22 octobre 2026, fenêtre de
 * 2 semaines, fuseau du système (les sessions sont à midi UTC, le même jour partout en Europe).
 */
@OptIn(ExperimentalTestApi::class)
class StatsTrendsUiTest {
    private val now = Instant.parse("2026-10-22T18:00:00Z")
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
        val finished = Instant.parse("2026-10-21T15:00:00Z")
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(Task(1, "Report", priority = 1, fibonacciPoints = 3, estimatedMinutes = 60, status = TaskStatus.DONE, createdAt = finished, updatedAt = finished)),
                workSessions = listOf(WorkSession(1, 1, Instant.parse("2026-10-21T11:00:00Z"), Instant.parse("2026-10-21T12:30:00Z"), finished)),
                settings = Settings(statsWindowWeeks = 2),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun eachChartHasAWrittenSummaryWithItsSample() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(1200.dp).height(3000.dp)) { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } } }
        waitText("Tracked time over time")
        // Du lundi 12 au jeudi 22 : 11 jours, un seul avec du temps, donc « not reliable ».
        val tracked = "Total 1 h 30 · 1 of 11 with time (n=1, not reliable) · peak: Wed 10/21 (1 h 30)"
        // Le graphique et son résumé ne font qu'un nœud pour le lecteur d'écran : il lit le résumé.
        assertTrue(onAllNodesWithContentDescription(tracked).fetchSemanticsNodes().isNotEmpty(), "le lecteur d'écran lit le résumé du graphique")
        onNodeWithText("Estimation bias per week").assertExists()
        onNodeWithText("Measured weeks: 1 of 2. Latest: ×1.50 (n=1, not reliable).").assertExists()
        onNodeWithText("Week of 10/19: ×1.50 · 1 h 30 real for 1 h estimated (n=1, not reliable)").assertExists()
    }
}
