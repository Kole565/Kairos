package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Langue de l'interface (docs/spec/i18n.md § Choix de la langue, issue #70) : le système avec repli anglais, le
 * choix de l'utilisateur, et le changement en direct sans quitter l'écran.
 */
@OptIn(ExperimentalTestApi::class)
class LanguageUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-09-29T07:00:00Z")
    }
    private val saved = Locale.getDefault()

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    /** Services avec les mêmes crochets de langue que le bureau : [system] est la langue du système, déjà posée par défaut. */
    private fun services(settings: Settings, system: String): AppServices = runBlocking {
        Locale.setDefault(Locale.forLanguageTag(system))
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot(settings = settings) }, clock)
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(
            repository, files, { _, _ -> }, clock = clock,
            systemLanguage = system,
            applyLanguage = { Locale.setDefault(Locale.forLanguageTag(it)) },
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) {
            onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    private fun androidx.compose.ui.test.ComposeUiTest.hasText(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aSystemLanguageOtherThanFrenchFallsBackToEnglish() = runComposeUiTest {
        val services = services(Settings(), "ru")
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("What is Kairos for?")
        check(!hasText("Accueil")) { "l'interface devrait être en anglais" }
    }

    @Test
    fun aFrenchSystemGetsFrench() = runComposeUiTest {
        val services = services(Settings(), "fr")
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Accueil")
        check(!hasText("Home")) { "l'interface devrait être en français" }
    }

    @Test
    fun theChosenLanguageBeatsTheSystem() = runComposeUiTest {
        val services = services(Settings(language = Settings.LANGUAGE_FR), "en")
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Accueil")
    }

    @Test
    fun changingTheLanguageSwitchesTheInterfaceLive() = runComposeUiTest {
        val services = services(Settings(), "en")
        setContent { KairosApp(Platform.DESKTOP) { services } }
        waitText("Home")
        runBlocking { services.repository.updateSettings(services.repository.snapshot.value.settings.copy(language = Settings.LANGUAGE_FR)) }
        waitText("Accueil")
        runBlocking { services.repository.updateSettings(services.repository.snapshot.value.settings.copy(language = Settings.LANGUAGE_SYSTEM)) }
        // « Système » revient à la langue du système (ici l'anglais), pas à celle de la surcharge précédente.
        waitText("Home")
    }
}
