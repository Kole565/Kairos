package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Évolutions (docs/spec/statistiques.md § Évolutions, issue #112) sur un jeu figé : aujourd'hui jeudi 22 octobre 2026,
 * fuseau UTC. Les valeurs attendues sont écrites à la main (fichier d'or en clair).
 */
class StatsTrendsTest {
    private val tz = TimeZone.UTC
    private val today = LocalDate(2026, 10, 22)
    private val now = Instant.parse("2026-10-22T18:00:00Z")

    private fun session(id: Long, task: Long, start: String, minutes: Int) =
        WorkSession(id, task, Instant.parse(start), Instant.parse(start) + minutes.minutes, Instant.parse(start))

    private fun done(id: Long, estimate: Int?, finished: String, manual: Int? = null) =
        Task(id, "T$id", estimatedMinutes = estimate, status = TaskStatus.DONE, manualTimeSpentMinutes = manual, createdAt = Instant.parse("2026-09-01T08:00:00Z"), updatedAt = Instant.parse(finished))

    private val sessions = listOf(
        session(1, 1, "2026-10-19T08:00:00Z", 90), // lundi de cette semaine
        session(2, 1, "2026-10-19T14:00:00Z", 30), // même jour : 120
        session(3, 2, "2026-10-21T09:00:00Z", 45), // mercredi
        session(4, 3, "2026-10-13T09:00:00Z", 60), // mardi de la semaine d'avant
        session(5, 4, "2026-09-30T09:00:00Z", 200), // hors fenêtre de 2 semaines
    )

    private val tasks = listOf(
        done(1, 60, "2026-10-19T16:00:00Z"), // estimé 60, réel 120
        done(2, 60, "2026-10-21T10:00:00Z", manual = 15), // estimé 60, réel 45 + 15 = 60
        done(3, 30, "2026-10-13T10:00:00Z"), // semaine d'avant : estimé 30, réel 60
        done(4, null, "2026-09-30T12:00:00Z"), // sans estimation : jamais dans le biais
        Task(5, "À faire", estimatedMinutes = 30, createdAt = now, updatedAt = now),
    )

    @Test
    fun tracked_minutes_per_day_over_the_window() {
        val tracked = StatsTrends.tracked(sessions, today, 2, now, tz)
        assertTrue(tracked.daily)
        // Du lundi 12 au jeudi 22 octobre : 11 jours.
        assertEquals(11, tracked.points.size)
        assertEquals(LocalDate(2026, 10, 12), tracked.points.first().start)
        assertEquals(today, tracked.points.last().start)
        assertEquals(60, tracked.points.single { it.start == LocalDate(2026, 10, 13) }.minutes)
        assertEquals(120, tracked.points.single { it.start == LocalDate(2026, 10, 19) }.minutes)
        assertEquals(45, tracked.points.single { it.start == LocalDate(2026, 10, 21) }.minutes)
        assertEquals(225, tracked.total)
        assertEquals(3, tracked.active)
        assertEquals(LocalDate(2026, 10, 19), tracked.busiest?.start)
        assertEquals(75, tracked.averageActive)
    }

    @Test
    fun tracked_minutes_switch_to_weeks_beyond_eight_weeks() {
        val tracked = StatsTrends.tracked(sessions, today, 12, now, tz)
        assertFalse(tracked.daily)
        assertEquals(12, tracked.points.size)
        assertEquals(LocalDate(2026, 8, 3), tracked.points.first().start)
        assertEquals(165, tracked.points.single { it.start == LocalDate(2026, 10, 19) }.minutes)
        assertEquals(60, tracked.points.single { it.start == LocalDate(2026, 10, 12) }.minutes)
        assertEquals(200, tracked.points.single { it.start == LocalDate(2026, 9, 28) }.minutes)
        assertEquals(425, tracked.total)
    }

    @Test
    fun weekly_bias_matches_the_global_bias_on_the_window() {
        val bias = StatsTrends.weeklyBias(tasks, sessions, today, 2, now, tz)
        assertEquals(listOf(LocalDate(2026, 10, 12), LocalDate(2026, 10, 19)), bias.map { it.weekStart })
        val last = bias[1]
        assertEquals(2, last.count)
        assertEquals(120, last.estimatedMinutes)
        assertEquals(180, last.realMinutes)
        assertEquals(1.5, last.ratio!!, 1e-9)
        assertFalse(last.reliable)
        assertEquals(2.0, bias[0].ratio!!, 1e-9)
        // La somme des semaines = le biais global sur les tâches de la fenêtre.
        val global = TaskStats.estimationBias(tasks.filter { it.id != 4L }, com.skohscripts.kairos.core.engine.TimeTracking.spentMinutesByTask(sessions, now, tasks))!!
        assertEquals(global.estimatedMinutes, bias.sumOf { it.estimatedMinutes })
        assertEquals(global.realMinutes, bias.sumOf { it.realMinutes })
        // Une semaine sans tâche : ratio nul, pas « 0 ».
        assertNull(StatsTrends.weeklyBias(tasks, sessions, today, 3, now, tz).first().ratio)
    }

    @Test
    fun the_window_follows_the_stats_settings_and_the_filters() {
        val input = StatsQuery().apply(tasks, sessions, Settings(statsWindowWeeks = 2), today, tz)
        val result = StatsTrends.compute(input.tasks, input.sessions, today, input.settings.statsWindowWeeks, now, tz)
        assertEquals(11, result.tracked.points.size)
        assertTrue(result.hasTracked)
        assertTrue(result.hasBias)
        // « Tout » remonte à la première activité, borné à 26 semaines.
        val all = StatsQuery(weeks = StatsQuery.ALL).apply(tasks, sessions, Settings(), today, tz)
        assertEquals(StatsTrends.MAX_WEEKS.coerceAtMost(all.settings.statsWindowWeeks), StatsTrends.compute(all.tasks, all.sessions, today, all.settings.statsWindowWeeks, now, tz).bias.size)
        // Rien à montrer sans session ni tâche faite.
        val empty = StatsTrends.compute(emptyList(), emptyList(), today, 4, now, tz)
        assertFalse(empty.hasTracked)
        assertFalse(empty.hasBias)
    }
}
