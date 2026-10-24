package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.monday
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.forecast.ForecastFixtures
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Semaine type d'un membre (docs/spec/equipe-charge.md § Capacité, issue #104). */
class WeekHoursCapacityTest {
    private fun day(n: Int) = monday.plus(DatePeriod(days = n))
    private val focus1 = Settings(holidaysFr = false, team = TeamSettings(focusFactor = 1.0))

    // Semaine de quatre jours, mercredi libre, vendredi en demi-journée.
    private val week = listOf(8.0, 8.0, 0.0, 8.0, 4.0)

    @Test
    fun aWeekTemplateGivesEachDayItsHoursAndAFreeDayCountsZero() {
        val m = member(1, hoursPerDay = 7.0, percent = 50).copy(weekHours = week)
        fun h(n: Int, holidays: Set<kotlinx.datetime.LocalDate> = emptySet()) = Capacity.dailyHours(m, day(n), holidays, emptyList(), focus1)
        assertEquals(4.0, h(0), 1e-9) // 8 h × 50 %
        assertEquals(0.0, h(2), 1e-9) // mercredi libre
        assertEquals(2.0, h(4), 1e-9) // vendredi 4 h × 50 %
        assertEquals(0.0, h(5), 1e-9) // le week-end reste non travaillé
        assertEquals(0.0, h(1, setOf(day(1))), 1e-9) // férié
        assertEquals(14.0, Capacity.weeklyHours(m, focus1), 1e-9) // 28 h × 50 %
        assertEquals(4, Capacity.workdays(m))
        assertEquals(14.0, Capacity.overRange(m, monday, day(6), emptySet(), emptyList(), focus1), 1e-9)
    }

    @Test
    fun withoutATemplateCapacityIsUnchanged() {
        val uniform = member(1, hoursPerDay = 7.5, percent = 80)
        val settings = Settings(holidaysFr = false, team = TeamSettings())
        assertNull(uniform.weekHours)
        assertEquals(7.5 * 0.8 * 0.8 * 5, Capacity.weeklyHours(uniform, settings), 1e-9)
        assertEquals(5, Capacity.workdays(uniform))
        assertEquals(7.5 * 0.8 * 0.8 * 20, Capacity.overRange(uniform, monday, day(27), emptySet(), emptyList(), settings), 1e-9)
    }

    @Test
    fun aForecastWithAFixedSeedFinishesLaterWithAFreeWednesday() {
        // 40 h de travail : cinq jours de 8 h finissent le vendredi ; sans le mercredi, le lundi suivant.
        val tasks = (1L..5L).map { ForecastFixtures.task(it, 1, hours = 8.0) }
        val uniform = ForecastFixtures.run(snapshot(tasks, listOf(member(1))), seed = 7L, runs = 200)
        val fourDays = ForecastFixtures.run(snapshot(tasks, listOf(member(1).copy(weekHours = listOf(8.0, 8.0, 0.0, 8.0, 8.0)))), seed = 7L, runs = 200)
        assertEquals(day(4), uniform.finishDate(50))
        assertEquals(day(7), fourDays.finishDate(50))
        assertEquals(day(7), fourDays.finishDate(95))
    }

    @Test
    fun theColumnTextRoundTripsAndRejectsAMalformedWeek() {
        assertEquals("8,8,0,8,4.5", TeamMember.encodeWeekHours(listOf(8.0, 8.0, 0.0, 8.0, 4.5)))
        assertEquals(listOf(8.0, 8.0, 0.0, 8.0, 4.5), TeamMember.decodeWeekHours("8,8,0,8,4.5"))
        assertNull(TeamMember.decodeWeekHours(null))
        assertNull(TeamMember.decodeWeekHours("8,8,8"))
        assertNull(TeamMember.decodeWeekHours("0,0,0,0,0"))
        assertNull(TeamMember.decodeWeekHours("8,8,x,8,8"))
        assertFalse(TeamMember.isValidWeek(listOf(8.0, 8.0, 8.0, 8.0, 25.0)))
    }

    @Test
    fun theFormValidatesTheWeekAndKeepsTheHiddenHours() {
        val base = MemberForm.Input(name = "Léa", hoursPerDay = "7", weekly = true, weekHours = listOf("8", "8", "", "8", "4,5"))
        val valid = MemberForm.validate(base).valid!!
        assertEquals(listOf(8.0, 8.0, 0.0, 8.0, 4.5), valid.weekHours)
        assertEquals(7.0, valid.hoursPerDay)
        // Interrupteur coupé : pas de semaine type.
        assertNull(MemberForm.validate(base.copy(weekly = false)).valid!!.weekHours)
        // Heures par jour masquées et invalides : la journée la plus longue les remplace.
        assertEquals(8.0, MemberForm.validate(base.copy(hoursPerDay = "")).valid!!.hoursPerDay)
        val errors = MemberForm.validate(base.copy(weekHours = listOf("8", "x", "", "30", "-1"))).errors
        assertEquals(FieldError(FieldErrorKind.NOT_NUMBER), errors[MemberForm.weekKey(1)])
        assertEquals(FieldError(FieldErrorKind.TOO_LARGE, "24"), errors[MemberForm.weekKey(3)])
        assertEquals(FieldError(FieldErrorKind.TOO_SMALL, "0"), errors[MemberForm.weekKey(4)])
        assertEquals(FieldError(FieldErrorKind.REQUIRED), MemberForm.validate(base.copy(weekHours = List(5) { "0" })).errors[MemberForm.weekKey(0)])
        // Un membre existant : sa semaine type ; sans elle, l'interrupteur part de ses heures par jour, chaque jour.
        val m = member(1, hoursPerDay = 6.0)
        assertEquals(List(5) { "" }, MemberForm.inputOf(m).weekHours)
        assertFalse(MemberForm.inputOf(m).weekly)
        assertEquals(List(5) { "6" }, MemberForm.withWeekly(MemberForm.inputOf(m), true).weekHours)
        // Des jours déjà saisis ne sont pas écrasés.
        val typed = base.copy(weekly = false)
        assertEquals(typed.weekHours, MemberForm.withWeekly(typed, true).weekHours)
        assertTrue(MemberForm.inputOf(m.copy(weekHours = week)).weekly)
    }
}
