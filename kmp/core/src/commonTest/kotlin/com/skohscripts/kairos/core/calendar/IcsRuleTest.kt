package com.skohscripts.kairos.core.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IcsRuleTest {
    private val from = LocalDate(2020, 1, 1).atTime(0, 0)
    private val to = LocalDate(2028, 1, 1).atTime(0, 0)

    private fun dates(rule: String, start: LocalDateTime, until: LocalDateTime? = null, from: LocalDateTime = this.from, to: LocalDateTime = this.to) =
        IcsRule.parse(rule)!!.expand(start, until, from, to).map { it.date.toString() }

    @Test
    fun weeklyWithIntervalAndWeekStart() {
        val start = LocalDate(2026, 10, 6).atTime(9, 0) // mardi
        assertEquals(
            listOf("2026-10-06", "2026-10-08", "2026-10-20", "2026-10-22"),
            dates("FREQ=WEEKLY;INTERVAL=2;BYDAY=TU,TH;WKST=MO;COUNT=4", start),
        )
    }

    @Test
    fun monthlyByRankedWeekdayAndLastFriday() {
        assertEquals(
            listOf("2026-10-13", "2026-11-10", "2026-12-08"),
            dates("FREQ=MONTHLY;BYDAY=2TU;COUNT=3", LocalDate(2026, 10, 13).atTime(9, 0)),
        )
        assertEquals(
            listOf("2026-10-30", "2026-11-27", "2026-12-25"),
            dates("FREQ=MONTHLY;BYDAY=-1FR;COUNT=3", LocalDate(2026, 10, 30).atTime(9, 0)),
        )
    }

    @Test
    fun lastWorkdayOfTheMonthWithSetPos() {
        assertEquals(
            listOf("2026-10-30", "2026-11-30", "2026-12-31"),
            dates("FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=3", LocalDate(2026, 10, 30).atTime(17, 0)),
        )
    }

    @Test
    fun theThirtyFirstSkipsShortMonthsAndNegativeMonthDayCountsFromTheEnd() {
        assertEquals(
            listOf("2026-10-31", "2026-12-31", "2027-01-31"),
            dates("FREQ=MONTHLY;COUNT=3", LocalDate(2026, 10, 31).atTime(8, 0)),
        )
        assertEquals(
            listOf("2026-10-31", "2026-11-30", "2026-12-31"),
            dates("FREQ=MONTHLY;BYMONTHDAY=-1;COUNT=3", LocalDate(2026, 10, 31).atTime(8, 0)),
        )
    }

    @Test
    fun yearlyByMonthAndDstRule() {
        assertEquals(
            listOf("2026-03-29", "2027-03-28", "2028-03-26"),
            dates("FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU", LocalDate(1601, 3, 25).atTime(2, 0), from = LocalDate(2026, 1, 1).atTime(0, 0), to = LocalDate(2029, 1, 1).atTime(0, 0)).filter { it >= "2026" },
        )
    }

    @Test
    fun untilIsInclusiveAndCountStartsAtDtstart() {
        val start = LocalDate(2026, 10, 19).atTime(9, 30)
        assertEquals(
            listOf("2026-10-19", "2026-10-20", "2026-10-21"),
            dates("FREQ=DAILY", start, until = LocalDate(2026, 10, 21).atTime(9, 30)),
        )
        // COUNT compte depuis DTSTART, même quand l'horizon commence plus tard.
        assertEquals(
            listOf("2026-10-19", "2026-10-20"),
            dates("FREQ=DAILY;COUNT=2", start, from = LocalDate(2026, 10, 25).atTime(0, 0)),
        )
    }

    @Test
    fun anOldEndlessSeriesStartsNearTheHorizon() {
        val start = LocalDate(2010, 1, 4).atTime(9, 0) // lundi
        val out = IcsRule.parse("FREQ=WEEKLY;BYDAY=MO")!!.expand(start, null, LocalDate(2026, 10, 19).atTime(0, 0), LocalDate(2026, 11, 3).atTime(0, 0))
        // DTSTART d'abord, puis seulement les semaines proches de l'horizon (le reste est filtré par l'appelant).
        assertEquals(LocalDate(2010, 1, 4).atTime(9, 0), out.first())
        assertEquals(listOf("2026-10-19", "2026-10-26", "2026-11-02"), out.drop(1).map { it.date.toString() }.filter { it >= "2026-10-19" })
        assertTrue(out.size < 10)
    }

    @Test
    fun unsupportedPartsAreRefused() {
        assertNull(IcsRule.parse("FREQ=HOURLY"))
        assertNull(IcsRule.parse("FREQ=DAILY;BYHOUR=9"))
        assertNull(IcsRule.parse("FREQ=WEEKLY;BYDAY=2MO"))
        assertNull(IcsRule.parse("FREQ=MONTHLY;BYMONTHDAY=32"))
        assertNull(IcsRule.parse("FREQ=MONTHLY;INTERVAL=0"))
        assertNull(IcsRule.parse("INTERVAL=2"))
    }
}
