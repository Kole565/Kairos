package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.engine.Affinity
import com.skohscripts.kairos.core.engine.TimeTracking
import com.skohscripts.kairos.core.engine.minutesBetween
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Quand on travaille vraiment, par type de tâche (docs/spec/planning-personnalise.md § Lot C, étapes C1 et C2). Pur : sessions, tâches,
 * instant, fuseau et réglages en paramètres. [measure] **observe seulement** (carte Statistiques) ; [affinity] en tire, si l'utilisateur l'a activé
 * (C2, désactivé par défaut), la préférence du planning.
 *
 * Les sessions du chrono sont la donnée : le chrono peut être lancé sur n'importe quelle tâche, depuis n'importe quel classement, donc
 * l'heure où l'on a vraiment travaillé est indépendante de l'heure où Kairos l'avait suggérée. Les minutes de chaque session sont
 * réparties entre les tranches de [BUCKET_HOURS] heures qu'elle traverse (heure locale du [TimeZone] passé) ; les sessions de moins de
 * [MIN_SESSION_MINUTES] minutes (démarrage-arrêt accidentel) sont ignorées ; les plus récentes pèsent plus que les plus anciennes de la
 * fenêtre (de 1 à 1/2). La part du type par tranche est comparée à celle de **toutes** les tâches (« lift ») : « je travaille à 10 h »
 * ne doit pas passer pour « les documents, c'est à 10 h ».
 */
object BestHours {
    /** Largeur d'une tranche en heures, et leur nombre dans la journée. */
    const val BUCKET_HOURS = 2
    const val BUCKETS = 24 / BUCKET_HOURS

    /** Une session plus courte n'est pas comptée (démarrage-arrêt accidentel). */
    const val MIN_SESSION_MINUTES = 5

    /** Sous ces effectifs (sessions retenues **et** minutes réelles), le profil d'un type est affiché mais « peu fiable ». */
    const val MIN_RELIABLE_SESSIONS = 5
    const val MIN_RELIABLE_MINUTES = 120

    /** Une tranche ne devient la « tranche de pointe » d'un type que si elle porte au moins cette part de son temps (pondéré). */
    private const val PEAK_MIN_SHARE = 0.2

    /**
     * Le profil d'un type ([type] vide : « sans type »). [share] : part du temps pondéré du type par tranche (somme 1) ; [lift] : cette part
     * divisée par la part de toutes les tâches dans la même tranche (> 1 : le type y est sur-représenté). [peak] : l'indice de la tranche au
     * plus fort lift parmi celles qui portent assez du temps du type, ou `null` quand le type se répartit sans pointe. [sessions] et
     * [minutes] : les effectifs retenus.
     */
    data class Profile(
        val type: String,
        val sessions: Int,
        val minutes: Int,
        val share: List<Double>,
        val lift: List<Double>,
        val peak: Int?,
        val reliable: Boolean,
    )

    /** Les profils, du type le plus chronométré au moins chronométré, et la répartition [global] de tout le temps retenu. */
    data class Result(val profiles: List<Profile>, val global: List<Double>, val sessions: Int) {
        val hasData: Boolean get() = profiles.isNotEmpty()
    }

    /** Les trois forces du réglage (0 Faible, 1 Moyenne, 2 Forte) : le diviseur de l'effort est `1 + force × gain`, gain ∈ [0, 1]. */
    private val STRENGTHS = doubleArrayOf(0.15, 0.3, 0.5)

    /** La force numérique du niveau [level] du réglage (un niveau hors de 0..2 est ramené dans les bornes). */
    fun strengthOf(level: Int): Double = STRENGTHS[level.coerceIn(0, STRENGTHS.lastIndex)]

    /** Une tranche ne compte pour la détection que si elle porte au moins cette part du temps du type (pas de pointe sur du bruit). */
    private const val AFFINITY_MIN_SHARE = 0.1

    /**
     * L'affinité du réglage C2 : `null` si [BestHoursSettings.enabled] est faux (rien ne change). Détection : le gain d'une tranche est son
     * « lift » moins 1, borné à [0, 1], pour les seuls types **fiables** et les tranches qui portent assez de leur temps ; les heures
     * préférées des réglages priment sur la détection.
     */
    fun affinity(settings: Settings, result: Result): Affinity? {
        val best = settings.schedule?.bestHours?.takeIf { it.enabled } ?: return null
        val detected = HashMap<String, List<Double>>()
        for (p in result.profiles) {
            if (!p.reliable || p.type.isEmpty()) continue
            detected[p.type.lowercase()] = List(BUCKETS) { b -> if (p.share[b] >= AFFINITY_MIN_SHARE) (p.lift[b] - 1.0).coerceIn(0.0, 1.0) else 0.0 }
        }
        val preferred = HashMap<String, Int>()
        for ((type, hour) in best.preferred) if (hour in 0..23 && type.isNotBlank()) preferred[type.trim().lowercase()] = hour
        return Affinity(strengthOf(best.strength), detected, preferred, BUCKET_HOURS)
    }

