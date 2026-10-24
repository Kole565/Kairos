package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Export CSV des statistiques (docs/spec/statistiques.md § Export CSV, issue #113). */
class StatsCsvTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 10, 22)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private fun task(id: Long, type: String) = Task(
        id, "T$id", taskType = type, fibonacciPoints = 3, estimatedMinutes = 60, status = TaskStatus.DONE,
        createdAt = Instant.parse("2026-10-01T08:00:00Z"), updatedAt = Instant.parse("2026-10-20T10:00:00Z"),
    )

    private fun session(id: Long, taskId: Long) =
        Instant.parse("2026-10-20T09:00:00Z").let { WorkSession(id, taskId, it, Instant.parse("2026-10-20T10:30:00Z"), it) }

    private fun csv(query: StatsQuery, tasks: List<Task>, sessions: List<WorkSession> = emptyList()): String {
        val input = query.apply(tasks, sessions, Settings(), today, utc)
        return StatsCsv.encode(query, input, query.dashboard(input, today, now, utc), query.hours(input, now, utc), today, now)
    }

    private fun section(csv: String, name: String): List<String> =
        csv.removePrefix("﻿").split("\r\n\r\n").map { it.split("\r\n") }.single { it.first() == name }.drop(1)

    @Test
    fun cellsAreQuotedAndFormulasNeutralized() {
        assertEquals("plain", StatsCsv.quote("plain"))
        assertEquals("\"a,b\"", StatsCsv.quote("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", StatsCsv.quote("say \"hi\""))
        assertEquals("\"two\nlines\"", StatsCsv.quote("two\nlines"))
        listOf("=1+1", "+1", "-1", "@SUM(A1)", "\tx").forEach { assertEquals("'$it", StatsCsv.text(it)) }
        assertEquals("Dev", StatsCsv.text("Dev"))
        assertEquals("", StatsCsv.text(""))
    }

    @Test
    fun numbersUseADecimalPointAndRoundHalfToEven() {
        assertEquals("1.32", StatsCsv.decimal(1.3249, 2))
        assertEquals("0.0", StatsCsv.decimal(0.0, 1))
        assertEquals("12.5", StatsCsv.decimal(12.5, 1))
        assertEquals("2", StatsCsv.decimal(2.5, 0))
        assertEquals("-0.50", StatsCsv.decimal(-0.5, 2))
    }

    @Test
    fun theFileHasABomCrlfAndEverySectionInScreenOrder() {
        val text = csv(StatsQuery(), listOf(task(1, "Dev")), listOf(session(1, 1)))
        assertTrue(text.startsWith("﻿kairos_stats\r\nkey,value\r\n"))
        assertTrue(text.endsWith("\r\n\r\n"))
        assertTrue('\n' !in text.replace("\r\n", ""))
        val names = text.removePrefix("﻿").removeSuffix("\r\n\r\n").split("\r\n\r\n").map { it.substringBefore("\r\n") }
        assertEquals(
            listOf("kairos_stats", "summary", "throughput", "calibration", "estimation_bias", "time_by_type", "focus", "hours_by_type", "backlog", "completeness"),
            names,
        )
        assertEquals("type,sessions,minutes,reliable,peak_start,peak_share_pct,peak_lift,efficiency_tasks,fastest_start,fastest_gain_pct," + (0..22 step 2).joinToString(",") { "share_" + it.toString().padStart(2, '0') }, section(text, "hours_by_type").first())
    }

    @Test
    fun theContextSaysThePeriodAndTheFilter() {
        val query = StatsQuery(DayFilter(query = "=evil", taskType = "Dev, ops"), weeks = StatsQuery.ALL)
        val context = section(csv(query, listOf(task(1, "Dev, ops"), task(2, "Meeting"))), "kairos_stats")
        assertTrue("period,all" in context)
        assertTrue("filter_query,'=evil" in context)
        assertTrue("filter_type,\"Dev, ops\"" in context)
        // Aucun titre ne contient « =evil » : rien n'est retenu, l'effectif total reste dit.
        assertTrue("tasks_kept,0" in context)
        assertTrue("tasks_total,2" in context)
        // Une période de 4 semaines : la fenêtre commence trois lundis avant celui de la semaine.
        val four = section(csv(StatsQuery(weeks = 4), listOf(task(1, "Dev"))), "kairos_stats")
        assertTrue("period,4" in four)
        assertTrue("window_start,2026-09-28" in four)
        assertTrue("window_end,2026-10-22" in four)
    }

    @Test
    fun anAbsentValueIsAnEmptyCellAndNoTypeIsEmpty() {
        val text = csv(StatsQuery(), listOf(task(1, "")), listOf(session(1, 1)))
        assertEquals(listOf("type,minutes,pct", ",90,100"), section(text, "time_by_type"))
        assertEquals("ratio,real_minutes,estimated_minutes,tasks,reliable", section(text, "estimation_bias").first())
        // Aucune tâche à faire : âge médian absent.
        assertTrue("median_age_days," in section(text, "backlog"))
    }
}
