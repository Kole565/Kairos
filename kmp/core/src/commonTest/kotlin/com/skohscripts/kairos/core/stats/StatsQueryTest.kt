package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Filtres de l'écran Stats (docs/spec/statistiques.md § Filtres, issue #114). */
class StatsQueryTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 10, 22) // jeudi
    private val now = Instant.parse("2026-10-22T12:00:00Z")
    private val settings = Settings()

    private fun task(id: Long, type: String, created: String = "2026-10-01T08:00:00Z", done: Boolean = true, points: Int = 3) = Task(
        id, "T$id", taskType = type, fibonacciPoints = points, estimatedMinutes = 60,
        status = if (done) TaskStatus.DONE else TaskStatus.TODO,
        createdAt = Instant.parse(created), updatedAt = Instant.parse("2026-10-20T10:00:00Z"),
    )

    private fun session(id: Long, taskId: Long, start: String, minutes: Long = 90) =
        Instant.parse(start).let { WorkSession(id, taskId, it, Instant.fromEpochMilliseconds(it.toEpochMilliseconds() + minutes * 60_000), it) }

    private val tasks = listOf(task(1, "Dev"), task(2, "Meeting"), task(3, "Dev", done = false))
    private val sessions = listOf(
        session(1, 1, "2026-10-20T09:00:00Z"),
        session(2, 2, "2026-10-19T14:00:00Z", 30),
        // Session d'une tâche disparue : comptée sans filtre, comme avant les filtres.
        session(3, 99, "2026-10-21T09:00:00Z", 45),
    )

    @Test
    fun theDefaultQueryChangesNothing() {
        val input = StatsQuery().apply(tasks, sessions, settings, today, utc)
        assertSame(tasks, input.tasks)
        assertSame(sessions, input.sessions)
        assertSame(settings, input.settings)
        assertEquals(TaskStats.dashboard(tasks, sessions, today, settings, now, utc), StatsQuery().dashboard(input, today, now, utc))
    }

    @Test
    fun aTypeFilterKeepsItsTasksAndTheirSessionsOnly() {
        val query = StatsQuery(DayFilter(taskType = "Dev"))
        val input = query.apply(tasks, sessions, settings, today, utc)
        assertEquals(listOf(1L, 3L), input.tasks.map { it.id })
        assertEquals(listOf(1L), input.sessions.map { it.id })
        assertEquals(3, input.total)
        val board = query.dashboard(input, today, now, utc)
        assertEquals(1, board.completedInWindow)
        assertEquals(90, board.trackedMinutesWindow)
        assertEquals(listOf("Dev"), board.timeByType.map { it.key })
        // La calibration ne porte que sur les tâches retenues.
        assertEquals(1, board.calibration.single().count)
        assertEquals(listOf("Dev"), query.hours(input, now, utc).profiles.map { it.type })
    }

    @Test
    fun aPeriodReplacesTheSettingsWindowWithoutTouchingTheCalibration() {
        val query = StatsQuery(weeks = 4)
        val input = query.apply(tasks, sessions, settings, today, utc)
        assertEquals(4, input.settings.statsWindowWeeks)
        val board = query.dashboard(input, today, now, utc)
        assertEquals(4, board.windowWeeks)
        assertEquals(4, board.throughput.size)
        assertEquals(TaskStats.dashboard(tasks, sessions, today, settings, now, utc).calibration, board.calibration)
    }

    @Test
    fun allGoesBackToTheFirstActivityAndKeepsTheLast26WeeksOfThroughput() {
        // Première activité : une tâche créée le mercredi 3 juillet 2024, semaine du lundi 1er juillet.
        val old = tasks + task(4, "Dev", created = "2024-07-03T08:00:00Z")
        val query = StatsQuery(weeks = StatsQuery.ALL)
        val input = query.apply(old, sessions, settings, today, utc)
        assertTrue(input.all)
        // Du lundi 1er juillet 2024 au lundi 19 octobre 2026 : 120 semaines d'écart, 121 semaines.
        assertEquals(121, input.settings.statsWindowWeeks)
        val board = query.dashboard(input, today, now, utc)
        assertEquals(StatsQuery.ALL_THROUGHPUT_WEEKS, board.throughput.size)
        assertEquals(LocalDate(2026, 10, 19), board.throughput.last().weekStart)
        // Sans aucune activité : une semaine.
        assertEquals(1, StatsQuery.weeksSinceFirstActivity(emptyList(), emptyList(), today, utc))
    }

    @Test
    fun theSettingsWindowJoinsThePeriodOptions() {
        assertEquals(listOf(4, 8, 12, StatsQuery.ALL), StatsQuery.periodOptions(Settings()))
        assertEquals(listOf(4, 6, 8, 12, StatsQuery.ALL), StatsQuery.periodOptions(Settings(statsWindowWeeks = 6)))
    }
}
