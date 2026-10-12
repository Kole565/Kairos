package com.skohscripts.kairos.core.team.forecast

import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.team.TeamFixtures
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.now
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.request
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.run
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.task
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures.timeZone
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Prévisions avec le travail perso de « moi » et les récurrences projetées (docs/spec/equipe-moi.md). */
class TeamWorkForecastTest {
    private val me = member(1, self = true)
    private val other = member(2)

    @Test
    fun myPersonalTasksAreInMyWorkScopeAndDelayMyTeamTask() {
        // « moi » : une tâche d'équipe de 8 h (P2) et une tâche perso de 8 h (P0, donc posée d'abord).
        val teamTask = task(1, me.id, hours = 8.0, priority = 2)
        val personal = TeamFixtures.task(2, hours = 8.0, priority = 0, space = TaskSpace.PERSONAL)
        val s = snapshot(listOf(teamTask, personal), listOf(me, other))
        val mine = MonteCarlo.prepare(request(s, scope = ForecastScope.Member(me.id)))
        assertEquals(2, mine.scopeSize)
        // Sans aléa : la tâche d'équipe finit mardi (J+1), plus lundi.
        val result = run(s, scope = ForecastScope.Tasks(setOf(1L)))
        assertEquals(monday.plus(DatePeriod(days = 1)), result.finish!!.p50!!)
    }

    @Test
    fun projectedOccurrencesTakeCapacityButAreNotInTheScope() {
        // Tâche quotidienne de 4 h de l'autre membre, échéance aujourd'hui : chaque jour de l'horizon en projette une.
        val daily = task(1, other.id, hours = 4.0, deadline = monday).copy(recurrence = TaskRecurrence.DAILY)
        val big = task(2, other.id, hours = 16.0, priority = 2)
        val s = snapshot(listOf(daily, big), listOf(other))
        val prepared = MonteCarlo.prepare(request(s))
        assertEquals(2, prepared.scopeSize)
        // 4 h par jour pour la récurrence : la tâche de 16 h prend plus de deux jours.
        val result = run(s, scope = ForecastScope.Tasks(setOf(2L)))
        assertTrue(result.finish!!.p50!! > monday.plus(DatePeriod(days = 1)))
    }

    @Test
    fun myDonePersonalTasksCountInMyThroughput() {
        // Tâches perso faites lundi dernier et il y a deux semaines : elles entrent dans le débit de « moi ».
        val lastWeek = monday.minus(DatePeriod(days = 7)).atStartOfDayIn(timeZone)
        val twoWeeks = monday.minus(DatePeriod(days = 14)).atStartOfDayIn(timeZone)
        val done = listOf(
            TeamFixtures.task(1, space = TaskSpace.PERSONAL, status = TaskStatus.DONE, updatedAt = lastWeek),
            TeamFixtures.task(2, space = TaskSpace.PERSONAL, status = TaskStatus.DONE, updatedAt = twoWeeks),
        )
        val data = ForecastData.build(snapshot(done, listOf(me)), monday, now, timeZone)
        assertEquals(2, data.throughputOf(me.id).sum())
        assertEquals(2, data.throughputOf().sum())
    }
}
