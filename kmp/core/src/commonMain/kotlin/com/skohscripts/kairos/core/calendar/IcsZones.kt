package com.skohscripts.kairos.core.calendar

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Une valeur de date iCalendar : jour entier, heure UTC, heure d'un fuseau nommé (`TZID`) ou heure « flottante ». */
sealed interface IcsTime {
    data class Day(val date: LocalDate) : IcsTime
    data class Utc(val instant: Instant) : IcsTime
    data class Zoned(val local: LocalDateTime, val tzid: String) : IcsTime
    data class Floating(val local: LocalDateTime) : IcsTime

    companion object {
        /** `20261024`, `20261024T093000`, `20261024T073000Z` (avec `VALUE=DATE` ou `TZID` pris de [property]) ; `null` si illisible. */
        fun of(property: IcsProperty): IcsTime? = parse(property.value.trim(), property.param("TZID"), property.param("VALUE"))

        /** Les valeurs d'une liste (`EXDATE:a,b`). */
        fun list(property: IcsProperty): List<IcsTime> =
            property.value.split(',').mapNotNull { parse(it.trim(), property.param("TZID"), property.param("VALUE")) }

        fun parse(value: String, tzid: String?, valueType: String?): IcsTime? {
            if (valueType.equals("DATE", ignoreCase = true) || (value.length == 8 && 'T' !in value)) return date(value)?.let(::Day)
            val t = value.indexOf('T').takeIf { it == 8 } ?: return null
            val date = date(value.substring(0, t)) ?: return null
            val rest = value.substring(t + 1)
            val utc = rest.endsWith("Z") || rest.endsWith("z")
            val digits = rest.removeSuffix("Z").removeSuffix("z")
            if (digits.length != 6 || !digits.all(Char::isDigit)) return null
            val (h, m, s) = Triple(digits.substring(0, 2).toInt(), digits.substring(2, 4).toInt(), digits.substring(4, 6).toInt())
            // 60 secondes (seconde intercalaire) : ramenée à 59.
            if (h > 23 || m > 59 || s > 60) return null
            val local = date.atTime(h, m, s.coerceAtMost(59))
            return when {
                utc -> Utc(local.toInstant(TimeZone.UTC))
                tzid != null -> Zoned(local, tzid)
                else -> Floating(local)
            }
        }

        private fun date(s: String): LocalDate? {
            if (s.length != 8 || !s.all(Char::isDigit)) return null
            return runCatching { LocalDate(s.substring(0, 4).toInt(), s.substring(4, 6).toInt(), s.substring(6, 8).toInt()) }.getOrNull()
        }
    }
}

/** `DURATION` iCalendar (`P1W`, `P1DT2H30M`, `-PT15M`) ; `null` si illisible. */
internal fun icsDuration(value: String): Duration? {
    var s = value.trim().uppercase()
    val negative = s.startsWith("-")
    s = s.removePrefix("-").removePrefix("+")
    if (!s.startsWith("P")) return null
    var total = Duration.ZERO
    var inTime = false
    var number = ""
    for (c in s.drop(1)) {
        when {
            c == 'T' -> inTime = true
            c.isDigit() -> number += c
            else -> {
                val n = number.toLongOrNull() ?: return null
                number = ""
                total += when {
                    c == 'W' && !inTime -> (n * 7).days
                    c == 'D' && !inTime -> n.days
                    c == 'H' && inTime -> n.hours
                    c == 'M' && inTime -> n.minutes
                    c == 'S' && inTime -> n.seconds
                    else -> return null
                }
            }
        }
    }
    if (number.isNotEmpty()) return null
    return if (negative) -total else total
}

/** Un fuseau dans lequel une heure locale d'un événement devient un instant. */
fun interface EventZone {
    fun toInstant(local: LocalDateTime): Instant
}

/**
 * Fuseaux d'un fichier iCalendar (docs/spec/connexions-externes.md § Lecture iCalendar) : le `VTIMEZONE` de même `TZID`
 * dans le fichier d'abord (il couvre les noms Windows d'Outlook, « Romance Standard Time »), sinon le nom IANA, essayé aussi
 * par le dernier segment `Région/Ville` d'un chemin (`/mozilla.org/…/Europe/Paris`) ; `null` = inconnu, jamais deviné.
 */
class IcsZones(calendar: IcsComponent) {
    private val definitions: Map<String, VTimeZone> =
        calendar.children("VTIMEZONE").mapNotNull { c -> c.first("TZID")?.value?.trim()?.let { id -> VTimeZone.of(c)?.let { id to it } } }.toMap()

    private val cache = HashMap<String, EventZone?>()

