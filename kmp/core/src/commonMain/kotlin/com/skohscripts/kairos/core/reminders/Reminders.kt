package com.skohscripts.kairos.core.reminders

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.engine.plusMinutes
import com.skohscripts.kairos.core.model.KairosSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/** Nature d'un rappel : avant une tâche à heure fixe, ou au début de la tâche suivante du plan. */
enum class ReminderKind { FIXED, NEXT }

/**
 * Un rappel (docs/spec/rappels.md) : la tâche [taskId] ([title]) prévue de [start] à [end], à rappeler à [at]. [key] est stable
 * tant que la tâche garde son heure : la veille ne montre jamais deux fois le même rappel.
 */
data class Reminder(
    val kind: ReminderKind,
    val taskId: Long,
    val title: String,
    val at: LocalDateTime,
    val start: LocalDateTime,
    val end: LocalDateTime,
) {
    val key: String get() = "${kind.name.lowercase()}-$taskId-$start"
}

/**
 * Rappels du plan d'un jour (docs/spec/rappels.md, issue #103). Pur : le jour, l'heure et le fuseau sont des paramètres. Le plan
 * de la vue Jour fait foi : une tâche faite, bloquée ou programmée un autre jour n'y est pas, donc n'a pas de rappel.
 */
object Reminders {
    /**
     * Rappels de [day] encore à venir après [now] (heure locale ; `null` = début du jour), triés par heure : avant chaque tâche
     * épinglée (`début − avance`), et, si l'option est cochée, au début de chaque tâche non épinglée placée après [now]. Vide si
     * les rappels sont désactivés (ou jamais réglés).
     */
    fun plan(snapshot: KairosSnapshot, day: LocalDate, now: LocalDateTime?, timeZone: TimeZone): List<Reminder> {
        val settings = snapshot.settings.reminders ?: return emptyList()
        if (!settings.enabled) return emptyList()
        val view = DayView.build(snapshot, day, now, timeZone)
        val lead = settings.leadMinutes.coerceAtLeast(0)
        return view.schedule.scheduled.mapNotNull { s ->
            val end = s.start.plusMinutes(s.durationMinutes)
            val reminder = when {
                s.pinned -> Reminder(ReminderKind.FIXED, s.task.id, s.task.title, s.start.plusMinutes(-lead), s.start, end)
                settings.nextTask -> Reminder(ReminderKind.NEXT, s.task.id, s.task.title, s.start, s.start, end)
                else -> null
            }
            // Rien n'est rattrapé : un rappel dont l'heure est passée (ou la tâche en cours) n'est pas rendu.
            reminder?.takeIf { now == null || it.at > now }
        }.sortedBy { it.at }
    }
}
