package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/** Le lien gardé par l'appareil : la cible (chemin ou URI), le nom affiché, l'empreinte du dernier texte écrit ou adopté. */
data class SavedLink(val target: String, val name: String, val hash: String)

/** Ce que la plateforme fournit au fichier lié : choisir, lire, écrire, garder le lien (docs/spec/export-import.md). */
interface LinkedFileIo {
    /** Sélecteur « enregistrer » ; la cible choisie, `null` si annulé. */
    suspend fun pickCreate(suggestedName: String): String?

    /** Sélecteur « ouvrir » ; la cible choisie, `null` si annulé. */
    suspend fun pickOpen(): String?

    suspend fun read(target: String): String

    /** Écrit [text] d'un bloc (atomique là où la plateforme le permet). */
    suspend fun write(target: String, text: String)

    fun displayName(target: String): String

    suspend fun loadLink(): SavedLink?

    /** Garde le lien (`null` : délié). */
    suspend fun saveLink(link: SavedLink?)
}

/**
 * Fichier lié du bureau et d'Android (docs/spec/export-import.md § Fichier lié du bureau et d'Android) : écrit l'export
 * [DEBOUNCE_MS] après le dernier changement, jamais par-dessus une modification faite ailleurs (empreinte [LinkHash]).
 * [changed] se branche sur `onChanged` du dépôt ; [flush] à la mise en arrière-plan et à la fermeture.
 */
class LinkedFileSync(
    private val io: LinkedFileIo,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) : LinkedStorage {
    private val mutable = MutableStateFlow<LinkedFileState>(LinkedFileState.NotLinked)
    override val state: StateFlow<LinkedFileState> = mutable.asStateFlow()

    private val lock = Mutex()
    private var link: SavedLink? = null
    private var latest: KairosSnapshot? = null
    private var dirty = false
    private var timer: Job? = null
    private var pendingTarget: String? = null

    /** Relit le lien gardé ; [current] = l'état de la base à l'ouverture (rien n'est écrit tant qu'il n'a pas changé). */
    suspend fun start(current: KairosSnapshot) {
        latest = current
        link = runCatching { io.loadLink() }.getOrNull()
        mutable.value = link?.let { LinkedFileState.Linked(it.name) } ?: LinkedFileState.NotLinked
    }

    /** Nouvel état de la base : écriture programmée, une seule pour une rafale. */
    fun changed(snapshot: KairosSnapshot) {
        latest = snapshot
        dirty = true
        if (link == null) return
        timer?.cancel()
        timer = scope.launch {
            delay(DEBOUNCE_MS)
            writeIfDirty()
        }
    }

    /** Écrit tout de suite ce qui attend (arrière-plan, fermeture). */
    suspend fun flush() {
        timer?.cancel()
        writeIfDirty()
    }

    private suspend fun writeIfDirty() = lock.withLock {
        if (!dirty) return@withLock
        if (guardedWrite()) dirty = false
    }

    private fun encode(snapshot: KairosSnapshot) = ExportCodec.encode(snapshot, KairosBuild.VERSION_NAME, clock.now())

    /** Relit le fichier, écrit si c'est encore celui écrit en dernier ; `true` si écrit. Appelé sous [lock]. */
    private suspend fun guardedWrite(force: Boolean = false): Boolean {
        val current = link ?: return false
        val snapshot = latest ?: return false
        if (!force && mutable.value is LinkedFileState.Changed) return false
        val onDisk = try {
            io.read(current.target)
        } catch (e: Throwable) {
            mutable.value = LinkedFileState.WriteFailed(current.name)
            return false
        }
        if (!force && LinkHash.of(onDisk) != current.hash) {
            mutable.value = LinkedFileState.Changed(current.name)
            return false
        }
        return write(current, encode(snapshot))
    }

    private suspend fun write(target: SavedLink, text: String): Boolean {
        try {
            io.write(target.target, text)
        } catch (e: Throwable) {
            mutable.value = LinkedFileState.WriteFailed(target.name)
            return false
        }
        val saved = target.copy(hash = LinkHash.of(text))
        link = saved
        runCatching { io.saveLink(saved) }
        mutable.value = LinkedFileState.Linked(saved.name)
        return true
    }

    override suspend fun createFile(): Boolean {
        val target = io.pickCreate(SUGGESTED_NAME) ?: return false
        val snapshot = latest ?: return false
        return lock.withLock {
            write(SavedLink(target, io.displayName(target), ""), encode(snapshot)).also { if (it) dirty = false }
        }
    }

    override suspend fun openFile(): OpenedFile? {
        val target = io.pickOpen() ?: return null
        val text = io.read(target)
        pendingTarget = target
        return OpenedFile(io.displayName(target), text)
    }

    /** Lie le fichier ouvert ; son contenu est la référence : le remplacement des données qui suit y est écrit. */
    override suspend fun adoptOpened(): Boolean = lock.withLock {
        val target = pendingTarget ?: return@withLock false
        val text = runCatching { io.read(target) }.getOrElse { return@withLock false }
        val saved = SavedLink(target, io.displayName(target), LinkHash.of(text))
        link = saved
        runCatching { io.saveLink(saved) }
        mutable.value = LinkedFileState.Linked(saved.name)
        pendingTarget = null
        true
    }

    override suspend fun unlink() = lock.withLock {
        timer?.cancel()
        link = null
        runCatching { io.saveLink(null) }
        mutable.value = LinkedFileState.NotLinked
    }

    override suspend fun overwrite() {
        lock.withLock { if (guardedWrite(force = true)) dirty = false }
    }

    override suspend fun readLinked(): OpenedFile? {
        val current = link ?: return null
        return runCatching { OpenedFile(current.name, io.read(current.target)) }.getOrNull()
    }

    override suspend fun acknowledge(text: String) {
        lock.withLock {
            val current = link ?: return@withLock
            link = current.copy(hash = LinkHash.of(text))
            mutable.value = LinkedFileState.Linked(current.name)
        }
        flush()
    }

    override suspend fun retry() {
        lock.withLock {
            dirty = true
            if (mutable.value is LinkedFileState.WriteFailed) link?.let { mutable.value = LinkedFileState.Linked(it.name) }
            if (guardedWrite()) dirty = false
        }
    }

    companion object {
        /** Délai entre le dernier changement et l'écriture. */
        const val DEBOUNCE_MS = 2_000L

        const val SUGGESTED_NAME = "kairos.json"
    }
}
