package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.Settings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime

/**
 * Journée de travail d'un jour donné (docs/spec/planning-personnalise.md § Lot B1). Pur : sans [Settings.schedule], tous les
 * jours ont la fenêtre `workdayStartHour`..`workdayEndHour` des réglages, comme avant (week-ends compris).
 */
object Schedule {
    /** Plage de travail d'un jour : de [start] (inclus) à [end] (exclu). */
    data class Window(val start: LocalDateTime, val end: LocalDateTime) {
        val startHour: Int get() = start.hour
        val endHour: Int get() = end.hour
    }

    private fun usual(day: LocalDate, settings: Settings) =
        Window(day.atTime(settings.workdayStartHour, 0), day.atTime(settings.workdayEndHour, 0))

    /**
     * La plage où placer les tâches de [day] ; `null` pour un **jour libre** (rien n'est planifié automatiquement). Un jour
     * sans horaire propre, ou dont l'horaire est incomplet ou incohérent (début ≥ fin), a la journée des réglages.
     */
    fun windowFor(day: LocalDate, settings: Settings): Window? {
        val own = settings.schedule?.weekdays?.get(Workdays.weekday(day)) ?: return usual(day, settings)
        if (own.free) return null
        val start = own.startHour
        val end = own.endHour
        return if (start != null && end != null && start in 0..23 && end in 0..23 && start < end) Window(day.atTime(start, 0), day.atTime(end, 0)) else usual(day, settings)
    }

    /**
     * La plage à **afficher** (frise, rail du temps réel) : celle de [windowFor], ou, un jour libre, la journée des réglages
     * (les créneaux et les tâches épinglées de ce jour restent visibles).
     */
    fun displayWindow(day: LocalDate, settings: Settings): Window = windowFor(day, settings) ?: usual(day, settings)
}
