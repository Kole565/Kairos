package com.skohscripts.kairos.core.calendar

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IcsParserTest {
    @Test
    fun recognisesACalendarByItsContent() {
        assertTrue(IcsParser.isCalendar("﻿\r\n  BEGIN:VCALENDAR\r\nEND:VCALENDAR"))
        assertTrue(IcsParser.isCalendar("begin:vcalendar\n"))
        assertFalse(IcsParser.isCalendar("{\"format\": \"kairos-export\"}"))
        assertFalse(IcsParser.isCalendar(""))
    }

    @Test
    fun unfoldsLinesAndUndoesEscapes() {
        val cal = IcsParser.parse(THUNDERBIRD)
        val summary = cal.children("VEVENT").first().first("SUMMARY")!!
        assertEquals("Stand-up, équipe; produit", IcsParser.text(summary.value))
        assertEquals("a\nb\\c", IcsParser.text("a\\nb\\\\c"))
    }

    @Test
    fun readsQuotedParametersWithColons() {
        val p = IcsParser.property("DTSTART;TZID=\"Romance: Standard Time\";VALUE=DATE-TIME:20261005T093000")!!
        assertEquals("DTSTART", p.name)
        assertEquals("Romance: Standard Time", p.param("TZID"))
        assertEquals("DATE-TIME", p.param("VALUE"))
        assertEquals("20261005T093000", p.value)
        // Deux-points dans la valeur : seul le premier hors guillemets coupe.
        assertEquals("https://example.org/a", IcsParser.property("URL:https://example.org/a")!!.value)
        assertEquals(null, IcsParser.property("sans deux points"))
    }

    @Test
    fun buildsNestedComponentsWithCrlfOrLf() {
        for (text in listOf(OUTLOOK, OUTLOOK.replace("\r\n", "\n"))) {
            val cal = IcsParser.parse(text)
            assertEquals("VCALENDAR", cal.name)
            assertEquals(1, cal.children("VTIMEZONE").size)
            assertEquals(2, cal.children("VTIMEZONE").single().children.size)
            assertEquals(4, cal.children("VEVENT").size)
        }
    }

    @Test
    fun aTextWithoutCalendarIsRefused() {
        assertFailsWith<IllegalArgumentException> { IcsParser.parse("BEGIN:VEVENT\nEND:VEVENT") }
    }
}
