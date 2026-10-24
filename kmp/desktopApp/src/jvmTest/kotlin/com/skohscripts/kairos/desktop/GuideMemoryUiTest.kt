package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.guide.GuideChapter
import com.skohscripts.kairos.ui.guide.FeatureCatalog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.GuideSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.screens.HomeScreen
import com.skohscripts.kairos.ui.settings.SettingsCategory
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Ce que le guide retient (docs/spec/visite-guidee.md § Persistance, issue #94) : « Nouveau » masqué pour la version,
 * chapitres parcourus jusqu'au bout cochés dans le hub, invitation à la première tâche qui ne revient plus, et les deux
 * chemins pour repartir de zéro (« Revoir le guide depuis le début », « Restaurer les valeurs par défaut »).
 */
@OptIn(ExperimentalTestApi::class)
class GuideMemoryUiTest {
    private val now = Instant.parse("2026-09-29T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val saved = Locale.getDefault()
    private val memory = GuideSettings(newsHidden = "3.6", readChapters = listOf("PLAN"), firstTaskDone = true)

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(settings: Settings = Settings()): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        repository.replaceAll(KairosSnapshot(settings = settings))
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock)
    }

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeUiTest.count(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    private fun ComposeUiTest.openHub() {
        onNodeWithContentDescription("Help").performClick()
        onNodeWithText("Discover Kairos").performClick()
        waitText("Create my first task")
    }

    @Test
    fun hidingTheNewsCallsTheShellAndAHiddenSectionStaysAway() = runComposeUiTest {
        // La 3.6.0 a ses cartes « Nouveau » (catalogue) : la section s'affiche, « Hide » prévient la coquille qui retient.
        var hidden by mutableStateOf(false)
        setContent { KairosTheme { HomeScreen(false, {}, {}, {}, version = "3.6.0", newsHidden = hidden, onHideNews = { hidden = true }) } }
        waitText("New in version 3.6.0")
        onNodeWithText("Hide").performClick()
        waitUntil(timeoutMillis = 15_000) { count("New in version 3.6.0") == 0 }
        assertEquals(true, hidden)
    }

    @Test
    fun aChapterReadToTheEndIsCheckedInTheHub() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openHub()
        // Ouvert puis refermé avant la fin : pas « lu ». Nombre de cartes tiré du catalogue.
        val plan = FeatureCatalog.chapter(GuideChapter.PLAN).size
        onNodeWithText("Plan").performClick()
        waitText("Step 1 of $plan")
        onNodeWithText("Skip the tour").performClick()
        assertEquals(null, services.repository.snapshot.value.settings.guide)
        openHub()
        onNodeWithText("Plan").performClick()
        waitText("Step 1 of $plan")
        repeat(plan - 1) { onNodeWithText("Next").performClick() }
        onNodeWithText("Finish").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.guide?.readChapters == listOf("PLAN") }
        openHub()
        waitText("$plan cards · read")
    }

    @Test
    fun theInvitationDoesNotComeBackOnceTheWalkthroughWasDone() = runComposeUiTest {
        val services = services(Settings(guide = GuideSettings(firstTaskDone = true)))
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        waitText("Today, in order")
        assertEquals(0, count("Create your first task"))
    }

    @Test
    fun startingTheGuideOverClearsItAtOnceAndCanBeUndone() = runComposeUiTest {
        val services = services(Settings(guide = memory))
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.ABOUT)
        waitText("See the guide again from the start")
        onNodeWithText("Start over").performScrollTo().performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.guide == null }
        // Plus rien à remettre à zéro : le bouton s'éteint ; « Undo » rend l'état d'avant.
        onNodeWithText("Start over").assertIsNotEnabled()
        waitText("Undo")
        onNodeWithText("Undo").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.guide == memory }
    }

    @Test
    fun restoringTheDefaultsAlsoClearsTheGuideOnSave() = runComposeUiTest {
        val services = services(Settings(guide = memory))
        val repository = services.repository
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Restore default settings")
        onNodeWithText("Restore default settings").performScrollTo().performClick()
        // Tous les champs valent déjà leur défaut : seul le guide rend le formulaire modifié. Rien n'est écrit avant « Save ».
        waitText("Unsaved changes")
        assertEquals(memory, repository.snapshot.value.settings.guide)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { repository.snapshot.value.settings.guide == null }
    }
}
