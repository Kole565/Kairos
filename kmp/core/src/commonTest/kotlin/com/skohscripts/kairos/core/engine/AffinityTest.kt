package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.day.DayView
import com.skohscripts.kairos.core.model.BestHoursSettings
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Le planning préfère, à score égal, les heures du type (docs/spec/planning-personnalise.md § Lot C, étape C2). */
class AffinityTest {
    private val day = LocalDate(2026, 10, 14)
    private val t0 = Instant.parse("2026-10-01T07:00:00Z")
    private val settings = Settings() // journée 9 h-17 h, creux de l'après-midi actif par défaut

    private fun task(id: Long, type: String, priority: Int = 1, points: Int = 3, deadline: LocalDate? = null) =
        Task(id, "T$id", priority = priority, fibonacciPoints = points, estimatedMinutes = 60, taskType = type, deadline = deadline, createdAt = t0, updatedAt = t0)

    private fun affinity(strength: Double = 0.5, preferred: Map<String, Int> = emptyMap(), detected: Map<String, List<Double>> = emptyMap()) =
        Affinity(strength, detected, preferred, bucketHours = 2)

    private fun place(tasks: List<Task>, affinity: Affinity?, urgency: Map<Long, Scheduling.SortKey> = emptyMap(), s: Settings = settings) =
        Scheduling.buildDaySchedule(tasks, emptyList(), day, null, s, emptySet(), urgency, emptyMap(), affinity)

    @Test
    fun withoutAffinityTheScoreTieFallsBackToTheId() {
        val day = place(listOf(task(1, "Dev"), task(2, "Mail")), null)
        assertEquals(listOf(1L, 2L), day.scheduled.map { it.task.id })
        assertTrue(day.scheduled.none { it.affinityApplied })
    }

    @Test
    fun anEmptyOrZeroStrengthAffinityChangesNothing() {
        val tasks = listOf(task(1, "Dev"), task(2, "Mail"))
        val plain = place(tasks, null)
        assertEquals(plain, place(tasks, affinity()))
        assertEquals(plain, place(tasks, affinity(strength = 0.0, preferred = mapOf("mail" to 9))))
    }

    @Test
    fun theTypeAtItsPreferredHourWinsATie() {
        val result = place(listOf(task(1, "Dev"), task(2, "Mail")), affinity(preferred = mapOf("mail" to 9)))
        val first = result.scheduled.first()
        assertEquals(2L, first.task.id)
        assertEquals(AffinitySource.MANUAL, first.affinity)
        assertTrue(first.affinityApplied)
        // La tâche suivante est à 10 h : plus d'affinité pour Mail, Dev suit sans mention.
        assertEquals(1L, result.scheduled[1].task.id)
        assertNull(result.scheduled[1].affinity)
    }

    @Test
    fun aDetectedBucketMarksTheNoteAsDetected() {
        val boosts = List(12) { if (it == 4) 1.0 else 0.0 } // 8-10 h
        val first = place(listOf(task(1, "Dev"), task(2, "Mail")), affinity(detected = mapOf("mail" to boosts))).scheduled.first()
        assertEquals(2L, first.task.id)
        assertEquals(AffinitySource.DETECTED, first.affinity)
    }

    @Test
    fun theNoteIsOnlyForPicksThatTheAffinityActuallyChanged() {
        // Mail est déjà la première sans affinité (id plus petit) : elle est placée là, mais ce n'est pas l'affinité qui l'a choisie.
        val result = place(listOf(task(1, "Mail"), task(2, "Dev")), affinity(preferred = mapOf("mail" to 9)))
        assertEquals(1L, result.scheduled.first().task.id)
        assertFalse(result.scheduled.first().affinityApplied)
    }

    @Test
    fun aMoreUrgentTaskStillWinsBecauseTheBoostIsBounded() {
        // P0 contre P1 : le rapport des valeurs (4) dépasse le gain maximal (1,5).
        val result = place(listOf(task(1, "Dev", priority = 0), task(2, "Mail", priority = 1)), affinity(preferred = mapOf("mail" to 9)))
        assertEquals(1L, result.scheduled.first().task.id)
        assertFalse(result.scheduled.first().affinityApplied)
    }

