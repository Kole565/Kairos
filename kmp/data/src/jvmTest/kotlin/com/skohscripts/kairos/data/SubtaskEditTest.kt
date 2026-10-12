package com.skohscripts.kairos.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.TaskRecurrence
import com.skohscripts.kairos.core.model.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/** Édition des sous-tâches existantes depuis la fiche de leur mère (docs/spec/vue-jour.md § Édition, issue #69). */
class SubtaskEditTest {
    private val now = Instant.parse("2026-09-28T07:00:00Z")
    private val clock = object : Clock {
        override fun now() = now
    }
    private val today = LocalDate(2026, 9, 28)

    private suspend fun empty(): KairosRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        return KairosRepository.open(KairosStore.open(driver), { KairosSnapshot() }, clock, timeZone = TimeZone.UTC)
    }

    private fun edit(title: String, vararg subtasks: SubtaskEdit, newSubtasks: String = "") =
        TaskEdit(title = title, pinDay = today, subtaskEdits = subtasks.toList(), newSubtasks = newSubtasks)

    private data class Family(val repo: KairosRepository, val mother: Long, val first: Long, val second: Long)

    private suspend fun family(): Family {
        val repo = empty()
        val mother = repo.createTask("Mère")!!
        val first = repo.createTask("Une", parentId = mother)!!
        val second = repo.createTask("Deux", parentId = mother)!!
        return Family(repo, mother, first, second)
    }

    private fun KairosRepository.task(id: Long) = snapshot.value.tasks.firstOrNull { it.id == id }

    @Test
    fun aSubtaskIsRenamedWithTheSameSave() = runTest {
        val f = family()
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "  Une, corrigée ", done = false)))
        assertEquals("Une, corrigée", f.repo.task(f.first)!!.title)
        assertEquals("Deux", f.repo.task(f.second)!!.title)
    }

    @Test
    fun anEmptyTitleKeepsTheOldOne() = runTest {
        val f = family()
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "   ", done = false)))
        assertEquals("Une", f.repo.task(f.first)!!.title)
    }

    @Test
    fun aSubtaskIsCompletedThenReopened() = runTest {
        val f = family()
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = true)))
        assertEquals(TaskStatus.DONE, f.repo.task(f.first)!!.status)
        assertEquals(TaskStatus.TODO, f.repo.task(f.second)!!.status)
        // Enregistrer sans toucher ne change rien (pas de bascule en double).
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = true)))
        assertEquals(TaskStatus.DONE, f.repo.task(f.first)!!.status)
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = false)))
        assertEquals(TaskStatus.TODO, f.repo.task(f.first)!!.status)
    }

    @Test
    fun completingARunningSubtaskClosesItsTimer() = runTest {
        val f = family()
        f.repo.startTimer(f.first)
        assertTrue(f.repo.snapshot.value.workSessions.any { it.taskId == f.first && it.endedAt == null })
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = true)))
        assertTrue(f.repo.snapshot.value.workSessions.none { it.endedAt == null })
    }

    @Test
    fun aSubtaskIsDeletedWithItsDependencies() = runTest {
        val f = family()
        // « Une » attend « Deux » : supprimer « Une » emporte cette dépendance, comme deleteTask.
        f.repo.updateTask(f.first, TaskEdit("Une", pinDay = today, blockerIds = setOf(f.second)))
        assertEquals(1, f.repo.snapshot.value.dependencies.size)
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = false, delete = true)))
        assertNull(f.repo.task(f.first))
        assertNotNull(f.repo.task(f.second))
        assertNotNull(f.repo.task(f.mother))
        assertTrue(f.repo.snapshot.value.dependencies.isEmpty())
    }

    @Test
    fun aTeamTasksSubtasksAreLeftAlone() = runTest {
        val repo = empty()
        val mother = repo.createTeamTask("Mère d'équipe")!!
        val child = repo.createTeamTask("Fille", parentId = mother)!!
        repo.updateTask(mother, edit("Mère d'équipe", SubtaskEdit(child, "Renommée", done = true), SubtaskEdit(child, "x", done = false, delete = true)))
        val kept = repo.task(child)!!
        assertEquals("Fille", kept.title)
        assertEquals(TaskStatus.TODO, kept.status)
    }

    @Test
    fun edits_renames_and_new_subtasks_are_saved_together() = runTest {
        val f = family()
        f.repo.updateTask(
            f.mother,
            edit("Mère", SubtaskEdit(f.first, "Une bis", done = true), SubtaskEdit(f.second, "Deux", done = false, delete = true), newSubtasks = "Trois"),
        )
        val children = f.repo.snapshot.value.tasks.filter { it.parentId == f.mother }
        assertEquals(listOf("Une bis", "Trois"), children.map { it.title })
        assertEquals(listOf(TaskStatus.DONE, TaskStatus.TODO), children.map { it.status })
    }

    @Test
    fun anIdThatIsNotASubtaskOfTheMotherIsIgnored() = runTest {
        val f = family()
        val other = f.repo.createTask("Autre mère")!!
        val stranger = f.repo.createTask("Étrangère", parentId = other)!!
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(stranger, "Piratée", done = true), SubtaskEdit(other, "Piratée", done = false, delete = true), SubtaskEdit(9_999, "?", done = true)))
        assertEquals("Étrangère", f.repo.task(stranger)!!.title)
        assertEquals(TaskStatus.TODO, f.repo.task(stranger)!!.status)
        assertNotNull(f.repo.task(other))
    }

    @Test
    fun completingARecurringSubtaskCreatesItsNextOccurrenceOnce() = runTest {
        val f = family()
        f.repo.updateTask(f.first, TaskEdit("Une", pinDay = today, deadline = LocalDate(2026, 9, 24), recurrence = TaskRecurrence.WEEKLY))
        val before = f.repo.snapshot.value.tasks.size
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = true)))
        assertEquals(before + 1, f.repo.snapshot.value.tasks.size)
        f.repo.updateTask(f.mother, edit("Mère", SubtaskEdit(f.first, "Une", done = true)))
        assertEquals(before + 1, f.repo.snapshot.value.tasks.size)
    }
}
