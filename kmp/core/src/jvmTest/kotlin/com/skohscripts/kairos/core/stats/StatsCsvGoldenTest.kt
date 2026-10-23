package com.skohscripts.kairos.core.stats

import com.skohscripts.kairos.core.day.DayFilter
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.model.Task
import com.skohscripts.kairos.core.model.TaskStatus
import com.skohscripts.kairos.core.model.WorkSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Le CSV d'un instantané fixé contre le fichier d'or `fixtures/stats-export.csv` (docs/spec/statistiques.md § Export CSV,
 * issue #113), octet pour octet. Un changement voulu du format : relancer avec `KAIROS_WRITE_GOLDEN=1`, relire le
 * fichier réécrit, puis monter `StatsCsv.FORMAT_VERSION` si un lecteur existant ne le lirait plus.
 */
class StatsCsvGoldenTest {
    private val utc = TimeZone.UTC
    private val today = LocalDate(2026, 10, 22)
    private val now = Instant.parse("2026-10-22T12:00:00Z")

    private fun task(
        id: Long, type: String, points: Int, estimate: Int, done: Boolean, created: String,
        updated: String = "2026-10-20T10:00:00Z", deadline: LocalDate? = null,
    ) = Task(
        id, "T$id", taskType = type, fibonacciPoints = points, estimatedMinutes = estimate, deadline = deadline,
        status = if (done) TaskStatus.DONE else TaskStatus.TODO,
        createdAt = Instant.parse(created), updatedAt = Instant.parse(updated),
    )

    private fun session(id: Long, taskId: Long, start: String, minutes: Long) =
        Instant.parse(start).let { WorkSession(id, taskId, it, Instant.fromEpochMilliseconds(it.toEpochMilliseconds() + minutes * 60_000), it) }

    private val tasks = listOf(
        task(1, "Dev", 3, 60, true, "2026-09-01T08:00:00Z", "2026-10-20T10:00:00Z", LocalDate(2026, 10, 21)),
        task(2, "Dev", 3, 60, true, "2026-09-10T08:00:00Z", "2026-10-13T10:00:00Z", LocalDate(2026, 10, 10)),
        task(3, "Dev", 5, 120, true, "2026-09-15T08:00:00Z", "2026-10-06T10:00:00Z"),
        task(4, "Réunion, équipe", 1, 30, true, "2026-10-01T08:00:00Z", "2026-10-15T10:00:00Z"),
        task(5, "=Admin", 2, 0, true, "2026-10-02T08:00:00Z", "2026-10-21T10:00:00Z"),
        task(6, "", 0, 45, true, "2026-10-05T08:00:00Z", "2026-10-19T10:00:00Z"),
        task(7, "Dev", 8, 240, false, "2026-08-01T08:00:00Z", "2026-08-02T10:00:00Z", LocalDate(2026, 10, 1)),
        task(8, "", 0, 0, false, "2026-10-18T08:00:00Z", "2026-10-18T08:00:00Z"),
    )
    private val sessions = listOf(
        session(1, 1, "2026-10-20T09:00:00Z", 90),
        session(2, 1, "2026-10-19T14:10:00Z", 50),
        session(3, 2, "2026-10-12T09:30:00Z", 75),
        session(4, 2, "2026-10-13T10:00:00Z", 40),
        session(5, 3, "2026-10-05T15:00:00Z", 160),
        session(6, 3, "2026-10-06T09:00:00Z", 30),
        session(7, 4, "2026-10-15T14:00:00Z", 60),
        session(8, 5, "2026-10-21T16:45:00Z", 25),
        session(9, 6, "2026-10-19T08:00:00Z", 55),
        session(10, 7, "2026-10-07T11:00:00Z", 35),
    )

    private fun csv(query: StatsQuery): String {
        val input = query.apply(tasks, sessions, Settings(), today, utc)
        return StatsCsv.encode(query, input, query.dashboard(input, today, now, utc), query.hours(input, now, utc), today, now)
    }

    private fun check(name: String, actual: String) {
        if (System.getenv("KAIROS_WRITE_GOLDEN") == "1") {
            File("src/jvmTest/resources/fixtures/$name").writeText(actual)
        }
        val expected = checkNotNull(javaClass.getResource("/fixtures/$name")) { "fixture $name absente" }.readText()
        assertEquals(expected, actual)
    }

    @Test
    fun theDefaultViewMatchesTheGoldenFile() = check("stats-export.csv", csv(StatsQuery()))

    @Test
    fun aFilteredViewOverEverythingMatchesTheGoldenFile() =
        check("stats-export-filtered.csv", csv(StatsQuery(DayFilter(taskType = "Dev"), weeks = StatsQuery.ALL)))
}
