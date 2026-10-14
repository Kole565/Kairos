package com.skohscripts.kairos.ui.day

import androidx.compose.runtime.Composable
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.duration_hours
import com.skohscripts.kairos.ui.generated.resources.duration_hours_minutes
import com.skohscripts.kairos.ui.generated.resources.duration_minutes
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * Dates, heures et nombres lisibles (« 30 sept. », « 09h15 », « 1 h 30 »).
 * Noms de mois et formats fixes par langue : le code commun n'a pas de
 * formatage localisé. Le français est la langue par défaut.
 */
internal object Dates {
    private val FR = listOf("janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc.")
    private val EN = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun english(language: String) = !UiLanguage.isFrench(language)

    fun short(date: LocalDate, language: String): String {
        val month = date.month.ordinal
        return if (english(language)) "${EN[month]} ${date.day}" else "${date.day} ${FR[month]}"
    }

    /** Comme [short], avec l'année (« 29 févr. 2028 », « Feb 29, 2028 ») si elle diffère de celle de [reference]. */
    fun short(date: LocalDate, language: String, reference: LocalDate): String {
        val text = short(date, language)
        return when {
            date.year == reference.year -> text
            english(language) -> "$text, ${date.year}"
            else -> "$text ${date.year}"
        }
    }

    private val FR_LONG = listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre")
    private val EN_LONG = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val FR_DAYS = listOf("lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche")
    private val EN_DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** « mardi 30 septembre 2026 », « 1er » le premier du mois ; « Tuesday, September 30, 2026 ». */
    fun long(date: LocalDate, language: String): String {
        val dow = date.dayOfWeek.ordinal
        val month = date.month.ordinal
        if (english(language)) return "${EN_DAYS[dow]}, ${EN_LONG[month]} ${date.day}, ${date.year}"
        val day = if (date.day == 1) "1er" else date.day.toString()
        return "${FR_DAYS[dow]} $day ${FR_LONG[month]} ${date.year}"
    }

    /** « mar. 30/09 » ; « Tue 9/30 ». */
    fun dayShort(date: LocalDate, language: String): String {
        val dow = date.dayOfWeek.ordinal
        return if (english(language)) {
            "${EN_DAYS[dow].take(3)} ${date.month.ordinal + 1}/${date.day}"
        } else {
            "${FR_DAYS[dow].take(3)}. ${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}"
        }
    }

    /** « 28/09 » ; « 9/28 ». */
    fun dayMonth(date: LocalDate, language: String): String =
        if (english(language)) "${date.month.ordinal + 1}/${date.day}"
        else "${date.day.toString().padStart(2, '0')}/${(date.month.ordinal + 1).toString().padStart(2, '0')}"

    /** « 09h15 » en français, « 09:15 » en anglais. */
    fun time(t: LocalTime, language: String): String {
        val h = t.hour.toString().padStart(2, '0')
        val m = t.minute.toString().padStart(2, '0')
        return if (english(language)) "$h:$m" else "${h}h$m"
    }

    fun time(t: LocalDateTime, language: String): String = time(t.time, language)

    /** Graduation de la frise : « 9h », « 9:00 ». */
    fun hour(h: Int, language: String): String = if (english(language)) "$h:00" else "${h}h"

    /** « HH:MM » pour un champ de saisie. */
    fun field(t: LocalTime): String = "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"

    /** Lit « 9:30 », « 09:30 » ou « 9h30 » ; `null` si invalide. */
    fun parseTime(text: String): LocalTime? {
        val parts = text.trim().lowercase().split(':', 'h')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].ifEmpty { "0" }.toIntOrNull() ?: return null
        return if (h in 0..23 && m in 0..59) LocalTime(h, m) else null
    }

    /**
     * Saisie souple d'une date : `2026-09-30` (ISO), `2026-9-30` ou `2026/9/30` (zéros facultatifs, `-`, `/` ou `.`)
     * et, en [french], `30/09/2026` (jour, mois, année). L'ordre mois/jour de l'anglais n'est pas accepté : il serait
     * ambigu avec le français (docs/spec/planning-personnalise.md § Lot A, 1). `null` si illisible ou impossible.
     */
    fun parseDate(text: String, french: Boolean = false): LocalDate? {
        val t = text.trim()
        if (t.isEmpty()) return null
        runCatching { LocalDate.parse(t) }.getOrNull()?.let { return it }
        val parts = t.split('-', '/', '.').map { it.trim() }
        if (parts.size != 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        val n = parts.map { it.toInt() }
        val (year, month, day) = when {
            parts[0].length == 4 -> Triple(n[0], n[1], n[2])
            french && parts[2].length == 4 -> Triple(n[2], n[1], n[0])
            else -> return null
        }
        return runCatching { LocalDate(year, month, day) }.getOrNull()
    }

    /**
     * Une décimale au plus, sans « .0 » : « 16 », « 6.2 ». Une valeur positive qui
     * s'arrondirait à 0 garde deux décimales (« 0.05 » : un score de 1 / 21 n'est pas nul).
     */
    fun number(value: Double, language: String): String =
        number(value).let { if (english(language)) it else it.replace('.', ',') }

    fun number(value: Double): String {
        if (value > 0 && value < 0.05) return (round(value * 100) / 100).takeIf { it > 0 }?.toString() ?: "0.01"
        val rounded = round(value * 10) / 10
        val whole = rounded.toLong()
        return if (rounded == whole.toDouble()) whole.toString() else rounded.toString()
    }
}

/** Durée lisible : « 1 h 30 », « 2 h », « 45 min ». */
@Composable
internal fun duration(minutes: Int): String {
    val m = maxOf(0, minutes)
    val hours = m / 60
    val rest = m % 60
    return when {
        hours > 0 && rest > 0 -> stringResource(Res.string.duration_hours_minutes, hours, rest.toString().padStart(2, '0'))
        hours > 0 -> stringResource(Res.string.duration_hours, hours)
        else -> stringResource(Res.string.duration_minutes, rest)
    }
}
