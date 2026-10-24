package com.skohscripts.kairos.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Horaires par jour de la semaine (docs/spec/planning-personnalise.md § Lots B1 et B2), sous-objet [Settings.schedule] :
 * `null` tant que l'utilisateur n'a rien personnalisé (même règle que [Settings.team] : sauver le formulaire sans y toucher
 * ne crée jamais cet objet, donc rien n'entre dans l'export). Ne jamais ajouter d'horaire en dehors de ce sous-objet.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ScheduleSettings(
    /**
     * Jours dont l'horaire diffère de la journée de travail des réglages. Clé : 0 = lundi … 6 = dimanche (convention du
     * projet, `Workdays.weekday`). Un jour absent est « comme d'habitude ».
     */
    val weekdays: Map<Int, DaySchedule> = emptyMap(),
    /**
     * Contextes (« Travail », « Maison ») : un nom et les types de tâche qu'ils regroupent (docs/spec/planning-personnalise.md
     * § Lot B2). Jamais écrits tant qu'il n'y en a pas : un export de la 3.4 reste identique.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val contexts: List<WorkContext> = emptyList(),
    /**
     * Le planning préfère, à score égal, les heures où un type est habituellement travaillé (docs/spec/planning-personnalise.md
     * § Lot C, étape C2). Nul tant que l'utilisateur n'y a pas touché : un export de la 3.5 reste identique.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val bestHours: BestHoursSettings? = null,
)

/**
 * Réglage de l'étape C2 : [enabled] (désactivé par défaut), [strength] (0 Faible, 1 Moyenne, 2 Forte : `BestHours.strengthOf`),
 * [preferred] : l'heure préférée (0 à 23) de certains types, par nom de type, qui prime sur la détection, et [measure] : ce que la
 * détection mesure (v2, issue #105 : [MEASURE_FREQUENCY] par défaut, [MEASURE_EFFICIENCY] ou [MEASURE_BOTH]), jamais écrit à sa valeur
 * par défaut pour qu'un export de la 3.6 reste identique.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BestHoursSettings(
    val enabled: Boolean = false,
    val strength: Int = 1,
    val preferred: Map<String, Int> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val measure: String = MEASURE_FREQUENCY,
) {
    companion object {
        const val MEASURE_FREQUENCY = "frequency"
        const val MEASURE_EFFICIENCY = "efficiency"
        const val MEASURE_BOTH = "both"

        /** Une valeur inconnue (export d'une version future, saisie) mesure la fréquence, comme la v1. */
        fun normalizeMeasure(value: String): String = when (value.trim().lowercase()) {
            MEASURE_EFFICIENCY -> MEASURE_EFFICIENCY
            MEASURE_BOTH -> MEASURE_BOTH
            else -> MEASURE_FREQUENCY
        }
    }
}

/**
 * Horaire d'un jour de la semaine : [free] (jour libre, rien n'est planifié automatiquement) ou une journée de travail
 * propre, de [startHour] à [endHour] (heures pleines comme les réglages de la journée ; 0 ≤ début < fin ≤ 23). Un jour
 * ni libre ni complet n'est pas écrit.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class DaySchedule(
    val free: Boolean = false,
    val startHour: Int? = null,
    val endHour: Int? = null,
    /**
     * Plusieurs plages, chacune avec son contexte (lot B2) : quand elle n'est pas vide, elle **remplace** [startHour] et
     * [endHour] (qui restent la forme d'une plage unique sans contexte, celle de la 3.4). Jamais écrite quand elle est vide.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val windows: List<WorkWindow> = emptyList(),
)

/** Une plage de travail d'un jour : de [startHour] à [endHour] (heures pleines), réservée au contexte [context] (son nom) ou ouverte à toute tâche si `null`. */
@Serializable
data class WorkWindow(
    val startHour: Int,
    val endHour: Int,
    val context: String? = null,
)

/** Un contexte : son [name] et les [types] de tâche (le type existant de chaque tâche) qui y vont. */
@Serializable
data class WorkContext(
    val name: String,
    val types: List<String> = emptyList(),
)
