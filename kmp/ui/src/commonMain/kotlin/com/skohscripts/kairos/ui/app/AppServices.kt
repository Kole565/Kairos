package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.data.KairosRepository
import com.skohscripts.kairos.ui.team.forecast.TeamUiState
import kotlin.time.Clock

/**
 * Ce que chaque plateforme fournit à l'interface commune
 * (docs/spec/architecture.md § Graphe) : la base ouverte, l'accès aux
 * fichiers, les sauvegardes, et l'emplacement des données à afficher.
 */
class AppServices(
    val repository: KairosRepository,
    val files: FileService,
    val backups: BackupStore,
    /** Emplacement lisible des données (chemin sur le bureau), `null` si sans objet. */
    val dataLocation: String? = null,
    /** Version web seulement : fichier lié (docs/spec/export-import.md § Version web). */
    val linkedStorage: LinkedStorage? = null,
    val clock: Clock = Clock.System,
    /** Notifications du chrono (docs/spec/temps-reel-chrono.md). */
    val notifier: ChronoNotifier = NoNotifier,
    /** La base vient d'être créée : accueil du premier lancement (docs/spec/accueil.md). */
    val firstLaunch: Boolean = false,
    /** Bureau : vérification des nouvelles versions (docs/spec/mises-a-jour.md). */
    val updates: UpdateService? = null,
    /** Bureau, copie portable seulement : raccourci (docs/spec/raccourci-portable.md). */
    val shortcuts: ShortcutService? = null,
    /** Espace Équipe : sélection du Backlog, choix et résultats des Prévisions, graine injectable (tests). */
    val teamUi: TeamUiState = TeamUiState(),
    /**
     * Langue du système (`fr`, `ru`…), lue par la plateforme **avant** toute surcharge (docs/spec/i18n.md § Choix de
     * la langue) ; `null` : celle que Compose annonce au premier affichage (tests d'un écran seul).
     */
    val systemLanguage: String? = null,
    /**
     * Applique la langue d'interface résolue (`fr` ou `en`) à la plateforme, **avant** que l'interface la lise :
     * locale par défaut de la JVM (bureau, Android) ou du navigateur (web). Sans effet par défaut (tests).
     */
    val applyLanguage: (String) -> Unit = {},
)

/** Choix d'un fichier par l'utilisateur (boîtes de dialogue du système). */
interface FileService {
    /** Propose d'enregistrer [text] ; `false` si l'utilisateur annule. */
    suspend fun saveText(suggestedName: String, text: String): Boolean

    /** Propose d'ouvrir un fichier texte ; `null` si l'utilisateur annule. */
    suspend fun openText(): String?
}

/** Sauvegardes automatiques (avant un import) : garde les plus récentes. */
fun interface BackupStore {
    suspend fun save(name: String, text: String)
}
