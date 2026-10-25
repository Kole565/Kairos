package com.skohscripts.kairos.ui.app

import kotlinx.coroutines.flow.StateFlow

/**
 * Fichier lié (docs/spec/export-import.md § Version web et § Fichier lié du bureau et d'Android) : une copie des
 * données, au format d'export, tenue à jour dans un vrai fichier choisi par l'utilisateur. Sur le web, elle protège d'un
 * nettoyage du navigateur ; sur le bureau et Android ([LinkedFileSync]), elle sert un dossier synchronisé.
 */
interface LinkedStorage {
    val state: StateFlow<LinkedFileState>

    /** Version web : bandeau d'incitation tant que rien n'est lié, textes du navigateur. */
    val browser: Boolean get() = false

    /** Crée un nouveau fichier et y écrit les données actuelles. `false` si annulé. */
    suspend fun createFile(): Boolean

    /**
     * Choisit un fichier Kairos existant : rend son contenu (le nom avant),
     * sans rien changer encore ; `null` si annulé. [adoptOpened] le lie ensuite,
     * une fois le remplacement des données confirmé.
     */
    suspend fun openFile(): OpenedFile?

    suspend fun adoptOpened(): Boolean

    /**
     * Redonne l'accès au fichier lié (geste de l'utilisateur requis). Si le
     * navigateur avait perdu les données, les recharge depuis le fichier.
     */
    suspend fun authorize(): Boolean = false

    /** Cesse d'écrire dans le fichier, qui reste tel quel. */
    suspend fun unlink()

    /** Modifié ailleurs ([LinkedFileState.Changed]) : écrit quand même les données de cet appareil. */
    suspend fun overwrite()

    /** Le contenu actuel du fichier lié (« L'importer »), `null` s'il est illisible. */
    suspend fun readLinked(): OpenedFile?

    /** Les données de [text] viennent d'être importées : il redevient la référence, l'écriture reprend. */
    suspend fun acknowledge(text: String)

    /** Après un échec d'écriture : réessaie tout de suite. */
    suspend fun retry()
}

class OpenedFile(val name: String, val text: String)

sealed interface LinkedFileState {
    /** Navigateur sans API File System Access (Firefox, Safari) : export manuel seulement. */
    data object Unsupported : LinkedFileState

    data object NotLinked : LinkedFileState

    data class Linked(val name: String) : LinkedFileState

    /**
     * L'accès au fichier doit être réaccordé ; [restore] : les données du
     * navigateur ont disparu et attendent d'être rechargées depuis le fichier.
     */
    data class NeedsPermission(val name: String, val restore: Boolean) : LinkedFileState

    /** L'enregistrement dans le navigateur a échoué (espace plein, stockage refusé). */
    data class SaveFailed(val detail: String) : LinkedFileState

    /** Le fichier lié a été modifié ailleurs : plus rien n'y est écrit avant un choix (« Écraser », « L'importer », « Délier »). */
    data class Changed(val name: String) : LinkedFileState

    /** Écrire dans le fichier lié a échoué (dossier disparu, accès retiré) ; les données restent dans la base. */
    data class WriteFailed(val name: String) : LinkedFileState
}

/** Empreinte d'un texte écrit dans le fichier lié : longueur et FNV-1a 64 bits, en hexadécimal. Pas de cryptographie : on compare. */
object LinkHash {
    fun of(text: String): String {
        var h = -0x340d631b7bdddcdbL
        for (c in text) {
            h = h xor c.code.toLong()
            h *= 0x100000001b3L
        }
        return "${text.length}:${h.toULong().toString(16)}"
    }
}
