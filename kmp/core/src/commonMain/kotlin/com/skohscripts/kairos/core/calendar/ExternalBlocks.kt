package com.skohscripts.kairos.core.calendar

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.DayMark
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Un événement lu dans un calendrier du téléphone (Android, `CalendarContract.Instances`), déjà déplié par le système :
 * [begin] et [end] en instants ; une journée entière ([allDay]) est donnée de minuit UTC à minuit UTC, fin exclue.
 */
data class DeviceEvent(
    val calendarName: String,
    val uid: String,
    val title: String,
    val begin: Instant,
    val end: Instant,
    val allDay: Boolean = false,
    val free: Boolean = false,
    val cancelled: Boolean = false,
    val declined: Boolean = false,
)

/** Créneaux et journées entières des calendriers du téléphone, jamais stockés. */
data class DeviceCalendarData(val blocks: List<TimeBlock> = emptyList(), val marks: List<DayMark> = emptyList())

/**
 * Calendriers du téléphone (docs/spec/connexions-externes.md § Étape 2) : mêmes règles qu'à l'import d'un `.ics` (libres,
 * annulés, refusés écartés ; journée entière en badge ; découpage à minuit), et fusion au moment du calcul, sans jamais
 * écrire en base.
 */
object ExternalBlocks {
    /** Les événements du téléphone en créneaux (identifiants négatifs, propres à cette liste) et en journées entières. */
    fun map(events: List<DeviceEvent>, deviceZone: TimeZone): DeviceCalendarData {
        val blocks = mutableListOf<TimeBlock>()
        val marks = mutableListOf<DayMark>()
        var next = -1L
        for (e in events) {
            if (e.free || e.cancelled || e.declined || e.end <= e.begin) continue
            val source = BlockSource.Device(e.calendarName, e.uid)
            if (e.allDay) {
                val start = e.begin.toLocalDateTime(TimeZone.UTC).date
                val last = e.end.toLocalDateTime(TimeZone.UTC).date.minus(DatePeriod(days = 1))
                marks += DayMark(next--, e.title, start, maxOf(start, last), source, e.begin)
                continue
            }
            val start = e.begin.toLocalDateTime(deviceZone)
            val end = e.end.toLocalDateTime(deviceZone)
            for ((a, b) in CalendarImport.splitAtMidnight(start, end)) {
                blocks += TimeBlock(next--, e.title, a, b, BlockKind.BUSY, createdAt = e.begin, source = source)
            }
        }
        return DeviceCalendarData(blocks, marks)
    }

    /**
     * La base vue par les écrans qui calculent un planning : les créneaux et journées du téléphone ajoutés ; un créneau
     * importé de même `UID` et de même début qu'un créneau du téléphone est retiré (le téléphone est la source vivante).
     */
    fun merge(snapshot: KairosSnapshot, device: DeviceCalendarData): KairosSnapshot {
        if (device.blocks.isEmpty() && device.marks.isEmpty()) return snapshot
        val liveBlocks = device.blocks.mapTo(HashSet()) { it.source!!.uid to it.start }
        val liveMarks = device.marks.mapTo(HashSet()) { it.source.uid to it.start }
        return snapshot.copy(
            timeBlocks = snapshot.timeBlocks.filterNot { b -> b.source is BlockSource.Imported && (b.source!!.uid to b.start) in liveBlocks } + device.blocks,
            dayMarks = snapshot.dayMarks.filterNot { m -> m.source is BlockSource.Imported && (m.source.uid to m.start) in liveMarks } + device.marks,
        )
    }
}
