package com.skohscripts.kairos.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.nav_team_backlog
import com.skohscripts.kairos.ui.generated.resources.nav_team_board
import com.skohscripts.kairos.ui.generated.resources.nav_team_forecast
import com.skohscripts.kairos.ui.generated.resources.nav_team_members
import com.skohscripts.kairos.ui.generated.resources.nav_home
import com.skohscripts.kairos.ui.generated.resources.title_team_backlog
import com.skohscripts.kairos.ui.generated.resources.title_team_board
import com.skohscripts.kairos.ui.generated.resources.title_team_forecast
import com.skohscripts.kairos.ui.generated.resources.title_team_members
import com.skohscripts.kairos.ui.generated.resources.title_home
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource

/**
 * Ce que la coquille sait afficher d'une destination, quel que soit l'espace :
 * libellé de navigation, titre de page et icônes (pleine quand elle est active).
 * [Destination] (espace Perso) et [TeamDestination] (espace Équipe) l'implémentent ;
 * la navigation n'a ainsi qu'une seule implémentation (docs/spec/equipe.md § Interface).
 */
sealed interface NavEntry {
    val label: StringResource
    val title: StringResource
    val icon: () -> ImageVector
    val selectedIcon: () -> ImageVector
}

/**
 * Les deux espaces de Kairos. `PERSONAL` est l'application d'avant l'espace
 * Équipe ; `TEAM` n'est atteignable que si la gestion d'équipe est activée
 * et enregistrée (`Settings.teamModeEnabled`).
 */
enum class Space { PERSONAL, TEAM }

/**
 * Les cinq destinations de l'espace Équipe, dans l'ordre de la navigation : l'Accueil (le même
 * écran que dans l'espace Perso) en tête. `BOARD` (Suivi) ouvre l'espace quand on y passe.
 * Cinq au plus, comme [Destination] (limite MD3) ; les Réglages sont dans la barre du haut.
 */
enum class TeamDestination(
    override val label: StringResource,
    override val title: StringResource,
    override val icon: () -> ImageVector,
    override val selectedIcon: () -> ImageVector,
) : NavEntry {
    HOME(Res.string.nav_home, Res.string.title_home, { KairosIcons.Home }, { KairosIcons.HomeFilled }),
    BOARD(Res.string.nav_team_board, Res.string.title_team_board, { KairosIcons.ViewKanban }, { KairosIcons.ViewKanbanFilled }),
    BACKLOG(Res.string.nav_team_backlog, Res.string.title_team_backlog, { KairosIcons.Stacks }, { KairosIcons.StacksFilled }),
    MEMBERS(Res.string.nav_team_members, Res.string.title_team_members, { KairosIcons.Groups }, { KairosIcons.GroupsFilled }),
    FORECAST(Res.string.nav_team_forecast, Res.string.title_team_forecast, { KairosIcons.Monitoring }, { KairosIcons.MonitoringFilled }),
    ;

    companion object {
        /** Destination ouverte en passant dans l'espace Équipe. Au lancement, c'est l'Accueil ([HOME]). */
        val START = BOARD
    }
}
