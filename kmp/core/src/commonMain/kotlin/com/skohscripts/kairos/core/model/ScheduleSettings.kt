package com.skohscripts.kairos.core.model

import kotlinx.serialization.Serializable

/**
 * Horaires par jour de la semaine (docs/spec/planning-personnalise.md § Lot B1), sous-objet [Settings.schedule] :
 * `null` tant que l'utilisateur n'a rien personnalisé (même règle que [Settings.team] : sauver le formulaire sans y toucher
 * ne crée jamais cet objet, donc rien n'entre dans l'export). Ne jamais ajouter d'horaire en dehors de ce sous-objet.
 */
@Serializable
data class ScheduleSettings(
    /**
     * Jours dont l'horaire diffère de la journée de travail des réglages. Clé : 0 = lundi … 6 = dimanche (convention du
     * projet, `Workdays.weekday`). Un jour absent est « comme d'habitude ».
     */
    val weekdays: Map<Int, DaySchedule> = emptyMap(),
)

/**
 * Horaire d'un jour de la semaine : [free] (jour libre, rien n'est planifié automatiquement) ou une journée de travail
 * propre, de [startHour] à [endHour] (heures pleines comme les réglages de la journée ; 0 ≤ début < fin ≤ 23). Un jour
 * ni libre ni complet n'est pas écrit.
 */
@Serializable
data class DaySchedule(
    val free: Boolean = false,
    val startHour: Int? = null,
    val endHour: Int? = null,
)
