package com.skohscripts.kairos.core.calendar

import com.skohscripts.kairos.core.engine.Recurrence
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Règle de répétition iCalendar (`RRULE`), dans le sous-ensemble que Kairos sait déplier
 * (docs/spec/connexions-externes.md § Lecture iCalendar). [until] est la valeur brute de `UNTIL` (date, heure locale ou UTC) :
 * l'appelant la ramène au fuseau de l'événement.
 */
data class IcsRule(
    val freq: Freq,
    val interval: Int = 1,
    val count: Int? = null,
    val until: String? = null,
    val byDay: List<WeekdayNum> = emptyList(),
    val byMonthDay: List<Int> = emptyList(),
    val byMonth: List<Int> = emptyList(),
    val bySetPos: List<Int> = emptyList(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
) {
    enum class Freq { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** Un jour de `BYDAY` : `TU`, `2TU`, `-1FR` ([ordinal] nul = tous ces jours de la période). */
    data class WeekdayNum(val ordinal: Int?, val day: DayOfWeek)

    /**
     * Débuts des occurrences, dans l'ordre, en heure locale de l'événement : [start] (`DTSTART`, toujours la première),
     * puis celles de la règle, jusqu'à [untilLocal] inclus et avant [to] (exclu). Sans `COUNT`, le calcul part de la
     * période qui précède [from] (une série commencée il y a dix ans ne coûte rien) ; avec `COUNT`, il compte depuis
     * [start]. Au plus [MAX_OCCURRENCES] débuts.
     */
    fun expand(start: LocalDateTime, untilLocal: LocalDateTime?, from: LocalDateTime, to: LocalDateTime): List<LocalDateTime> {
        val out = mutableListOf<LocalDateTime>()
        var produced = 0
        fun accept(t: LocalDateTime): Boolean {
            if (untilLocal != null && t > untilLocal) return false
            if (t >= to) return false
            produced++
            out += t
            return count == null || produced < count
        }
        if (!accept(start) || out.size >= MAX_OCCURRENCES) return out
        val first = if (count == null) skipTo(start.date, from.date) else 0L
        var k = first
        var periods = 0
        while (periods++ < MAX_PERIODS && out.size < MAX_OCCURRENCES) {
            val anchor = periodStart(start.date, k * interval)
            if (anchor.atTime(0, 0) >= to) break
            if (untilLocal != null && anchor.atTime(0, 0) > untilLocal) break
            for (day in candidates(anchor, start.date)) {
                val t = day.atTime(start.time)
                if (t <= start) continue
                if (!accept(t)) return out
            }
            k++
        }
        return out
    }

    /** Index de période (en unités de [interval]) juste avant [from] : 0 si [from] précède le début. */
    private fun skipTo(start: LocalDate, from: LocalDate): Long {
        if (from <= start) return 0
        val units: Long = when (freq) {
            Freq.DAILY -> start.daysUntil(from).toLong()
            Freq.WEEKLY -> start.daysUntil(from).toLong() / 7
            Freq.MONTHLY -> monthIndex(from) - monthIndex(start)
            Freq.YEARLY -> (from.year - start.year).toLong()
        }
        return (units / interval - 1).coerceAtLeast(0)
    }

    /** Premier jour de la période décalée de [units] périodes depuis celle de [start]. */
    private fun periodStart(start: LocalDate, units: Long): LocalDate = when (freq) {
        Freq.DAILY -> start.plus(DatePeriod(days = units.toInt()))
        Freq.WEEKLY -> weekStartOf(start).plus(DatePeriod(days = (units * 7).toInt()))
        Freq.MONTHLY -> monthFirst(monthIndex(start) + units)
        Freq.YEARLY -> LocalDate((start.year + units).toInt(), 1, 1)
    }

    private fun weekStartOf(d: LocalDate): LocalDate =
        d.minus(DatePeriod(days = (d.dayOfWeek.isoDayNumber - weekStart.isoDayNumber + 7) % 7))

    /** Jours de la période commençant à [anchor], triés, `BYSETPOS` appliqué. */
    private fun candidates(anchor: LocalDate, start: LocalDate): List<LocalDate> {
        val days: List<LocalDate> = when (freq) {
            Freq.DAILY -> listOf(anchor).filter { monthOk(it) && monthDayOk(it) && weekdayOk(it) }
            Freq.WEEKLY -> {
                val wanted = byDay.map { it.day }.ifEmpty { listOf(start.dayOfWeek) }
                (0 until 7).map { anchor.plus(DatePeriod(days = it)) }.filter { it.dayOfWeek in wanted && monthOk(it) }
            }
            Freq.MONTHLY -> if (monthOk(anchor)) inMonth(anchor, start) else emptyList()
            Freq.YEARLY -> {
                val months = byMonth.ifEmpty { listOf(start.month.ordinal + 1) }
                if (byMonth.isEmpty() && byDay.any { it.ordinal != null } && byMonthDay.isEmpty()) {
                    inYear(anchor.year)
                } else {
                    months.sorted().flatMap { m -> inMonth(LocalDate(anchor.year, m, 1), start) }
                }
            }
        }
        val sorted = days.distinct().sorted()
        if (bySetPos.isEmpty()) return sorted
        return bySetPos.mapNotNull { p -> if (p > 0) sorted.getOrNull(p - 1) else sorted.getOrNull(sorted.size + p) }.distinct().sorted()
    }

    /** Jours du mois de [first] : `BYMONTHDAY` et/ou `BYDAY` (rang dans le mois), sinon le jour du mois de [start]. */
    private fun inMonth(first: LocalDate, start: LocalDate): List<LocalDate> {
        val length = Recurrence.daysInMonth(first.year, first.month.ordinal + 1)
        val all = (1..length).map { LocalDate(first.year, first.month, it) }
        val byMd = if (byMonthDay.isEmpty()) null else byMonthDay.mapNotNull { d ->
            val day = if (d > 0) d else length + 1 + d
            if (day in 1..length) day else null
        }.toSet()
        val byWd = if (byDay.isEmpty()) null else byDay.flatMap { wd -> nth(all, wd) }.toSet()
        return when {
            byMd != null && byWd != null -> all.filter { it.day in byMd && it in byWd }
            byMd != null -> all.filter { it.day in byMd }
            byWd != null -> all.filter { it in byWd }
            // Le 31 dans un mois de 30 jours : pas d'occurrence (RFC 5545), jamais ramené au 30.
            else -> all.filter { it.day == start.day }
        }
    }

    /** `BYDAY` à rang sans `BYMONTH` en annuel : rang dans l'année. */
    private fun inYear(year: Int): List<LocalDate> {
        val first = LocalDate(year, 1, 1)
        val all = (0 until first.daysUntil(LocalDate(year + 1, 1, 1))).map { first.plus(DatePeriod(days = it)) }
        return byDay.flatMap { nth(all, it) }
    }

    private fun nth(days: List<LocalDate>, wd: WeekdayNum): List<LocalDate> {
        val matching = days.filter { it.dayOfWeek == wd.day }
        val n = wd.ordinal ?: return matching
        return listOfNotNull(if (n > 0) matching.getOrNull(n - 1) else matching.getOrNull(matching.size + n))
    }

    private fun monthOk(d: LocalDate) = byMonth.isEmpty() || (d.month.ordinal + 1) in byMonth

    private fun monthDayOk(d: LocalDate): Boolean {
        if (byMonthDay.isEmpty()) return true
        val length = Recurrence.daysInMonth(d.year, d.month.ordinal + 1)
        return byMonthDay.any { md -> if (md > 0) d.day == md else d.day == length + 1 + md }
    }

    private fun weekdayOk(d: LocalDate) = byDay.isEmpty() || byDay.any { it.day == d.dayOfWeek }

    companion object {
        /** Débuts calculés au plus par série (un `DAILY` sur 6 mois en fait 184). */
        const val MAX_OCCURRENCES = 2_000

        /** Périodes parcourues au plus : une règle qui ne produit jamais rien (31 février) s'arrête. */
        private const val MAX_PERIODS = 20_000

        private val DAYS = mapOf(
            "MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
            "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY,
        )

        /** La règle, ou `null` si une partie n'est pas reconnue (« répétition non reconnue », jamais à moitié comprise). */
        fun parse(value: String): IcsRule? {
            val parts = value.split(';').filter { it.isNotBlank() }.associate { p ->
                val eq = p.indexOf('=')
                if (eq <= 0) return null
                p.substring(0, eq).trim().uppercase() to p.substring(eq + 1).trim()
            }
            val freq = when (parts["FREQ"]?.uppercase()) {
                "DAILY" -> Freq.DAILY
                "WEEKLY" -> Freq.WEEKLY
                "MONTHLY" -> Freq.MONTHLY
                "YEARLY" -> Freq.YEARLY
                else -> return null
            }
            val known = setOf("FREQ", "INTERVAL", "COUNT", "UNTIL", "BYDAY", "BYMONTHDAY", "BYMONTH", "BYSETPOS", "WKST")
            if (parts.keys.any { it !in known }) return null
            fun ints(key: String, range: IntRange): List<Int>? = parts[key]?.split(',')?.map { s ->
                val n = s.trim().toIntOrNull() ?: return null
                if (n == 0 || kotlin.math.abs(n) !in range) return null
                n
            }
            val interval = parts["INTERVAL"]?.let { it.toIntOrNull()?.takeIf { n -> n in 1..1000 } ?: return null } ?: 1
            val count = parts["COUNT"]?.let { it.toIntOrNull()?.takeIf { n -> n >= 1 } ?: return null }
            val byDay = parts["BYDAY"]?.split(',')?.map { raw ->
                val s = raw.trim().uppercase()
                val day = DAYS[s.takeLast(2)] ?: return null
                val num = s.dropLast(2)
                val ordinal = if (num.isEmpty()) null else num.toIntOrNull()?.takeIf { it != 0 && kotlin.math.abs(it) <= 53 } ?: return null
                // Un rang n'a de sens qu'en mensuel ou annuel.
                if (ordinal != null && freq != Freq.MONTHLY && freq != Freq.YEARLY) return null
                WeekdayNum(ordinal, day)
            }.orEmpty()
            val byMonthDay = ints("BYMONTHDAY", 1..31) ?: if ("BYMONTHDAY" in parts) return null else emptyList()
            val byMonth = (ints("BYMONTH", 1..12) ?: if ("BYMONTH" in parts) return null else emptyList()).also { m -> if (m.any { it < 0 }) return null }
            val bySetPos = ints("BYSETPOS", 1..366) ?: if ("BYSETPOS" in parts) return null else emptyList()
            val wkst = parts["WKST"]?.let { DAYS[it.uppercase()] ?: return null } ?: DayOfWeek.MONDAY
            return IcsRule(freq, interval, count, parts["UNTIL"], byDay, byMonthDay, byMonth, bySetPos, wkst)
        }

        private fun monthIndex(d: LocalDate): Long = d.year * 12L + d.month.ordinal

        private fun monthFirst(index: Long): LocalDate = LocalDate(index.floorDiv(12).toInt(), index.mod(12) + 1, 1)
    }
}
