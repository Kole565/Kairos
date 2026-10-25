package com.skohscripts.kairos.core.calendar

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Un créneau à créer, en heures locales de l'appareil, jamais à cheval sur minuit. [uid] : `UID` de l'événement (le même pour
 * toutes les occurrences d'une série), dérivé du début et du titre s'il manque.
 */
data class PlannedEvent(val start: LocalDateTime, val end: LocalDateTime, val title: String, val uid: String)

/** Une journée entière (ou plusieurs) : badge sans blocage, du [start] au [end] inclus. */
data class PlannedDay(val start: LocalDate, val end: LocalDate, val title: String, val uid: String)

/** Pourquoi un événement du fichier n'est pas importé (compté par événement, pas par occurrence). */
enum class SkipReason { FREE, CANCELLED, PAST, BEYOND_HORIZON, UNSUPPORTED_RULE, UNKNOWN_ZONE, INVALID, TOO_MANY }

/**
 * Ce que l'import d'un fichier `.ics` ferait (docs/spec/connexions-externes.md § Lecture iCalendar), sans rien écrire :
 * [name] = `X-WR-CALNAME` ; [unknownZones] = les `TZID` que Kairos n'a pas su lire (leurs événements sont comptés
 * [SkipReason.UNKNOWN_ZONE]).
 */
data class CalendarPlan(
    val name: String?,
    val events: List<PlannedEvent>,
    val days: List<PlannedDay>,
    val skipped: Map<SkipReason, Int>,
    val unknownZones: Set<String> = emptySet(),
) {
    val first: LocalDate? get() = (events.map { it.start.date } + days.map { it.start }).minOrNull()
    val last: LocalDate? get() = (events.map { it.end.date } + days.map { it.end }).maxOrNull()
    val isEmpty: Boolean get() = events.isEmpty() && days.isEmpty()
}

object CalendarImport {
    /** Horizon du dépliage des répétitions : d'aujourd'hui à 6 mois. */
    const val HORIZON_MONTHS = 6

    /** Au plus autant de créneaux, et autant de badges, par import ; au-delà, les plus lointains sont écartés. */
    const val MAX_EVENTS = 10_000

    /** Longueur gardée d'un titre (une ligne). */
    const val MAX_TITLE = 200

    /**
     * Plan d'import de [text] pour [today] dans [deviceZone].
     * @throws IllegalArgumentException si le texte n'est pas un calendrier.
     */
    fun plan(text: String, today: LocalDate, deviceZone: TimeZone): CalendarPlan {
        val calendar = IcsParser.parse(text)
        val zones = IcsZones(calendar)
        val name = calendar.first("X-WR-CALNAME")?.let { title(it.value) }?.takeIf { it.isNotEmpty() }
        val horizonStart = today.atTime(0, 0)
        val horizonEnd = today.plus(DatePeriod(months = HORIZON_MONTHS)).atTime(0, 0)
        val acc = Accumulator()
        val all = calendar.children("VEVENT")
        val overrides = all.filter { it.first("RECURRENCE-ID") != null }
        val masterUids = all.filter { it.first("RECURRENCE-ID") == null }.mapNotNull { it.first("UID")?.value }.toSet()
        val context = Context(zones, deviceZone, horizonStart, horizonEnd)
        for (event in all) {
            val uid = event.first("UID")?.value
            val isOverride = event.first("RECURRENCE-ID") != null
            // Une occurrence déplacée ou annulée d'une série connue : traitée ici comme un événement seul, et retirée de sa série.
            val moved = if (!isOverride && uid != null) overrides.filter { it.first("UID")?.value == uid } else emptyList()
            if (isOverride && uid != null && uid in masterUids && event.status() == "CANCELLED") continue
            importEvent(event, moved, context, acc)
        }
        return acc.result(name)
    }

    private class Context(val zones: IcsZones, val device: TimeZone, val from: LocalDateTime, val to: LocalDateTime)

    private class Accumulator {
        val events = mutableListOf<PlannedEvent>()
        val days = mutableListOf<PlannedDay>()
        val skipped = LinkedHashMap<SkipReason, Int>()
        val unknownZones = LinkedHashSet<String>()

        fun skip(reason: SkipReason, n: Int = 1) {
            skipped[reason] = (skipped[reason] ?: 0) + n
        }

        fun result(name: String?): CalendarPlan {
            val sortedEvents = events.distinct().sortedWith(compareBy({ it.start }, { it.end }, { it.title }, { it.uid }))
            val sortedDays = days.distinct().sortedWith(compareBy({ it.start }, { it.end }, { it.title }, { it.uid }))
            val tooMany = (sortedEvents.size - MAX_EVENTS).coerceAtLeast(0) + (sortedDays.size - MAX_EVENTS).coerceAtLeast(0)
            if (tooMany > 0) skip(SkipReason.TOO_MANY, tooMany)
            return CalendarPlan(name, sortedEvents.take(MAX_EVENTS), sortedDays.take(MAX_EVENTS), skipped.toMap(), unknownZones.toSet())
        }
    }

    private fun IcsComponent.status(): String = first("STATUS")?.value?.trim()?.uppercase().orEmpty()

    private fun importEvent(event: IcsComponent, moved: List<IcsComponent>, ctx: Context, acc: Accumulator) {
        if (event.status() == "CANCELLED") return acc.skip(SkipReason.CANCELLED)
        val start = event.first("DTSTART")?.let(IcsTime::of) ?: return acc.skip(SkipReason.INVALID)
        val title = event.first("SUMMARY")?.let { title(it.value) }.orEmpty()
        // Sans UID (fichier fait à la main) : un identifiant tiré du début et du titre, stable d'un import à l'autre.
        val uid = event.first("UID")?.value?.trim()?.takeIf { it.isNotEmpty() } ?: "${event.first("DTSTART")!!.value.trim()}/$title"
        val ruleText = event.first("RRULE")?.value
        val rule = ruleText?.let { IcsRule.parse(it) ?: return acc.skip(SkipReason.UNSUPPORTED_RULE) }
        if (start is IcsTime.Day) return importDay(event, start.date, rule, title, uid, moved, ctx, acc)

        // Libre : transparent, ou « libre » / « travaille ailleurs » d'Outlook. Provisoire et absent restent occupés.
        val busyStatus = event.first("X-MICROSOFT-CDO-BUSYSTATUS")?.value?.trim()?.uppercase()
        val free = event.first("TRANSP")?.value?.trim().equals("TRANSPARENT", ignoreCase = true) || busyStatus == "FREE" || busyStatus == "WORKINGELSEWHERE"
        if (free) return acc.skip(SkipReason.FREE)

        // Fuseau de l'événement : celui de DTSTART (UTC et flottant compris) ; inconnu → écarté, jamais deviné.
        val (zone, local) = when (start) {
            is IcsTime.Utc -> EventZone { it.toInstant(TimeZone.UTC) } to start.instant.toLocalDateTime(TimeZone.UTC)
            is IcsTime.Floating -> EventZone { it.toInstant(ctx.device) } to start.local
            is IcsTime.Zoned -> (ctx.zones.resolve(start.tzid) ?: return unknown(start.tzid, acc)) to start.local
            is IcsTime.Day -> return
        }
        val startInstant = zone.toInstant(local)
        val duration = duration(event, startInstant, ctx) ?: return acc.skip(SkipReason.INVALID)
        if (duration <= Duration.ZERO) return acc.skip(SkipReason.INVALID)

        val excluded = HashSet<LocalDateTime>()
        for (p in event.all("EXDATE")) for (t in IcsTime.list(p)) localIn(t, zone, ctx)?.let(excluded::add)
        for (m in moved) m.first("RECURRENCE-ID")?.let(IcsTime::of)?.let { localIn(it, zone, ctx) }?.let(excluded::add)

        // Occurrences en heure locale du fuseau de l'événement, avec une marge d'un jour de chaque côté de l'horizon.
        val margin = DatePeriod(days = 2)
        val fromLocal = ctx.from.date.minus(margin).atTime(0, 0)
        val toLocal = ctx.to.date.plus(margin).atTime(0, 0)
        val ruled = rule?.expand(local, rule.until?.let { untilIn(it, zone, ctx) }, fromLocal, toLocal) ?: listOf(local)
        // RDATE : des débuts de plus, dans le fuseau de l'événement.
        val extra = event.all("RDATE").flatMap(IcsTime::list).mapNotNull { localIn(it, zone, ctx) }
        val starts = (ruled + extra).distinct().sorted()
        var kept = 0
        var later = false
        for (s in starts) {
            if (s in excluded) continue
            val begin = zone.toInstant(s)
            val end = begin + duration
            val startLocal = begin.toLocalDateTime(ctx.device)
            val endLocal = end.toLocalDateTime(ctx.device)
            if (endLocal <= ctx.from) continue
            if (startLocal >= ctx.to) { later = true; continue }
            // Les morceaux d'avant aujourd'hui ne sont pas importés : réimporter remplace tout ce qui commence à partir d'aujourd'hui.
            for ((a, b) in splitAtMidnight(startLocal, endLocal)) if (a >= ctx.from) acc.events += PlannedEvent(a, b, title, uid)
            kept++
        }
        // Rien dans l'horizon : « au-delà » si une occurrence tombe après, sinon « passé ».
        if (kept == 0) acc.skip(if (later) SkipReason.BEYOND_HORIZON else SkipReason.PAST)
    }

    /**
     * Un morceau par jour : la vue Semaine range un créneau à son jour de début, la vue Jour le retient s'il chevauche la
     * journée. Une fin à minuit pile n'entame pas le lendemain.
     */
    internal fun splitAtMidnight(start: LocalDateTime, end: LocalDateTime): List<Pair<LocalDateTime, LocalDateTime>> {
        val out = mutableListOf<Pair<LocalDateTime, LocalDateTime>>()
        var from = start
        while (from < end) {
            val midnight = from.date.plus(DatePeriod(days = 1)).atTime(0, 0)
            val to = if (end < midnight) end else midnight
            out += from to to
            from = to
        }
        return out
    }

    /**
     * Journée entière : un badge, libre ou occupée (choix du propriétaire, 2026-10-24 : elle ne bloque rien ; Google marque
     * ses journées entières « disponible » par défaut, les écarter perdrait congés et déplacements).
     */
    private fun importDay(event: IcsComponent, start: LocalDate, rule: IcsRule?, title: String, uid: String, moved: List<IcsComponent>, ctx: Context, acc: Accumulator) {
        val endProperty = event.first("DTEND")?.let(IcsTime::of)
        val days: Int = when {
            endProperty is IcsTime.Day -> (endProperty.date.toEpochDays() - start.toEpochDays()).toInt()
            event.first("DURATION") != null -> icsDuration(event.first("DURATION")!!.value)?.inWholeDays?.toInt() ?: return acc.skip(SkipReason.INVALID)
            else -> 1
        }.coerceAtLeast(1)
        val excluded = HashSet<LocalDate>()
        for (p in event.all("EXDATE")) for (t in IcsTime.list(p)) dateOf(t, ctx)?.let(excluded::add)
        for (m in moved) m.first("RECURRENCE-ID")?.let(IcsTime::of)?.let { dateOf(it, ctx) }?.let(excluded::add)
        val today = ctx.from.date
        val horizonEnd = ctx.to.date
        val starts = rule?.expand(start.atTime(0, 0), rule.until?.let { u -> IcsTime.parse(u, null, null)?.let { dateOf(it, ctx) }?.atTime(0, 0) }, today.minus(DatePeriod(days = days)).atTime(0, 0), horizonEnd.atTime(0, 0))
            ?.map { it.date } ?: listOf(start)
        var kept = 0
        var later = false
        for (s in starts) {
            if (s in excluded) continue
            val last = s.plus(DatePeriod(days = days - 1))
            if (last < today) continue
            if (s >= horizonEnd) { later = true; continue }
            acc.days += PlannedDay(s, last, title, uid)
            kept++
        }
        if (kept == 0) acc.skip(if (later) SkipReason.BEYOND_HORIZON else SkipReason.PAST)
    }

    private fun unknown(tzid: String, acc: Accumulator) {
        acc.unknownZones += tzid
        acc.skip(SkipReason.UNKNOWN_ZONE)
    }

    /** Durée : `DTEND` (dans son propre fuseau), sinon `DURATION`, sinon nulle (événement instantané, écarté). */
    private fun duration(event: IcsComponent, startInstant: Instant, ctx: Context): Duration? {
        event.first("DTEND")?.let(IcsTime::of)?.let { end ->
            val endInstant = when (end) {
                is IcsTime.Utc -> end.instant
                is IcsTime.Floating -> end.local.toInstant(ctx.device)
                is IcsTime.Zoned -> ctx.zones.resolve(end.tzid)?.toInstant(end.local) ?: return null
                is IcsTime.Day -> end.date.atTime(0, 0).toInstant(ctx.device)
            }
            return endInstant - startInstant
        }
        event.first("DURATION")?.let { return icsDuration(it.value) }
        return Duration.ZERO
    }

    /** Une date d'`EXDATE` / `RECURRENCE-ID` en heure locale du fuseau de l'événement (une date seule : minuit). */
    private fun localIn(t: IcsTime, zone: EventZone, ctx: Context): LocalDateTime? = when (t) {
        is IcsTime.Zoned -> {
            val own = ctx.zones.resolve(t.tzid) ?: return null
            val instant = own.toInstant(t.local)
            // Ramener l'instant à l'heure locale de l'événement : chercher l'heure locale qui donne cet instant.
            sameInstantLocal(instant, zone, t.local)
        }
        is IcsTime.Utc -> sameInstantLocal(t.instant, zone, t.instant.toLocalDateTime(TimeZone.UTC))
        is IcsTime.Floating -> t.local
        is IcsTime.Day -> t.date.atTime(0, 0)
    }

    /**
     * L'heure locale du fuseau [zone] qui correspond à [instant]. [EventZone] ne convertit que dans un sens : on corrige
     * [guess] par l'écart observé (deux passes suffisent, les décalages variant d'une heure au plus).
     */
    private fun sameInstantLocal(instant: Instant, zone: EventZone, guess: LocalDateTime): LocalDateTime {
        var local = guess
        repeat(3) {
            val diff = instant - zone.toInstant(local)
            if (diff == Duration.ZERO) return local
            local = local.toInstant(TimeZone.UTC).plus(diff).toLocalDateTime(TimeZone.UTC)
        }
        return local
    }

    private fun untilIn(value: String, zone: EventZone, ctx: Context): LocalDateTime? = when (val t = IcsTime.parse(value, null, null)) {
        is IcsTime.Day -> t.date.atTime(23, 59, 59)
        null -> null
        else -> localIn(t, zone, ctx)
    }

    private fun dateOf(t: IcsTime, ctx: Context): LocalDate? = when (t) {
        is IcsTime.Day -> t.date
        is IcsTime.Floating -> t.local.date
        is IcsTime.Zoned -> t.local.date
        is IcsTime.Utc -> t.instant.toLocalDateTime(ctx.device).date
    }

    /** Titre d'une ligne : échappements défaits, espaces et retours à la ligne réduits, [MAX_TITLE] caractères au plus. */
    internal fun title(value: String): String =
        IcsParser.text(value).replace(Regex("\\s+"), " ").trim().take(MAX_TITLE)
}
