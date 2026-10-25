package com.skohscripts.kairos.ui.day

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatesTest {
    @Test
    fun aFarDateGetsItsYear() {
        val today = LocalDate(2026, 10, 12)
        assertEquals("28 févr.", Dates.short(LocalDate(2026, 2, 28), "fr", today))
        assertEquals("29 févr. 2028", Dates.short(LocalDate(2028, 2, 29), "fr", today))
        assertEquals("Feb 29, 2028", Dates.short(LocalDate(2028, 2, 29), "en", today))
    }

    @Test
    fun shortDatesPerLanguage() {
        assertEquals("30 sept.", Dates.short(LocalDate(2026, 9, 30), "fr"))
        assertEquals("Sep 30", Dates.short(LocalDate(2026, 9, 30), "en"))
        // Repli anglais (docs/spec/i18n.md, issue #70) : une langue sans traduction donne des dates anglaises.
        assertEquals("Jan 1", Dates.short(LocalDate(2027, 1, 1), "pt"))
        assertEquals("1 janv.", Dates.short(LocalDate(2027, 1, 1), "fr-CA"))
        assertEquals("30 sep", Dates.short(LocalDate(2026, 9, 30), "es"))
        assertEquals("30. Sep", Dates.short(LocalDate(2026, 9, 30), "de"))
        assertEquals("30 сент.", Dates.short(LocalDate(2026, 9, 30), "ru"))
        assertEquals("30 set", Dates.short(LocalDate(2026, 9, 30), "it"))
    }

    @Test
    fun everyLanguageHasTwelveMonthsAndSevenDays() {
        // Une table de mois ou de jours incomplète lèverait une exception d'index en pleine interface.
        for (language in com.skohscripts.kairos.core.settings.UiLanguage.SUPPORTED) {
            for (month in 1..12) {
                val date = LocalDate(2026, month, 5)
                assertEquals(true, Dates.short(date, language).isNotBlank(), "$language $month")
                assertEquals(true, Dates.long(date, language).isNotBlank(), "$language $month")
                assertEquals(true, Dates.monthYear(date, language).isNotBlank(), "$language $month")
            }
            for (weekday in 0..6) assertEquals(true, Dates.weekdayAbbrev(weekday, language).isNotBlank(), "$language $weekday")
        }
    }

    @Test
    fun longDatesPerLanguage() {
        val d = LocalDate(2026, 9, 29)
        assertEquals("martes 29 de septiembre de 2026", Dates.long(d, "es"))
        assertEquals("Dienstag, 29. September 2026", Dates.long(d, "de"))
        assertEquals("вторник, 29 сентября 2026", Dates.long(d, "ru"))
        assertEquals("martedì 29 settembre 2026", Dates.long(d, "it"))
        // « 1er » reste français.
        assertEquals("giovedì 1 ottobre 2026", Dates.long(LocalDate(2026, 10, 1), "it"))
    }

    @Test
    fun monthAndYearUseTheNominativeInRussian() {
        val d = LocalDate(2026, 9, 5)
        assertEquals("сентябрь 2026", Dates.monthYear(d, "ru"))
        assertEquals("septiembre de 2026", Dates.monthYear(d, "es"))
        assertEquals("September 2026", Dates.monthYear(d, "de"))
    }

    @Test
    fun numericDaysPerLanguage() {
        val d = LocalDate(2026, 9, 28)
        assertEquals("28/09", Dates.dayMonth(d, "es"))
        assertEquals("28.09.", Dates.dayMonth(d, "de"))
        assertEquals("28.09", Dates.dayMonth(d, "ru"))
        assertEquals("lun 28/09", Dates.dayShort(d, "it"))
        assertEquals("Mo 28.09.", Dates.dayShort(d, "de"))
    }

    @Test
    fun numbersUseTheDecimalCommaExceptInEnglish() {
        for (language in listOf("fr", "es", "de", "ru", "it")) assertEquals("6,2", Dates.number(6.24, language), language)
        assertEquals("6.2", Dates.number(6.24, "en"))
    }

    @Test
    fun smallPositiveNumbersAreNeverShownAsZero() {
        assertEquals("16", Dates.number(16.0))
        assertEquals("6.2", Dates.number(6.24))
        assertEquals("0.05", Dates.number(1.0 / 21))
        assertEquals("0.01", Dates.number(0.001))
        assertEquals("0", Dates.number(0.0))
    }

    @Test
    fun timesPerLanguage() {
        assertEquals("09h05", Dates.time(LocalTime(9, 5), "fr"))
        assertEquals("09:05", Dates.time(LocalTime(9, 5), "en"))
        assertEquals("9h", Dates.hour(9, "fr"))
        for (language in listOf("es", "de", "ru", "it")) {
            assertEquals("09:05", Dates.time(LocalTime(9, 5), language), language)
            assertEquals("9:00", Dates.hour(9, language), language)
        }
    }

    @Test
    fun timeFieldAcceptsCommonForms() {
        assertEquals(LocalTime(9, 30), Dates.parseTime("9:30"))
        assertEquals(LocalTime(9, 30), Dates.parseTime(" 09h30 "))
        assertEquals(LocalTime(14, 0), Dates.parseTime("14h"))
        assertNull(Dates.parseTime("24:00"))
        assertNull(Dates.parseTime("9"))
        assertEquals("07:05", Dates.field(LocalTime(7, 5)))
    }

    @Test
    fun scoreHasOneDecimalAtMostWithoutTrailingZero() {
        assertEquals("16", Dates.number(16.0))
        assertEquals("6.2", Dates.number(6.2142))
        assertEquals("0.1", Dates.number(0.1111))
        assertEquals("2", Dates.number(1.96))
    }

    @Test
    fun longAndShortDaysPerLanguage() {
        assertEquals("mardi 29 septembre 2026", Dates.long(LocalDate(2026, 9, 29), "fr"))
        assertEquals("jeudi 1er octobre 2026", Dates.long(LocalDate(2026, 10, 1), "fr"))
        assertEquals("Tuesday, September 29, 2026", Dates.long(LocalDate(2026, 9, 29), "en"))
        assertEquals("mar. 29/09", Dates.dayShort(LocalDate(2026, 9, 29), "fr"))
        assertEquals("Tue 9/29", Dates.dayShort(LocalDate(2026, 9, 29), "en"))
        assertEquals("28/09", Dates.dayMonth(LocalDate(2026, 9, 28), "fr"))
    }
}
