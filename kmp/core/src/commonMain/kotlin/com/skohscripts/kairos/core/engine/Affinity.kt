package com.skohscripts.kairos.core.engine

import kotlinx.datetime.LocalTime
import kotlin.math.abs

/**
 * D'où vient l'affinité qui a fait préférer un créneau : une [MANUAL] heure préférée du type, ou la détection sur le chrono : l'heure
 * habituelle ([DETECTED], fréquence), l'heure où l'on finit le plus vite ([EFFICIENT], efficacité) ou les deux ([COMBINED]).
 */
enum class AffinitySource { DETECTED, MANUAL, EFFICIENT, COMBINED }

/**
 * Affinité d'un type de tâche pour une heure (docs/spec/planning-personnalise.md § Lot C, étape C2). Pur : la mesure (`BestHours.affinity`)
 * ou les réglages la construisent, le moteur ne fait que l'interroger.
 *
 * [strength] : force de l'ajustement (≥ 0, `0` neutre). [detected] : par type (en minuscules), le gain de chacune des tranches de la
 * journée ∈ [0, 1] (types fiables seulement). [preferred] : par type (en minuscules), l'heure préférée (0 à 23), qui **prime** sur la détection.
 */
class Affinity(
    val strength: Double,
    private val detected: Map<String, List<Double>>,
    private val preferred: Map<String, Int>,
    private val bucketHours: Int,
    /** La source des gains [detected] : ce que mesure le réglage (fréquence par défaut). */
    private val detectedSource: AffinitySource = AffinitySource.DETECTED,
) {
    /** Vrai si l'affinité ne peut rien changer : force nulle, ou aucun type connu. */
    val isEmpty: Boolean get() = strength <= 0 || (detected.isEmpty() && preferred.isEmpty())

    private fun key(type: String) = type.trim().lowercase()

    /** Gain ∈ [0, 1] du type [taskType] à l'heure [time] : 0 sans type, sans préférence ni profil fiable. */
    fun boost(taskType: String, time: LocalTime): Double {
        val type = key(taskType)
        if (type.isEmpty()) return 0.0
        val hour = time.hour + time.minute / 60.0
        preferred[type]?.let { return maxOf(0.0, 1.0 - abs(hour - it) / PREFERRED_RADIUS_HOURS) }
        val buckets = detected[type] ?: return 0.0
        return buckets.getOrNull(time.hour / bucketHours) ?: 0.0
    }

    /** Diviseur de l'effort à l'heure [time] : `1 + force × gain`, jamais en dessous de 1. */
    fun factor(taskType: String, time: LocalTime): Double = 1.0 + maxOf(0.0, strength) * boost(taskType, time)

    /** La source de l'affinité de [taskType] : l'heure préférée si le type en a une, sinon la détection. */
    fun source(taskType: String): AffinitySource = if (key(taskType) in preferred) AffinitySource.MANUAL else detectedSource

    companion object {
        /** Une heure préférée « vers 12 h » : le gain décroît linéairement jusqu'à 0 à ± ce nombre d'heures. */
        const val PREFERRED_RADIUS_HOURS = 3.0
    }
}
