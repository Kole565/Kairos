package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.staticCompositionLocalOf

/** Affiche un message bref (snackbar de la coquille) : confirmation d'export, erreur d'import… */
val LocalMessages = staticCompositionLocalOf<(String) -> Unit> { {} }

/**
 * Message bref avec une action (« Annuler ») : le texte, le libellé de l'action et ce qu'elle fait. Sans effet par défaut
 * (écrans testés seuls) ; la coquille le fournit avec son snackbar.
 */
val LocalUndoMessages = staticCompositionLocalOf<(String, String, () -> Unit) -> Unit> { { _, _, _ -> } }
