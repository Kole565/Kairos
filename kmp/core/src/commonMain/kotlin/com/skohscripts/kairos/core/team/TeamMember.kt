package com.skohscripts.kairos.core.team

import com.skohscripts.kairos.core.settings.SettingsForm
import kotlin.time.Instant

/**
 * Membre de l'équipe (docs/spec/equipe.md § Modèle) : une fiche dans la base
 * du manager, pas un compte. [uid] est l'identité stable utilisée par les
 * échanges de fichiers (les [id] entiers ne sortent jamais d'une base) ;
 * [isSelf] désigne « C'est moi » (au plus un membre).
 */
data class TeamMember(
    val id: Long,
    val uid: String,
    val name: String,
    val role: String = "",
    /** Quotité en % (1-100). */
    val availabilityPercent: Int = 100,
    val hoursPerDay: Double,
    val isSelf: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Instant,
    val updatedAt: Instant,
    /** Date du dernier rapport d'avancement intégré pour ce membre (docs/spec/equipe-echanges.md) ; `null` = aucun. */
    val lastReportAt: Instant? = null,
    /**
     * Semaine type (docs/spec/equipe-charge.md § Capacité, issue #104) : heures du lundi au vendredi ([WEEK_DAYS] valeurs, 0 = jour
     * libre) ; `null` = [hoursPerDay] chaque jour ouvré, comme avant.
     */
    val weekHours: List<Double>? = null,
) {
    companion object {
        /** Jours d'une semaine type : du lundi au vendredi (le calendrier de Kairos ne travaille pas le week-end). */
        const val WEEK_DAYS = 5

        /** Texte de la colonne `week_hours` : « 8,8,7.5,8,0 » (point décimal). */
        fun encodeWeekHours(hours: List<Double>?): String? = hours?.joinToString(",") { SettingsForm.formatDecimal(it) }

        /** Lecture de la colonne : `null` si absente ou mal formée (cinq nombres de 0 à 24, au moins un positif). */
        fun decodeWeekHours(text: String?): List<Double>? {
            val values = text?.split(',')?.map { it.trim().toDoubleOrNull() ?: return null } ?: return null
            return values.takeIf { isValidWeek(it) }
        }

        /** Cinq nombres de 0 à 24, au moins un jour travaillé. */
        fun isValidWeek(hours: List<Double>): Boolean =
            hours.size == WEEK_DAYS && hours.all { it.isFinite() && it in 0.0..24.0 } && hours.any { it > 0.0 }
    }
}
