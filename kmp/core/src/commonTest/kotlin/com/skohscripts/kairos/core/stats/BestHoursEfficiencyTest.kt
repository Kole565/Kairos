package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.AffinitySource
import com.skohscripts.kairos.core.model.BestHoursSettings
import com.skohscripts.kairos.core.model.ScheduleSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.settings.SettingsForm
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Meilleures heures v2 : l'efficacité (docs/spec/planning-personnalise.md § Lot C v2, issue #105). */
class BestHoursEfficiencyTest {
    private val now = Instant.parse("2026-10-14T12:00:00Z") // mercredi
    private val t0 = Instant.parse("2026-09-01T08:00:00Z")
    private val utc = TimeZone.UTC
    private var nextTask = 1L
    private var nextSession = 1L
    private val tasks = mutableListOf<Task>()
    private val sessions = mutableListOf<WorkSession>()

    /** Une tâche [type] terminée, estimée à [estimate] min, chronométrée le [day] octobre de [start] pendant [real] min. */
    private fun done(type: String, estimate: Int?, day: Int, start: String, real: Int) {
        val id = nextTask++
        tasks += Task(id, "T$id", taskType = type, status = TaskStatus.DONE, estimatedMinutes = estimate, createdAt = t0, updatedAt = now)
        val begin = LocalDateTime(2026, 10, day, start.substringBefore(':').toInt(), start.substringAfter(':').toInt()).toInstant(utc)
        sessions += WorkSession(nextSession++, id, begin, Instant.fromEpochMilliseconds(begin.toEpochMilliseconds() + real * 60_000L), t0)
    }

    private fun measure() = BestHours.measure(tasks, sessions, now, Settings(), utc)

    private fun settings(measure: String) =
        Settings(schedule = ScheduleSettings(bestHours = BestHoursSettings(enabled = true, measure = measure)))

    @Test
    fun theHourWhereATypeFinishesFasterThanUsualStandsOut() {
        // Six tâches à 9 h au temps prévu, six à 14 h en 40 min pour 60 prévues (1,5 fois plus vite).
        repeat(6) { done("Dev", 60, 5 + it, "09:00", 60) }
        repeat(6) { done("Dev", 60, 5 + it, "14:00", 40) }
        val dev = measure().profiles.single()
        assertEquals(12, dev.efficiencyTasks)
        assertTrue(dev.efficiencyReliable)
        assertEquals(1 / sqrt(1.5), dev.efficiency[4]!!, 1e-9) // 8-10 h
        assertEquals(sqrt(1.5), dev.efficiency[7]!!, 1e-9) // 14-16 h
        assertNull(dev.efficiency[5])
        assertEquals(7, dev.fastest)
        val affinity = BestHours.affinity(settings(BestHoursSettings.MEASURE_EFFICIENCY), measure())!!
        assertEquals((sqrt(1.5) - 1) / 0.5, affinity.boost("Dev", LocalTime(14, 0)), 1e-9)
        assertEquals(0.0, affinity.boost("Dev", LocalTime(9, 0)))
        assertEquals(AffinitySource.EFFICIENT, affinity.source("Dev"))
    }

    @Test
    fun aTypeAlwaysUnderestimatedIsNotSlowEverywhere() {
        // Toujours deux fois plus long que prévu, le matin comme l'après-midi : aucune heure ne ressort.
        repeat(4) { done("Docs", 30, 5 + it, "09:00", 60) }
        repeat(4) { done("Docs", 30, 5 + it, "15:00", 60) }
        val docs = measure().profiles.single()
        assertEquals(1.0, docs.efficiency[4]!!, 1e-9)
        assertEquals(1.0, docs.efficiency[7]!!, 1e-9)
        assertNull(docs.fastest)
    }

