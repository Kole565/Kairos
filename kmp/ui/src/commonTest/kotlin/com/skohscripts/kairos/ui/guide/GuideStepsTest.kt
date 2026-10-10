package com.skohscripts.kairos.ui.guide

import com.skohscripts.kairos.ui.navigation.Destination
import com.skohscripts.kairos.ui.navigation.NavEntry
import com.skohscripts.kairos.ui.navigation.TeamDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Contenu de la visite guidée et de l'aide (docs/spec/accueil.md). Les chaînes elles-mêmes : `StringsParityTest`. */
class GuideStepsTest {
    @Test
    fun theVisitsHaveSixStepsEach() {
        assertEquals(6, GuideSteps.of(GuideKind.PERSONAL).size)
        assertEquals(6, GuideSteps.of(GuideKind.TEAM).size)
    }

    @Test
    fun theKairosTourOnlyOpensPersonalScreensAndTheTeamTourOnlyTeamScreens() {
        // Les Réglages (dernière étape) sont communs aux deux espaces et ne changent pas d'espace.
        assertTrue(GuideSteps.of(GuideKind.PERSONAL).all { it.target is GuideTarget.Personal || it.target == GuideTarget.Settings })
        assertEquals(GuideTarget.Settings, GuideSteps.of(GuideKind.PERSONAL).last().target)
        assertTrue(GuideSteps.of(GuideKind.TEAM).all { it.target is GuideTarget.Team })
    }

    @Test
    fun onlyTheLastTeamStepOpensTheExchangesTab() {
        val steps = GuideSteps.of(GuideKind.TEAM)
        assertEquals(listOf(false, false, false, false, false, true), steps.map { (it.target as GuideTarget.Team).exchanges })
        // Les échanges vivent dans l'écran Équipe.
        assertEquals(TeamDestination.MEMBERS, (steps.last().target as GuideTarget.Team).destination)
    }

    @Test
    fun everyStepHasItsOwnTitleAndText() {
        for (kind in GuideKind.entries) {
            val steps = GuideSteps.of(kind)
            assertEquals(steps.size, steps.map { it.title }.toSet().size)
            assertEquals(steps.size, steps.map { it.body }.toSet().size)
        }
    }

    @Test
    fun everyScreenButHomeHasAHelpTopicAndEveryTopicIsReachable() {
        val entries: List<NavEntry> = Destination.entries + TeamDestination.entries
        // L'Accueil n'a pas de « ? » ; les Réglages (écran secondaire) ont leur sujet, choisi par la coquille.
        assertEquals(listOf<NavEntry>(Destination.HOME, TeamDestination.HOME), entries.filter { HelpTopic.of(it, exchanges = false) == null })
        val reached = entries.mapNotNull { HelpTopic.of(it, exchanges = false) }.toSet() +
            HelpTopic.of(TeamDestination.MEMBERS, exchanges = true) + HelpTopic.SETTINGS
        assertEquals(HelpTopic.entries.toSet(), reached)
    }

    @Test
    fun theExchangesTabHasItsOwnHelpAndOnlyOnTheTeamScreen() {
        assertEquals(HelpTopic.TEAM_MEMBERS, HelpTopic.of(TeamDestination.MEMBERS, exchanges = false))
        assertEquals(HelpTopic.TEAM_EXCHANGES, HelpTopic.of(TeamDestination.MEMBERS, exchanges = true))
        // L'onglet n'a pas d'effet ailleurs.
        assertEquals(HelpTopic.DAY, HelpTopic.of(Destination.DAY, exchanges = true))
    }
}
