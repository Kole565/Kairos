package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Page de démarrage (docs/spec/demarrage.md) : horloge de test pilotée à la main
 * (`autoAdvance = false`) pour vérifier les durées, puis rendue automatique pour attendre l'application.
 */
@OptIn(ExperimentalTestApi::class)
class StartupSplashUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
    }
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(firstLaunch: Boolean = false): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, clock = clock, firstLaunch = firstLaunch)
    }

    private fun ComposeUiTest.splashShown() = onAllNodesWithTag("startup-splash").fetchSemanticsNodes().isNotEmpty()

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun theSplashPlaysThenFadesIntoTheApp() = runComposeUiTest {
        val services = services()
        mainClock.autoAdvance = false
        setContent { KairosApp(Platform.DESKTOP, splash = true) { services } }
        mainClock.advanceTimeBy(1_000)
        assertTrue(splashShown(), "la page est encore là à 1 s")
        mainClock.advanceTimeBy(1_500)
        assertTrue(splashShown(), "la page est encore là à 2,5 s (pause sur le logo, fondu)")
        mainClock.advanceTimeBy(1_000)
        assertTrue(!splashShown(), "la page a disparu après ≈ 3,1 s")
        mainClock.autoAdvance = true
        // L'application ouverte sur l'Accueil (docs/spec/accueil.md).
        waitText("What is Kairos for?")
    }

    @Test
    fun aTapSkipsTheSplash() = runComposeUiTest {
        val services = services()
        mainClock.autoAdvance = false
        setContent { KairosApp(Platform.DESKTOP, splash = true) { services } }
        mainClock.advanceTimeBy(300)
        onNodeWithTag("startup-splash").performClick()
        // Fondu seul (0,5 s) : bien avant la fin du tour de l'aiguille (2,2 s).
        mainClock.advanceTimeBy(800)
        assertTrue(!splashShown(), "un toucher abrège la page")
    }

    @Test
    fun theWelcomeWaitsForTheSplash() = runComposeUiTest {
        val services = services(firstLaunch = true)
        mainClock.autoAdvance = false
        setContent { KairosApp(Platform.DESKTOP, splash = true) { services } }
        mainClock.advanceTimeBy(1_500)
        assertTrue(splashShown())
        assertTrue(onAllNodesWithText("Welcome to Kairos").fetchSemanticsNodes().isEmpty(), "pas d'accueil par-dessus la page")
        mainClock.advanceTimeBy(2_000)
        assertTrue(!splashShown())
        mainClock.autoAdvance = true
        waitText("Welcome to Kairos")
    }
}
