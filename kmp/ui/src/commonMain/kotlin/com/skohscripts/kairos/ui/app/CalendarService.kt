package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.skohscripts.kairos.core.calendar.DeviceCalendarData
import com.skohscripts.kairos.core.calendar.ExternalBlocks
import com.skohscripts.kairos.core.model.KairosSnapshot
import kotlinx.coroutines.flow.StateFlow

/** Un calendrier du téléphone : identifiant (propre à l'appareil), nom, compte. */
data class DeviceCalendar(val id: Long, val name: String, val account: String)

/** État des calendriers du téléphone (docs/spec/connexions-externes.md § Étape 2). */
sealed interface DeviceCalendarState {
    /** Pas activé : rien n'est lu. */
    data object Off : DeviceCalendarState

    /** Activé, mais l'autorisation de lire le calendrier a été refusée ou retirée : plus rien n'est lu. */
    data object Denied : DeviceCalendarState

    /** Lus en direct : les calendriers du téléphone et ceux choisis (aucun par défaut). */
    data class On(val calendars: List<DeviceCalendar>, val selected: Set<Long>) : DeviceCalendarState
}

/**
 * Calendriers du téléphone, Android seulement (`AppServices.calendars`, nul ailleurs) : leurs événements sont lus en direct,
 * jamais stockés ni exportés ; seuls les écrans qui calculent un planning les ajoutent ([withDeviceCalendars]).
 */
interface CalendarService {
    val state: StateFlow<DeviceCalendarState>

    /** Créneaux et journées des calendriers choisis, d'il y a un mois à dans 6 mois. */
    val data: StateFlow<DeviceCalendarData>

    /** « Utiliser les calendriers du téléphone » : demande l'autorisation (seulement à ce geste). */
    suspend fun enable()

    suspend fun disable()

    suspend fun select(ids: Set<Long>)

    /** Ouvre les réglages de Kairos dans le système (autorisation retirée). */
    fun openSystemSettings()
}

/** [snapshot] avec les calendriers du téléphone, pour un écran qui calcule un planning ; tel quel sans service. */
@Composable
fun withDeviceCalendars(services: AppServices, snapshot: KairosSnapshot): KairosSnapshot {
    val service = services.calendars ?: return snapshot
    val data by service.data.collectAsState()
    return remember(snapshot, data) { ExternalBlocks.merge(snapshot, data) }
}
