package com.skohscripts.kairos.ui.app

import com.skohscripts.kairos.ui.theme.KairosButton
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosTextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.data.ExportCodec
import com.skohscripts.kairos.data.ImportException
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.import_confirm_action
import com.skohscripts.kairos.ui.generated.resources.import_confirm_title
import com.skohscripts.kairos.ui.generated.resources.import_done
import com.skohscripts.kairos.ui.generated.resources.import_error_corrupted
import com.skohscripts.kairos.ui.generated.resources.import_error_not_export
import com.skohscripts.kairos.ui.generated.resources.import_error_too_new
import com.skohscripts.kairos.ui.generated.resources.linked_unlink
import com.skohscripts.kairos.ui.generated.resources.linked_intro
import com.skohscripts.kairos.ui.generated.resources.linked_changed
import com.skohscripts.kairos.ui.generated.resources.linked_overwrite
import com.skohscripts.kairos.ui.generated.resources.linked_import
import com.skohscripts.kairos.ui.generated.resources.linked_import_confirm_body
import com.skohscripts.kairos.ui.generated.resources.linked_open_confirm_body
import com.skohscripts.kairos.ui.generated.resources.linked_write_failed
import com.skohscripts.kairos.ui.generated.resources.linked_retry
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.web_authorize
import com.skohscripts.kairos.ui.generated.resources.web_link_create
import com.skohscripts.kairos.ui.generated.resources.web_link_open
import com.skohscripts.kairos.ui.generated.resources.web_link_open_confirm_body
import com.skohscripts.kairos.ui.generated.resources.web_linked
import com.skohscripts.kairos.ui.generated.resources.web_not_linked
import com.skohscripts.kairos.ui.generated.resources.web_reauthorize
import com.skohscripts.kairos.ui.generated.resources.web_restore
import com.skohscripts.kairos.ui.generated.resources.web_save_failed
import com.skohscripts.kairos.ui.generated.resources.web_unsupported
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Messages d'échec d'import, communs à l'import manuel et au fichier lié. */
internal suspend fun importErrorMessage(e: ImportException): String = getString(
    when (e.reason) {
        ImportException.Reason.NOT_AN_EXPORT -> Res.string.import_error_not_export
        ImportException.Reason.TOO_NEW -> Res.string.import_error_too_new
        ImportException.Reason.CORRUPTED -> Res.string.import_error_corrupted
    },
)

