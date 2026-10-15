package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.WorkContext
import com.skohscripts.kairos.core.model.WorkWindow
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

/** Plages multiples et contextes à l'écran (docs/spec/planning-personnalise.md § Lot B2) : Réglages et vue Jour, horloge figée un mardi. */
@OptIn(ExperimentalTestApi::class)
class SchedulePlanningUiTest {
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
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }

    /** Le champ de texte [index] (0-based) qui porte l'étiquette [label] : plusieurs plages ont les mêmes. */
    private fun ComposeUiTest.hours(label: String, index: Int, text: String) {
        val field = onAllNodes(hasSetTextAction() and hasText(label))[index].performScrollTo()
        field.performTextClearance()
        field.performTextInput(text)
    }

    /** Une pastille (cliquable) de ce texte, pas le champ de texte qui porte la même valeur. */
    private val chip = { text: String -> hasText(text) and hasClickAction() and SemanticsMatcher.keyNotDefined(androidx.compose.ui.semantics.SemanticsActions.SetText) }

    @Test
    fun aDayWithTwoWindowsAndAContextIsSavedAsTheSchedule() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings()
        waitText("Contexts")
        // Un contexte « Work » avec le type « Développement ».
        onAllNodes(hasSetTextAction() and hasText("Context name"))[0].performScrollTo().performTextInput("Work")
        onAllNodes(chip("Développement"))[0].performScrollTo().performClick()
        // Mardi : deux plages, la seconde réservée à « Work ».
        onAllNodesWithText("Custom")[1].performScrollTo().performClick()
        onNodeWithText("Add a window").performScrollTo().performClick()
        waitText("Window 2")
        hours("To (hour, 0-23)", 0, "12")
        hours("From (hour, 0-23)", 1, "14")
        hours("To (hour, 0-23)", 1, "17")
        onAllNodes(chip("Work"))[1].performScrollTo().performClick()
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.schedule != null }
        val schedule = repository.snapshot.value.settings.schedule!!
        assertEquals(listOf(WorkWindow(9, 12), WorkWindow(14, 17, "Work")), schedule.weekdays.getValue(1).windows)
        assertEquals(listOf(WorkContext("Work", listOf("Développement"))), schedule.contexts)
    }

    @Test
    fun aWindowStartingBeforeThePreviousEndIsRefusedOnItsStart() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings()
        waitText("Contexts")
        onAllNodesWithText("Custom")[1].performScrollTo().performClick()
        onNodeWithText("Add a window").performScrollTo().performClick()
        waitText("Window 2")
        // La première plage finit à 18 h ; la seconde commence à 17 h.
        hours("From (hour, 0-23)", 1, "17")
        onNodeWithText("Save").performClick()
        waitText("At least 18.")
        assertEquals(null, repository.snapshot.value.settings.schedule)
    }

    @Test
    fun aTaskWhoseContextHasNoWindowTodaySaysWhyAndTheTimelineNamesTheContext() = runComposeUiTest {
        val services = services()
        val repository = services.repository
        runBlocking {
            val code = repository.createTask("Write code")!!
            val plants = repository.createTask("Water the plants")!!
            listOf(code, plants).forEach { repository.setPriority(it, 1); repository.setPoints(it, 2) }
            repository.updateTask(code, com.skohscripts.kairos.data.TaskEdit(title = "Write code", priority = 1, points = 2, taskType = "dev", pinDay = kotlinx.datetime.LocalDate(2026, 9, 29)))
            repository.updateTask(plants, com.skohscripts.kairos.data.TaskEdit(title = "Water the plants", priority = 1, points = 2, taskType = "maison", pinDay = kotlinx.datetime.LocalDate(2026, 9, 29)))
            // Le test tourne un mardi : une seule plage, réservée au travail.
            repository.updateSettings(
                repository.snapshot.value.settings.copy(
                    schedule = ScheduleSettings(
                        mapOf(1 to DaySchedule(windows = listOf(WorkWindow(9, 12, "Work")))),
                        listOf(WorkContext("Work", listOf("dev")), WorkContext("Home", listOf("maison"))),
                    ),
                ),
            )
        }
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        // Liste paresseuse : on défile jusqu'à la ligne de la tâche sans créneau.
        waitUntil(timeoutMillis = 15_000) { onAllNodes(hasScrollToIndexAction()).fetchSemanticsNodes().isNotEmpty() }
        onNode(hasScrollToIndexAction()).performScrollToNode(hasText("No Home window today", substring = true))
        waitText("No Home window today", substring = true)
        // La frise (colonne de droite en largeur bureau) nomme le contexte de la plage.
        waitText("Work")
    }
}
