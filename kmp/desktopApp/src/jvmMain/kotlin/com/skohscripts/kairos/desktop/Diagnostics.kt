package com.skohscripts.kairos.desktop

import com.skohscripts.kairos.core.KairosBuild
import com.skohscripts.kairos.ui.app.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.time.LocalDateTime
import java.util.Locale

/**
 * Journal de diagnostic du bureau (docs/spec/bureau-avance.md § Journal de diagnostic) : interrupteur = présence de
 * `diagnostics.on` dans le dossier des données (propre à cet ordinateur, hors des réglages exportés) ; journal
 * `diagnostics.log`, une ligne par événement, plafonné à [MAX_BYTES] (le fichier plein devient `diagnostics.1.log`). Jamais de
 * contenu de tâche ni de chemin : les appelants n'écrivent que du vocabulaire fixe et des nombres.
 */
class FileDiagnostics(private val dir: File, private val folderSource: DataDirectory.Source = DataDirectory.Source.DEFAULT) : Diagnostics {
    private val flag = File(dir, FLAG)
    private val logFile = File(dir, LOG)
    private val enabledFlow = MutableStateFlow(flag.isFile)
    override val enabled: StateFlow<Boolean> = enabledFlow

    override fun setEnabled(on: Boolean) {
        runCatching {
            dir.mkdirs()
            if (on) flag.writeText("on\n") else flag.delete()
        }
        enabledFlow.value = flag.isFile
        if (on) log("diagnostics on")
    }

    @Synchronized
    override fun log(event: String) {
        if (!enabledFlow.value) return
        runCatching {
            dir.mkdirs()
            if (logFile.isFile && logFile.length() > MAX_BYTES) {
                val previous = File(dir, PREVIOUS)
                previous.delete()
                logFile.renameTo(previous)
            }
            logFile.appendText("${LocalDateTime.now()} ${event.replace('\n', ' ')}\n")
        }
    }

    override fun report(): String = buildString {
        append("Kairos ${KairosBuild.VERSION_NAME}\n")
        append("${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}\n")
        append("Java ${System.getProperty("java.version")}, ${Locale.getDefault().toLanguageTag()}\n")
        append("Data folder: ${folderSource.name.lowercase()}\n")
        append("\n--- ").append(LOG).append(if (enabledFlow.value) "" else " (off)").append(" ---\n")
        tail(logFile).forEach { append(it).append('\n') }
        append("\n--- ").append(CrashLog.FILE_NAME).append(" ---\n")
        tail(File(dir, CrashLog.FILE_NAME)).forEach { append(it).append('\n') }
    }

    private fun tail(file: File): List<String> = runCatching { if (file.isFile) file.readLines().takeLast(TAIL_LINES) else emptyList() }.getOrDefault(emptyList())

    companion object {
        const val FLAG = "diagnostics.on"
        const val LOG = "diagnostics.log"
        const val PREVIOUS = "diagnostics.1.log"
        const val MAX_BYTES = 256 * 1024L
        const val TAIL_LINES = 200
    }
}
