package com.skohscripts.kairos.ui.day

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Saisie souple et raccourcis des champs date (docs/spec/planning-personnalise.md § Lot A, 1). */
class DateInputTest {
    private val sept30 = LocalDate(2026, 9, 30)

    @Test
    fun isoAndLooseYearFirstDatesAreAccepted() {
        assertEquals(sept30, Dates.parseDate("2026-09-30"))
        assertEquals(sept30, Dates.parseDate("2026-9-30"))
        assertEquals(sept30, Dates.parseDate(" 2026/9/30 "))
        assertEquals(sept30, Dates.parseDate("2026.09.30"))
    }

    @Test
    fun dayFirstDatesAreAcceptedExceptInEnglish() {
        assertEquals(sept30, Dates.parseDate("30/09/2026", dayFirst = true))
        assertEquals(sept30, Dates.parseDate("30-9-2026", dayFirst = true))
        // Ni le français sans la langue, ni l'ordre mois/jour de l'anglais : ambigus.
        assertNull(Dates.parseDate("30/09/2026", dayFirst = false))
        assertNull(Dates.parseDate("09/30/2026", dayFirst = true))
    }

    @Test
    fun unreadableOrImpossibleDatesAreRejected() {
        listOf("", "  ", "demain", "2026-13-01", "2026-02-30", "2026-9", "30/09/26", "2026-9-30-1", "20x6-09-30").forEach {
            assertNull(Dates.parseDate(it, dayFirst = true), it)
        }
    }

    @Test
    fun shortcutsStartFromTodayAndNeverRepeatADate() {
        // Mardi 29 septembre 2026.
        val tuesday = dateShortcuts(LocalDate(2026, 9, 29))
        assertEquals(
            listOf(DateShortcut.TODAY, DateShortcut.TOMORROW, DateShortcut.NEXT_MONDAY, DateShortcut.IN_A_WEEK),
            tuesday.map { it.first },
        )
        assertEquals(listOf(LocalDate(2026, 9, 29), LocalDate(2026, 9, 30), LocalDate(2026, 10, 5), LocalDate(2026, 10, 6)), tuesday.map { it.second })
        // Un lundi : le lundi prochain est dans une semaine, proposé une seule fois.
        val monday = dateShortcuts(LocalDate(2026, 9, 28))
        assertEquals(listOf(DateShortcut.TODAY, DateShortcut.TOMORROW, DateShortcut.NEXT_MONDAY), monday.map { it.first })
        assertEquals(LocalDate(2026, 10, 5), monday.last().second)
        // Un dimanche : le lundi prochain est demain.
        assertEquals(LocalDate(2026, 9, 28), dateShortcuts(LocalDate(2026, 9, 27)).single { it.first == DateShortcut.NEXT_MONDAY }.second)
    }
}
