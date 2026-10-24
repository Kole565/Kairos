package com.skohscripts.kairos.core.month

import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.DaySchedule
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Vue Mois (docs/spec/vue-mois.md) sur novembre 2026 : commence un dimanche, 11 novembre férié, mercredis libres. */
class MonthViewTest {
    private val t0 = Instant.parse("2026-11-01T08:00:00Z")
    private val settings = Settings(
        holidaysFr = true,
        // Mercredi (2) libre : aucune plage de travail.
        schedule = ScheduleSettings(mapOf(2 to DaySchedule(free = true))),
    )

    private fun task(id: Long, deadline: LocalDate? = null, scheduled: LocalDate? = null, minutes: Int? = 60, status: TaskStatus = TaskStatus.TODO, p: Int? = 1) =
        Task(id, "T$id", priority = p, fibonacciPoints = 3, deadline = deadline, scheduledDate = scheduled, estimatedMinutes = minutes, status = status, createdAt = t0, updatedAt = t0)

    private fun day(view: MonthView, d: Int) = view.days[d - 1]

    @Test
    fun the_grid_starts_on_monday_and_covers_the_whole_month() {
        val view = MonthView.build(KairosSnapshot(settings = settings), LocalDate(2026, 11, 18))
        assertEquals(LocalDate(2026, 11, 1), view.first)
        assertEquals(30, view.days.size)
        // Le 1er novembre 2026 est un dimanche : six cases vides avant lui.
        assertEquals(6, view.leading)
        assertEquals(6, view.weeks.size)
        assertTrue(view.weeks.all { it.size == 7 })
        assertNull(view.weeks.first().first())
        assertEquals(LocalDate(2026, 11, 1), view.weeks.first().last()?.date)
        assertEquals(LocalDate(2026, 11, 30), view.weeks.last().first()?.date)
        // Mois d'avant et d'après, changement d'année compris.
        assertEquals(LocalDate(2026, 12, 1), MonthView.shift(view.first, 1))
        assertEquals(LocalDate(2027, 1, 1), MonthView.shift(LocalDate(2026, 12, 1), 1))
        assertEquals(LocalDate(2025, 12, 1), MonthView.shift(LocalDate(2026, 1, 1), -1))
    }

    @Test
    fun planned_tasks_deadlines_holidays_free_days_and_overload() {
        val tasks = listOf(
            task(1, scheduled = LocalDate(2026, 11, 3)),
            // Due le 3, programmée le 5 : prévue le 5, échéance le 3.
            task(2, deadline = LocalDate(2026, 11, 3), scheduled = LocalDate(2026, 11, 5)),
            // Trois tâches de 4 h le lundi 9 : 12 h pour 9 h de journée, moins la réunion.
            task(3, deadline = LocalDate(2026, 11, 9), minutes = 240),
            task(4, deadline = LocalDate(2026, 11, 9), minutes = 240),
            task(5, scheduled = LocalDate(2026, 11, 9), minutes = null),
            // Faite : ne compte nulle part.
            task(6, deadline = LocalDate(2026, 11, 3), status = TaskStatus.DONE),
        )
        val blocks = listOf(
            TimeBlock(1, "Réunion", LocalDateTime(2026, 11, 9, 10, 0), LocalDateTime(2026, 11, 9, 12, 0), createdAt = t0),
            // Série « le 11 du mois » : le 11 novembre est férié, elle recule au mardi 10.
            TimeBlock(2, "Comptable", LocalDateTime(2026, 10, 11, 14, 0), LocalDateTime(2026, 10, 11, 15, 0), recurrence = BlockRecurrence.MONTHLY_DAY, createdAt = t0),
        )
        val view = MonthView.build(KairosSnapshot(tasks = tasks, timeBlocks = blocks, settings = settings), LocalDate(2026, 11, 1))
        assertEquals(listOf(1L), day(view, 3).planned.map { it.id })
        assertEquals(listOf(2L), day(view, 3).deadlines.map { it.id })
        assertEquals(listOf(2L), day(view, 5).planned.map { it.id })
        assertEquals(emptyList(), day(view, 5).deadlines)
        // Lundi 9 : 240 + 240 + 30 (durée par défaut) = 510 min pour 540 - 120 = 420 min.
        val monday = day(view, 9)
        assertEquals(510, monday.plannedMinutes)
        assertEquals(420, monday.capacityMinutes)
        assertTrue(monday.overloaded)
        assertFalse(day(view, 3).overloaded)
        // La série mensuelle tombe le 10, pas le 11 (férié).
        assertEquals(listOf("Comptable"), day(view, 10).slots.map { it.title })
        assertEquals(emptyList(), day(view, 11).slots)
        assertTrue(day(view, 11).holiday)
        assertFalse(day(view, 10).holiday)
        // Mercredi 18 : jour libre, sans capacité ; rien n'y est prévu, donc pas de surcharge.
        assertTrue(day(view, 18).dayOff)
        assertEquals(0, day(view, 18).capacityMinutes)
        assertFalse(day(view, 18).overloaded)
        assertFalse(day(view, 17).dayOff)
    }
}
