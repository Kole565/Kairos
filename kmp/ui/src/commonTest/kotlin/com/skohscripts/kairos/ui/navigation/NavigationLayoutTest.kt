package com.skohscripts.kairos.ui.navigation

import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.Platform
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationLayoutTest {
    @Test
    fun wideWindowsGetTheRailOnEveryPlatform() {
        for (platform in Platform.entries) {
            assertEquals(NavigationLayout.RAIL, NavigationLayout.choose(platform, 600.dp))
            assertEquals(NavigationLayout.RAIL, NavigationLayout.choose(platform, 1200.dp))
        }
    }

    @Test
    fun onlyAndroidEverGetsABottomBar() {
        assertEquals(NavigationLayout.BOTTOM_BAR, NavigationLayout.choose(Platform.ANDROID, 411.dp))
        // Un navigateur ou une fenêtre de bureau rétrécis : barre en haut, jamais en bas.
        assertEquals(NavigationLayout.TOP_BAR, NavigationLayout.choose(Platform.DESKTOP, 411.dp))
        assertEquals(NavigationLayout.TOP_BAR, NavigationLayout.choose(Platform.WEB, 599.dp))
    }

    @Test
    fun atMostFiveDestinationsStartingOnHome() {
        // Limite MD3 d'une barre de navigation ; l'Accueil en tête, puis l'ordre du flux GTD ; pas de Réglages (barre du haut).
        assertEquals(
            listOf(Destination.HOME, Destination.NOTES, Destination.DAY, Destination.WEEK, Destination.STATS),
            Destination.entries,
        )
        assertEquals(Destination.HOME, Destination.START)
        assertEquals(Destination.DAY, Destination.WORK)
    }

    @Test
    fun theTeamSpaceAlsoStartsItsBarWithHome() {
        assertEquals(
            listOf(TeamDestination.HOME, TeamDestination.BOARD, TeamDestination.BACKLOG, TeamDestination.MEMBERS, TeamDestination.FORECAST),
            TeamDestination.entries,
        )
        // Passer dans l'espace Équipe ouvre le Suivi, pas l'Accueil.
        assertEquals(TeamDestination.BOARD, TeamDestination.START)
    }
}
