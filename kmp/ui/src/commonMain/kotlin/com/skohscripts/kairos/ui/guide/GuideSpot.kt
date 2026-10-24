package com.skohscripts.kairos.ui.guide

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.guide_spot_here
import com.skohscripts.kairos.ui.app.LocalMessages
import com.skohscripts.kairos.ui.icons.KairosIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** Durée de la mise en valeur d'une section (docs/spec/visite-guidee.md, issue #95). */
internal const val SPOT_MILLIS = 3_000L

/**
 * Section qu'une carte du guide demande de montrer ([pending], posée par la coquille après avoir ouvert l'écran) et
 * celle mise en valeur ([shown]). Porté par `NavState.guideSpot`, fourni aux écrans par [LocalGuideSpot].
 */
class GuideSpotState {
    var pending by mutableStateOf<GuideSection?>(null)
    var shown by mutableStateOf<GuideSection?>(null)

    /** Section cernée tant qu'une étape de visite dure (visite des Réglages, issue #98), sans la limite de [SPOT_MILLIS]. */
    var held by mutableStateOf<GuideSection?>(null)

    /** Met [section] en valeur [SPOT_MILLIS], puis l'efface si rien ne l'a remplacée entre-temps. */
    suspend fun show(section: GuideSection) {
        shown = section
        delay(SPOT_MILLIS)
        if (shown == section) shown = null
    }
}

val LocalGuideSpot = compositionLocalOf { GuideSpotState() }

/**
 * Contour et étiquette « Ici » autour de [content] tant que [section] est mise en valeur : contour primaire de 2 dp aux
 * coins des cartes (le même que la ligne mise en évidence par l'accompagnement de la première tâche) et étiquette avec
 * icône, jamais la couleur seule. Ni l'un ni l'autre ne s'anime : ils apparaissent et disparaissent d'un coup.
 */
@Composable
fun GuideMark(section: GuideSection, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val spot = LocalGuideSpot.current
    val marked = spot.shown == section || spot.held == section
    val scheme = MaterialTheme.colorScheme
    Box(
        if (marked) modifier.border(BorderStroke(2.dp, scheme.primary), MaterialTheme.shapes.medium) else modifier,
        contentAlignment = Alignment.TopEnd,
    ) {
        content()
        if (marked) {
            val here = stringResource(Res.string.guide_spot_here)
            Surface(
                color = scheme.primary,
                contentColor = scheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.padding(8.dp).semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Icon(KairosIcons.Spot, contentDescription = null, modifier = Modifier.size(14.dp))
                    Text(here, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/**
 * [GuideMark] qui se fait aussi défiler à l'écran : quand la demande en attente est la sienne, il la consomme, attend
 * deux images (l'écran doit être mesuré) et se met en vue. Pour les colonnes défilantes (Réglages, Statistiques) ; la vue
 * Jour, paresseuse, défile elle-même (`DayScreen`).
 */
@Composable
fun GuideSpot(section: GuideSection, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val spot = LocalGuideSpot.current
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val wanted = spot.pending == section
    LaunchedEffect(wanted) {
        if (!wanted) return@LaunchedEffect
        spot.pending = null
        // Hors de l'effet : consommer la demande change sa clé, ce qui l'annulerait avant la fin de la mise en valeur.
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { requester.bringIntoView() }
            spot.show(section)
        }
    }
    GuideMark(section, modifier.bringIntoViewRequester(requester), content)
}

/**
 * [section] est demandée mais absente de l'écran (vide) : consomme la demande et le dit par un message bref
 * ([GuideSection.missing]) ; l'écran reste en haut (docs/spec/visite-guidee.md, choix du propriétaire).
 */
@Composable
fun MissingGuideSection(section: GuideSection) {
    val spot = LocalGuideSpot.current
    val messages = LocalMessages.current
    val wanted = spot.pending == section
    LaunchedEffect(wanted) {
        if (!wanted) return@LaunchedEffect
        spot.pending = null
        section.missing?.let { messages(getString(it)) }
    }
}
