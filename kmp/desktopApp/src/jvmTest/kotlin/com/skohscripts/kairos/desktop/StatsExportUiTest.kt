package com.skohscripts.kairos.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
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
 * « Exporter… » des Stats (docs/spec/statistiques.md § Export, issue #113) : le CSV de la vue filtrée, l'image d'une
 * carte à 1 200 px de large, rien d'écrit dans la base ; pas de bouton sans données ; le menu tient à 360 dp.
 */
@OptIn(ExperimentalTestApi::class)
class StatsExportUiTest {
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Fichiers « enregistrés » par l'écran, gardés pour les vérifier. */
    private class Files : FileService {
        val texts = mutableMapOf<String, String>()
        val images = mutableMapOf<String, ImageBitmap>()
        override suspend fun saveText(suggestedName: String, text: String): Boolean {
            texts[suggestedName] = text
            return true
        }

        override suspend fun saveImage(suggestedName: String, image: ImageBitmap): Boolean {
            images[suggestedName] = image
            return true
        }

        override suspend fun openText(): String? = null
    }

    private fun task(id: Long, title: String, type: String) = Task(
        id, title, priority = 1, fibonacciPoints = 3, estimatedMinutes = 60, taskType = type, status = TaskStatus.DONE,
        createdAt = Instant.parse("2026-10-01T08:00:00Z"), updatedAt = Instant.parse("2026-10-20T10:00:00Z"),
    )

    private fun session(id: Long, taskId: Long, start: String) =
        Instant.parse(start).let { WorkSession(id, taskId, it, Instant.fromEpochMilliseconds(it.toEpochMilliseconds() + 90 * 60_000), it) }

    private fun services(files: FileService, empty: Boolean = false): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        if (!empty) {
            repository.replaceAll(
                KairosSnapshot(
                    tasks = listOf(task(1, "Write the spec", "Dev"), task(2, "Weekly sync", "Meeting")),
                    workSessions = listOf(session(1, 1, "2026-10-20T09:00:00Z"), session(2, 2, "2026-10-19T14:00:00Z")),
                    settings = Settings(taskTypes = "Dev,Meeting"),
                ),
            )
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.clickable(text: String) = onNode(hasText(text) and hasClickAction())

    @Test
    fun theCsvIsTheFilteredViewAndAnImageIsOneCardAt1200px() = runComposeUiTest {
        val files = Files()
        val services = services(files)
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText("task(s) done · 8 wk")
        val before = services.repository.snapshot.value

        // Vue filtrée : 4 semaines, type « Dev ».
        clickable("4 wk").performClick()
        onNodeWithText("Search / filter").performClick()
        clickable("Type").performScrollTo().performClick()
        clickable("Dev").performClick()
        waitText("Filtered: 1 of 2 tasks")

        // 40 dp dessinés, aire tactile de 48 dp (`minimumInteractiveComponentSize`, docs/spec/densite.md).
        onNodeWithContentDescription("Export…").assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp).performClick()
        clickable("Tables (CSV)").performClick()
        waitText("Statistics exported.")
        val (name, csv) = files.texts.entries.single()
        assertEquals("kairos-stats-20261022-1200.csv".length, name.length)
        assertTrue(name.startsWith("kairos-stats-") && name.endsWith(".csv"), name)
        assertTrue("period,4\r\n" in csv && "filter_type,Dev\r\n" in csv && "tasks_kept,1\r\ntasks_total,2\r\n" in csv, csv)
        assertTrue("time_by_type\r\ntype,minutes,pct\r\nDev,90,100\r\n" in csv, csv)

        onNodeWithContentDescription("Export…").performClick()
        clickable("Image: Weekly throughput").performClick()
        waitText("Image saved.")
        val (imageName, image) = files.images.entries.single()
        assertTrue(imageName.startsWith("kairos-stats-throughput-") && imageName.endsWith(".png"), imageName)
        assertEquals(1200, image.width)
        assertTrue(image.height > 200, "image trop basse : ${image.height}")
        // Rien n'est écrit dans la base.
        assertEquals(before, services.repository.snapshot.value)
    }

    /** Écran sombre (issue #72) : l'image d'une carte reste claire, elle va dans un rapport ou à l'impression. */
    @Test
    fun anImageStaysLightOnADarkScreen() = runComposeUiTest {
        val files = Files()
        val services = services(files)
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD, systemDark = true) { services } }
        waitText("task(s) done · 8 wk")
        onNodeWithContentDescription("Export…").performClick()
        clickable("Image: Weekly throughput").performClick()
        waitText("Image saved.")
        val corner = files.images.values.single().toPixelMap()[4, 4]
        assertTrue(corner.luminance() > 0.8f, "fond de l'image sombre : $corner")
    }

    @Test
    fun noButtonWithoutData() = runComposeUiTest {
        val services = services(Files(), empty = true)
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("No data to analyse yet", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, onAllNodesWithContentDescription("Export…").fetchSemanticsNodes().size)
    }

    /** Fenêtre de 360 × 900 (densité 1) : le menu, une fenêtre surgissante, se place dans la fenêtre et non dans une boîte. */
    @Test
    fun theMenuFitsA360dpWindow() = runDesktopComposeUiTest(360, 900) {
        val services = services(Files())
        setContent { KairosApp(Platform.DESKTOP, Destination.STATS, TeamDestination.BOARD) { services } }
        waitText("task(s) done · 8 wk")
        val button = onNodeWithContentDescription("Export…").getUnclippedBoundsInRoot()
        assertTrue(button.left >= 0.dp && button.right <= 360.dp, "bouton hors de la fenêtre de 360 dp : $button")
        onNodeWithContentDescription("Export…").performClick()
        val item = clickable("Image: When you work on each type").getUnclippedBoundsInRoot()
        assertTrue(item.left >= 0.dp && item.right <= 360.dp, "menu hors de la fenêtre de 360 dp : $item")
    }
}
