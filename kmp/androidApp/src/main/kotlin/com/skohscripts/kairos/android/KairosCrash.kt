package com.skohscripts.kairos.android

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Rapport de plantage local (docs/spec/demarrage.md § Rapport de plantage, issue #65). Aucun réseau, aucune
 * permission : l'exception non rattrapée est écrite dans `files/crash/last.txt`, puis le gestionnaire d'origine
 * du système prend le relais (le processus s'arrête comme avant). Au lancement suivant, `MainActivity` montre
 * ce rapport au lieu d'ouvrir l'application ; l'utilisateur le partage ou l'ignore.
 */
object KairosCrash {
    private const val FOLDER = "crash"
    private const val FILE = "last.txt"

    /** Pose le gestionnaire ; à appeler une fois par processus (`KairosApplication`, donc aussi pour les récepteurs). */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Un échec d'écriture ne doit jamais masquer le plantage d'origine.
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Le rapport en attente, `null` s'il n'y en a pas (ou s'il est illisible). */
    fun pending(context: Context): String? = runCatching { file(context).takeIf { it.isFile }?.readText() }.getOrNull()

    /** Supprime le rapport : le prochain lancement ouvre l'application. */
    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(File(context.filesDir, FOLDER), FILE)

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val target = file(context)
        // Le premier rapport est gardé : un second plantage (l'écran de rapport lui-même, par exemple) ne
        // doit pas effacer la cause d'origine.
        if (target.exists()) return
        target.parentFile?.mkdirs()
        target.writeText(report(context, thread, error))
    }

    private fun report(context: Context, thread: Thread, error: Throwable): String {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        return buildString {
            appendLine("Kairos ${info?.versionName ?: "?"} (${context.packageName})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Locale ${Locale.getDefault().toLanguageTag()} · thread ${thread.name}")
            appendLine()
            append(Log.getStackTraceString(error))
        }
    }
}
