package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.data.KairosStore
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.AppServices
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.TeamDestination
import com.skohscripts.kairos.ui.settings.SettingsCategory
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Locale
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cartes du bureau (docs/spec/bureau-avance.md, issue #115) : dossier des données et journal de diagnostic. */
@OptIn(ExperimentalTestApi::class)
class DesktopSettingsUiTest {
    private val saved = Locale.getDefault()
    private val home = createTempDirectory("home").toFile()
    private val root = File(home, "Kairos")
    private val default = File(root, "v3").apply { mkdirs(); File(this, DataFolder.DATABASE).writeText("base") }
    private val target = File(home, "Sync").absolutePath

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    private fun services(): AppServices = runBlocking {
        val repository = KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() })
        val files = object : FileService {
            override suspend fun saveText(suggestedName: String, text: String) = false
            override suspend fun openText(): String? = null
        }
        val folder = object : com.skohscripts.kairos.ui.app.DataFolderService by DesktopDataFolder(
            DataDirectory.Resolved(default, DataDirectory.Source.DEFAULT, default), root,
        ) {
            override suspend fun pick(): String = target
        }
        AppServices(repository, files, { _, _ -> }, dataFolder = folder, diagnostics = FileDiagnostics(default))
    }

    private fun ComposeUiTest.waitText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 15_000) { onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun choosingAnEmptyFolderSaysTheDataWillBeCopiedAtTheNextStart() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.DATA)
        waitText("Data folder")
        onNodeWithText("Change folder…").performScrollTo().performClick()
        waitText("will copy your data to $target", substring = true)
        onNodeWithText("Confirm").performClick()
        waitText("Restart Kairos to use: $target")
        assertEquals(listOf(target, "copy-from=${default.path}"), File(root, DataFolder.POINTER).readLines())
    }

    @Test
    fun theDiagnosticLogIsOffByDefaultAndCanBeTurnedOn() = runComposeUiTest {
        val services = services()
        setContent { KairosApp(Platform.DESKTOP, Destination.DAY, TeamDestination.BOARD) { services } }
        openSettings(SettingsCategory.ABOUT)
        waitText("Keep a diagnostic log")
        assertTrue(!File(default, FileDiagnostics.FLAG).exists())
        onNodeWithText("Keep a diagnostic log").performScrollTo().performClick()
        waitUntil(timeoutMillis = 5_000) { File(default, FileDiagnostics.FLAG).exists() }
        onNodeWithText("Copy diagnostics").performScrollTo().performClick()
        // Copié, ou presse-papiers indisponible dans la session de test : un message le dit dans les deux cas.
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithText("Diagnostics copied", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("file", substring = true, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
