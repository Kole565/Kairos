package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.DataFolderService
import com.skohscripts.kairos.ui.app.Diagnostics
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.action_cancel
import com.skohscripts.kairos.ui.generated.resources.diagnostics_body
import com.skohscripts.kairos.ui.generated.resources.diagnostics_copied
import com.skohscripts.kairos.ui.generated.resources.diagnostics_copy
import com.skohscripts.kairos.ui.generated.resources.diagnostics_switch
import com.skohscripts.kairos.ui.generated.resources.diagnostics_title
import com.skohscripts.kairos.ui.generated.resources.file_error
import com.skohscripts.kairos.ui.generated.resources.folder_body
import com.skohscripts.kairos.ui.generated.resources.folder_change
import com.skohscripts.kairos.ui.generated.resources.folder_confirm
import com.skohscripts.kairos.ui.generated.resources.folder_confirm_copy
import com.skohscripts.kairos.ui.generated.resources.folder_confirm_existing
import com.skohscripts.kairos.ui.generated.resources.folder_confirm_title
import com.skohscripts.kairos.ui.generated.resources.folder_default
import com.skohscripts.kairos.ui.generated.resources.folder_failed
import com.skohscripts.kairos.ui.generated.resources.folder_fixed
import com.skohscripts.kairos.ui.generated.resources.folder_pending
import com.skohscripts.kairos.ui.generated.resources.folder_sync
import com.skohscripts.kairos.ui.generated.resources.folder_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosOutlinedButton
import com.skohscripts.kairos.ui.theme.KairosSpacing
import com.skohscripts.kairos.ui.theme.KairosTextButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/**
 * Dossier des données du bureau (docs/spec/bureau-avance.md § Dossier des données) : le dossier ouvert, celui retenu pour le
 * prochain démarrage, un dossier qui n'a pas pu servir ; « Changer de dossier… » et « Revenir au dossier par défaut » disent ce
 * qui va se passer (copie ou ouverture) et attendent la confirmation. Fixé au lancement : rien à changer d'ici.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DataFolderCard(folder: DataFolderService) {
    val scope = rememberCoroutineScope()
    val messages = LocalMessages.current
    val pending by folder.pending.collectAsState()
    var confirm by remember { mutableStateOf<String?>(null) }
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Database, stringResource(Res.string.folder_title), stringResource(Res.string.folder_body))
        folder.failed?.let { Text(stringResource(Res.string.folder_failed, it, folder.current), style = MaterialTheme.typography.bodyMedium) }
        pending?.let { Text(stringResource(Res.string.folder_pending, it), style = MaterialTheme.typography.bodyMedium) }
        if (folder.fixedAtLaunch) {
            Text(stringResource(Res.string.folder_fixed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SettingsCard
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KairosOutlinedButton(onClick = { scope.launch { folder.pick()?.let { confirm = it } } }, contentPadding = KairosButtonIconPadding) {
                Icon(KairosIcons.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(Res.string.folder_change), modifier = Modifier.padding(start = 6.dp))
            }
            if (folder.current != folder.defaultPath || pending != null) {
                KairosTextButton(onClick = { confirm = folder.defaultPath }) { Text(stringResource(Res.string.folder_default)) }
            }
        }
        Text(stringResource(Res.string.folder_sync), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    confirm?.let { target ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(Res.string.folder_confirm_title)) },
            text = {
                Text(
                    if (folder.hasData(target)) stringResource(Res.string.folder_confirm_existing, target, folder.current)
                    else stringResource(Res.string.folder_confirm_copy, target),
                )
            },
            confirmButton = {
                KairosTextButton(onClick = {
                    confirm = null
                    scope.launch { runCatching { folder.choose(target) }.onFailure { messages(getString(Res.string.file_error)) } }
                }) { Text(stringResource(Res.string.folder_confirm)) }
            },
            dismissButton = { KairosTextButton(onClick = { confirm = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/**
 * Journal de diagnostic du bureau (docs/spec/bureau-avance.md § Journal de diagnostic) : l'interrupteur (désactivé par défaut,
 * propre à cet ordinateur) et « Copier le diagnostic », toujours proposé (système et rapports de plantage même sans journal).
 */
@Composable
internal fun DiagnosticsCard(diagnostics: Diagnostics) {
    val enabled by diagnostics.enabled.collectAsState()
    val clipboard = LocalClipboardManager.current
    val messages = LocalMessages.current
    val scope = rememberCoroutineScope()
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Description, stringResource(Res.string.diagnostics_title), stringResource(Res.string.diagnostics_body))
        SwitchRow(enabled, stringResource(Res.string.diagnostics_switch)) { diagnostics.setEnabled(it) }
        KairosOutlinedButton(onClick = {
            // Presse-papiers indisponible (session sans écran) : le message le dit au lieu de quitter.
            val copied = runCatching { clipboard.setText(AnnotatedString(diagnostics.report())) }.isSuccess
            scope.launch { messages(getString(if (copied) Res.string.diagnostics_copied else Res.string.file_error)) }
        }, contentPadding = KairosButtonIconPadding) {
            Icon(KairosIcons.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(Res.string.diagnostics_copy), modifier = Modifier.padding(start = 6.dp))
        }
    }
}
