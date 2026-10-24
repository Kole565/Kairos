package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.DataFolderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

/**
 * Dossier des données choisi dans les Réglages (docs/spec/bureau-avance.md § Dossier des données) : retenu dans
 * `data-location.txt` de la racine Kairos (hors du dossier des données), appliqué au **prochain démarrage**. Première ligne :
 * le dossier ; deuxième, facultative, `copy-from=<dossier>` : la base à y copier s'il n'en a pas. Jamais de déplacement.
 */
object DataFolder {
    const val POINTER = "data-location.txt"
    const val DATABASE = "kairos.db"
    private const val COPY_FROM = "copy-from="

    /** Le dossier [dir] contient une base Kairos. */
    fun hasData(dir: File): Boolean = File(dir, DATABASE).isFile

    /**
     * Au démarrage, avant toute ouverture : lit le choix, fait la copie en attente, et rend le dossier à ouvrir. Échec (dossier non
     * créable, copie impossible) : le choix est oublié, Kairos ouvre le dossier source et [DataDirectory.Resolved.failed] le dit.
     */
    fun applyPending(root: File, default: File): DataDirectory.Resolved {
        val pointer = File(root, POINTER)
        val lines = runCatching { pointer.takeIf { it.isFile }?.readLines() }.getOrNull()?.map { it.trim() }?.filter { it.isNotEmpty() }
        val chosen = lines?.firstOrNull()?.let(::File) ?: return DataDirectory.Resolved(default, DataDirectory.Source.DEFAULT, default)
        val source = lines.getOrNull(1)?.takeIf { it.startsWith(COPY_FROM) }?.substringAfter(COPY_FROM)?.let(::File)
        val ok = runCatching {
            chosen.mkdirs()
            check(chosen.isDirectory && chosen.canWrite()) { "dossier inutilisable" }
            if (source != null && !hasData(chosen) && hasData(source)) copyDatabase(source, chosen)
            if (source != null) pointer.writeText(chosen.path + "\n")
        }.isSuccess
        if (ok) return DataDirectory.Resolved(chosen, DataDirectory.Source.CHOSEN, default)
        runCatching { pointer.delete() }
        val fallback = source?.takeIf { hasData(it) } ?: default
        return DataDirectory.Resolved(fallback, if (fallback == default) DataDirectory.Source.DEFAULT else DataDirectory.Source.CHOSEN, default, failed = chosen)
    }

    /** Copie `kairos.db` et ses fichiers de journal SQLite (`-wal`, `-shm`) : la base n'est pas encore ouverte. */
    private fun copyDatabase(from: File, to: File) {
        for (suffix in listOf("", "-wal", "-shm")) {
            val file = File(from, DATABASE + suffix)
            if (file.isFile) file.copyTo(File(to, DATABASE + suffix), overwrite = false)
        }
    }

    /**
     * Retient [target] pour le prochain démarrage, depuis le dossier ouvert [current] : rien si c'est le même ; le dossier par
     * défaut efface le choix (et demande la copie s'il n'a pas de base) ; sinon le choix, avec la copie si [target] n'a pas de base.
     */
    fun choose(root: File, default: File, current: File, target: File) {
        val pointer = File(root, POINTER)
        val t = target.absoluteFile
        fun write(copyFrom: File?) {
            root.mkdirs()
            pointer.writeText(t.path + "\n" + (copyFrom?.let { "$COPY_FROM${it.path}\n" } ?: ""))
        }
        when {
            // Le dossier déjà ouvert : le choix en attente est annulé.
            t == current.absoluteFile -> if (t == default.absoluteFile) pointer.delete() else write(null)
            t == default.absoluteFile && hasData(t) -> pointer.delete()
            else -> write(current.takeUnless { hasData(t) })
        }
    }

    /** Le dossier retenu pour le prochain démarrage, s'il diffère de [current]. */
    fun pending(root: File, default: File, current: File): File? {
        val first = runCatching { File(root, POINTER).takeIf { it.isFile }?.readLines()?.firstOrNull()?.trim() }.getOrNull()
        val next = first?.takeIf { it.isNotEmpty() }?.let(::File) ?: default
        return next.takeIf { it.absoluteFile != current.absoluteFile }
    }
}

/** [DataFolderService] du bureau : le dossier résolu au démarrage ([resolved]) et la racine Kairos où vit le choix. */
class DesktopDataFolder(private val resolved: DataDirectory.Resolved, private val root: File) : DataFolderService {
    override val current: String = resolved.dir.absolutePath
    override val defaultPath: String = resolved.default.absolutePath
    override val fixedAtLaunch: Boolean = resolved.source == DataDirectory.Source.ARGUMENT || resolved.source == DataDirectory.Source.ENVIRONMENT
    override val failed: String? = resolved.failed?.absolutePath
    private val pendingFlow = MutableStateFlow(if (fixedAtLaunch) null else DataFolder.pending(root, resolved.default, resolved.dir)?.absolutePath)
    override val pending: StateFlow<String?> = pendingFlow

    override fun hasData(path: String): Boolean = DataFolder.hasData(File(path))

    override suspend fun pick(): String? = withContext(Dispatchers.IO) {
        var chosen: String? = null
        SwingUtilities.invokeAndWait {
            val chooser = JFileChooser(resolved.dir.parentFile).apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile?.absolutePath
        }
        chosen
    }

    override suspend fun choose(path: String) {
        if (fixedAtLaunch) return
        withContext(Dispatchers.IO) { DataFolder.choose(root, resolved.default, resolved.dir, File(path)) }
        pendingFlow.value = DataFolder.pending(root, resolved.default, resolved.dir)?.absolutePath
    }
}