    @Test
    fun anArtificiallySuggestedHourDoesNotReinforceItself() {
        // Kairos a placé « Dev » à 10 h : dix tâches y sont faites, cinq à 16 h, toutes au temps prévu ; « Mail » à 16 h.
        repeat(10) { done("Dev", 60, 1 + it, "10:00", 60) }
        repeat(5) { done("Dev", 60, 1 + it, "16:00", 60) }
        repeat(5) { done("Mail", 30, 1 + it, "16:00", 30) }
        val frequency = BestHours.affinity(settings(BestHoursSettings.MEASURE_FREQUENCY), measure())!!
        val efficiency = BestHours.affinity(settings(BestHoursSettings.MEASURE_EFFICIENCY), measure())!!
        // La fréquence voit 10 h ; l'efficacité, non.
        assertTrue(frequency.boost("Dev", LocalTime(10, 0)) > 0.0)
        assertEquals(0.0, efficiency.boost("Dev", LocalTime(10, 0)), 1e-9)
        // Suivre la suggestion dix fois de plus n'y change rien.
        repeat(10) { done("Dev", 60, 12 + it % 3, "10:00", 60) }
        val after = measure().profiles.first { it.type == "Dev" }
        assertEquals(1.0, after.efficiency[5]!!, 1e-9)
        assertEquals(0.0, BestHours.affinity(settings(BestHoursSettings.MEASURE_EFFICIENCY), measure())!!.boost("Dev", LocalTime(10, 0)), 1e-9)
    }

    @Test
    fun tooFewTasksGiveNoEfficiency() {
        repeat(4) { done("Dev", 60, 5 + it, "14:00", 30) }
        // Une tâche sans estimation ne compte pas.
        done("Dev", null, 9, "14:00", 30)
        val dev = measure().profiles.single()
        assertEquals(4, dev.efficiencyTasks)
        assertFalse(dev.efficiencyReliable)
        assertTrue(dev.efficiency.all { it == null })
        // Assez de tâches, mais une seule à 9 h : la tranche reste sans valeur.
        done("Dev", 60, 10, "14:00", 30)
        done("Dev", 60, 11, "09:00", 60)
        val again = measure().profiles.single()
        assertTrue(again.efficiencyReliable)
        assertNull(again.efficiency[4])
        assertEquals(1.0, again.efficiency[7]!!, 0.2)
    }

    @Test
    fun bothMeasuresAreAveraged() {
        repeat(6) { done("Dev", 60, 5 + it, "09:00", 60) }
        repeat(6) { done("Dev", 60, 5 + it, "14:00", 40) }
        repeat(6) { done("Mail", 30, 5 + it, "09:00", 30) }
        val result = measure()
        val f = BestHours.affinity(settings(BestHoursSettings.MEASURE_FREQUENCY), result)!!
        val e = BestHours.affinity(settings(BestHoursSettings.MEASURE_EFFICIENCY), result)!!
        val both = BestHours.affinity(settings(BestHoursSettings.MEASURE_BOTH), result)!!
        val at = LocalTime(14, 0)
        assertEquals((f.boost("Dev", at) + e.boost("Dev", at)) / 2, both.boost("Dev", at), 1e-9)
        assertTrue(both.boost("Dev", at) > 0.0)
        assertEquals(AffinitySource.COMBINED, both.source("Dev"))
        assertEquals(AffinitySource.DETECTED, f.source("Dev"))
    }

    @Test
    fun theMeasureSettingIsNormalizedAndNeverWrittenByDefault() {
        val base = Settings(schedule = ScheduleSettings(bestHours = BestHoursSettings(enabled = true)))
        val values = SettingsForm.values(base)
        assertEquals("frequency", values.getValue("schedule.bestHours.measure"))
        val saved = SettingsForm.validate(values + ("schedule.bestHours.measure" to "efficiency"), base).settings!!
        assertEquals("efficiency", saved.schedule!!.bestHours!!.measure)
        val unknown = SettingsForm.validate(values + ("schedule.bestHours.measure" to "speed"), base).settings!!
        assertEquals("frequency", unknown.schedule!!.bestHours!!.measure)
        val json = Json { encodeDefaults = true; explicitNulls = false }
        assertFalse("measure" in json.encodeToString(BestHoursSettings(enabled = true)))
        assertTrue("\"measure\":\"both\"" in json.encodeToString(BestHoursSettings(enabled = true, measure = "both")))
    }
}
