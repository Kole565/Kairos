package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.ui.app.SavedLink
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fichier lié du bureau (docs/spec/export-import.md § Fichier lié) : écriture atomique, lien gardé dans le dossier des données. */
class DesktopLinkedFileTest {
    @Test
    fun writesReplaceTheFileInOneMoveAndLeaveNoTemporaryFile() = runBlocking {
        val data = Files.createTempDirectory("kairos-data").toFile()
        val sync = Files.createTempDirectory("kairos-sync").toFile()
        val target = java.io.File(sync, "kairos.json")
        val io = DesktopLinkedFile(data) { _, _ -> target }
        assertEquals(target.absolutePath, io.pickCreate("kairos.json"))
        io.write(target.absolutePath, "premier")
        io.write(target.absolutePath, "second, plus long que le premier")
        assertEquals("second, plus long que le premier", io.read(target.absolutePath))
        assertEquals(listOf("kairos.json"), sync.list()!!.toList())
        assertEquals("kairos.json", io.displayName(target.absolutePath))
    }

    @Test
    fun theLinkIsKeptNextToTheDatabaseAndForgotten() = runBlocking {
        val data = Files.createTempDirectory("kairos-data").toFile()
        val io = DesktopLinkedFile(data) { _, _ -> null }
        assertNull(io.loadLink())
        val link = SavedLink("/sync/kairos.json", "kairos.json", "12:abc")
        io.saveLink(link)
        assertTrue(java.io.File(data, DesktopLinkedFile.LINK_FILE).isFile)
        assertEquals(link, DesktopLinkedFile(data).loadLink())
        io.saveLink(null)
        assertNull(io.loadLink())
    }
}
