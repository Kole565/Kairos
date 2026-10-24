package com.skohscripts.kairos.core.stats

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Instant

/**
 * Export CSV de l'écran Stats (docs/spec/statistiques.md § Export CSV, issue #113) : une section par carte, à partir des
 * objets déjà affichés (rien n'est recalculé). Pur. UTF-8 avec BOM, CRLF, virgule ; nombres au point décimal, dates ISO,
 * en-têtes anglais stables : le fichier ne dépend pas de la langue de l'interface.
 */
object StatsCsv {
    const val FORMAT = "kairos-stats"
    const val FORMAT_VERSION = 1

    fun encode(
        query: StatsQuery,
        input: StatsQuery.Input,
        dashboard: TaskStats.Dashboard,
        hours: BestHours.Result,
        today: LocalDate,
        now: Instant,
    ): String {
        val out = StringBuilder("﻿")
        fun section(name: String, header: List<String>, rows: List<List<String>>) {
            line(out, listOf(name))
            line(out, header)
            rows.forEach { line(out, it) }
            out.append("\r\n")
        }
        val weeks = dashboard.windowWeeks
        val filter = query.filter
        section(
            "kairos_stats", listOf("key", "value"),
            listOf(
                listOf("format", FORMAT),
                listOf("format_version", FORMAT_VERSION.toString()),
                listOf("generated_at", now.toString()),
                listOf("today", today.toString()),
                listOf("period", if (input.all) "all" else weeks.toString()),
                listOf("window_weeks", weeks.toString()),
                listOf("window_start", TaskStats.monday(today).minus(DatePeriod(days = 7 * (weeks - 1))).toString()),
                listOf("window_end", today.toString()),
                listOf("filter_query", text(filter.query.trim())),
                listOf("filter_priority", filter.priority?.toString().orEmpty()),
                listOf("filter_project", text(filter.project.orEmpty())),
                listOf("filter_type", text(filter.taskType.orEmpty())),
                listOf("filter_points", filter.points?.toString().orEmpty()),
                listOf("tasks_kept", input.tasks.size.toString()),
                listOf("tasks_total", input.total.toString()),
            ),
        )
        val flow = dashboard.flow
        section(
            "summary", listOf("metric", "value"),
            listOf(
                listOf("completed", dashboard.completedInWindow.toString()),
                listOf("tracked_minutes", dashboard.trackedMinutesWindow.toString()),
                listOf("median_completion_days", flow.completionDelayDays?.toString().orEmpty()),
                listOf("deadlines_met_pct", flow.deadlineHitPct?.toString().orEmpty()),
                listOf("deadlines_total", flow.deadlineTotal.toString()),
            ),
        )
        section(
            "throughput", listOf("week_start", "completed", "points"),
            dashboard.throughput.map { listOf(it.weekStart.toString(), it.completed.toString(), it.points.toString()) },
        )
        section(
            "calibration", listOf("points", "tasks", "median_minutes", "reliable"),
            dashboard.calibration.map { listOf(it.key, it.count.toString(), it.medianMinutes?.toString().orEmpty(), it.reliable.toString()) },
        )
        section(
            "estimation_bias", listOf("ratio", "real_minutes", "estimated_minutes", "tasks", "reliable"),
            listOfNotNull(dashboard.bias?.let { listOf(decimal(it.ratio, 2), it.realMinutes.toString(), it.estimatedMinutes.toString(), it.count.toString(), it.reliable.toString()) }),
        )
        section(
            "time_by_type", listOf("type", "minutes", "pct"),
            dashboard.timeByType.map { listOf(text(it.key), it.minutes.toString(), it.pct.toString()) },
        )
        val focus = dashboard.focus
        section(
            "focus", listOf("sessions", "total_minutes", "avg_session_minutes"),
            listOf(listOf(focus.sessionCount.toString(), focus.totalMinutes.toString(), focus.avgSessionMinutes?.toString().orEmpty())),
        )
        val slots = (0 until BestHours.BUCKETS).map { "share_" + BestHours.startHour(it).toString().padStart(2, '0') }
        section(
            "hours_by_type",
            listOf("type", "sessions", "minutes", "reliable", "peak_start", "peak_share_pct", "peak_lift", "efficiency_tasks", "fastest_start", "fastest_gain_pct") + slots,
            hours.profiles.map { p ->
                val peak = p.peak
                listOf(
                    text(p.type), p.sessions.toString(), p.minutes.toString(), p.reliable.toString(),
                    peak?.let { BestHours.startHour(it).toString().padStart(2, '0') + ":00" }.orEmpty(),
                    peak?.let { decimal(p.share[it] * 100, 1) }.orEmpty(),
                    peak?.let { decimal(p.lift[it], 2) }.orEmpty(),
                    p.efficiencyTasks.toString(),
                    p.fastest?.let { BestHours.startHour(it).toString().padStart(2, '0') + ":00" }.orEmpty(),
                    p.fastest?.let { decimal((p.efficiency[it]!! - 1) * 100, 1) }.orEmpty(),
                ) + p.share.map { decimal(it * 100, 1) }
            },
        )
        section(
            "backlog", listOf("metric", "value"),
            listOf(
                listOf("open", flow.openCount.toString()),
                listOf("median_age_days", flow.medianAgeDays?.toString().orEmpty()),
                listOf("overdue", flow.overdueCount.toString()),
                listOf("stale", flow.staleCount.toString()),
            ),
        )
        val c = dashboard.completeness
        section(
            "completeness", listOf("field", "with", "total", "pct"),
            listOf(
                listOf("points", c.withPoints.toString(), c.total.toString(), c.pointsPct.toString()),
                listOf("estimate", c.withEstimate.toString(), c.total.toString(), c.estimatePct.toString()),
                listOf("type", c.withType.toString(), c.total.toString(), c.typePct.toString()),
            ),
        )
        return out.toString()
    }

    private fun line(out: StringBuilder, cells: List<String>) {
        cells.joinTo(out, ",") { quote(it) }
        out.append("\r\n")
    }

    /** Guillemets RFC 4180 si la cellule contient une virgule, un guillemet ou un saut de ligne (guillemets doublés). */
    internal fun quote(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + cell.replace("\"", "\"\"") + "\"" else cell

    /**
     * Un texte saisi par l'utilisateur (type, filtre) : apostrophe en tête s'il commence comme une formule de tableur
     * (`=`, `+`, `-`, `@`, tabulation, CR), sans quoi « =HYPERLINK(…) » s'exécuterait à l'ouverture (« CSV injection »).
     */
    internal fun text(value: String): String =
        if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value

    /** [x] à [places] décimales, point décimal quelle que soit la langue, arrondi au pair (comme `pyRound`). */
    internal fun decimal(x: Double, places: Int): String {
        var factor = 1L
        repeat(places) { factor *= 10 }
        val scaled = round(x * factor).toLong()
        val whole = abs(scaled) / factor
        val fraction = (abs(scaled) % factor).toString().padStart(places, '0')
        val sign = if (scaled < 0) "-" else ""
        return if (places == 0) "$sign$whole" else "$sign$whole.$fraction"
    }
}
