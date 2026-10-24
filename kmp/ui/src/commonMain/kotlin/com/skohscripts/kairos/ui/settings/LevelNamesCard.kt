package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.FIBONACCI_SCALE
import com.skohscripts.kairos.core.model.PRIORITY_VALUES
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.FieldErrorKind
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.ui.day.Levels
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.level_name_too_long
import com.skohscripts.kairos.ui.generated.resources.levels_points_title
import com.skohscripts.kairos.ui.generated.resources.levels_priority_title
import com.skohscripts.kairos.ui.generated.resources.settings_section_levels
import com.skohscripts.kairos.ui.generated.resources.settings_section_levels_body
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Largeur d'un champ de nom : un nom tient en 16 caractères, deux champs côte à côte dès que la carte le permet. */
private val LEVEL_FIELD_MAX_WIDTH = 240.dp

/**
 * Noms des niveaux (docs/spec/planning-personnalise.md § Lot D, issue #93) : un champ par priorité et par palier de points,
 * liés aux champs `levelNames.*` de `SettingsForm`. Le libellé d'un champ est le nom par défaut du niveau (jamais le nom
 * choisi, pour qu'on sache toujours ce qu'on renomme) ; vide, le niveau garde ce nom.
 */
@Composable
internal fun LevelNamesCard(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Flag, stringResource(Res.string.settings_section_levels), stringResource(Res.string.settings_section_levels_body))
        LevelGroup(Res.string.levels_priority_title, PRIORITY_VALUES, SettingsForm::priorityNameKey, values, errors, onChange) { p ->
            "P$p ${stringResource(Levels.priorityName(p))}"
        }
        LevelGroup(Res.string.levels_points_title, FIBONACCI_SCALE, SettingsForm::pointsNameKey, values, errors, onChange) { points ->
            "$points ${stringResource(Levels.pointsName(points))}"
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LevelGroup(
    title: StringResource,
    levels: List<Int>,
    key: (Int) -> String,
    values: Map<String, String>,
    errors: Map<String, FieldError>,
    onChange: (String, String) -> Unit,
    label: @Composable (Int) -> String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { level ->
                val fieldKey = key(level)
                val error = errors[fieldKey]
                OutlinedTextField(
                    value = values[fieldKey].orEmpty(),
                    onValueChange = { onChange(fieldKey, it) },
                    label = { Text(label(level)) },
                    isError = error != null,
                    supportingText = error?.let { { Text(levelError(it)) } },
                    singleLine = true,
                    modifier = Modifier.widthIn(max = LEVEL_FIELD_MAX_WIDTH).fillMaxWidth()
                        .bringIntoViewRequester(LocalErrorReveal.current.requester(fieldKey)),
                )
            }
        }
    }
}

/** « 16 caractères au plus » plutôt que « Au plus 16 », qui se lirait comme un nombre. */
@Composable
private fun levelError(error: FieldError): String =
    if (error.kind == FieldErrorKind.TOO_LARGE) stringResource(Res.string.level_name_too_long, error.bound.orEmpty())
    else errorMessage(error, minExclusive = false)
