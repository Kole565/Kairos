package com.skohscripts.kairos.core.calendar

import com.skohscripts.kairos.core.model.BlockKind
import com.skohscripts.kairos.core.model.BlockSource
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TimeBlock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Calendriers du téléphone (docs/spec/connexions-externes.md § Étape 2) : mêmes règles qu'à l'import, fusion sans écriture. */
class ExternalBlocksTest {
    private val paris = TimeZone.of("Europe/Paris")
    private fun at(d: Int, h: Int, m: Int = 0): Instant = LocalDate(2026, 10, d).atTime(h, m).toInstant(paris)
    private fun utcMidnight(d: Int): Instant = LocalDate(2026, 10, d).atTime(0, 0).toInstant(TimeZone.UTC)

    @Test
    fun theSameRulesAsTheImport() {
        val events = listOf(
            DeviceEvent("Travail", "a", "Point", at(26, 9, 30), at(26, 10)),
            DeviceEvent("Travail", "b", "Libre", at(26, 12), at(26, 13), free = true),
            DeviceEvent("Travail", "c", "Annulé", at(26, 14), at(26, 15), cancelled = true),
            DeviceEvent("Travail", "d", "Refusé", at(26, 16), at(26, 17), declined = true),
            DeviceEvent("Travail", "e", "Nuit", at(26, 23), at(27, 1)),
            DeviceEvent("Perso", "f", "Congés", utcMidnight(28), utcMidnight(30), allDay = true),
        )
        val data = ExternalBlocks.map(events, paris)
        assertEquals(
            listOf(
                Triple("Point", LocalDate(2026, 10, 26).atTime(9, 30), LocalDate(2026, 10, 26).atTime(10, 0)),
                Triple("Nuit", LocalDate(2026, 10, 26).atTime(23, 0), LocalDate(2026, 10, 27).atTime(0, 0)),
                Triple("Nuit", LocalDate(2026, 10, 27).atTime(0, 0), LocalDate(2026, 10, 27).atTime(1, 0)),
            ),
            data.blocks.map { Triple(it.title, it.start, it.end) },
        )
        assertTrue(data.blocks.all { it.id < 0 && it.kind == BlockKind.BUSY && it.source is BlockSource.Device })
        assertEquals(data.blocks.map { it.id }.toSet().size, data.blocks.size)
        // Journée entière : un badge du 28 au 29 (fin exclue), jamais un créneau.
        val mark = data.marks.single()
        assertEquals(LocalDate(2026, 10, 28) to LocalDate(2026, 10, 29), mark.start to mark.end)
        assertEquals(BlockSource.Device("Perso", "f"), mark.source)
    }

    @Test
    fun mergeAddsThePhoneAndDropsTheSameImportedOccurrence() {
        val created = Instant.parse("2026-10-20T08:00:00Z")
        val start = LocalDate(2026, 10, 26).atTime(9, 30)
        val end = LocalDate(2026, 10, 26).atTime(10, 0)
        val imported = TimeBlock(1, "Point", start, end, createdAt = created, source = BlockSource.Imported(1, "a"))
        val otherWeek = imported.copy(id = 2, start = start.date.let { LocalDate(2026, 11, 2) }.atTime(9, 30), end = LocalDate(2026, 11, 2).atTime(10, 0))
        val manual = TimeBlock(3, "Dentiste", start, end, createdAt = created)
        val snapshot = KairosSnapshot(timeBlocks = listOf(imported, otherWeek, manual))
        assertSame(snapshot, ExternalBlocks.merge(snapshot, DeviceCalendarData()))
        val device = ExternalBlocks.map(listOf(DeviceEvent("Travail", "a", "Point", at(26, 9, 30), at(26, 10))), paris)
        val merged = ExternalBlocks.merge(snapshot, device)
        // L'occurrence du téléphone remplace celle importée de même UID et même début ; l'autre semaine et le créneau manuel restent.
        assertEquals(listOf(2L, 3L), merged.timeBlocks.filter { it.id > 0 }.map { it.id })
        assertEquals(1, merged.timeBlocks.count { it.source is BlockSource.Device })
    }
}
