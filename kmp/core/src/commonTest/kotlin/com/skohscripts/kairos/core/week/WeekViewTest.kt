package com.skohscripts.kairos.core.week

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class WeekViewTest {
    private val t0 = Instant.parse("2026-09-28T07:00:00Z")

    private fun task(id: Long, deadline: LocalDate? = null, p: Int? = null, status: TaskStatus = TaskStatus.TODO, updated: Instant = t0, type: String = "") =
        Task(id, "T$id", priority = p, deadline = deadline, status = status, taskType = type, createdAt = t0, updatedAt = updated)

    @Test
    fun scheduled_tasks_appear_on_their_day_once() {
        val wed = LocalDate(2026, 9, 30)
        val thu = LocalDate(2026, 10, 1)
        val snapshot = KairosSnapshot(
            tasks = listOf(
                task(1).copy(scheduledDate = wed), // programmée seulement : ce jour-là
                task(2, deadline = wed).copy(scheduledDate = wed), // échéance le même jour : une seule ligne (l'échéance)
                task(3, deadline = thu).copy(scheduledDate = wed), // programmée avant son échéance : aux deux jours
                task(4, status = TaskStatus.DONE).copy(scheduledDate = wed), // faite : pas programmée
                task(5).copy(scheduledDate = LocalDate(2026, 10, 7)), // semaine suivante
            ),
        )
        val view = WeekView.build(snapshot, wed, t0, TimeZone.UTC)
        val day = view.days.single { it.date == wed }
        assertEquals(listOf(1L, 3L), day.scheduled.map { it.id })
        assertEquals(listOf(2L), day.tasks.map { it.id })
        assertEquals(listOf(3L), view.days.single { it.date == thu }.tasks.map { it.id })
    }

    @Test
    fun seven_days_with_deadlines_done_and_blocks() {
        val wed = LocalDate(2026, 9, 30)
        val snapshot = KairosSnapshot(
            tasks = listOf(
                task(1, wed, p = 2), task(2, wed), task(3, wed, p = 0), task(4, wed, status = TaskStatus.ARCHIVED),
                task(5, status = TaskStatus.DONE, updated = Instant.parse("2026-09-30T10:00:00Z")),
                task(6, LocalDate(2026, 10, 5)), // semaine suivante
            ),
            timeBlocks = listOf(
                TimeBlock(1, "Déjeuner", LocalDateTime(2026, 9, 1, 12, 0), LocalDateTime(2026, 9, 1, 13, 0), recurrence = BlockRecurrence.WEEKDAYS, createdAt = t0),
                TimeBlock(2, "Point", LocalDateTime(2026, 9, 30, 9, 0), LocalDateTime(2026, 9, 30, 9, 30), createdAt = t0),
            ),
            workSessions = listOf(
                WorkSession(1, 1, Instant.parse("2026-09-29T08:00:00Z"), Instant.parse("2026-09-29T09:30:00Z"), t0),
                WorkSession(2, 5, Instant.parse("2026-09-21T08:00:00Z"), Instant.parse("2026-09-21T09:00:00Z"), t0),
            ),
        )
        val week = WeekView.build(snapshot, LocalDate(2026, 10, 2), t0, TimeZone.UTC)
        assertEquals(LocalDate(2026, 9, 28), week.monday)
        assertEquals(7, week.days.size)
        val wednesday = week.days[2]
        assertEquals(listOf(3L, 1L, 2L), wednesday.tasks.map { it.id })
        assertEquals(listOf(5L), wednesday.done.map { it.id })
        assertEquals(listOf("Point", "Déjeuner"), wednesday.blocks.map { it.title })
        assertEquals(emptyList(), week.days[5].blocks) // samedi : pas de déjeuner « jours ouvrés »
        assertEquals(90, week.spentMinutes)
        assertEquals(emptyMap(), week.spentByType)
        assertEquals(listOf(1L), WeekView.build(snapshot, wed, t0, TimeZone.UTC, DayFilter(priority = 2)).days[2].tasks.map { it.id })
    }
}