/**
 * Bandeau du fichier lié (docs/spec/export-import.md § Version web et § Fichier lié du bureau et d'Android). Version web :
 * vue Jour seulement quand il y a quelque chose à faire ([showWhenLinked] faux), Réglages toujours. Bureau et Android : la
 * carte Données propose et montre le lien ; la vue Jour ne montre qu'un fichier modifié ailleurs ou une écriture en échec.
 * Bandeau neutre (une dégradation n'est pas un danger), sauf les échecs d'enregistrement, en conteneur d'erreur (charte
 * § Bannières).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StorageBanner(services: AppServices, showWhenLinked: Boolean) {
    val storage = services.linkedStorage ?: return
    val state by storage.state.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    // Fichier à importer après confirmation : ouvert (« Ouvrir un fichier Kairos ») ou modifié ailleurs (« L'importer »).
    var opened by remember { mutableStateOf<Pending?>(null) }

    val text: String
    var error = false
    var warning = true
    val actions = mutableListOf<Pair<String, () -> Unit>>()
    val openAction = stringResource(Res.string.web_link_open) to {
        scope.launch {
            val file = storage.openFile() ?: return@launch
            try {
                opened = Pending(file, ExportCodec.decode(file.text), changed = false)
            } catch (e: ImportException) {
                messages(importErrorMessage(e))
            }
        }
        Unit
    }
    val unlinkAction = stringResource(Res.string.linked_unlink) to { scope.launch { storage.unlink() }; Unit }
    when (val s = state) {
        LinkedFileState.Unsupported -> text = stringResource(Res.string.web_unsupported)
        LinkedFileState.NotLinked -> {
            // Bureau et Android : les données sont déjà dans un fichier ; on propose sans insister (carte Données seule).
            if (!storage.browser && !showWhenLinked) return
            text = stringResource(if (storage.browser) Res.string.web_not_linked else Res.string.linked_intro)
            warning = storage.browser
            actions += stringResource(Res.string.web_link_create) to { scope.launch { storage.createFile() }; Unit }
            actions += openAction
        }
        is LinkedFileState.Linked -> {
            if (!showWhenLinked) return
            text = stringResource(Res.string.web_linked, s.name)
            warning = false
            actions += openAction
            actions += unlinkAction
        }
        is LinkedFileState.NeedsPermission -> {
            text = stringResource(if (s.restore) Res.string.web_restore else Res.string.web_reauthorize, s.name)
            actions += stringResource(Res.string.web_authorize) to { scope.launch { storage.authorize() }; Unit }
        }
        is LinkedFileState.SaveFailed -> {
            text = stringResource(Res.string.web_save_failed, s.detail)
            error = true
        }
        is LinkedFileState.Changed -> {
            text = stringResource(Res.string.linked_changed, s.name)
            actions += stringResource(Res.string.linked_overwrite) to { scope.launch { storage.overwrite() }; Unit }
            actions += stringResource(Res.string.linked_import) to {
                scope.launch {
                    val file = storage.readLinked() ?: return@launch messages(getString(Res.string.file_error))
                    try {
                        opened = Pending(file, ExportCodec.decode(file.text), changed = true)
                    } catch (e: ImportException) {
                        messages(importErrorMessage(e))
                    }
                }
                Unit
            }
            actions += unlinkAction
        }
        is LinkedFileState.WriteFailed -> {
            text = stringResource(Res.string.linked_write_failed, s.name)
            error = true
            actions += stringResource(Res.string.linked_retry) to { scope.launch { storage.retry() }; Unit }
            actions += unlinkAction
        }
    }

    val scheme = MaterialTheme.colorScheme
    Surface(
        color = if (error) scheme.errorContainer else scheme.surfaceContainerHigh,
        contentColor = if (error) scheme.onErrorContainer else scheme.onSurface,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                Icon(if (warning || error) KairosIcons.Warning else KairosIcons.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (actions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 32.dp)) {
                    actions.forEachIndexed { index, (label, action) ->
                        if (index == 0) KairosButton(onClick = action) { Text(label) } else KairosOutlinedButton(onClick = action) { Text(label) }
                    }
                }
            }
        }
    }

    opened?.let { pending ->
        val body = when {
            pending.changed -> Res.string.linked_import_confirm_body
            storage.browser -> Res.string.web_link_open_confirm_body
            else -> Res.string.linked_open_confirm_body
        }
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(stringResource(Res.string.import_confirm_title)) },
            text = { Text(stringResource(body, pending.file.name)) },
            confirmButton = {
                KairosTextButton(onClick = {
                    opened = null
                    scope.launch {
                        // Comme « Importer » : sauvegarde d'abord ; le fichier devient ensuite la référence du lien.
                        if (!pending.changed && !storage.adoptOpened()) return@launch
                        if (replaceWithBackup(services, pending.snapshot, messages)) {
                            if (pending.changed) storage.acknowledge(pending.file.text)
                            messages(getString(Res.string.import_done, pending.snapshot.tasks.size))
                        }
                    }
                }) { Text(stringResource(Res.string.import_confirm_action)) }
            },
            dismissButton = { KairosTextButton(onClick = { opened = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** Un fichier lu et décodé, en attente de confirmation ; [changed] : le fichier lié modifié ailleurs (« L'importer »). */
private class Pending(val file: OpenedFile, val snapshot: KairosSnapshot, val changed: Boolean)