    fun resolve(tzid: String): EventZone? = cache.getOrPut(tzid) { lookup(tzid.trim().removeSurrounding("\"")) }

    private fun lookup(id: String): EventZone? {
        definitions[id]?.let { v -> return EventZone { v.toInstant(it) } }
        for (name in listOfNotNull(id, REGION_CITY.find(id)?.value)) {
            val zone = runCatching { TimeZone.of(name) }.getOrNull() ?: continue
            return EventZone { it.toInstant(zone) }
        }
        return null
    }

    companion object {
        private val REGION_CITY = Regex("[A-Za-z]+/[A-Za-z_\\-]+(/[A-Za-z_\\-]+)?$")
    }
}

/**
 * Un `VTIMEZONE` : ses changements (`STANDARD`, `DAYLIGHT`), chacun avec l'heure locale du premier changement (`DTSTART`,
 * dans l'ancien décalage), les décalages avant et après, et une règle annuelle ou des dates.
 */
internal class VTimeZone(private val observances: List<Observance>) {
    class Observance(val start: LocalDateTime, val from: UtcOffset, val to: UtcOffset, val rule: IcsRule?, val dates: List<LocalDateTime>)

    /** Décalage en vigueur à [instant] : celui du dernier changement passé, sinon l'ancien décalage du premier. */
    fun offsetAt(instant: Instant): UtcOffset {
        val year = instant.toLocalDateTime(TimeZone.UTC).year
        val from = LocalDate(year - 1, 1, 1).atTime(0, 0)
        val to = LocalDate(year + 2, 1, 1).atTime(0, 0)
        val transitions = observances.flatMap { o ->
            val onsets = (o.rule?.expand(o.start, o.rule.until?.let(::untilLocal), from, to) ?: listOf(o.start)) + o.dates
            onsets.map { it.toInstant(o.from) to o.to }
        }.sortedBy { it.first }
        return transitions.lastOrNull { it.first <= instant }?.second
            ?: observances.minByOrNull { it.start }?.from
            ?: UtcOffset.ZERO
    }

    /** Heure locale → instant, en deux passes : décalage estimé à l'heure lue comme UTC, puis corrigé. */
    fun toInstant(local: LocalDateTime): Instant {
        val guess = offsetAt(local.toInstant(UtcOffset.ZERO))
        val first = local.toInstant(guess)
        val corrected = offsetAt(first)
        return if (corrected == guess) first else local.toInstant(corrected)
    }

    private fun untilLocal(value: String): LocalDateTime? = when (val t = IcsTime.parse(value, null, null)) {
        is IcsTime.Day -> t.date.atTime(23, 59, 59)
        is IcsTime.Utc -> t.instant.toLocalDateTime(TimeZone.UTC)
        is IcsTime.Zoned -> t.local
        is IcsTime.Floating -> t.local
        null -> null
    }

    companion object {
        fun of(component: IcsComponent): VTimeZone? {
            val observances = component.children.filter { it.name == "STANDARD" || it.name == "DAYLIGHT" }.mapNotNull { o ->
                val start = (o.first("DTSTART")?.let(IcsTime::of) as? IcsTime.Floating)?.local
                    ?: (o.first("DTSTART")?.let(IcsTime::of) as? IcsTime.Zoned)?.local
                    ?: return@mapNotNull null
                val from = o.first("TZOFFSETFROM")?.value?.let(::offset) ?: return@mapNotNull null
                val to = o.first("TZOFFSETTO")?.value?.let(::offset) ?: return@mapNotNull null
                val rule = o.first("RRULE")?.value?.let { IcsRule.parse(it) ?: return@mapNotNull null }
                val dates = o.all("RDATE").flatMap(IcsTime::list).mapNotNull { (it as? IcsTime.Floating)?.local ?: (it as? IcsTime.Zoned)?.local }
                Observance(start, from, to, rule, dates)
            }
            return observances.takeIf { it.isNotEmpty() }?.let(::VTimeZone)
        }

        /** `+0100`, `-0500`, `+053000`. */
        internal fun offset(value: String): UtcOffset? {
            val s = value.trim()
            if (s.length !in 5..7 || (s[0] != '+' && s[0] != '-')) return null
            val digits = s.substring(1)
            if (!digits.all(Char::isDigit) || (digits.length != 4 && digits.length != 6)) return null
            val sign = if (s[0] == '-') -1 else 1
            val h = digits.substring(0, 2).toInt()
            val m = digits.substring(2, 4).toInt()
            val sec = if (digits.length == 6) digits.substring(4, 6).toInt() else 0
            return runCatching { UtcOffset(hours = sign * h, minutes = sign * m, seconds = sign * sec) }.getOrNull()
        }
    }
}
