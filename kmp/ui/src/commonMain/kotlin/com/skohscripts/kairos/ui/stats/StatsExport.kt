package com.skohscripts.kairos.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.stats_export
import com.skohscripts.kairos.ui.generated.resources.stats_export_csv
import com.skohscripts.kairos.ui.generated.resources.stats_export_image
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.compose.resources.stringResource

/** Largeur de l'image d'une carte, quel que soit l'appareil (docs/spec/statistiques.md § Export). */
internal val STATS_IMAGE_WIDTH = 600.dp

/** Densité de l'image : 600 dp donnent 1 200 px. */
internal const val STATS_IMAGE_DENSITY = 2f

/**
 * « Exporter… » (issue #113) : bouton à icône de 48 dp, info-bulle, menu « Tableaux (CSV) » puis « Image : <carte> »
 * pour chacune des cartes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsExportButton(onCsv: () -> Unit, onImage: (StatsCard) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.stats_export)
    Box {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) {
            IconButton(onClick = { open = true }) { Icon(KairosIcons.Download, contentDescription = label) }
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.stats_export_csv)) },
                leadingIcon = { Icon(KairosIcons.TableView, contentDescription = null) },
                onClick = {
                    open = false
                    onCsv()
                },
            )
            StatsCard.entries.forEach { card ->
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.stats_export_image, stringResource(card.title))) },
                    leadingIcon = { Icon(KairosIcons.Image, contentDescription = null) },
                    onClick = {
                        open = false
                        onImage(card)
                    },
                )
            }
        }
    }
}

/** L'image d'une carte : la carte à [STATS_IMAGE_WIDTH] sur le fond de l'écran, la légende en pied. */
@Composable
internal fun StatsCardImage(content: @Composable () -> Unit, caption: String) {
    Column(
        Modifier.width(STATS_IMAGE_WIDTH).background(MaterialTheme.colorScheme.surface).padding(KairosSpacing.l),
        verticalArrangement = Arrangement.spacedBy(KairosSpacing.m),
    ) {
        content()
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Compose [content] **hors écran** et rend son image une fois dessinée : un `Layout` de taille nulle mesure le contenu
 * sans contrainte sous une densité forcée ([STATS_IMAGE_DENSITY], police ×1, la même image sur tous les appareils) et ne
 * le dessine que dans un `GraphicsLayer` (jamais `drawLayer` : rien n'apparaît à l'écran). Le calque marche sur les
 * trois plateformes, là où `ImageComposeScene` (captures des magasins) n'existe que sur le bureau.
 */
@Composable
internal fun OffscreenCapture(onCaptured: (ImageBitmap) -> Unit, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val drawn = remember { CompletableDeferred<Unit>() }
    val captured by rememberUpdatedState(onCaptured)
    LaunchedEffect(layer) {
        drawn.await()
        // Deux images de plus : une ressource (texte, police) chargée après le premier dessin y est.
        repeat(2) { withFrameNanos { } }
        captured(layer.toImageBitmap())
    }
    Layout(
        content = {
            CompositionLocalProvider(LocalDensity provides Density(STATS_IMAGE_DENSITY, 1f)) {
                Box(
                    Modifier.drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawn.complete(Unit)
                    },
                ) { content() }
            }
        },
    ) { measurables, _ ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(0, 0) { placeables.forEach { it.place(0, 0) } }
    }
}
