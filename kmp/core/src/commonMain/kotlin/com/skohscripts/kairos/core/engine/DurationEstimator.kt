package com.skohscripts.kairos.core.engine

import com.skohscripts.kairos.core.model.KairosSnapshot
import com.skohscripts.kairos.core.model.LearningSettings
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskSpace
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.stats.TaskStats
import kotlin.math.floor
import kotlin.time.Instant

/**
 * Durée apprise d'une tâche sans estimation explicite (docs/spec/durees-apprises.md) : la médiane du temps réellement passé sur
 * ses dernières occurrences. Pur : ni horloge ni accès aux données (l'instant et l'historique sont des paramètres). Le modèle
 * d'apprentissage automatique d'une version majeure future se branchera ici, derrière [estimate], avec une [Source] de plus.
 */
object DurationEstimator {
    /** D'où vient la durée : les occurrences de la même série, ou à défaut les tâches de même titre. */
    enum class Source { SERIES, TITLE }

    /**
     * La durée apprise : [minutes] (arrondie à 5 min), le nombre d'occurrences retenues [samples] et si elle est [reliable]
     * (au moins [LearningSettings.minSamples] occurrences retenues). Une durée peu fiable s'affiche mais ne s'applique pas.
     */
    data class Estimate(val minutes: Int, val source: Source, val samples: Int, val reliable: Boolean)

    /** Une occurrence passée : tâche terminée et le temps réellement passé dessus. */
    data class Sample(val taskId: Long, val seriesId: Long?, val title: String, val space: TaskSpace, val minutes: Int)

    /** Temps minimal pour qu'une occurrence compte : un démarrage-arrêt accidentel (1 minute ou moins) est ignoré. */
    const val MIN_SAMPLE_MINUTES = 2

    /** Une occurrence au-delà de ce multiple de la médiane (chrono oublié) est écartée. */
    private const val OUTLIER_FACTOR = 3.0

    /**
     * Les occurrences passées : tâches **terminées**, au temps passé (chrono et saisie manuelle) d'au moins
     * [MIN_SAMPLE_MINUTES]. Une tâche reçue d'un manager n'en est jamais (isolation d'espace).
     */
    fun samples(tasks: List<Task>, spentByTask: Map<Long, Int>): List<Sample> =
        tasks.filter { it.status == TaskStatus.DONE && it.origin == null }.mapNotNull { t ->
            val spent = spentByTask[t.id] ?: return@mapNotNull null
            if (spent < MIN_SAMPLE_MINUTES) null else Sample(t.id, t.seriesId, t.title, t.space, spent)
        }

    /** Titre comparé sans tenir compte des espaces autour ni de la casse. */
    private fun key(title: String) = title.trim().lowercase()

    /**
     * La durée apprise de [task], ou `null` : désactivée, tâche reçue, estimation explicite, ou aucune occurrence passée. On
     * essaie la série (occurrences engendrées les unes des autres, qui survivent à un renommage), puis le même titre : la
     * première qui est fiable gagne, à défaut la première qui a des occurrences (peu fiable). Seuls les échantillons du
     * même espace comptent. [samples] vient de [samples] ; l'ordre n'a pas d'importance (on retient les plus récentes par
     * identifiant, jamais par date de modification).
     */
    fun estimate(task: Task, samples: List<Sample>, settings: Settings): Estimate? {
        val learning = settings.learning ?: LearningSettings()
        if (!learning.enabled || task.origin != null) return null
        if (task.estimatedMinutes != null && task.estimatedMinutes > 0) return null
        val sameSpace = samples.filter { it.space == task.space && it.taskId != task.id }
        val series = task.seriesId?.let { id -> sameSpace.filter { it.seriesId == id || it.taskId == id } }.orEmpty()
        val titled = sameSpace.filter { key(it.title) == key(task.title) }
        val cap = maxOf(5, (settings.workdayEndHour - settings.workdayStartHour) * 60)
        var weak: Estimate? = null
        for ((source, candidates) in listOf(Source.SERIES to series, Source.TITLE to titled)) {
            val estimate = fromSamples(candidates, source, learning, cap) ?: continue
            if (estimate.reliable) return estimate
            if (weak == null) weak = estimate
        }
        return weak
    }

