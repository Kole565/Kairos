package com.skohscripts.kairos.ui.guide

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.card_besthours_apply_body
import com.skohscripts.kairos.ui.generated.resources.card_besthours_apply_title
import com.skohscripts.kairos.ui.generated.resources.card_contexts_body
import com.skohscripts.kairos.ui.generated.resources.card_contexts_title
import com.skohscripts.kairos.ui.generated.resources.card_hours_body
import com.skohscripts.kairos.ui.generated.resources.card_hours_title
import com.skohscripts.kairos.ui.generated.resources.tour_save_body
import com.skohscripts.kairos.ui.generated.resources.tour_save_title
import com.skohscripts.kairos.ui.generated.resources.tour_workday_body
import com.skohscripts.kairos.ui.generated.resources.tour_workday_title
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.settings.SettingsNav

/** Une étape de la visite des Réglages : la carte qu'elle cerne ([section], `null` : la barre d'enregistrement) et son texte. */
internal class SettingsTourStep(val section: GuideSection?, val step: CoachStep)

/**
 * Visite des Réglages (docs/spec/premiere-tache.md § Première tâche récurrente et visite des Réglages, issue #98) : une carte à la
 * fois, de la journée de travail aux meilleures heures, puis « Enregistrer ». Les textes des horaires, des contextes et des
 * meilleures heures sont ceux du catalogue (rien n'est écrit deux fois).
 */
internal object SettingsTour {
    val STEPS: List<SettingsTourStep> = listOf(
        SettingsTourStep(GuideSection.SETTINGS_WORKDAY, CoachStep({ KairosIcons.Work }, Res.string.tour_workday_title, Res.string.tour_workday_body)),
        SettingsTourStep(GuideSection.SETTINGS_HOURS, CoachStep({ KairosIcons.Schedule }, Res.string.card_hours_title, Res.string.card_hours_body)),
        SettingsTourStep(GuideSection.SETTINGS_CONTEXTS, CoachStep({ KairosIcons.Layers }, Res.string.card_contexts_title, Res.string.card_contexts_body)),
        SettingsTourStep(GuideSection.SETTINGS_BEST_HOURS, CoachStep({ KairosIcons.LightMode }, Res.string.card_besthours_apply_title, Res.string.card_besthours_apply_body)),
        SettingsTourStep(null, CoachStep({ KairosIcons.Check }, Res.string.tour_save_title, Res.string.tour_save_body, save = true)),
    )
}

/**
 * Suit l'étape de la visite : ouvre la catégorie de sa carte, l'amène à l'écran ([GuideSpotState.pending]) et la cerne tant que
 * l'étape dure ([GuideSpotState.held]). Quitter les Réglages termine la visite. Rien n'est écrit : le formulaire reste celui de l'écran.
 */
@Composable
internal fun SettingsTourEffect(nav: SettingsNav) {
    val spot = LocalGuideSpot.current
    val step = nav.tourStep
    LaunchedEffect(step) {
        val section = step?.let { SettingsTour.STEPS.getOrNull(it) }?.section
        spot.held = section
        if (section != null) {
            nav.category = section.settingsCategory
            spot.pending = section
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            nav.tourStep = null
            spot.held = null
        }
    }
}

/** Le panneau de la visite, en bas des Réglages : le même que celui de la fiche, non bloquant ; « Passer » et « Terminer » n'écrivent rien. */
@Composable
internal fun SettingsTourPanel(nav: SettingsNav) {
    val index = nav.tourStep ?: return
    val spot = LocalGuideSpot.current
    val steps = SettingsTour.STEPS
    val end = { nav.tourStep = null; spot.held = null }
    CoachPanel(
        step = steps[index.coerceIn(0, steps.lastIndex)].step,
        index = index,
        total = steps.size,
        onPrevious = if (index > 0) ({ nav.tourStep = index - 1 }) else null,
        onNext = { nav.tourStep = (index + 1).coerceAtMost(steps.lastIndex) },
        onSkip = end,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
