package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.data.SubtaskEdit
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.subtask_delete
import com.skohscripts.kairos.ui.generated.resources.subtask_deleted_description
import com.skohscripts.kairos.ui.generated.resources.subtask_done_description
import com.skohscripts.kairos.ui.generated.resources.subtask_restore
import com.skohscripts.kairos.ui.generated.resources.subtask_title
import com.skohscripts.kairos.ui.generated.resources.subtasks_existing
import com.skohscripts.kairos.ui.generated.resources.subtasks_existing_hint
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosRowIconButton
import org.jetbrains.compose.resources.stringResource

/**
 * Brouillon d'une sous-tâche existante dans la fiche de sa mère (docs/spec/vue-jour.md § Édition, issue #69) :
 * titre, état, et marque de suppression. Rien n'est écrit avant « Enregistrer » ([edits]).
 */
internal data class SubtaskDraft(val id: Long, val title: String, val done: Boolean, val deleted: Boolean = false) {
    companion object {
        fun of(task: Task) = SubtaskDraft(task.id, task.title, task.status == TaskStatus.DONE)
    }
}

/**
 * Sous-tâches de [mother] telles que la fiche les montre : celles qui existent (archivées exclues), dans l'ordre de
 * création.
 */
internal fun subtasksOf(mother: Long, tasks: List<Task>): List<Task> =
    tasks.filter { it.parentId == mother && it.status != TaskStatus.ARCHIVED }.sortedBy { it.id }

/** Ce que l'enregistrement doit changer : les brouillons qui diffèrent de la tâche d'origine, rien sinon. */
internal fun List<SubtaskDraft>.edits(original: List<Task>): List<SubtaskEdit> {
    val byId = original.associateBy { it.id }
    return mapNotNull { d ->
        val task = byId[d.id] ?: return@mapNotNull null
        val changed = d.deleted || d.title.trim() != task.title || d.done != (task.status == TaskStatus.DONE)
        if (changed) SubtaskEdit(d.id, d.title, d.done, d.deleted) else null
    }
}

/**
 * Liste des sous-tâches existantes : case « faite », titre modifiable, suppression (annulable jusqu'à l'enregistrement).
 * Une ligne supprimée reste visible, barrée, avec « Annuler la suppression ».
 */
@Composable
internal fun SubtaskEditor(drafts: List<SubtaskDraft>, onChange: (SubtaskDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(Res.string.subtasks_existing), style = MaterialTheme.typography.labelLarge, modifier = Modifier.heading())
        Text(
            stringResource(Res.string.subtasks_existing_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        drafts.forEach { draft ->
            // Une clé par sous-tâche : la saisie d'une ligne ne perd pas son curseur quand une autre est supprimée.
            key(draft.id) { SubtaskRow(draft, onChange) }
        }
    }
}

@Composable
private fun SubtaskRow(draft: SubtaskDraft, onChange: (SubtaskDraft) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        if (draft.deleted) {
            val description = stringResource(Res.string.subtask_deleted_description, draft.title)
            Text(
                draft.title,
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 12.dp).semantics { contentDescription = description },
            )
            KairosRowIconButton(KairosIcons.Close, stringResource(Res.string.subtask_restore), onClick = { onChange(draft.copy(deleted = false)) })
        } else {
            val description = stringResource(Res.string.subtask_done_description, draft.title)
            Checkbox(
                checked = draft.done,
                onCheckedChange = null,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .toggleable(draft.done, role = Role.Checkbox, onValueChange = { onChange(draft.copy(done = it)) })
                    .padding(horizontal = 12.dp)
                    .semantics { contentDescription = description },
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onChange(draft.copy(title = it)) },
                label = { Text(stringResource(Res.string.subtask_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            KairosRowIconButton(
                KairosIcons.Delete,
                stringResource(Res.string.subtask_delete),
                onClick = { onChange(draft.copy(deleted = true)) },
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
