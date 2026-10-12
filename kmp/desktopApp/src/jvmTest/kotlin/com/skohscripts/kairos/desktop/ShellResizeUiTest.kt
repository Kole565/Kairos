package com.skohscripts.kairos.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.data.TaskEdit
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
 * Redimensionner la fenêtre au travers de 600 dp (rail <-> barre haute, docs/spec/navigation-theme.md) garde l'écran
 * tel quel : un dialogue d'édition ouvert reste ouvert. Base en mémoire, horloge figée, interface en anglais.
 */
@OptIn(ExperimentalTestApi::class)
class ShellResizeUiTest {
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
        val id = repository.createTask("Quarterly report")!!
        repository.updateTask(id, TaskEdit(title = "Quarterly report", priority = 1, points = 3, estimatedMinutes = 60, pinDay = LocalDate(2026, 9, 29)))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    @Test
    fun anOpenEditDialogSurvivesCrossingTheRailBreakpoint() = runComposeUiTest {
        val services = services()
        var width by mutableStateOf(900.dp)
        setContent {
            Box(Modifier.width(width).height(900.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithContentDescription("Edit").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithContentDescription("Edit").onFirst().performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Edit task").fetchSemanticsNodes().isNotEmpty() }

        // Rail -> barre haute, puis retour : le dialogue ne se ferme jamais.
        width = 400.dp
        waitForIdle()
        assertEquals(1, onAllNodesWithText("Edit task").fetchSemanticsNodes().size)
        width = 900.dp
        waitForIdle()
        assertEquals(1, onAllNodesWithText("Edit task").fetchSemanticsNodes().size)
    }

    @Test
    fun theCaptureSegmentsKeepTheSameHeightWhenALabelWraps() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(340.dp).height(700.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Time slot").fetchSemanticsNodes().isNotEmpty() }
        val task = onAllNodes(hasText("Task") and hasClickAction()).onFirst().getUnclippedBoundsInRoot()
        val slot = onNode(hasText("Time slot") and hasClickAction()).getUnclippedBoundsInRoot()
        // À 340 dp, le libellé court « Time slot » évite le retour à la ligne : les deux segments ont la même hauteur (40 dp).
        assertEquals(task.height, slot.height, "segments de hauteurs différentes : $task / $slot")
    }

    @Test
    fun theMouseWheelScrollsTheChipRowOfANarrowWindow() = runComposeUiTest {
        val services = services()
        setContent { Box(Modifier.width(340.dp).height(700.dp)) { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Time slot").fetchSemanticsNodes().isNotEmpty() }
        // Cinq pastilles ne tiennent pas dans 340 dp : la dernière (« Stats ») est hors de la rangée.
        val row = onNodeWithContentDescription("Main navigation")
        val before = onNodeWithText("Stats").getUnclippedBoundsInRoot().left
        row.performMouseInput { moveTo(center); scroll(5f) }
        waitForIdle()
        assertTrue(onNodeWithText("Stats").getUnclippedBoundsInRoot().left < before, "la rangée n'a pas défilé")
    }
}
