package com.skohscripts.kairos.ui.app

import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.Composable

/**
 * À poser sur un champ multiligne : Tab (Maj+Tab) passe au champ suivant (précédent) au lieu d'insérer une tabulation,
 * qui piégeait le clavier (docs/spec/accessibilite.md). Les autres touches, et Ctrl/Alt+Tab, ne sont pas touchées.
 */
@Composable
fun Modifier.tabLeavesField(): Modifier {
    val focus = LocalFocusManager.current
    return onPreviewKeyEvent { e ->
        if (e.key != Key.Tab || e.isCtrlPressed || e.isAltPressed) return@onPreviewKeyEvent false
        if (e.type == KeyEventType.KeyDown) focus.moveFocus(if (e.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
        true
    }
}
