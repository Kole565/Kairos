package com.skohscripts.kairos.core.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes

class IcsZonesTest {
    private val outlook = IcsParser.parse(OUTLOOK)

    @Test
    fun aVtimezoneFollowsItsDaylightSavingRules() {
        val v = VTimeZone.of(outlook.children("VTIMEZONE").single())!!
        val summer = LocalDate(2026, 10, 24).atTime(9, 0)
        val winter = LocalDate(2026, 10, 26).atTime(9, 0)
        assertEquals(summer.toInstant(UtcOffset(hours = 2)), v.toInstant(summer))
        assertEquals(winter.toInstant(UtcOffset(hours = 1)), v.toInstant(winter))
        // Même résultat que la base IANA pour Paris.
        val paris = TimeZone.of("Europe/Paris")
        for (day in 1..31) {
            val t = LocalDate(2026, 3, day).atTime(12, 0)
            assertEquals(t.toInstant(paris), v.toInstant(t), "le $day mars")
        }
    }

    @Test
    fun windowsNamesComeFromTheVtimezoneAndMozillaPathsFromIana() {
        val zones = IcsZones(outlook)
        val t = LocalDate(2026, 10, 19).atTime(9, 30)
        assertEquals(t.toInstant(TimeZone.of("Europe/Paris")), zones.resolve("Romance Standard Time")!!.toInstant(t))
        assertEquals(t.toInstant(TimeZone.of("Europe/Paris")), zones.resolve("/mozilla.org/20070129_1/Europe/Paris")!!.toInstant(t))
        // Nom Windows sans VTIMEZONE dans le fichier : pas de table de correspondance, inconnu (jamais deviné).
        assertNull(zones.resolve("Pacific Standard Time"))
    }

    @Test
    fun aZoneKnownOnlyByItsVtimezoneIsUsedAndAnUnknownOneIsNot() {
        val zones = IcsZones(IcsParser.parse(ZONES))
        val t = LocalDate(2026, 10, 21).atTime(13, 30)
        assertEquals(t.toInstant(UtcOffset(hours = 5, minutes = 30)), assertNotNull(zones.resolve("Kairos Test Zone")).toInstant(t))
        assertNull(zones.resolve("Mars Standard Time"))
    }

    @Test
    fun durationsAndOffsets() {
        assertEquals((7 * 24 * 60).minutes, icsDuration("P1W"))
        assertEquals((24 * 60 + 150).minutes, icsDuration("P1DT2H30M"))
        assertEquals((-15).minutes, icsDuration("-PT15M"))
        assertNull(icsDuration("1H"))
        assertEquals(UtcOffset(hours = -5), VTimeZone.offset("-0500"))
        assertEquals(UtcOffset(hours = 5, minutes = 30), VTimeZone.offset("+053000"))
        assertNull(VTimeZone.offset("0100"))
    }
}
