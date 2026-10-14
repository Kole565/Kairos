package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.engine.DurationEstimator
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import com.skohscripts.kairos.core.model.TeamSettings
import com.skohscripts.kairos.core.team.TeamFixtures.member
import com.skohscripts.kairos.core.team.TeamFixtures.mondayMorning
import com.skohscripts.kairos.core.team.TeamFixtures.snapshot
import com.skohscripts.kairos.core.team.TeamFixtures.stamp
import com.skohscripts.kairos.core.team.TeamFixtures.task
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

/** Durées apprises dans les calculs d'équipe (docs/spec/durees-apprises.md § Portée). */
class LearnedTeamLoadTest {
    private val settings = TeamFixtures.settings(TeamSettings(focusFactor = 1.0, horizonWeeks = 1, hoursPerPoint = 1.0))

    /** Trois « Rapport » faits en 2 h chacun (temps chronométré), puis un « Rapport » à faire, assigné à A, sans estimation, à 5 points : la calibration par points n'a rien pour ce palier (les trois faites sont à 3 points). */
    private fun history(s: Settings = settings) = snapshot(
        tasks = (1L..3L).map { task(it, 1, hours = null, points = 3, status = TaskStatus.DONE).copy(title = "Rapport") } +
            task(10, 1, hours = null, points = 5).copy(title = "Rapport"),
        members = listOf(member(1, "A")),
        settings = s,
    ).copy(
        workSessions = (1L..3L).map { WorkSession(it, it, stamp, stamp + 120.minutes, stamp) },
    )

    private fun loadOf(s: Settings) = TeamLoad.build(history(s), mondayMorning, TimeZone.UTC).members.single().loadHours

    @Test
    fun anUnestimatedTaskIsLoadedWithItsLearnedDuration() {
        // 2 h apprises (médiane des trois occurrences) au lieu de 5 points × 1 h/point.
        assertEquals(2.0, loadOf(settings), 1e-9)
    }

    @Test
    fun switchingTheFeatureOffRestoresThePointsBasedLoad() {
        assertEquals(5.0, loadOf(settings.copy(learning = LearningSettings(enabled = false))), 1e-9)
    }

    @Test
    fun theSnapshotReadByTheTeamCalculationsCarriesTheLearnedEstimateOnOpenTasksOnly() {
        val learned = DurationEstimator.withLearnedEstimates(history(), mondayMorning)
        assertEquals(120, learned.tasks.single { it.id == 10L }.estimatedMinutes)
        // Les occurrences passées (faites) ne sont jamais réécrites : ni la calibration ni les erreurs d'estimation n'en dépendent.
        assertEquals(null, learned.tasks.single { it.id == 1L }.estimatedMinutes)
        // Une estimation explicite l'emporte.
        val explicit = history().let { s -> s.copy(tasks = s.tasks.map { if (it.id == 10L) it.copy(estimatedMinutes = 45) else it }) }
        assertEquals(45, DurationEstimator.withLearnedEstimates(explicit, mondayMorning).tasks.single { it.id == 10L }.estimatedMinutes)
    }
}
