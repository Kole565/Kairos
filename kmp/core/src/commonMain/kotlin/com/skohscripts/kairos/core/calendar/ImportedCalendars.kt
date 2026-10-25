package com.skohscripts.kairos.core.calendar

import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.CalendarSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime

/** Un calendrier importé, pour la carte « Calendriers » : ses créneaux à venir et ses badges à venir. */
data class ImportedCalendar(val source: CalendarSource, val upcomingBlocks: Int, val upcomingMarks: Int)

/** Calendriers importés d'une base (docs/spec/connexions-externes.md § Interface). */
object ImportedCalendars {
    /** Les calendriers, par nom ; « à venir » = créneau qui commence à partir de [today], badge qui finit à partir de [today]. */
    fun of(snapshot: KairosSnapshot, today: LocalDate): List<ImportedCalendar> {
        val from = today.atTime(0, 0)
        return snapshot.calendarSources.sortedBy { it.name.lowercase() }.map { c ->
            ImportedCalendar(
                c,
                snapshot.timeBlocks.count { (it.source as? BlockSource.Imported)?.calendarId == c.id && it.start >= from },
                snapshot.dayMarks.count { (it.source as? BlockSource.Imported)?.calendarId == c.id && it.end >= today },
            )
        }
    }

    /** Le calendrier de ce nom (casse et espaces ignorés aux extrémités), s'il existe. */
    fun named(snapshot: KairosSnapshot, name: String): CalendarSource? =
        snapshot.calendarSources.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** Créneaux que réimporter sous [name] remplacerait : ceux de ce calendrier qui commencent à partir de [today] (ses badges à venir aussi). */
    fun replaced(snapshot: KairosSnapshot, name: String, today: LocalDate): Int =
        named(snapshot, name)?.let { c -> of(snapshot, today).first { it.source.id == c.id }.upcomingBlocks } ?: 0
}
