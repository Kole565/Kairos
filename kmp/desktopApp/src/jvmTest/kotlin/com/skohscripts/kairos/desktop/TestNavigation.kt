package com.skohscripts.kairos.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.skohscripts.kairos.ui.settings.SettingsCategory

/**
 * Ouvre les Réglages par la roue crantée de la barre du haut (écran secondaire depuis le 2026-10-10,
 * docs/spec/navigation-theme.md), puis, si elle est donnée, la [category] (docs/spec/reglages.md). 15 s comme les
 * autres attentes des tests d'interface (runner de CI à deux cœurs).
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openSettings(category: SettingsCategory? = null) {
    waitUntil(conditionDescription = "roue crantée « Settings »", timeoutMillis = 15_000) {
        onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithContentDescription("Settings").performClick()
    category?.let { openSettingsCategory(it) }
}

/** Ouvre une catégorie des Réglages par sa ligne dans la liste (balise `settings-category-<NOM>`). */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.openSettingsCategory(category: SettingsCategory) {
    val tag = "settings-category-${category.name}"
    waitUntil(conditionDescription = "catégorie « ${category.name} »", timeoutMillis = 15_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
    onNodeWithTag(tag).performScrollTo().performClick()
}
