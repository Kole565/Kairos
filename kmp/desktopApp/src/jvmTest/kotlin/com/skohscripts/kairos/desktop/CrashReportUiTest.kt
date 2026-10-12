package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import com.skohscripts.kairos.ui.app.CrashReportScreen
import com.skohscripts.kairos.ui.theme.KairosTheme
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Écran du rapport de plantage (docs/spec/demarrage.md § Rapport de plantage, issue #65). */
@OptIn(ExperimentalTestApi::class)
class CrashReportUiTest {
    private val saved = Locale.getDefault()

    @BeforeTest
    fun english() = Locale.setDefault(Locale.ENGLISH)

    @AfterTest
    fun restore() = Locale.setDefault(saved)

    @Test
    fun theReportIsShownAndBothActionsAreReachable() = runComposeUiTest {
        var shared = 0
        var continued = 0
        setContent {
            KairosTheme { CrashReportScreen("Kairos 3.3.0\njava.lang.IllegalStateException: boom", { shared++ }, { continued++ }) }
        }
        onNodeWithText("Kairos stopped unexpectedly").assertExists()
        onNodeWithText("Kairos 3.3.0", substring = true).assertExists()
        onNodeWithText("Share the report").performClick()
        assertEquals(1, shared)
        assertEquals(0, continued)
        onNodeWithText("Open Kairos").performClick()
        assertEquals(1, continued)
    }
}
