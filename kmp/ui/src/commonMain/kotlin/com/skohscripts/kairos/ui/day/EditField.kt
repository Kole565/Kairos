package com.skohscripts.kairos.ui.day

/**
 * Les champs de la fiche d'une tâche, dans l'ordre où elle les présente (docs/spec/premiere-tache.md § Un coach par-dessus la vraie
 * fiche). Le coach de la première tâche rattache une étape à chacun ; chaque champ porte la balise de test `edit-field-<NOM>`, ce
 * qui permet de vérifier que la liste du coach et la fiche disent la même chose. [advanced] : le champ est dans les options
 * avancées, que le coach ouvre quand il l'explique.
 */
internal enum class EditField(val advanced: Boolean = false) {
    TITLE,
    DESCRIPTION,
    PRIORITY,
    POINTS,
    MINUTES,
    DEADLINE,
    SCHEDULED(advanced = true),
    PROJECT(advanced = true),
    MANUAL_TIME(advanced = true),
    RECURRENCE(advanced = true),
    TYPE(advanced = true),
    PIN(advanced = true),
    SUBTASKS(advanced = true),
    BLOCKERS(advanced = true),
    ;

    /** Balise de test du champ. */
    val tag: String get() = "edit-field-$name"
}
