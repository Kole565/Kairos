package com.skohscripts.kairos.desktop

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Journal de diagnostic du bureau (docs/spec/bureau-avance.md § Journal de diagnostic, issue #115). */
class DiagnosticsTest {
    private val dir = createTempDirectory("diag").toFile()

    @Test
    fun offByDefaultNothingIsWritten() {
        val d = FileDiagnostics(dir)
        assertFalse(d.enabled.value)
        d.log("export ok")
        assertFalse(File(dir, FileDiagnostics.LOG).exists())
        assertFalse(File(dir, FileDiagnostics.FLAG).exists())
    }

    @Test
    fun theSwitchIsRememberedOnThisComputerAndTheLogIsCapped() {
        FileDiagnostics(dir).setEnabled(true)
        val d = FileDiagnostics(dir)
        assertTrue(d.enabled.value)
        repeat(4000) { d.log("update ok 200 proxy=no $it ${"x".repeat(60)}") }
        val log = File(dir, FileDiagnostics.LOG)
        assertTrue(log.length() <= FileDiagnostics.MAX_BYTES + 200)
        assertTrue(File(dir, FileDiagnostics.PREVIOUS).exists())
        d.setEnabled(false)
        assertFalse(FileDiagnostics(dir).enabled.value)
    }

    @Test
    fun theReportGathersSystemLogAndCrashes() {
        val d = FileDiagnostics(dir, DataDirectory.Source.CHOSEN)
        d.setEnabled(true)
        d.log("export ok")
        CrashLog.write(dir, "main", IllegalStateException("boom"))
        val report = d.report()
        assertTrue(report.startsWith("Kairos "))
        assertTrue("Data folder: chosen" in report)
        assertTrue("export ok" in report)
        assertTrue("IllegalStateException: boom" in report)
        // La source du dossier, jamais son chemin.
        assertFalse(dir.path in report)
        assertEquals(1, report.lines().count { it.endsWith(" export ok") })
    }
}
