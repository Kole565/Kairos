package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.WorkWindow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime

/**
 * Journée de travail d'un jour donné (docs/spec/planning-personnalise.md § Lots B1 et B2). Pur : sans [Settings.schedule], tous
 * les jours ont la fenêtre `workdayStartHour`..`workdayEndHour` des réglages, comme avant (week-ends compris).
 */
object Schedule {
    /**
     * Plage de travail d'un jour : de [start] (inclus) à [end] (exclu). [context] : le nom du contexte à qui elle est réservée ;
     * `null` : ouverte à toute tâche (la journée habituelle, une plage sans contexte).
     */
    data class Window(val start: LocalDateTime, val end: LocalDateTime, val context: String? = null) {
        val startHour: Int get() = start.hour
        val endHour: Int get() = end.hour
    }

    private fun usual(day: LocalDate, settings: Settings) =
        Window(day.atTime(settings.workdayStartHour, 0), day.atTime(settings.workdayEndHour, 0))

    private fun valid(window: WorkWindow) = window.startHour in 0..23 && window.endHour in 0..23 && window.startHour < window.endHour

    /**
     * Les plages où placer les tâches de [day], dans l'ordre ; **vide** pour un **jour libre** (rien n'est planifié
     * automatiquement). Un jour sans horaire propre, ou dont l'horaire est incomplet ou incohérent (début ≥ fin), a la journée
     * des réglages. Plusieurs plages (lot B2) : celles qui sont incohérentes, ou qui commencent avant la fin de la précédente,
     * sont ignorées ; s'il n'en reste aucune, la journée des réglages.
     */
    fun windowsFor(day: LocalDate, settings: Settings): List<Window> {
        val own = settings.schedule?.weekdays?.get(Workdays.weekday(day)) ?: return listOf(usual(day, settings))
        if (own.free) return emptyList()
        if (own.windows.isNotEmpty()) {
            val kept = mutableListOf<Window>()
            for (w in own.windows.filter(::valid).sortedBy { it.startHour }) {
                val start = day.atTime(w.startHour, 0)
                if (kept.isNotEmpty() && start < kept.last().end) continue
                kept += Window(start, day.atTime(w.endHour, 0), w.context)
            }
            return kept.ifEmpty { listOf(usual(day, settings)) }
        }
        val start = own.startHour
        val end = own.endHour
        return if (start != null && end != null && start in 0..23 && end in 0..23 && start < end) listOf(Window(day.atTime(start, 0), day.atTime(end, 0))) else listOf(usual(day, settings))
    }

    /**
     * L'étendue de la journée : du début de la première plage à la fin de la dernière ; `null` pour un **jour libre**. Une seule
     * plage : cette plage (B1).
     */
    fun windowFor(day: LocalDate, settings: Settings): Window? {
        val windows = windowsFor(day, settings)
        if (windows.isEmpty()) return null
        return Window(windows.first().start, windows.last().end, windows.singleOrNull()?.context)
    }

    /**
     * L'étendue à **afficher** (frise, rail du temps réel) : celle de [windowFor], ou, un jour libre, la journée des réglages
     * (les créneaux et les tâches épinglées de ce jour restent visibles).
     */
    fun displayWindow(day: LocalDate, settings: Settings): Window = windowFor(day, settings) ?: usual(day, settings)

    /**
     * Le contexte d'une tâche de type [taskType] : le premier contexte qui le liste, `null` (**sans contexte**) si le type est
     * vide ou dans aucun contexte. Un type n'est dans qu'un contexte (le formulaire le refuse).
     */
    fun contextOf(taskType: String, settings: Settings): String? {
        val type = taskType.trim()
        if (type.isEmpty()) return null
        return settings.schedule?.contexts?.firstOrNull { c -> c.types.any { it.trim().equals(type, ignoreCase = true) } }?.name
    }

    /**
     * Une plage accueille une tâche de contexte [taskContext] : toujours si la tâche est sans contexte ou la plage ouverte,
     * sinon seulement si c'est le même contexte (comparé sans la casse).
     */
    fun accepts(window: Window, taskContext: String?): Boolean =
        taskContext == null || window.context == null || window.context.equals(taskContext, ignoreCase = true)
}
