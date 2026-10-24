package com.skohscripts.kairos.desktop

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Dossier des données du bureau (docs/spec/bureau-avance.md § Dossier des données, issue #115). */
class DataFolderTest {
    private val home = createTempDirectory("home").toFile()
    private val env = mapOf("XDG_DATA_HOME" to File(home, "share").path)
    private val root = File(home, "share/Kairos")
    private val default = File(root, "v3")

    private fun resolve(args: List<String> = emptyList(), extra: Map<String, String> = emptyMap()) =
        DataDirectory.resolveAll(args, env + extra, "Linux", home.path)

    private fun database(dir: File, text: String = "base") = File(dir, DataFolder.DATABASE).apply { dir.mkdirs(); writeText(text) }

    @Test
    fun nothingChosenOpensTheDefaultFolderAndWritesNothing() {
        val r = resolve()
        assertEquals(default, r.dir)
        assertEquals(DataDirectory.Source.DEFAULT, r.source)
        assertFalse(File(root, DataFolder.POINTER).exists())
    }

    @Test
    fun theArgumentWinsOverTheEnvironmentWhichWinsOverTheChoice() {
        val chosen = File(home, "chosen")
        DataFolder.choose(root, default, default, chosen)
        assertEquals(DataDirectory.Source.CHOSEN, resolve().source)
        assertEquals(File("/env"), resolve(extra = mapOf("KAIROS_DATA_DIR" to "/env")).dir)
        val byArg = resolve(listOf("--data-dir=/arg"), mapOf("KAIROS_DATA_DIR" to "/env"))
        assertEquals(File("/arg"), byArg.dir)
        assertEquals(DataDirectory.Source.ARGUMENT, byArg.source)
    }

    @Test
    fun anEmptyFolderReceivesACopyAtTheNextStartAndTheOldOneStaysIdentical() {
        database(default, "mes données")
        val chosen = File(home, "Sync/Kairos")
        DataFolder.choose(root, default, default, chosen)
        assertEquals(chosen.absoluteFile, DataFolder.pending(root, default, default))
        // Rien n'est copié avant le démarrage suivant.
        assertFalse(DataFolder.hasData(chosen))
        val r = resolve()
        assertEquals(chosen, r.dir)
        assertEquals("mes données", File(chosen, DataFolder.DATABASE).readText())
        assertEquals("mes données", File(default, DataFolder.DATABASE).readText())
        // La copie est faite une fois : le choix reste, sans copie en attente.
        assertEquals(listOf(chosen.path), File(root, DataFolder.POINTER).readLines())
        assertNull(r.failed)
    }

    @Test
    fun aFolderThatAlreadyHasDataIsOpenedWithoutCopy() {
        database(default, "défaut")
        val chosen = File(home, "other")
        database(chosen, "autre")
        DataFolder.choose(root, default, default, chosen)
        assertEquals(chosen, resolve().dir)
        assertEquals("autre", File(chosen, DataFolder.DATABASE).readText())
        // Revenir au dossier par défaut, qui a déjà une base : le choix est effacé.
        DataFolder.choose(root, default, chosen, default)
        assertFalse(File(root, DataFolder.POINTER).exists())
        assertEquals(default, resolve().dir)
    }

    @Test
    fun anUnusableFolderLeavesKairosOnThePreviousOne() {
        database(default)
        // Un fichier à la place du dossier : impossible d'y créer la base.
        val blocked = File(home, "blocked").apply { writeText("pas un dossier") }
        DataFolder.choose(root, default, default, blocked)
        val r = resolve()
        assertEquals(default, r.dir)
        assertEquals(blocked, r.failed)
        assertFalse(File(root, DataFolder.POINTER).exists())
        assertTrue(DataFolder.hasData(default))
    }

    @Test
    fun choosingTheCurrentFolderCancelsTheChoice() {
        DataFolder.choose(root, default, default, File(home, "x"))
        DataFolder.choose(root, default, default, default)
        assertNull(DataFolder.pending(root, default, default))
    }
}
