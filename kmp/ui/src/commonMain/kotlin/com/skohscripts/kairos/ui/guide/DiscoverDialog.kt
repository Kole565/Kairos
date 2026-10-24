package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.theme.LocalKairosExtraColors
import com.skohscripts.kairos.ui.generated.resources.discover_chapter_read
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.app.heading
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.discover_chapter_count
import com.skohscripts.kairos.ui.generated.resources.discover_close
import com.skohscripts.kairos.ui.generated.resources.discover_first_task
import com.skohscripts.kairos.ui.generated.resources.discover_first_task_hint
import com.skohscripts.kairos.ui.generated.resources.discover_first_team_task
import com.skohscripts.kairos.ui.generated.resources.discover_first_team_task_hint
import com.skohscripts.kairos.ui.generated.resources.discover_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosTextButton
import org.jetbrains.compose.resources.stringResource

/**
 * Hub « Découvrir Kairos » (docs/spec/visite-guidee.md) : la liste des chapitres du catalogue, chacun rouvrant ses cartes à tout
 * moment, et l'accompagnement de la première tâche (docs/spec/premiere-tache.md) à rejouer. [teamEnabled] : le chapitre de l'équipe
 * n'est proposé que si l'espace Équipe est activé. Choisir une entrée ferme le hub ; rien n'est mémorisé.
 */
@Composable
fun DiscoverDialog(
    teamEnabled: Boolean,
    onFirstTask: () -> Unit,
    onChapter: (GuideChapter) -> Unit,
    onDismiss: () -> Unit,
    /** Chapitres parcourus jusqu'au bout (docs/spec/visite-guidee.md § Persistance, issue #94). */
    read: Set<GuideChapter> = emptySet(),
    /** « Créer ma première tâche d'équipe » (issue #97), sous le chapitre de l'équipe, seulement s'il est proposé. */
    onFirstTeamTask: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(KairosIcons.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(stringResource(Res.string.discover_title), modifier = Modifier.heading()) },
        text = {
            // Défile : sur un écran bas, toutes les entrées restent atteignables.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Entry(KairosIcons.Add, stringResource(Res.string.discover_first_task), stringResource(Res.string.discover_first_task_hint), onClick = onFirstTask)
                FeatureCatalog.chapters(teamEnabled).forEach { chapter ->
                    val count = stringResource(Res.string.discover_chapter_count, FeatureCatalog.chapter(chapter).size)
                    // Lu : la coche en vert « ok » et « · lu » dans le texte, jamais la couleur seule.
                    val done = chapter in read
                    Entry(chapter.icon(), stringResource(chapter.title), if (done) stringResource(Res.string.discover_chapter_read, count) else count, done) {
                        onChapter(chapter)
                    }
                    if (chapter == GuideChapter.TEAM) {
                        Entry(KairosIcons.Add, stringResource(Res.string.discover_first_team_task), stringResource(Res.string.discover_first_team_task_hint), onClick = onFirstTeamTask)
                    }
                }
            }
        },
        confirmButton = { KairosTextButton(onClick = onDismiss) { Text(stringResource(Res.string.discover_close)) } },
    )
}

/** Ligne cliquable (≥ 48 dp : `ListItem` à deux lignes), fond transparent pour garder celui du dialogue. */
@Composable
private fun Entry(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, hint: String, read: Boolean = false, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(hint) },
        trailingContent = if (read) {
            { Icon(KairosIcons.CheckCircleFilled, contentDescription = null, tint = LocalKairosExtraColors.current.ok) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
    )
}
