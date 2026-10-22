package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Filtres de l'écran Stats (docs/spec/statistiques.md § Filtres, issue #114) : les facettes de la vue Jour ([filter]) et la
 * période ([weeks] : `null` = fenêtre des réglages, [ALL] = depuis la première activité). Pur. Sans filtre ni période,
 * l'entrée est exactement celle d'avant les filtres : le tableau de bord reste identique aux fichiers d'or.
 */
data class StatsQuery(val filter: DayFilter = DayFilter(), val weeks: Int? = null) {
    /** Ce que reçoivent les calculs : tâches et sessions retenues, réglages à la fenêtre choisie ; [total] = tâches avant filtre. */
    data class Input(val tasks: List<Task>, val sessions: List<WorkSession>, val settings: Settings, val all: Boolean, val total: Int)

    fun apply(tasks: List<Task>, sessions: List<WorkSession>, settings: Settings, today: LocalDate, tz: TimeZone): Input {
        val keptTasks = if (filter.active) tasks.filter(filter::matches) else tasks
        // Sans filtre, toutes les sessions comptent (y compris celles d'une tâche disparue), comme avant les filtres.
        val keptSessions = if (filter.active) {
            val ids = keptTasks.mapTo(HashSet()) { it.id }
            sessions.filter { it.taskId in ids }
        } else {
            sessions
        }
        val window = when (weeks) {
            null -> settings.statsWindowWeeks
            ALL -> weeksSinceFirstActivity(keptTasks, keptSessions, today, tz)
            else -> weeks
        }
        val adjusted = if (window == settings.statsWindowWeeks) settings else settings.copy(statsWindowWeeks = window)
        return Input(keptTasks, keptSessions, adjusted, weeks == ALL, tasks.size)
    }

    /** Tableau de bord sur l'entrée filtrée ; pour [ALL], le débit garde les [ALL_THROUGHPUT_WEEKS] dernières semaines. */
    fun dashboard(input: Input, today: LocalDate, now: Instant, tz: TimeZone): TaskStats.Dashboard {
        val board = TaskStats.dashboard(input.tasks, input.sessions, today, input.settings, now, tz)
        return if (input.all) board.copy(throughput = board.throughput.takeLast(ALL_THROUGHPUT_WEEKS)) else board
    }

    /** « Quand tu travailles chaque type » sur la même entrée. */
    fun hours(input: Input, now: Instant, tz: TimeZone): BestHours.Result = BestHours.measure(input.tasks, input.sessions, now, input.settings, tz)

    companion object {
        /** Période « Tout ». */
        const val ALL = 0

        /** Semaines de débit gardées pour « Tout » : une ligne par semaine sur deux ans ferait une liste de cent lignes. */
        const val ALL_THROUGHPUT_WEEKS = 26

        /** Périodes proposées : 4, 8, 12 et la fenêtre des réglages si elle n'y est pas, triées, puis [ALL]. */
        fun periodOptions(settings: Settings): List<Int> =
            (listOf(4, 8, 12) + maxOf(1, settings.statsWindowWeeks)).distinct().sorted() + ALL

        /** Semaines du lundi de la première activité (création d'une tâche, début d'une session) à aujourd'hui, au moins 1. */
        internal fun weeksSinceFirstActivity(tasks: List<Task>, sessions: List<WorkSession>, today: LocalDate, tz: TimeZone): Int {
            val first = (tasks.map { it.createdAt } + sessions.map { it.startedAt }).minOrNull() ?: return 1
            val start = TaskStats.monday(first.toLocalDateTime(tz).date)
            return maxOf(1, start.daysUntil(TaskStats.monday(today)) / 7 + 1)
        }
    }
}
