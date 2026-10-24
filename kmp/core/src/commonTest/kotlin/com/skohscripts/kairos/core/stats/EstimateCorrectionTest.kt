package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/** « Corriger les estimations… » (docs/spec/statistiques.md, issue #101) : quelles tâches, quelles valeurs. */
class EstimateCorrectionTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")

    private fun task(id: Long, estimate: Int?, status: TaskStatus = TaskStatus.DONE, space: TaskSpace = TaskSpace.PERSONAL, origin: String? = null, manual: Int? = null, updated: String = "2026-10-1${id % 10}T10:00:00Z") =
        Task(id, "T$id", estimatedMinutes = estimate, status = status, space = space, origin = origin, manualTimeSpentMinutes = manual, createdAt = now, updatedAt = Instant.parse(updated))

    private fun session(id: Long, taskId: Long, minutes: Int) =
        WorkSession(id, taskId, Instant.parse("2026-10-10T08:00:00Z"), Instant.parse("2026-10-10T08:00:00Z").plus(kotlin.time.Duration.parse("${minutes}m")), now)

    @Test
    fun only_finished_personal_timed_tasks_whose_estimate_differs() {
        val snapshot = KairosSnapshot(
            tasks = listOf(
                task(1, 30), // 45 min chronométrées : 30 → 45
                task(2, null), // sans estimation : → 20
                task(3, 60), // déjà juste : rien
                task(4, 30, status = TaskStatus.TODO), // pas faite
                task(5, 30, space = TaskSpace.TEAM), // tâche d'équipe
                task(6, 30, origin = "pack"), // reçue d'un manager
                task(7, 30, manual = 50), // temps saisi seul, sans chrono : rien de mesuré
                task(8, 30, manual = 15), // chrono 10 + saisi 15 = 25
            ),
            workSessions = listOf(
                session(1, 1, 45), session(2, 2, 20), session(3, 3, 60), session(4, 4, 90),
                session(5, 5, 90), session(6, 6, 90), session(7, 8, 10),
            ),
        )
        val got = EstimateCorrection.candidates(snapshot, now)
        assertEquals(setOf(1L, 2L, 8L), got.map { it.task.id }.toSet())
        assertEquals(30, got.single { it.task.id == 1L }.oldMinutes)
        assertEquals(45, got.single { it.task.id == 1L }.realMinutes)
        assertEquals(null, got.single { it.task.id == 2L }.oldMinutes)
        assertEquals(25, got.single { it.task.id == 8L }.realMinutes)
        // Les plus récemment terminées d'abord.
        assertEquals(listOf(8L, 2L, 1L), got.map { it.task.id })
    }

    @Test
    fun nothing_to_correct_without_timed_finished_tasks() {
        assertEquals(emptyList(), EstimateCorrection.candidates(KairosSnapshot(tasks = listOf(task(1, 30))), now))
        assertEquals(emptyList(), EstimateCorrection.candidates(KairosSnapshot(), now))
    }
}
