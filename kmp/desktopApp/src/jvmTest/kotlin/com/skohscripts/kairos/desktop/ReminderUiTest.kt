package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ReminderSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Veille des rappels (docs/spec/rappels.md, issue #103) : à 13 h 50, la tâche épinglée à 14 h a son bandeau « Starts at 14:00
 * (in 10 min) », une seule fois ; rien avant l'heure. Horloge simulée, fuseau du système.
 */
@OptIn(ExperimentalTestApi::class)
class ReminderUiTest {
    private val zone = TimeZone.currentSystemDefault()
    private var now: Instant = LocalDateTime(2026, 9, 29, 13, 49, 58).toInstant(zone)
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
        repository.replaceAll(
            KairosSnapshot(
                tasks = listOf(
                    Task(1, "Call Marc", priority = 1, fibonacciPoints = 2, estimatedMinutes = 30, pinnedStart = LocalDateTime(2026, 9, 29, 14, 0), createdAt = now, updatedAt = now),
                ),
                settings = Settings(reminders = ReminderSettings(enabled = true)),
            ),
        )
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private val banner = "Call Marc · Starts at 14:00 (in 10 min)"

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun theFixedTimeTaskIsRemindedOnceAtItsTime() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Call Marc", substring = true).fetchSemanticsNodes().isNotEmpty() }
        // Avant l'heure : rien.
        mainClock.advanceTimeBy(1_500)
        waitForIdle()
        assertEquals(0, count(banner))
        // 13 h 50 passées : le bandeau.
        now = LocalDateTime(2026, 9, 29, 13, 50, 1).toInstant(zone)
        waitUntil(conditionDescription = "bandeau du rappel", timeoutMillis = 15_000) { count(banner) == 1 }
        // Une seule fois, même quand le temps passe encore.
        now = LocalDateTime(2026, 9, 29, 13, 52, 0).toInstant(zone)
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
        assertEquals(1, count(banner))
    }
}
