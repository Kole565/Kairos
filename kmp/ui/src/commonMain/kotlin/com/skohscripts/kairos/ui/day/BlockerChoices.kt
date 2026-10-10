package com.skohscripts.kairos.ui.day

import com.skohscripts.kairos.core.engine.Dependencies
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskDependency

/**
 * Une case de la liste « Bloquée par » du dialogue d'édition (docs/spec/dependances.md) : son libellé, et si la cocher
 * créerait un cycle ([cyclic] : la tâche candidate attend déjà, directement ou non, la tâche éditée).
 */
internal data class BlockerChoice(val task: Task, val label: String, val cyclic: Boolean)

internal object BlockerChoices {
    /**
     * Les bloqueurs possibles de [task] parmi [candidates] (elle exclue), dans l'ordre reçu. Libellé : « Mère › titre »
     * pour une sous-tâche (comme dans les listes de Jour) ; deux libellés identiques (une tâche et sa prochaine
     * occurrence récurrente, par exemple) se distinguent par leur date (échéance, sinon date prévue), sinon par leur
     * numéro. [titles] donne le titre de toute tâche (les mères ne sont pas forcément candidates).
     */
    fun of(task: Task, candidates: List<Task>, dependencies: List<TaskDependency>, titles: Map<Long, String>, language: String): List<BlockerChoice> {
        val edges = dependencies.map { Dependencies.Edge(blocked = it.taskId, blocker = it.blockerId) }
        val others = candidates.filter { it.id != task.id }
        val base = others.associate { t -> t.id to (t.parentId?.let(titles::get)?.let { "$it › ${t.title}" } ?: t.title) }
        val repeated = base.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return others.map { t ->
            val label = base.getValue(t.id)
            val distinct = if (label in repeated) {
                val date = t.deadline ?: t.scheduledDate
                "$label (${date?.let { Dates.short(it, language) } ?: "#${t.id}"})"
            } else {
                label
            }
            BlockerChoice(t, distinct, Dependencies.wouldCreateCycle(edges, task.id, t.id))
        }
    }
}
