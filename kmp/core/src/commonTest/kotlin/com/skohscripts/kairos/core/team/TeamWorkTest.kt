package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockRecurrence
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.model.TimeBlock
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.mondayMorning
import com.skohscripts.kairos.core.team.TeamFixtures.settings
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.TeamFixtures.stamp
import com.skohscripts.kairos.core.team.TeamFixtures.task
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/** Travail perso de « moi », récurrences projetées, créneaux et temps passé dans les calculs d'équipe (docs/spec/equipe-moi.md). */
class TeamWorkTest {
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))
    private val me = member(1, self = true)
    private val other = member(2)

    /** Tâche perso de [hours] heures. */
    private fun personal(id: Long, hours: Double = 4.0, excluded: Boolean = false, origin: String? = null, status: TaskStatus = TaskStatus.TODO) =
        task(id, hours = hours, space = TaskSpace.PERSONAL, status = status).copy(teamExcluded = excluded, origin = origin)

    private fun plan(s: KairosSnapshot, weeks: Int = 4): LoadPlan.Result {
        val input = TeamWork.planInput(s, monday, mondayMorning, Capacity.horizonEnd(monday, weeks))
        return LoadPlan.build(input.snapshot, monday, horizonWeeks = weeks, spent = input.spent, busy = input.busy)
    }

    // --- La vue -----------------------------------------------------------------------------------------------------

    @Test
    fun aPersonalTaskCountsForMeWhenTeamManagementIsOnAndMeExists() {
        val s = snapshot(listOf(personal(1)), listOf(me, other))
        val viewed = TeamWork.view(s).tasks.single()
        assertEquals(TaskSpace.TEAM, viewed.space)
        assertEquals(me.id, viewed.assigneeId)
        assertTrue(TeamWork.counts(s.tasks.single(), s))
        // Le plan de « moi » la pose : 4 h de charge.
        assertEquals(4.0, plan(s).of(me.id)!!.loadHours, 1e-9)
    }

    @Test
    fun nothingCountsWithoutMeOrWithTeamManagementOff() {
        val noMe = snapshot(listOf(personal(1)), listOf(other))
        assertSame(noMe, TeamWork.view(noMe))
        assertNull(TeamWork.self(noMe))
        val off = snapshot(listOf(personal(1)), listOf(me), settings = settings().copy(team = TeamSettings(enabled = false)))
        assertSame(off, TeamWork.view(off))
        assertFalse(TeamWork.counts(off.tasks.single(), off))
        // Un « moi » archivé ne compte pas.
        val archived = snapshot(listOf(personal(1)), listOf(member(1, self = true, archived = true)))
        assertSame(archived, TeamWork.view(archived))
    }

    @Test
    fun excludedReceivedAndArchivedTasksDoNotCount() {
        val s = snapshot(
            listOf(personal(1, excluded = true), personal(2, origin = "{}"), personal(3, status = TaskStatus.ARCHIVED)),
            listOf(me),
        )
        assertSame(s, TeamWork.view(s))
        assertFalse(TeamWork.countable(s.tasks[1]))
        assertFalse(TeamWork.countable(s.tasks[2]))
        assertTrue(TeamWork.countable(s.tasks[0]))
        assertEquals(0.0, plan(s).of(me.id)!!.loadHours, 1e-9)
    }

    @Test
    fun myPersonalTasksNeverReachTheBacklogTheTrackingOrAPack() {
        // Une tâche perso qui compte, une tâche d'équipe au backlog et une assignée à l'autre membre (paquet possible).
        val backlog = task(2, assignee = null).copy(teamUid = "uid-2")
        val forOther = task(3, assignee = other.id).copy(teamUid = "uid-3")
        val s = snapshot(listOf(personal(1), backlog, forOther), listOf(me, other)).let {
            it.copy(settings = it.settings.copy(team = it.settings.team!!.copy(identity = "team-1")))
        }
        // Même en lisant par erreur la vue de calcul, elle n'est ni au backlog (elle est à « moi ») ni dans un paquet (pas de teamUid).
        for (source in listOf(s, TeamWork.view(s))) {
            val board = TeamBoard.build(source, monday, kotlinx.datetime.TimeZone.UTC)
            assertTrue((board.toQualify + board.ready).none { it.task.id == 1L })
            for (m in listOf(me, other)) {
                val pack = com.skohscripts.kairos.core.team.exchange.PackBuilder.build(source, m.id, "p", mondayMorning)
                assertTrue(pack == null || pack.tasks.none { it.title == "T1" }, "paquet de ${m.name}")
            }
        }
        // Le Suivi lit la base telle quelle : la tâche perso n'est sur aucune ligne.
        assertTrue(TeamBoard.build(s, monday, kotlinx.datetime.TimeZone.UTC).lanes.none { lane ->
            (lane.todo + lane.inProgress + lane.done).any { it.task.id == 1L }
        })
    }

    @Test
    fun theViewIsIdempotentAndLeavesTeamTasksAlone() {
        val s = snapshot(listOf(personal(1), task(2, assignee = other.id)), listOf(me, other))
        val once = TeamWork.view(s)
        assertEquals(once, TeamWork.view(once))
        assertEquals(s.tasks[1], once.tasks[1])
    }

    // --- Récurrences ------------------------------------------------------------------------------------------------

    @Test
    fun aWeeklyTaskProjectsOneOccurrencePerWeekOfTheHorizon() {
        // Échéance vendredi 9 oct. ; horizon de 4 semaines (jusqu'au dimanche 1er nov.).
        val weekly = task(1, assignee = other.id, hours = 1.0, deadline = day(4)).copy(recurrence = TaskRecurrence.WEEKLY, teamUid = "w")
        val s = snapshot(listOf(weekly), listOf(other))
        val projected = TeamWork.occurrences(s, monday, Capacity.horizonEnd(monday, 4))
        assertEquals(listOf(day(11), day(18), day(25)), projected.map { it.deadline })
        assertEquals(listOf(-1L, -2L, -3L), projected.map { it.id })
        assertTrue(projected.all { it.scheduledDate == it.deadline && TeamWork.isProjected(it) })
        assertEquals("w@${day(11)}", projected.first().teamUid)
        // Chacune n'est posée qu'à partir de son jour, et pèse 1 h : 4 h de charge au total.
        val p = plan(s)
        assertEquals(day(11), p.task(-1)!!.start)
        assertEquals(4.0, p.of(other.id)!!.loadHours, 1e-9)
        assertEquals(0.0, p.of(other.id)!!.waitHours, 0.0)
        assertEquals("T1", p.tasksById.getValue(-1).title)
    }

    @Test
    fun aMonthlyOnDaySeriesProjectsTheNextMonthsOnTheBusinessDayBefore() {
        // Le 15 de chaque mois ; le 15 nov. 2026 est un dimanche : vendredi 13.
        val monthly = task(1, assignee = other.id, hours = 2.0, deadline = LocalDate(2026, 10, 15))
            .copy(recurrence = TaskRecurrence.MONTHLY_ON_DAY, recurrenceDayOfMonth = 15)
        val s = snapshot(listOf(monthly), listOf(other))
        val projected = TeamWork.occurrences(s, monday, Capacity.horizonEnd(monday, 8))
        assertEquals(listOf(LocalDate(2026, 11, 13)), projected.map { it.deadline })
    }

    @Test
    fun onlyTheLatestOccurrenceOfASeriesIsProjectedFrom() {
        val older = task(1, assignee = other.id, deadline = day(-3)).copy(title = "Rapport", recurrence = TaskRecurrence.WEEKLY)
        val newer = task(2, assignee = other.id, deadline = day(4)).copy(title = "Rapport", recurrence = TaskRecurrence.WEEKLY)
        val projected = TeamWork.occurrences(snapshot(listOf(older, newer), listOf(other)), monday, Capacity.horizonEnd(monday, 2))
        assertEquals(listOf(day(11)), projected.map { it.deadline })
    }

    // --- Disponibilité ----------------------------------------------------------------------------------------------

    @Test
    fun aTaskScheduledLaterIsNotStartedBeforeItsDateAndTheMemberTakesTheNextOne() {
        val later = task(1, assignee = other.id, priority = 0).copy(scheduledDate = day(2))
        val now = task(2, assignee = other.id, priority = 2)
        val p = plan(snapshot(listOf(later, now), listOf(other)))
        assertEquals(monday, p.task(2)!!.end)
        assertEquals(day(2), p.task(1)!!.start)
        // Mardi sans rien de disponible : ce n'est pas de l'attente.
        assertEquals(0.0, p.of(other.id)!!.waitHours, 0.0)
    }

    // --- Créneaux ---------------------------------------------------------------------------------------------------

    private fun block(start: LocalDate, from: Int, to: Int, recurrence: BlockRecurrence = BlockRecurrence.NONE, kind: BlockKind = BlockKind.BUSY) =
        TimeBlock(start.toEpochDays(), "Réunion", start.atTime(from, 0), start.atTime(to, 0), kind, recurrence, stamp)

    @Test
    fun busyBlocksInTheWorkdayAreCountedRecurringOnesIncludedButNotDeepWork() {
        val s = snapshot(emptyList(), listOf(me)).copy(
            timeBlocks = listOf(
                block(monday, 10, 12),
                block(monday, 7, 10), // déborde avant 9 h : 1 h compte
                block(monday, 14, 16, kind = BlockKind.DEEPWORK),
                block(day(-7), 16, 17, BlockRecurrence.WEEKLY),
            ),
        )
        assertEquals(4.0, TeamWork.busyHours(s, monday), 1e-9)
        assertEquals(0.0, TeamWork.busyHours(s, day(1)), 1e-9)
    }

    @Test
    fun aMeetingReducesOnlyMyCapacityThatDay() {
        val tasks = listOf(task(1, assignee = me.id), task(2, assignee = other.id))
        val s = snapshot(tasks, listOf(me, other)).copy(timeBlocks = listOf(block(monday, 10, 12)))
        val p = plan(s)
        // « moi » : 6 h lundi, sa tâche de 8 h finit mardi ; l'autre membre n'est pas touché.
        assertEquals(day(1), p.task(1)!!.end)
        assertEquals(monday, p.task(2)!!.end)
        assertEquals(6.0, Capacity.dailyHours(me, monday, emptySet(), emptyList(), settings(), busyHours = 2.0), 1e-9)
        // Le focus s'applique après les créneaux.
        val focus = settings(TeamSettings(focusFactor = 0.5))
        assertEquals(3.0, Capacity.dailyHours(me, monday, emptySet(), emptyList(), focus, busyHours = 2.0), 1e-9)
    }

    // --- Temps passé ------------------------------------------------------------------------------------------------

    @Test
    fun timeSpentIsDeductedDownToTenPercentAndADeclaredProgressWins() {
        val t = task(1, assignee = other.id, hours = 5.0)
        val s = settings()
        assertEquals(2.0, Effort.remaining(t, emptyList(), s, spentMinutes = 180).first!!, 1e-9)
        assertEquals(0.5, Effort.remaining(t, emptyList(), s, spentMinutes = 290).first!!, 1e-9)
        assertEquals(2.5, Effort.remaining(t.copy(progressPercent = 50), emptyList(), s, spentMinutes = 290).first!!, 1e-9)
        assertEquals(5.0, Effort.remaining(t, emptyList(), s).first!!, 1e-9)
    }

    @Test
    fun thePlanDeductsTheChronometeredTime() {
        val t = task(1, assignee = me.id, hours = 5.0)
        val session = WorkSession(1, 1, mondayMorning - 4.hours, mondayMorning - 1.hours, stamp)
        val s = snapshot(listOf(t), listOf(me)).copy(workSessions = listOf(session))
        assertEquals(2.0, plan(s).of(me.id)!!.loadHours, 1e-9)
    }

    // --- Historique -------------------------------------------------------------------------------------------------

    @Test
    fun myDonePersonalTasksFeedTheTeamCalibration() {
        // Trois tâches perso faites à 3 points, 6 h chacune : la médiane du palier devient fiable (n = 3).
        val done = (1L..3L).map { id -> personal(id, hours = 1.0, status = TaskStatus.DONE).copy(estimatedMinutes = null, manualTimeSpentMinutes = 360) }
        val s = snapshot(done, listOf(me))
        val calibration = Effort.teamCalibration(s, mondayMorning)
        val three = calibration.single { it.key == "3" }
        assertEquals(360, three.medianMinutes)
        assertTrue(three.reliable)
        // Sans « moi », elles ne comptent pas.
        assertTrue(Effort.teamCalibration(snapshot(done, listOf(other)), mondayMorning).none { it.key == "3" && it.reliable })
    }
}
