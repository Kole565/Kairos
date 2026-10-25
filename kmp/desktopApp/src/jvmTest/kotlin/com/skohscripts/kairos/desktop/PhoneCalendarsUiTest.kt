package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.calendar.DeviceCalendarData
import com.skohscripts.kairos.core.calendar.DeviceEvent
import com.skohscripts.kairos.core.calendar.ExternalBlocks
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.BackupStore
import com.skohscripts.kairos.ui.app.CalendarService
import com.skohscripts.kairos.ui.app.DeviceCalendar
import com.skohscripts.kairos.ui.app.DeviceCalendarState
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Calendriers du téléphone (issue #138, docs/spec/connexions-externes.md § Étape 2), avec un service simulé : l'interrupteur
 * demande l'accès, aucun calendrier coché d'office, les créneaux du calendrier coché apparaissent dans la vue Jour en
 * lecture seule (ni « Modifier » ni « Supprimer »), jamais en base ; accès retiré : le message et les réglages du système.
 */
@OptIn(ExperimentalTestApi::class)
class PhoneCalendarsUiTest {
    private val now = Instant.parse("2026-10-26T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private class FakeCalendars(private val granted: Boolean) : CalendarService {
        override val state = MutableStateFlow<DeviceCalendarState>(DeviceCalendarState.Off)
        override val data = MutableStateFlow(DeviceCalendarData())
        var settingsOpened = false
        private val calendars = listOf(DeviceCalendar(1, "Travail", "moi@exemple.fr"), DeviceCalendar(2, "Perso", "Perso"))
        private val events = listOf(DeviceEvent("Travail", "a", "Revue téléphone", Instant.parse("2026-10-26T12:00:00Z"), Instant.parse("2026-10-26T13:00:00Z")))

        override suspend fun enable() {
            state.value = if (granted) DeviceCalendarState.On(calendars, emptySet()) else DeviceCalendarState.Denied
        }

        override suspend fun disable() {
            state.value = DeviceCalendarState.Off
            data.value = DeviceCalendarData()
        }

        override suspend fun select(ids: Set<Long>) {
            state.value = DeviceCalendarState.On(calendars, ids)
            data.value = if (1L in ids) ExternalBlocks.map(events, TimeZone.currentSystemDefault()) else DeviceCalendarData()
        }

        override fun openSystemSettings() {
            settingsOpened = true
        }
    }

    private fun services(calendars: CalendarService): AppServices = runBlocking {
        val repo = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = true
            override suspend fun openText(): String? = null
        }
        AppServices(repo, files, BackupStore { _, _ -> }, clock = clock, calendars = calendars)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = text, timeoutMillis = 10_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun phoneCalendarsAreChosenThenReadOnlyInTheDayView() = runComposeUiTest {
        val calendars = FakeCalendars(granted = true)
        val services = services(calendars)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Use my phone calendars")
        onNodeWithText("Use my phone calendars").performScrollTo().performClick()
        waitText("Choose the calendars to use", substring = true)
        waitText("Travail · moi@exemple.fr")
        assertEquals(DeviceCalendarState.On(calendarsOf(calendars), emptySet()), calendars.state.value)
        onNodeWithText("Travail · moi@exemple.fr").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { (calendars.state.value as? DeviceCalendarState.On)?.selected == setOf(1L) }

        // Vue Jour : le créneau du téléphone, en lecture seule, jamais en base.
        onNodeWithText("Day").performClick()
        waitText("Time slot / deep work")
        onNodeWithText("Time slot / deep work").performClick()
        waitText("Phone calendar “Travail”")
        assertTrue(onAllNodesWithContentDescription("Delete the imported slot").fetchSemanticsNodes().isEmpty())
        assertTrue(services.repository.snapshot.value.timeBlocks.isEmpty())
    }

    @Test
    fun aRefusedOrWithdrawnAccessSaysSoAndOpensTheSystemSettings() = runComposeUiTest {
        val calendars = FakeCalendars(granted = false)
        val services = services(calendars)
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Use my phone calendars")
        onNodeWithText("Use my phone calendars").performScrollTo().performClick()
        waitText("Kairos no longer has access to the phone calendars.")
        onNodeWithText("Open system settings").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { calendars.settingsOpened }
    }

    private fun calendarsOf(fake: FakeCalendars) = (fake.state.value as DeviceCalendarState.On).calendars
}
