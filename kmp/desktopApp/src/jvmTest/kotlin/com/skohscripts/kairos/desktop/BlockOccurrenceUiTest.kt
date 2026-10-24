package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TimeBlock
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * « Seulement celle-ci » sur un créneau récurrent (docs/spec/recurrence.md, issue #110) : sauter le jour, « Annuler »
 * depuis le message, changer les heures de ce jour seulement, puis « Rétablir ». Horloge figée au mardi 29 septembre 2026.
 */
@OptIn(ExperimentalTestApi::class)
class BlockOccurrenceUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private val today = LocalDate(2026, 9, 29)

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(
            KairosSnapshot(
                timeBlocks = listOf(
                    TimeBlock(1, "Lunch", LocalDateTime(2026, 9, 1, 12, 0), LocalDateTime(2026, 9, 1, 13, 0), recurrence = BlockRecurrence.DAILY, createdAt = now),
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

    private fun ComposeUiTest.openSlot() {
        onAllNodesWithContentDescription("Edit").onFirst().performScrollTo().performClick()
        waitText("Only this one")
    }

    @Test
    fun oneOccurrenceIsSkippedUndoneChangedAndRestored() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Time slot / deep work")
        onNodeWithText("Time slot / deep work").performClick()
        waitText("Lunch")

        // « Seulement celle-ci » est le choix par défaut d'un récurrent : sauter ce jour.
        openSlot()
        onNodeWithText("All occurrences").assertExists()
        onNodeWithText("Skip this day").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.blockExceptions.isNotEmpty() }
        assertTrue(repository.snapshot.value.blockExceptions.single().skipped)
        waitText("skipped this day")
        // Le message propose de défaire le saut.
        waitText("Undo")
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.blockExceptions.isEmpty() }

        // Heures de ce jour seulement : la série garde les siennes.
        openSlot()
        onNode(hasSetTextAction() and hasText("12:00")).performTextClearance()
        // Le formulaire de saisie, derrière le dialogue, a aussi ses champs vides : ceux du dialogue sont les derniers.
        onAllNodes(hasSetTextAction() and hasText("Start (HH:MM)")).onLast().performTextInput("12:30")
        onNode(hasSetTextAction() and hasText("13:00")).performTextClearance()
        onAllNodes(hasSetTextAction() and hasText("End (HH:MM)")).onLast().performTextInput("13:30")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.blockExceptions.isNotEmpty() }
        val exception = repository.snapshot.value.blockExceptions.single()
        assertEquals(today, exception.date)
        assertEquals(LocalTime(12, 30), exception.start)
        assertEquals(LocalDateTime(2026, 9, 1, 12, 0), repository.snapshot.value.timeBlocks.single().start)
        waitText("changed this day")

        // « Rétablir » rend l'occurrence de la série.
        openSlot()
        onNodeWithText("Restore").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.blockExceptions.isEmpty() }
    }
}
