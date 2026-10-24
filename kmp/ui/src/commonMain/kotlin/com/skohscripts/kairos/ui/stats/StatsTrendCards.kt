package com.skohscripts.kairos.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.stats.StatsTrends
import com.skohscripts.kairos.ui.day.Dates
import com.skohscripts.kairos.ui.day.duration
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.stats_bias_trend_empty
import com.skohscripts.kairos.ui.generated.resources.stats_bias_trend_hint
import com.skohscripts.kairos.ui.generated.resources.stats_bias_trend_summary
import com.skohscripts.kairos.ui.generated.resources.stats_bias_trend_title
import com.skohscripts.kairos.ui.generated.resources.stats_bias_week_row
import com.skohscripts.kairos.ui.generated.resources.stats_sample
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_empty
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_hint_daily
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_hint_weekly
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_scale
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_summary
import com.skohscripts.kairos.ui.generated.resources.stats_tracked_title
import com.skohscripts.kairos.ui.generated.resources.stats_unreliable
import com.skohscripts.kairos.ui.icons.KairosIcons
import org.jetbrains.compose.resources.stringResource
import kotlin.math.round

/**
 * « Temps chronométré au fil du temps » (docs/spec/statistiques.md § Évolutions, issue #112) : une barre par jour (par
 * semaine au-delà de 8 semaines), d'une seule teinte (le primaire) ; au-dessus, l'échelle (« max 2 h ») ; dessous, les
 * dates de début et de fin, puis le résumé écrit (total, effectif, pointe), qui est aussi ce que dit le lecteur d'écran.
 */
@Composable
internal fun TrackedCard(trends: StatsTrends.Result, language: String) {
    val tracked = trends.tracked
    Panel(
        KairosIcons.ShowChart,
        stringResource(Res.string.stats_tracked_title),
        stringResource(if (tracked.daily) Res.string.stats_tracked_hint_daily else Res.string.stats_tracked_hint_weekly),
    ) {
        val busiest = tracked.busiest
        if (!trends.hasTracked || busiest == null) {
            Muted(stringResource(Res.string.stats_tracked_empty))
            return@Panel
        }
        val label = { p: StatsTrends.TrackedPoint -> if (tracked.daily) Dates.dayShort(p.start, language) else Dates.dayMonth(p.start, language) }
        // Peu de jours avec du temps : « peu fiable », comme les autres agrégats.
        val sample = stringResource(if (tracked.active >= MIN_ACTIVE) Res.string.stats_sample else Res.string.stats_unreliable, tracked.active)
        val summary = stringResource(
            Res.string.stats_tracked_summary, duration(tracked.total), tracked.active, tracked.points.size, sample, label(busiest), duration(busiest.minutes),
        )
        Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(Res.string.stats_tracked_scale, duration(busiest.minutes)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val max = busiest.minutes.toFloat()
            Row(
                Modifier.fillMaxWidth().height(CHART_HEIGHT.dp).clearAndSetSemantics { }
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f), RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(if (tracked.points.size > 30) 1.dp else 2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                tracked.points.forEach { p ->
                    val h = if (p.minutes == 0) 0f else (CHART_HEIGHT * p.minutes / max).coerceAtLeast(2f)
                    Box(Modifier.weight(1f).height(h.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)))
                }
            }
            Row(Modifier.fillMaxWidth().clearAndSetSemantics { }) {
                Text(label(tracked.points.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(label(tracked.points.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics { })
        }
    }
}

/**
 * « Biais d'estimation par semaine » : un point par semaine mesurée (creux si peu fiable), reliés, et la ligne pointillée
 * ×1 ; au-dessus de ×[MAX_RATIO] le point est posé au plafond (la valeur reste écrite). Dessous, une ligne écrite par semaine
 * mesurée, la plus récente d'abord : le graphique illustre ces lignes, il ne les remplace pas.
 */
@Composable
internal fun BiasTrendCard(trends: StatsTrends.Result, language: String) {
    Panel(KairosIcons.TrendingUp, stringResource(Res.string.stats_bias_trend_title), stringResource(Res.string.stats_bias_trend_hint)) {
        val measured = trends.bias.filter { it.ratio != null }
        if (measured.isEmpty()) {
            Muted(stringResource(Res.string.stats_bias_trend_empty))
            return@Panel
        }
        val latest = measured.last()
        val latestSample = stringResource(if (latest.reliable) Res.string.stats_sample else Res.string.stats_unreliable, latest.count)
        val summary = stringResource(Res.string.stats_bias_trend_summary, measured.size, trends.bias.size, twoDecimals(latest.ratio!!), latestSample)
        Text(summary, style = MaterialTheme.typography.bodyMedium)
        val primary = MaterialTheme.colorScheme.primary
        val reference = MaterialTheme.colorScheme.outline
        val surface = MaterialTheme.colorScheme.surface
        val top = maxOf(2.0, measured.maxOf { it.ratio!! }.coerceAtMost(MAX_RATIO))
        Canvas(Modifier.fillMaxWidth().height(CHART_HEIGHT.dp).padding(vertical = 6.dp).clearAndSetSemantics { }) {
            val n = trends.bias.size
            // Les points des bords restent entiers : l'abscisse est décalée de leur rayon (et de leur contour).
            val inset = 6.dp.toPx()
            fun x(i: Int) = if (n == 1) size.width / 2 else inset + (size.width - 2 * inset) * i / (n - 1)
            fun y(r: Double) = (size.height * (1 - r.coerceAtMost(top) / top)).toFloat()
            drawLine(reference, Offset(0f, y(1.0)), Offset(size.width, y(1.0)), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            var previous: Offset? = null
            trends.bias.forEachIndexed { i, w ->
                val r = w.ratio ?: return@forEachIndexed
                val point = Offset(x(i), y(r))
                previous?.let { drawLine(primary, it, point, strokeWidth = 2.dp.toPx()) }
                previous = point
            }
            trends.bias.forEachIndexed { i, w ->
                val r = w.ratio ?: return@forEachIndexed
                val point = Offset(x(i), y(r))
                if (w.reliable) {
                    drawCircle(primary, 4.dp.toPx(), point)
                } else {
                    drawCircle(surface, 4.dp.toPx(), point)
                    drawCircle(primary, 4.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            measured.asReversed().forEach { w ->
                val sample = stringResource(if (w.reliable) Res.string.stats_sample else Res.string.stats_unreliable, w.count)
                Text(
                    stringResource(Res.string.stats_bias_week_row, Dates.dayMonth(w.weekStart, language), twoDecimals(w.ratio!!), duration(w.realMinutes), duration(w.estimatedMinutes), sample),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Hauteur des deux graphiques. */
private const val CHART_HEIGHT = 96

/** Au-delà, le point est posé au plafond du graphique (la valeur reste écrite). */
private const val MAX_RATIO = 4.0

/** Sous trois jours (ou semaines) avec du temps, la série est « peu fiable », comme les autres agrégats. */
private const val MIN_ACTIVE = 3

private fun twoDecimals(x: Double): String {
    val cents = round(x * 100).toLong()
    return "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
