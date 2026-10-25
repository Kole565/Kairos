package com.skohscripts.kairos.ui.day

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.block_untitled
import com.skohscripts.kairos.ui.generated.resources.calendar_busy
import com.skohscripts.kairos.ui.generated.resources.day_mark_all_day
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.stringResource

/**
 * Journées entières des calendriers importés (docs/spec/connexions-externes.md § Interface) : une pastille neutre par
 * marque, icône calendrier et titre. Neutre : un badge informe, il ne signale ni urgence ni blocage.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayMarkBadges(marks: List<DayMark>, modifier: Modifier = Modifier) {
    if (marks.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        marks.forEach { Badge(markTitle(it), icon = KairosIcons.Event) }
    }
}

/** Titre d'une marque ; vide (titres non gardés à l'import) : « Toute la journée ». */
@Composable
internal fun markTitle(mark: DayMark): String = mark.title.ifEmpty { stringResource(Res.string.day_mark_all_day) }

/** Nom de chaque calendrier importé, par identifiant ; fourni par `KairosApp` (vide dans un écran testé seul). */
val LocalCalendarNames = staticCompositionLocalOf<Map<Long, String>> { emptyMap() }

/** Nom de l'origine d'un créneau ou d'une journée : le calendrier importé, ou le calendrier du téléphone. */
@Composable
internal fun sourceName(source: BlockSource): String = when (source) {
    is BlockSource.Imported -> LocalCalendarNames.current[source.calendarId].orEmpty()
    is BlockSource.Device -> source.calendarName
}

/** Titre affiché d'un créneau : vide, « Occupé » s'il est importé (titres non gardés), « (sans titre) » sinon. */
@Composable
internal fun blockTitle(block: TimeBlock): String =
    block.title.ifEmpty { stringResource(if (block.imported) Res.string.calendar_busy else Res.string.block_untitled) }
