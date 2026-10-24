package com.skohscripts.kairos.ui.app

import kotlinx.coroutines.flow.StateFlow

/**
 * Dossier des données du bureau (docs/spec/bureau-avance.md § Dossier des données) : celui qui est ouvert, celui choisi pour le
 * prochain démarrage, et le moyen d'en changer. Le bureau seul le fournit ; ailleurs, `AppServices.dataFolder` est nul.
 */
interface DataFolderService {
    /** Dossier ouvert. */
    val current: String

    /** Dossier par défaut du système. */
    val defaultPath: String

    /** Le dossier est fixé au lancement (`--data-dir=` ou `KAIROS_DATA_DIR`) : on ne le change pas d'ici. */
    val fixedAtLaunch: Boolean

    /** Dossier choisi pour le prochain démarrage, `null` s'il n'y en a pas. */
    val pending: StateFlow<String?>

    /** Dossier choisi qui n'a pas pu servir à ce démarrage, `null` sinon. */
    val failed: String?

    /** Le dossier [path] contient déjà une base Kairos. */
    fun hasData(path: String): Boolean

    /** Sélecteur de dossiers du système ; `null` si l'on annule. */
    suspend fun pick(): String?

    /** Retient [path] pour le prochain démarrage (copie des données s'il n'en a pas) ; lève une exception en cas d'échec. */
    suspend fun choose(path: String)
}

/**
 * Journal de diagnostic du bureau (docs/spec/bureau-avance.md § Journal de diagnostic) : local, désactivé par défaut, sans
 * contenu de tâche. [log] n'écrit rien quand il est éteint et n'échoue jamais.
 */
interface Diagnostics {
    val enabled: StateFlow<Boolean>

    fun setEnabled(on: Boolean)

    /** Un événement en vocabulaire fixe (« export ok », « import report ok »…), jamais du texte saisi. */
    fun log(event: String)

    /** Texte à copier : système, fin du journal, fin des rapports de plantage. */
    fun report(): String
}
