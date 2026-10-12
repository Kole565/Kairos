package com.skohscripts.kairos.ui.day

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * À poser sur un champ de saisie de la vue Jour : les raccourcis de l'écran (`N`, `/`, docs/spec/vue-jour.md) ne
 * s'y déclenchent pas. Le champ lit d'abord la touche ; si elle remonte (le caractère n'arrive qu'après, en
 * « KeyTyped »), elle est arrêtée ici au lieu d'atteindre l'écran, qui renvoyait « n » vers « Nouvelle tâche » et
 * faisait perdre le titre du créneau en cours de saisie. Sans effet sur la saisie elle-même.
 */
internal fun Modifier.quietShortcuts(): Modifier = onKeyEvent { e ->
    e.type == KeyEventType.KeyDown && !e.isCtrlPressed && !e.isMetaPressed && !e.isAltPressed && (e.key == Key.N || e.key == Key.Slash)
}
