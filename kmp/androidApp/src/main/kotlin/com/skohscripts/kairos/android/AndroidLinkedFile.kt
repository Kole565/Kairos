package com.skohscripts.kairos.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.skohscripts.kairos.ui.app.LinkedFileIo
import com.skohscripts.kairos.ui.app.SavedLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fichier lié d'Android (docs/spec/export-import.md § Fichier lié du bureau et d'Android) : sélecteurs du Storage Access
 * Framework avec accès persistant (aucune permission dans le manifeste ; un dossier Syncthing comme un fournisseur de
 * documents Nextcloud), écriture en mode `"wt"`, lien gardé dans les préférences de l'application (propre à l'appareil).
 */
class AndroidLinkedFile(context: Context) : LinkedFileIo {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override suspend fun pickCreate(suggestedName: String): String? =
        withContext(Dispatchers.Main) { KairosProcess.activity?.pickLinkedFile(create = true, suggestedName) }?.toString()

    override suspend fun pickOpen(): String? =
        withContext(Dispatchers.Main) { KairosProcess.activity?.pickLinkedFile(create = false, null) }?.toString()

    override suspend fun read(target: String): String = withContext(Dispatchers.IO) {
        app.contentResolver.openInputStream(Uri.parse(target))!!.use { it.readBytes().decodeToString() }
    }

    // « wt » : certains fournisseurs ignorent la troncature en « w » et laissent des octets en trop à la fin.
    override suspend fun write(target: String, text: String) = withContext(Dispatchers.IO) {
        app.contentResolver.openOutputStream(Uri.parse(target), "wt")!!.use { it.write(text.toByteArray()) }
    }

    override fun displayName(target: String): String = runCatching {
        app.contentResolver.query(Uri.parse(target), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: Uri.parse(target).lastPathSegment.orEmpty()

    override suspend fun loadLink(): SavedLink? {
        val target = prefs.getString(KEY_TARGET, null) ?: return null
        return SavedLink(target, prefs.getString(KEY_NAME, null).orEmpty(), prefs.getString(KEY_HASH, null).orEmpty())
    }

    override suspend fun saveLink(link: SavedLink?) {
        prefs.edit().apply {
            if (link == null) clear() else putString(KEY_TARGET, link.target).putString(KEY_NAME, link.name).putString(KEY_HASH, link.hash)
        }.apply()
    }

    private companion object {
        const val PREFS = "linked-file"
        const val KEY_TARGET = "target"
        const val KEY_NAME = "name"
        const val KEY_HASH = "hash"
    }
}