    @Test
    fun anOverdueTaskIsNeverOvertaken() {
        val overdue = task(1, "Dev", deadline = day.let { LocalDate(2026, 10, 10) })
        val result = place(listOf(overdue, task(2, "Mail")), affinity(preferred = mapOf("mail" to 9)))
        assertEquals(1L, result.scheduled.first().task.id)
        assertFalse(result.scheduled.first().affinityApplied)
    }

    @Test
    fun aTaskRaisedByADependencyIsNeverMoved() {
        val tasks = listOf(task(1, "Dev"), task(2, "Mail"))
        // La tâche 1 est remontée (clé d'urgence dérivée plus petite que la sienne) : l'affinité de la 2 ne la dépasse pas.
        val raised = Scheduling.sortKey(tasks[0], day, settings).let { it.copy(negScore = it.negScore - 5) }
        val result = place(tasks.reversed(), affinity(preferred = mapOf("mail" to 9)), urgency = mapOf(1L to raised))
        assertEquals(1L, result.scheduled.first().task.id)
        assertFalse(result.scheduled.first().affinityApplied)
    }

    @Test
    fun theDisplayedScoreAndTheSortKeyIgnoreTheAffinity() {
        val a = task(1, "Dev")
        val before = Scheduling.sortKey(a, day, settings)
        place(listOf(a, task(2, "Mail")), affinity(preferred = mapOf("dev" to 9)))
        assertEquals(before, Scheduling.sortKey(a, day, settings))
    }

    @Test
    fun theAffinityCanOutweighTheDipAndOnlyItsOwnNoteIsShown() {
        // 14 h, creux à demi-intensité : la lourde et urgente (P0, 13 points) cède à la légère (P2, 1 point)…
        val s = settings.copy(workdayStartHour = 14, workdayEndHour = 18)
        val heavy = task(1, "Dev", priority = 0, points = 13)
        val light = task(2, "Mail", priority = 2, points = 1)
        val dip = Scheduling.buildDaySchedule(listOf(heavy, light), emptyList(), day, null, s).scheduled.first()
        assertEquals(2L, dip.task.id)
        assertTrue(dip.dipApplied)
        // …sauf si l'heure est celle de son type : l'affinité la ramène, et la mention du creux disparaît.
        val both = Scheduling.buildDaySchedule(listOf(heavy, light), emptyList(), day, null, s, affinity = affinity(preferred = mapOf("dev" to 14))).scheduled.first()
        assertEquals(1L, both.task.id)
        assertTrue(both.affinityApplied)
        assertFalse(both.dipApplied)
        assertEquals(LocalDateTime(2026, 10, 14, 14, 0), both.start)
    }

    @Test
    fun theDayViewMeasuresTheSessionsOnlyWhenTheSettingIsOn() {
        // Dev se travaille chaque matin à 9 h, Doc chaque après-midi à 15 h (6 sessions d'une heure chacun, le contraste que mesure le
        // « lift »), Mail jamais : Mail a l'id le plus petit.
        val tasks = listOf(task(1, "Mail"), task(2, "Dev"), task(3, "Doc"))
        val sessions = (1..6).flatMap { d ->
            listOf(2L to 9, 3L to 15).map { (id, hour) ->
                val start = LocalDateTime(2026, 10, 5 + d, hour, 0).toInstant(TimeZone.UTC)
                WorkSession(d * 10L + id, id, start, start.plus(1.hours), t0)
            }
        }
        fun view(bestHours: BestHoursSettings?) = DayView.build(
            KairosSnapshot(tasks = tasks, workSessions = sessions, settings = settings.copy(schedule = bestHours?.let { ScheduleSettings(bestHours = it) })),
            day, null, TimeZone.UTC,
        ).schedule.scheduled
        // Sans réglage ou désactivé : l'ordre d'avant.
        assertEquals(listOf(1L, 2L, 3L), view(null).map { it.task.id })
        assertEquals(listOf(1L, 2L, 3L), view(BestHoursSettings(enabled = false)).map { it.task.id })
        // Activé : Dev, habituellement à 9 h, passe en premier à 9 h, avec la mention « détectée ».
        val on = view(BestHoursSettings(enabled = true))
        assertEquals(2L, on.first().task.id)
        assertEquals(AffinitySource.DETECTED, on.first().affinity)
    }
}
