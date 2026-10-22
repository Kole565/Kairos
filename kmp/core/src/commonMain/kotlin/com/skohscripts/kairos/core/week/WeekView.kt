package com.skohscripts.kairos.core.week

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.engine.Recurrence
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.Workdays
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.stats.TaskStats
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Un jour de la grille semaine : échéances à faire, tâches faites ce jour-là, créneaux, et les tâches **programmées pour**
 * ce jour ([scheduled], dont l'échéance n'est pas déjà ce jour-là : une tâche, une seule ligne).
 */
data class WeekDay(
    val date: LocalDate,
    val tasks: List<Task>,
    val done: List<Task>,
    val blocks: List<TimeBlock>,
    val scheduled: List<Task> = emptyList(),
)

/**
 * Vue Semaine (docs/spec/vue-semaine.md) :
 * sept jours du lundi au dimanche ; tâches à faire groupées par **échéance**
 * (priorité d'abord, sans priorité en dernier), tâches faites par jour de
 * complétion (titre), tâches programmées pour le jour, créneaux du jour (occurrences des récurrents comprises)
 * ; temps chronométré de la semaine, total et par type. Le filtre réduit les
 * listes, jamais la grille.
 */
data class WeekView(
    val monday: LocalDate,
    val days: List<WeekDay>,
    val spentMinutes: Int,
    val spentByType: Map<String, Int>,
) {
    companion object {
        fun build(snapshot: KairosSnapshot, anyDay: LocalDate, now: Instant, timeZone: TimeZone, filter: DayFilter = DayFilter()): WeekView {
            val monday = TaskStats.monday(anyDay)
            val sunday = monday.plus(DatePeriod(days = 6))
            val open = snapshot.tasks.filter { it.status == TaskStatus.TODO && filter.matches(it) }
            val done = snapshot.tasks.filter { it.status == TaskStatus.DONE && filter.matches(it) }
            val templates = snapshot.timeBlocks.filter { it.recurrence != BlockRecurrence.NONE }
            // Fériés des réglages : un créneau « jours ouvrés » les saute (docs/spec/recurrence.md, issue #109).
            val holidays = Workdays.holidaysFor(monday, snapshot.settings.holidaysFr, snapshot.settings.extraHolidays)
            val occurrences = Recurrence.expandRecurringBlocks(templates, monday, sunday, holidays)
            val oneOff = snapshot.timeBlocks.filter { it.recurrence == BlockRecurrence.NONE }
            val days = (0..6).map { i ->
                val day = monday.plus(DatePeriod(days = i))
                WeekDay(
                    date = day,
                    tasks = open.filter { it.deadline == day }.sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 })),
                    done = done.filter { it.updatedAt.toLocalDateTime(timeZone).date == day }.sortedBy { it.title },
                    blocks = (oneOff + occurrences).filter { it.start.date == day }.sortedBy { it.start },
                    scheduled = open.filter { it.scheduledDate == day && it.deadline != day }
                        .sortedWith(compareBy<Task>({ it.priority == null }, { it.priority ?: 999 }, { it.title })),
                )
            }
            val sessions = TimeTracking.sessionsInRange(snapshot.workSessions, monday, sunday, timeZone)
            val typeById = snapshot.tasks.associate { it.id to it.taskType }
            return WeekView(
                monday = monday,
                days = days,
                spentMinutes = TimeTracking.totalMinutes(sessions, now),
                spentByType = TimeTracking.spentMinutesByType(sessions, typeById, now).filter { (k, v) -> k.isNotEmpty() && v > 0 },
            )
        }
    }
}
