package com.skohscripts.kairos.ui.navigation

import androidx.compose.ui.graphics.vector.ImageVector
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.nav_day
import com.skohscripts.kairos.ui.generated.resources.nav_home
import com.skohscripts.kairos.ui.generated.resources.nav_notes
import com.skohscripts.kairos.ui.generated.resources.nav_stats
import com.skohscripts.kairos.ui.generated.resources.nav_week
import com.skohscripts.kairos.ui.generated.resources.title_day
import com.skohscripts.kairos.ui.generated.resources.title_home
import com.skohscripts.kairos.ui.generated.resources.title_notes
import com.skohscripts.kairos.ui.generated.resources.title_stats
import com.skohscripts.kairos.ui.generated.resources.title_week
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.StringResource

/**
 * Les cinq destinations de premier niveau : l'Accueil en tête, puis l'ordre du flux GTD, capturer
 * (Notes) avant de traiter et faire (Jour). Cinq au plus : limite MD3 d'une barre de navigation.
 * Les Réglages ne sont pas une destination : la roue crantée de la barre du haut les ouvre
 * (écran secondaire [Secondary.SETTINGS], docs/spec/navigation-theme.md).
 */
enum class Destination(
    override val label: StringResource,
    override val title: StringResource,
    override val icon: () -> ImageVector,
    override val selectedIcon: () -> ImageVector,
) : NavEntry {
    HOME(Res.string.nav_home, Res.string.title_home, { KairosIcons.Home }, { KairosIcons.HomeFilled }),
    NOTES(Res.string.nav_notes, Res.string.title_notes, { KairosIcons.Notes }, { KairosIcons.NotesFilled }),
    DAY(Res.string.nav_day, Res.string.title_day, { KairosIcons.Today }, { KairosIcons.TodayFilled }),
    WEEK(Res.string.nav_week, Res.string.title_week, { KairosIcons.DateRange }, { KairosIcons.DateRangeFilled }),
    STATS(Res.string.nav_stats, Res.string.title_stats, { KairosIcons.BarChart }, { KairosIcons.BarChartFilled }),
    ;

    companion object {
        /** Écran d'ouverture de l'application : l'Accueil (docs/spec/accueil.md). */
        val START = HOME

        /** Destination ouverte en passant dans l'espace Perso : on y vient pour travailler, pas pour l'Accueil. */
        val WORK = DAY
    }
}
