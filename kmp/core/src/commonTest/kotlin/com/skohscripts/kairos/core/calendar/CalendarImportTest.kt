package com.skohscripts.kairos.core.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CalendarImportTest {
    private val paris = TimeZone.of("Europe/Paris")
    private val today = LocalDate(2026, 10, 19)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate(y, m, d).atTime(h, min)
    private fun ev(start: LocalDateTime, end: LocalDateTime, title: String) = Triple(start, end, title)
    private val CalendarPlan.slots get() = events.map { Triple(it.start, it.end, it.title) }
    private val CalendarPlan.marks get() = days.map { Triple(it.start, it.end, it.title) }

    @Test
    fun outlookSeriesWithWindowsZoneExdateMovedOccurrenceAndTentative() {
        val plan = CalendarImport.plan(OUTLOOK, today, paris)
        assertEquals("Calendrier", plan.name)
        val team = "Point d'équipe"
        assertEquals(
            listOf(
                ev(at(2026, 10, 19, 9, 30), at(2026, 10, 19, 10), team),
                ev(at(2026, 10, 22, 14), at(2026, 10, 22, 16), "Atelier client"),
                // 26 octobre : EXDATE ; 2 novembre : déplacée au 3 à 14 h. L'heure reste 9 h 30 après le changement d'heure.
                ev(at(2026, 11, 3, 14), at(2026, 11, 3, 14, 30), "Point d'équipe (décalé)"),
                ev(at(2026, 11, 9, 9, 30), at(2026, 11, 9, 10), team),
                ev(at(2026, 11, 16, 9, 30), at(2026, 11, 16, 10), team),
                ev(at(2026, 11, 23, 9, 30), at(2026, 11, 23, 10), team),
                // UNTIL (08:30 UTC = 9 h 30 à Paris) inclus.
                ev(at(2026, 11, 30, 9, 30), at(2026, 11, 30, 10), team),
            ),
            plan.slots,
        )
        assertEquals(mapOf(SkipReason.FREE to 1), plan.skipped)
        assertTrue(plan.days.isEmpty())
    }

    @Test
    fun googleUtcTimesMonthlyRankAllDayAndSkips() {
        val plan = CalendarImport.plan(GOOGLE, today, paris)
        assertEquals("Travail", plan.name)
        assertEquals(
            listOf(
                ev(at(2026, 11, 10, 9), at(2026, 11, 10, 10), "Revue mensuelle"),
                ev(at(2026, 12, 8, 9), at(2026, 12, 8, 10), "Revue mensuelle"),
                ev(at(2027, 1, 12, 9), at(2027, 1, 12, 10), "Revue mensuelle"),
            ),
            plan.slots,
        )
        // Journée entière (même transparente) : un badge du 28 au 30 inclus.
        assertEquals(listOf(Triple(LocalDate(2026, 10, 28), LocalDate(2026, 10, 30), "Conférence à Lyon")), plan.marks)
        assertEquals(mapOf(SkipReason.CANCELLED to 1, SkipReason.FREE to 1, SkipReason.PAST to 1), plan.skipped)
        assertEquals(LocalDate(2026, 10, 28), plan.first)
        assertEquals(LocalDate(2027, 1, 12), plan.last)
    }

    @Test
    fun thunderbirdFoldedTitleDurationAndPathZone() {
        val plan = CalendarImport.plan(THUNDERBIRD, today, paris)
        assertNull(plan.name)
        val standUp = "Stand-up, équipe; produit"
        assertEquals(
            listOf(
                ev(at(2026, 10, 20, 9, 15), at(2026, 10, 20, 9, 30), standUp),
                ev(at(2026, 10, 21, 9, 15), at(2026, 10, 21, 9, 30), standUp),
                ev(at(2026, 10, 21, 17), at(2026, 10, 21, 18), "Rendez-vous"),
                ev(at(2026, 10, 22, 9, 15), at(2026, 10, 22, 9, 30), standUp),
                ev(at(2026, 10, 23, 9, 15), at(2026, 10, 23, 9, 30), standUp),
                ev(at(2026, 10, 26, 9, 15), at(2026, 10, 26, 9, 30), standUp),
            ),
            plan.slots,
        )
    }

    @Test
    fun aDstWeekKeepsTheMeetingAtItsLocalHour() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:dst
            SUMMARY:Hebdo
            DTSTART;TZID=Europe/Paris:20261019T090000
            DTEND;TZID=Europe/Paris:20261019T093000
            RRULE:FREQ=WEEKLY;COUNT=2
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        // Appareil à Paris : 9 h les deux semaines ; appareil en UTC : 7 h puis 8 h (le changement d'heure du 25 octobre).
        assertEquals(listOf(at(2026, 10, 19, 9), at(2026, 10, 26, 9)), CalendarImport.plan(text, today, paris).slots.map { it.first })
        assertEquals(listOf(at(2026, 10, 19, 7), at(2026, 10, 26, 8)), CalendarImport.plan(text, today, TimeZone.UTC).slots.map { it.first })
    }

    @Test
    fun unknownZonesAreReportedNeverGuessedAndVtimezoneIsUsed() {
        val plan = CalendarImport.plan(ZONES, today, paris)
        assertEquals(setOf("Mars Standard Time"), plan.unknownZones)
        assertEquals(mapOf(SkipReason.UNKNOWN_ZONE to 1), plan.skipped)
        assertEquals(listOf(ev(at(2026, 10, 21, 10), at(2026, 10, 21, 11), "Bombay")), plan.slots)
    }

    @Test
    fun unsupportedRulesLaterEventsAndInvalidOnes() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:a
            DTSTART:20261020T090000Z
            DTEND:20261020T100000Z
            RRULE:FREQ=HOURLY
            END:VEVENT
            BEGIN:VEVENT
            UID:b
            DTSTART:20280101T090000Z
            DTEND:20280101T100000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:c
            DTSTART:20261020T090000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:d
            SUMMARY:Sans début
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val plan = CalendarImport.plan(text, today, paris)
        assertTrue(plan.isEmpty)
        assertEquals(mapOf(SkipReason.UNSUPPORTED_RULE to 1, SkipReason.BEYOND_HORIZON to 1, SkipReason.INVALID to 2), plan.skipped)
    }

    @Test
    fun recurringAllDayAndLongTitles() {
        val long = "x".repeat(300)
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:birthday
            SUMMARY:$long
            DTSTART;VALUE=DATE:20001024
            RRULE:FREQ=YEARLY
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val plan = CalendarImport.plan(text, today, paris)
        assertEquals(listOf(Triple(LocalDate(2026, 10, 24), LocalDate(2026, 10, 24), "x".repeat(CalendarImport.MAX_TITLE))), plan.marks)
    }

    @Test
    fun midnightSplitsRdatesWorkingElsewhereAndUids() {
        val text = """
            BEGIN:VCALENDAR
            BEGIN:VEVENT
            UID:night
            SUMMARY:Mise en production
            DTSTART;TZID=Europe/Paris:20261020T220000
            DTEND;TZID=Europe/Paris:20261021T020000
            END:VEVENT
            BEGIN:VEVENT
            UID:extra
            SUMMARY:Comité
            DTSTART;TZID=Europe/Paris:20261022T100000
            DTEND;TZID=Europe/Paris:20261022T110000
            RDATE;TZID=Europe/Paris:20261029T100000,20261105T100000
            END:VEVENT
            BEGIN:VEVENT
            UID:remote
            SUMMARY:Télétravail
            DTSTART;TZID=Europe/Paris:20261023T090000
            DTEND;TZID=Europe/Paris:20261023T180000
            X-MICROSOFT-CDO-BUSYSTATUS:WORKINGELSEWHERE
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:Sans UID
            DTSTART;TZID=Europe/Paris:20261024T090000
            DTEND;TZID=Europe/Paris:20261024T100000
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val plan = CalendarImport.plan(text, today, paris)
        assertEquals(
            listOf(
                ev(at(2026, 10, 20, 22), at(2026, 10, 21, 0), "Mise en production"),
                ev(at(2026, 10, 21, 0), at(2026, 10, 21, 2), "Mise en production"),
                ev(at(2026, 10, 22, 10), at(2026, 10, 22, 11), "Comité"),
                ev(at(2026, 10, 24, 9), at(2026, 10, 24, 10), "Sans UID"),
                ev(at(2026, 10, 29, 10), at(2026, 10, 29, 11), "Comité"),
                ev(at(2026, 11, 5, 10), at(2026, 11, 5, 11), "Comité"),
            ),
            plan.slots,
        )
        assertEquals(mapOf(SkipReason.FREE to 1), plan.skipped)
        assertEquals(listOf("night", "night"), plan.events.filter { it.title == "Mise en production" }.map { it.uid })
        // Sans UID : un identifiant tiré du début et du titre, le même à chaque import.
        assertEquals("20261024T090000/Sans UID", plan.events.single { it.title == "Sans UID" }.uid)
        assertEquals(plan, CalendarImport.plan(text, today, paris))
    }

    @Test
    fun fiveThousandEventsAreReadQuickly() {
        val events = (0 until 5_000).joinToString("\n") { i ->
            val day = LocalDate(2026, 10, 19).plus(i % 150, kotlinx.datetime.DateTimeUnit.DAY)
            val d = "${day.year}${(day.month.ordinal + 1).toString().padStart(2, '0')}${day.day.toString().padStart(2, '0')}"
            "BEGIN:VEVENT\nUID:e$i\nSUMMARY:Réunion $i\nDTSTART;TZID=Europe/Paris:${d}T${(8 + i % 9).toString().padStart(2, '0')}0000\nDURATION:PT30M\nEND:VEVENT"
        }
        val text = "BEGIN:VCALENDAR\n$events\nEND:VCALENDAR"
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val plan = CalendarImport.plan(text, today, paris)
        assertTrue(started.elapsedNow() < kotlin.time.Duration.parse("2s"), "analyse en ${started.elapsedNow()}")
        assertEquals(5_000, plan.events.size)
    }

    @Test
    fun aJsonExportIsNotACalendar() {
        assertFailsWith<IllegalArgumentException> { CalendarImport.plan("{\"format\":\"kairos-export\"}", today, paris) }
    }
}
