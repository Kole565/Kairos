package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import kotlin.time.Instant

/**
 * « Corriger les estimations… » (docs/spec/statistiques.md § Corriger les estimations, issue #101) : les tâches terminées
 * dont l'estimation diffère du temps réellement passé, pour remplacer l'une par l'autre **sur demande**, après aperçu.
 * Pur : l'instant est un paramètre, rien n'est écrit ici.
 */
object EstimateCorrection {
    /** Une tâche à corriger : son estimation d'avant (`null` = aucune) et le temps réel qui la remplacerait. */
    data class Candidate(val task: Task, val oldMinutes: Int?, val realMinutes: Int)

    /**
     * Tâches **faites**, de l'espace Perso, non reçues d'un manager, **chronométrées** (au moins une minute de session de
     * chrono : sans elle, aucun temps « réel » mesuré) et dont l'estimation n'est pas déjà le temps réel. Temps réel = celui
     * des statistiques (sessions, plus temps saisi à la main, `TimeTracking.spentMinutesByTask`). Les plus récemment
     * terminées d'abord (`updatedAt`), puis par titre.
     */
    fun candidates(snapshot: KairosSnapshot, now: Instant): List<Candidate> {
        val done = snapshot.tasks.filter { it.status == TaskStatus.DONE && it.space == TaskSpace.PERSONAL && it.origin == null }
        if (done.isEmpty()) return emptyList()
        val timed = TimeTracking.spentMinutesByTask(snapshot.workSessions, now)
        val spent = TimeTracking.spentMinutesByTask(snapshot.workSessions, now, done)
        return done.mapNotNull { t ->
            if ((timed[t.id] ?: 0) <= 0) return@mapNotNull null
            val real = spent[t.id] ?: return@mapNotNull null
            if (t.estimatedMinutes == real) null else Candidate(t, t.estimatedMinutes, real)
        }.sortedWith(compareByDescending<Candidate> { it.task.updatedAt }.thenBy { it.task.title })
    }
}
