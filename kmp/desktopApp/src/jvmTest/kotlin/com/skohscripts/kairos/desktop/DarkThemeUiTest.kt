package com.skohscripts.kairos.desktop

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Thème sombre (docs/spec/apparence.md § Thème sombre, issue #72) : Kairos suit le système, l'interrupteur à deux états
 * choisit l'inverse, et le rebasculer rend « suivre le système ». Le fond est lu sur l'image rendue (coin haut gauche).
 */
@OptIn(ExperimentalTestApi::class)
class DarkThemeUiTest {
    private val clock = object : Clock {
        override fun now() = Instant.parse("2026-10-22T07:00:00Z")
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

    private fun ComposeUiTest.waitText(text: String) =
        waitUntil(conditionDescription = "texte « $text »", timeoutMillis = 15_000) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    /** Le fond de l'interface est sombre (luminance du coin haut gauche, sur la surface). */
    private fun ComposeUiTest.dark(): Boolean = onRoot().captureToImage().toPixelMap()[2, 2].luminance() < 0.2f

    private fun ComposeUiTest.waitDark(dark: Boolean) =
        waitUntil(conditionDescription = if (dark) "fond sombre" else "fond clair", timeoutMillis = 15_000) { dark() == dark }

    private fun ComposeUiTest.toggle() = onNode(hasText("Dark theme") and isToggleable()).performScrollTo()

    @Test
    fun onALightSystemTheSwitchChoosesDarkThenFollowsTheSystemAgain() = runComposeUiTest {
        val services = services()
        val themes = mutableListOf<Boolean>()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, systemDark = false, onDarkTheme = { themes += it }) { services } }
        openSettings(SettingsCategory.GENERAL)
        waitText("Follows the system.")
        waitDark(false)
        toggle().assertIsOff().performClick()
        waitText("Chosen here: switch it back to follow the system again.")
        // Rien ne change avant « Enregistrer ».
        assertEquals(Settings.THEME_MODE_SYSTEM, services.repository.snapshot.value.settings.themeMode)
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.themeMode == Settings.THEME_MODE_DARK }
        waitDark(true)
        waitUntil(timeoutMillis = 15_000) { themes.lastOrNull() == true }

        // Rebasculé vers le thème du système : on le suit à nouveau, aucune valeur « light » n'est figée.
        toggle().assertIsOn().performClick()
        waitText("Follows the system.")
        onNodeWithText("Save").performClick()
        waitUntil(timeoutMillis = 15_000) { services.repository.snapshot.value.settings.themeMode == Settings.THEME_MODE_SYSTEM }
        waitDark(false)
        waitUntil(timeoutMillis = 15_000) { themes.lastOrNull() == false }
    }

    @Test
    fun onADarkSystemKairosIsDarkWithoutAnySetting() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, systemDark = true) { services } }
        openSettings(SettingsCategory.GENERAL)
        waitText("Follows the system.")
        waitDark(true)
        toggle().assertIsOn()
        assertEquals(Settings.THEME_MODE_SYSTEM, services.repository.snapshot.value.settings.themeMode)
    }
}
