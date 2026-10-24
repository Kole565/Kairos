package com.skohscripts.kairos.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.Secondary
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * APK installé à la main (docs/spec/mises-a-jour.md § Android, issue #143) : Réglages → Général montre la version et « See
 * the latest version », qui ouvre la page de téléchargement anglaise ; installé par un magasin, la carte n'existe pas.
 */
@OptIn(ExperimentalTestApi::class)
class DownloadPageUiTest {
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(downloadPage: Boolean): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() })
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        AppServices(repository, files, { _, _ -> }, downloadPage = downloadPage)
    }

    @Test
    fun aHandInstalledApkLinksToTheDownloadPage() = runComposeUiTest {
        val opened = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        }
        val services = services(downloadPage = true)
        setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                KairosApp(Platform.ANDROID, Destination.DAY, TeamDestination.BOARD, initialSecondary = Secondary.SETTINGS) { services }
            }
        }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("General").fetchSemanticsNodes().isNotEmpty() }
        onAllNodesWithText("Language, appearance, updates").fetchSemanticsNodes().isNotEmpty().let { assertEquals(true, it) }
        onNodeWithTag("settings-category-GENERAL").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("See the latest version").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithText("Installed version: ${KairosBuild.VERSION_NAME}", substring = true).assertExists()
        onNodeWithText("See the latest version").performScrollTo().performClick()
        waitForIdle()
        assertEquals(listOf("https://skohscripts.github.io/Kairos/en/"), opened)
    }

    @Test
    fun aStoreInstallHasNoCard() = runComposeUiTest {
        val services = services(downloadPage = false)
        setContent { KairosApp(Platform.ANDROID, Destination.DAY, TeamDestination.BOARD, initialSecondary = Secondary.SETTINGS) { services } }
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("General").fetchSemanticsNodes().isNotEmpty() }
        onNodeWithTag("settings-category-GENERAL").performClick()
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(0, onAllNodesWithText("See the latest version").fetchSemanticsNodes().size)
    }
}
