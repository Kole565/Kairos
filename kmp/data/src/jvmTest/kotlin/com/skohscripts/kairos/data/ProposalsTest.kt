package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamEventKind
import com.skohscripts.kairos.core.team.TeamEventSource
import com.skohscripts.kairos.core.team.exchange.ProposalDecision
import com.skohscripts.kairos.core.team.exchange.ProposalState
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Tâches proposées par un membre au manager (docs/spec/equipe-echanges.md § Propositions, issue #121). */
class ProposalsTest {
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
    fun aProposalReachesTheBacklogOnceAndIsAdoptedByTheNextPackage() = runTest {
        val manager = blank()
        manager.updateSettings(Settings(team = TeamSettings(enabled = true, name = "Équipe Plateforme", managerName = "Corentin")))
        val lea = manager.createMember("Léa", "Dev", 100, 7.0, false)!!
        val a = manager.createTeamTask("Migration API")!!
        manager.updateTask(a, TaskEdit(title = "Migration API", priority = 1, points = 5, pinDay = day, reassign = Reassignment(lea)))
        val member = blank()
        member.receivePack(manager.exportPack(lea, "pack-1")!!)
        val origin = member.origins().single().key

        // Le membre propose deux tâches personnelles ; une troisième reste privée.
        val bug = member.createTask("Corriger l'import CSV")!!
        member.updateTask(bug, TaskEdit(title = "Corriger l'import CSV", description = "Plante sur les accents", priority = 0, points = 3, estimatedMinutes = 90, taskType = "Bug", pinDay = day, proposal = ProposalChange(origin)))
        val step = member.createTask("Documenter la migration")!!
        member.updateTask(step, TaskEdit(title = "Documenter la migration", pinDay = day, proposal = ProposalChange(origin)))
        member.createTask("Rendez-vous médecin")
        val proposed = member.snapshot.value.tasks.single { it.id == bug }
        assertEquals(origin, proposed.proposedTo)
        assertNotNull(proposed.teamUid)

        tick.at("2026-10-01T16:00:00Z")
        val report = member.buildReport(origin)!!
        // Rien de privé : ni priorité, ni points, ni la tâche non proposée.
        assertFalse("Rendez-vous médecin" in report)
        assertTrue("\"proposals\"" in report)
        val plan = manager.previewReport(report)
        assertEquals(listOf(ProposalState.NEW, ProposalState.NEW), plan.proposals.map { it.state })

        // Intégrer sans décision ne crée rien.
        val stepUid = member.snapshot.value.tasks.single { it.id == step }.teamUid!!
        manager.integrateReport(
            report,
            mapOf(proposed.teamUid!! to ProposalDecision.Add("Corriger l'import CSV (accents)"), stepUid to ProposalDecision.Reject),
        )
        val created = manager.snapshot.value.tasks.single { it.teamUid == proposed.teamUid }
        assertEquals("Corriger l'import CSV (accents)", created.title)
        assertEquals(TaskSpace.TEAM, created.space)
        assertNull(created.assigneeId) // au backlog
        assertEquals("Plante sur les accents", created.description)
        assertEquals(90, created.estimatedMinutes)
        assertEquals("Bug", created.taskType)
        assertNull(created.priority) // la priorité du membre ne sort pas
        val event = manager.snapshot.value.teamEvents.single { it.taskId == created.id && it.kind == TeamEventKind.CREATED }
        assertEquals(TeamEventSource.REPORT, event.source)
        assertEquals(lea, event.memberId)
        assertEquals(listOf(stepUid), manager.snapshot.value.settings.team!!.rejectedProposals)

        // Le rapport suivant porte encore les deux propositions : aucune n'est à redécider, rien n'est dupliqué.
        tick.at("2026-10-02T16:00:00Z")
        val next = member.buildReport(origin)!!
        assertEquals(listOf(ProposalState.IN_TEAM, ProposalState.REJECTED), manager.previewReport(next).proposals.map { it.state })
        manager.integrateReport(next, mapOf(proposed.teamUid!! to ProposalDecision.Add("x")))
        assertEquals(1, manager.snapshot.value.tasks.count { it.teamUid == proposed.teamUid })

        // Le manager l'assigne à Léa et l'envoie : le Kairos de Léa la rattache à sa tâche, sans doublon.
        manager.updateTask(created.id, TaskEdit(title = created.title, priority = 1, points = 3, pinDay = day, reassign = Reassignment(lea)))
        tick.at("2026-10-03T09:00:00Z")
        member.receivePack(manager.exportPack(lea, "pack-2")!!)
        val adopted = member.snapshot.value.tasks.filter { it.teamUid == proposed.teamUid }
        assertEquals(1, adopted.size)
        assertEquals(bug, adopted.single().id)
        assertNotNull(adopted.single().origin)
        // Reçue, elle n'est plus une proposition.
        assertFalse(member.buildReport(origin)!!.contains("Corriger l'import CSV\""))
    }

    @Test
    fun aReportWithoutProposalsHasNoProposalsField() = runTest {
        val manager = blank()
        manager.updateSettings(Settings(team = TeamSettings(enabled = true, name = "P", managerName = "C")))
        val lea = manager.createMember("Léa", "Dev", 100, 7.0, false)!!
        val a = manager.createTeamTask("Migration API")!!
        manager.updateTask(a, TaskEdit(title = "Migration API", priority = 1, points = 5, pinDay = day, reassign = Reassignment(lea)))
        val member = blank()
        member.receivePack(manager.exportPack(lea, "pack-1")!!)
        val report = member.buildReport(member.origins().single().key)!!
        assertFalse("proposals" in report)
        assertTrue(manager.previewReport(report).proposals.isEmpty())
    }
}
