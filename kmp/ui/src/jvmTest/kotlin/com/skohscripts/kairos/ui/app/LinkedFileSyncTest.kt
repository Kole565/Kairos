package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Note
import com.skohscripts.kairos.data.ExportCodec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Fichier lié du bureau et d'Android (docs/spec/export-import.md § Fichier lié) : délai, garde, échec, lien gardé. */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkedFileSyncTest {
    private val now = Instant.parse("2026-10-25T08:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }

    /** Un « disque » en mémoire : fichiers par cible, lien gardé, nombre d'écritures, panne à la demande. */
    private class FakeIo : LinkedFileIo {
        val files = mutableMapOf<String, String>()
        var saved: SavedLink? = null
        var writes = 0
        var broken = false
        var next: String? = "/sync/kairos.json"

        override suspend fun pickCreate(suggestedName: String) = next
        override suspend fun pickOpen() = next
        override suspend fun read(target: String): String {
            check(!broken) { "dossier disparu" }
            return files[target] ?: error("absent")
        }
        override suspend fun write(target: String, text: String) {
            check(!broken) { "dossier disparu" }
            files[target] = text
            writes++
        }
        override fun displayName(target: String) = target.substringAfterLast('/')
        override suspend fun loadLink() = saved
        override suspend fun saveLink(link: SavedLink?) {
            saved = link
        }
    }

    private fun snapshot(n: Int) = KairosSnapshot(notes = (1..n).map { Note(it.toLong(), "note $it", createdAt = now, updatedAt = now) })

    @Test
    fun aBurstOfChangesGivesOneWriteAfterTheDelay() = runTest {
        val io = FakeIo()
        val sync = LinkedFileSync(io, backgroundScope, clock)
        sync.start(snapshot(0))
        assertTrue(sync.createFile())
        assertEquals(1, io.writes)
        assertEquals(LinkedFileState.Linked("kairos.json"), sync.state.value)
        for (n in 1..5) {
            sync.changed(snapshot(n))
            advanceTimeBy(500)
        }
        runCurrent()
        assertEquals(1, io.writes)
        advanceTimeBy(LinkedFileSync.DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(2, io.writes)
        // Le fichier écrit est un export valide de la dernière base.
        assertEquals(snapshot(5).notes, ExportCodec.decode(io.files.getValue("/sync/kairos.json")).notes)
    }

    @Test
    fun flushWritesAtOnceAndTheLinkIsFoundAgainAtStartup() = runTest {
        val io = FakeIo()
        val sync = LinkedFileSync(io, backgroundScope, clock)
        sync.start(snapshot(0))
        sync.createFile()
        sync.changed(snapshot(1))
        sync.flush()
        assertEquals(2, io.writes)
        // Rien à écrire : flush ne réécrit pas.
        sync.flush()
        assertEquals(2, io.writes)
        val again = LinkedFileSync(io, backgroundScope, clock)
        again.start(snapshot(1))
        assertEquals(LinkedFileState.Linked("kairos.json"), again.state.value)
        assertEquals(LinkHash.of(io.files.getValue("/sync/kairos.json")), io.saved!!.hash)
    }

    @Test
    fun aFileChangedElsewhereIsNeverOverwrittenWithoutAChoice() = runTest {
        val io = FakeIo()
        val sync = LinkedFileSync(io, backgroundScope, clock)
        sync.start(snapshot(0))
        sync.createFile()
        // Un autre appareil écrit le fichier.
        val foreign = ExportCodec.encode(snapshot(9), "3.10.0", now)
        io.files["/sync/kairos.json"] = foreign
        sync.changed(snapshot(1))
        sync.flush()
        assertIs<LinkedFileState.Changed>(sync.state.value)
        assertEquals(foreign, io.files.getValue("/sync/kairos.json"))
        // Les changements suivants n'écrivent pas non plus.
        sync.changed(snapshot(2))
        sync.flush()
        assertEquals(foreign, io.files.getValue("/sync/kairos.json"))

        // « L'importer » : on lit le fichier, la base le reçoit, il redevient la référence.
        assertEquals(foreign, sync.readLinked()!!.text)
        sync.changed(snapshot(9))
        sync.acknowledge(foreign)
        assertEquals(LinkedFileState.Linked("kairos.json"), sync.state.value)
        assertEquals(snapshot(9).notes, ExportCodec.decode(io.files.getValue("/sync/kairos.json")).notes)

        // « Écraser » : écrit malgré une nouvelle modification ailleurs.
        io.files["/sync/kairos.json"] = foreign.replace("note 1", "note un")
        sync.changed(snapshot(3))
        sync.flush()
        assertIs<LinkedFileState.Changed>(sync.state.value)
        sync.overwrite()
        assertEquals(snapshot(3).notes, ExportCodec.decode(io.files.getValue("/sync/kairos.json")).notes)
        assertEquals(LinkedFileState.Linked("kairos.json"), sync.state.value)
    }

    @Test
    fun aWriteFailureIsReportedThenClearedAndUnlinkStopsWriting() = runTest {
        val io = FakeIo()
        val sync = LinkedFileSync(io, backgroundScope, clock)
        sync.start(snapshot(0))
        sync.createFile()
        io.broken = true
        sync.changed(snapshot(1))
        sync.flush()
        assertEquals(LinkedFileState.WriteFailed("kairos.json"), sync.state.value)
        io.broken = false
        sync.retry()
        assertEquals(LinkedFileState.Linked("kairos.json"), sync.state.value)
        assertEquals(snapshot(1).notes, ExportCodec.decode(io.files.getValue("/sync/kairos.json")).notes)

        sync.unlink()
        assertNull(io.saved)
        val writes = io.writes
        sync.changed(snapshot(2))
        sync.flush()
        assertEquals(writes, io.writes)
        assertEquals(LinkedFileState.NotLinked, sync.state.value)
    }

    @Test
    fun openingAFileAdoptsItsContentAsTheReference() = runTest {
        val io = FakeIo()
        val existing = ExportCodec.encode(snapshot(4), "3.10.0", now)
        io.files["/sync/kairos.json"] = existing
        val sync = LinkedFileSync(io, backgroundScope, clock)
        sync.start(snapshot(0))
        assertEquals(existing, sync.openFile()!!.text)
        assertTrue(sync.adoptOpened())
        // La base reçoit le fichier (remplacement), puis l'écriture qui suit ne voit pas de modification étrangère.
        sync.changed(snapshot(4))
        sync.flush()
        assertEquals(LinkedFileState.Linked("kairos.json"), sync.state.value)
        assertEquals(snapshot(4).notes, ExportCodec.decode(io.files.getValue("/sync/kairos.json")).notes)
    }
}
