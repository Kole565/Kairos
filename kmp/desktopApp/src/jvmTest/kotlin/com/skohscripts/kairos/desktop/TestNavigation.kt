package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick

/**
 * Ouvre les Réglages par la roue crantée de la barre du haut (écran secondaire depuis le 2026-10-10,
 * docs/spec/navigation-theme.md). 15 s comme les autres attentes des tests d'interface (runner de CI à deux cœurs).
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openSettings() {
    waitUntil(conditionDescription = "roue crantée « Settings »", timeoutMillis = 15_000) {
        onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithContentDescription("Settings").performClick()
}