    /** Début de la tranche [bucket], en heures (0, 2, 4…). */
    fun startHour(bucket: Int): Int = bucket * BUCKET_HOURS

    /** Les tranches de l'heure locale `hour` (0 à 23). */
    fun bucketOf(hour: Int): Int = hour / BUCKET_HOURS

    /**
     * Mesure sur la fenêtre des Statistiques (`statsWindowWeeks` semaines, jusqu'à aujourd'hui) : [tasks] donne le type de chaque session.
     */
    fun measure(tasks: List<Task>, sessions: List<WorkSession>, now: Instant, settings: Settings, timeZone: TimeZone): Result {
        val today = now.toLocalDateTime(timeZone).date
        val weeks = maxOf(1, settings.statsWindowWeeks)
        val windowStart = TaskStats.monday(today).minus(DatePeriod(days = 7 * (weeks - 1)))
        val windowDays = maxOf(1, windowStart.daysUntil(today) + 1)
        val typeById = tasks.associate { it.id to it.taskType.trim() }

        val weighted = LinkedHashMap<String, DoubleArray>()
        val sessionCount = LinkedHashMap<String, Int>()
        val minutesCount = LinkedHashMap<String, Int>()
        for (s in TimeTracking.sessionsInRange(sessions, windowStart, today, timeZone)) {
            val minutes = TimeTracking.sessionMinutes(s, now)
            if (minutes < MIN_SESSION_MINUTES) continue
            val start = s.startedAt.toLocalDateTime(timeZone)
            val end = (s.endedAt ?: now).toLocalDateTime(timeZone)
            // Plus récente = plus lourde : de 1 (aujourd'hui) à 1/2 (premier jour de la fenêtre).
            val weight = 1.0 - 0.5 * minOf(1.0, start.date.daysUntil(today).toDouble() / windowDays)
            val type = typeById[s.taskId] ?: ""
            val bins = weighted.getOrPut(type) { DoubleArray(BUCKETS) }
            splitIntoBuckets(start, end).forEachIndexed { bucket, m -> bins[bucket] += m * weight }
            sessionCount[type] = (sessionCount[type] ?: 0) + 1
            minutesCount[type] = (minutesCount[type] ?: 0) + minutes
        }
        if (weighted.isEmpty()) return Result(emptyList(), List(BUCKETS) { 0.0 }, 0)

        val globalTotals = DoubleArray(BUCKETS)
        weighted.values.forEach { bins -> bins.forEachIndexed { i, v -> globalTotals[i] += v } }
        val globalSum = globalTotals.sum()
        val global = globalTotals.map { it / globalSum }

        val profiles = weighted.map { (type, bins) ->
            val sum = bins.sum()
            val share = bins.map { it / sum }
            val lift = share.mapIndexed { i, s -> if (global[i] > 0) s / global[i] else 0.0 }
            val peak = share.indices.filter { share[it] >= PEAK_MIN_SHARE }.maxByOrNull { lift[it] }
            val sessionsKept = sessionCount.getValue(type)
            val minutesKept = minutesCount.getValue(type)
            Profile(type, sessionsKept, minutesKept, share, lift, peak, reliable = sessionsKept >= MIN_RELIABLE_SESSIONS && minutesKept >= MIN_RELIABLE_MINUTES)
        }.sortedWith(compareByDescending<Profile> { it.minutes }.thenBy { it.type })
        return Result(profiles, global, sessionCount.values.sum())
    }

    /** Les minutes de la session [start]..[end] (heure locale) réparties entre les tranches qu'elle traverse. */
    private fun splitIntoBuckets(start: LocalDateTime, end: LocalDateTime): DoubleArray {
        val out = DoubleArray(BUCKETS)
        var cursor = start
        while (cursor < end) {
            val bucket = bucketOf(cursor.hour)
            val boundaryHour = startHour(bucket) + BUCKET_HOURS
            val boundary = if (boundaryHour >= 24) cursor.date.plus(DatePeriod(days = 1)).atTime(0, 0) else cursor.date.atTime(boundaryHour, 0)
            val segmentEnd = minOf(end, boundary)
            out[bucket] += minutesBetween(cursor, segmentEnd).toDouble()
            cursor = segmentEnd
        }
        return out
    }
}
