package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.ExchangeNote
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.exchange.PackField
import com.skohscripts.kairos.core.team.exchange.ReportChange
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Notes jointes aux échanges d'équipe (docs/spec/equipe-echanges.md § Notes, issue #120) : un manager, une membre, deux bases. */
class ExchangeNotesTest {
    private class Tick(var current: Instant) : Clock {
        override fun now() = current
        fun at(iso: String) {
            current = Instant.parse(iso)
        }
    }

    private val tick = Tick(Instant.parse("2026-09-30T07:00:00Z"))
    private val day = LocalDate(2026, 9, 30)

    private suspend fun blank(): KairosRepository =
        KairosRepository.open(KairosStore.open(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)), { KairosSnapshot() }, tick, timeZone = TimeZone.UTC)

    @Test
    fun notesGoBothWaysWithAuthorAndDateAndEnterTheJournal() = runTest {
        val manager = blank()
        manager.updateSettings(Settings(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Corentin")))
        val lea = manager.createMember("Léa", "Dev", 100, 7.0, false)!!
        val a = manager.createTeamTask("Migration API")!!
        manager.updateTask(a, TaskEdit(title = "Migration API", priority = 1, points = 5, pinDay = day, reassign = Reassignment(lea), noteOut = "  Commence par la base. "))
        assertEquals("Commence par la base.", manager.snapshot.value.tasks.single { it.id == a }.noteOut)

        // Paquet : la note du manager arrive chez la membre, signée et datée du paquet ; elle est vue dans l'aperçu.
        val pack = manager.exportPack(lea, "pack-1")!!
        assertTrue("\"note\": \"Commence par la base.\"" in pack)
        val member = blank()
        assertEquals(listOf("Migration API" to "Commence par la base."), member.previewPack(pack).notes)
        member.receivePack(pack)
        val received = member.snapshot.value.tasks.single { it.origin != null }
        assertEquals(ExchangeNote("Commence par la base.", Instant.parse("2026-09-30T07:00:00Z"), "Corentin"), received.noteIn)
        // La note envoyée entre au journal, une seule fois même si le paquet est renvoyé.
        tick.at("2026-09-30T09:00:00Z")
        val again = manager.exportPack(lea, "pack-2")!!
        assertEquals(1, manager.snapshot.value.teamEvents.count { it.kind == TeamEventKind.NOTE })
        // Recevoir la même note ne change ni son texte ni sa date.
        member.receivePack(again)
        assertEquals(Instant.parse("2026-09-30T07:00:00Z"), member.snapshot.value.tasks.single { it.origin != null }.noteIn!!.at)

        // Rapport : la note de la membre arrive chez le manager, datée du rapport, et entre au journal (source rapport).
        member.updateTask(received.id, TaskEdit(title = received.title, priority = 1, points = 5, pinDay = day, noteOut = "Bloquée : clé d'API attendue."))
        tick.at("2026-10-01T16:00:00Z")
        val report = member.buildReport(member.origins().single().key)!!
        assertTrue("Bloquée : clé d'API attendue." in report)
        val plan = manager.previewReport(report)
        assertEquals(listOf<ReportChange>(ReportChange.Note(null, "Bloquée : clé d'API attendue.")), plan.updates.single().changes)
        manager.integrateReport(report)
        val task = manager.snapshot.value.tasks.single { it.id == a }
        assertEquals(ExchangeNote("Bloquée : clé d'API attendue.", Instant.parse("2026-10-01T16:00:00Z"), "Léa"), task.noteIn)
        val event = manager.snapshot.value.teamEvents.last { it.kind == TeamEventKind.NOTE }
        assertEquals(TeamEventSource.REPORT, event.source)
        assertEquals(lea, event.memberId)

        // Un paquet sans note efface la note reçue chez la membre.
        manager.updateTask(a, TaskEdit(title = "Migration API", priority = 1, points = 5, pinDay = day, noteOut = ""))
        tick.at("2026-10-02T09:00:00Z")
        val cleared = manager.exportPack(lea, "pack-3")!!
        assertFalse("\"note\"" in cleared)
        assertTrue(PackField.NOTE in member.previewPack(cleared).updated.single().changed)
        member.receivePack(cleared)
        assertNull(member.snapshot.value.tasks.single { it.origin != null }.noteIn)

        // L'export complet garde les notes et les relit.
        val export = ExportCodec.encode(manager.snapshot.value, "3.9.0", tick.current)
        assertEquals(task.noteIn, ExportCodec.decode(export).tasks.single { it.id == a }.noteIn)
    }

    @Test
    fun aTaskWithoutNotesExportsAsBefore() = runTest {
        val repo = blank()
        repo.createTask("Rendez-vous")
        val text = ExportCodec.encode(repo.snapshot.value, "3.9.0", tick.current)
        assertFalse("noteOut" in text)
        assertFalse("noteIn" in text)
    }
}