    private fun fromSamples(candidates: List<Sample>, source: Source, learning: LearningSettings, cap: Int): Estimate? {
        if (candidates.isEmpty()) return null
        val recent = candidates.sortedByDescending { it.taskId }.take(maxOf(1, learning.history)).map { it.minutes.toDouble() }
        val first = TaskStats.median(recent) ?: return null
        // Un chrono oublié (plus de trois fois la médiane) ne tire pas la durée vers le haut.
        val kept = recent.filter { it <= first * OUTLIER_FACTOR }
        val median = TaskStats.median(kept) ?: return null
        return Estimate(roundToFive(median).coerceIn(5, cap), source, kept.size, reliable = kept.size >= maxOf(1, learning.minSamples))
    }

    /** Au multiple de 5 le plus proche, la moitié vers le haut (arithmétique propre, pas de demi-pair). */
    private fun roundToFive(minutes: Double): Int = floor(minutes / 5.0 + 0.5).toInt() * 5

    /**
     * Les durées apprises de toutes les tâches **à faire** du [snapshot] sans estimation explicite (fiables ou non), au temps
     * [now]. Un seul calcul des occurrences passées pour toutes les tâches.
     */
    fun estimates(snapshot: KairosSnapshot, now: Instant): Map<Long, Estimate> {
        val settings = snapshot.settings
        if (settings.learning?.enabled == false) return emptyMap()
        val past = samples(snapshot.tasks, TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks))
        if (past.isEmpty()) return emptyMap()
        val out = LinkedHashMap<Long, Estimate>()
        for (t in snapshot.tasks) {
            if (t.status != TaskStatus.TODO) continue
            estimate(t, past, settings)?.let { out[t.id] = it }
        }
        return out
    }

    /**
     * Le [snapshot] où chaque tâche **à faire** sans estimation explicite porte sa durée apprise **fiable** comme estimation
     * (`estimatedMinutes`) : la lecture des calculs d'équipe (charge, prévisions, répartition, projection des occurrences), qui
     * lisent alors la durée apprise sans autre changement. Les tâches faites ne sont jamais touchées (ni la calibration, ni les
     * erreurs d'estimation passées). Sans durée apprise, le même objet est rendu.
     */
    fun withLearnedEstimates(snapshot: KairosSnapshot, now: Instant): KairosSnapshot {
        val applied = appliedMinutes(estimates(snapshot, now))
        if (applied.isEmpty()) return snapshot
        return snapshot.copy(tasks = snapshot.tasks.map { t -> applied[t.id]?.let { t.copy(estimatedMinutes = it) } ?: t })
    }

    /**
     * Ce qu'on attend de [task] en minutes pour les alertes du chrono : son estimation explicite, sinon sa durée apprise
     * **fiable**, sinon `null` (pas d'alerte de dépassement). Une seule tâche : un seul passage sur l'historique.
     */
    fun effectiveMinutes(task: Task, snapshot: KairosSnapshot, now: Instant): Int? {
        task.estimatedMinutes?.takeIf { it > 0 }?.let { return it }
        if (snapshot.settings.learning?.enabled == false) return null
        val past = samples(snapshot.tasks, TimeTracking.spentMinutesByTask(snapshot.workSessions, now, snapshot.tasks))
        return estimate(task, past, snapshot.settings)?.takeIf { it.reliable }?.minutes
    }

    /** Les durées apprises **fiables**, en minutes : ce que le planning, les alertes et les projections appliquent. */
    fun appliedMinutes(estimates: Map<Long, Estimate>): Map<Long, Int> =
        estimates.filterValues { it.reliable }.mapValues { it.value.minutes }
}
