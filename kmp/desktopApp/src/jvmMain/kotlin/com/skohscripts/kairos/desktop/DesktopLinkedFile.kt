package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.LinkedFileIo
import com.skohscripts.kairos.ui.app.SavedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.awt.FileDialog
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Fichier lié du bureau (docs/spec/export-import.md § Fichier lié du bureau et d'Android) : boîtes de dialogue du système,
 * écriture atomique (fichier temporaire du même dossier puis déplacement : un outil de synchronisation ne voit jamais un
 * fichier à moitié écrit), lien gardé dans `linked-file.json` du dossier des données (propre à cet ordinateur).
 */
class DesktopLinkedFile(
    private val dataDir: File,
    private val choose: suspend (mode: Int, name: String?) -> File? = { mode, name -> DialogFiles.choose(mode, name) },
) : LinkedFileIo {
    @Serializable
    private data class LinkJson(val target: String, val name: String, val hash: String)

    private val json = Json { ignoreUnknownKeys = true }
    private val linkFile get() = File(dataDir, LINK_FILE)

    override suspend fun pickCreate(suggestedName: String): String? = choose(FileDialog.SAVE, suggestedName)?.absolutePath

    override suspend fun pickOpen(): String? = choose(FileDialog.LOAD, null)?.absolutePath

    override suspend fun read(target: String): String = withContext(Dispatchers.IO) { File(target).readText() }

    override suspend fun write(target: String, text: String) = withContext(Dispatchers.IO) {
        val file = File(target)
        val tmp = File(file.absoluteFile.parentFile, ".${file.name}.kairos-tmp")
        tmp.writeText(text)
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            tmp.delete()
        }
        Unit
    }

    override fun displayName(target: String): String = File(target).name

    override suspend fun loadLink(): SavedLink? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(LinkJson.serializer(), linkFile.readText()) }.getOrNull()?.let { SavedLink(it.target, it.name, it.hash) }
    }

    override suspend fun saveLink(link: SavedLink?) = withContext(Dispatchers.IO) {
        if (link == null) {
            linkFile.delete()
        } else {
            dataDir.mkdirs()
            linkFile.writeText(json.encodeToString(LinkJson.serializer(), LinkJson(link.target, link.name, link.hash)))
        }
        Unit
    }

    companion object {
        const val LINK_FILE = "linked-file.json"
    }
}
