package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.settings.FieldError
import com.skohscripts.kairos.core.settings.SettingsForm
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.besthours_enable
import com.skohscripts.kairos.ui.generated.resources.besthours_hour_label
import com.skohscripts.kairos.ui.generated.resources.besthours_no_types
import com.skohscripts.kairos.ui.generated.resources.besthours_preferred_body
import com.skohscripts.kairos.ui.generated.resources.besthours_preferred_title
import com.skohscripts.kairos.ui.generated.resources.besthours_settings_body
import com.skohscripts.kairos.ui.generated.resources.besthours_settings_title
import com.skohscripts.kairos.ui.generated.resources.besthours_strength
import com.skohscripts.kairos.ui.generated.resources.besthours_strength_0
import com.skohscripts.kairos.ui.generated.resources.besthours_strength_1
import com.skohscripts.kairos.ui.generated.resources.besthours_strength_2
import com.skohscripts.kairos.ui.generated.resources.contexts_unknown_type
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import com.skohscripts.kairos.ui.theme.KairosSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private const val ENABLED = "schedule.bestHours.enabled"
private const val STRENGTH = "schedule.bestHours.strength"
private const val PREFERRED = "schedule.bestHours.preferred"

private val LEVELS: List<StringResource> = listOf(Res.string.besthours_strength_0, Res.string.besthours_strength_1, Res.string.besthours_strength_2)

/**
 * Meilleures heures (docs/spec/planning-personnalise.md § Lot C, étape C2) : un interrupteur (désactivé par défaut), la force en
 * trois niveaux (Faible, Moyenne, Forte) et, par type de tâche, une heure préférée facultative qui prime sur la détection. Liée aux
 * champs `schedule.bestHours.*` ; la liste des heures est un seul champ texte `type=heure,…` (`SettingsForm.parsePreferred`) dont un
 * type sans heure est absent. Un type qui n'est plus dans « Types de tâche » garde son heure, signalé « type inconnu » : jamais perdu
 * en silence.
 */
@Composable
internal fun BestHoursCard(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    val enabled = values[ENABLED] == "true"
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(stringResource(Res.string.besthours_settings_title), stringResource(Res.string.besthours_settings_body))
        SwitchRow(enabled, stringResource(Res.string.besthours_enable)) { onChange(ENABLED, it.toString()) }
        if (!enabled) return@SettingsCard
        val level = (values[STRENGTH]?.toIntOrNull() ?: 1).coerceIn(0, LEVELS.lastIndex)
        val strengthLabel = stringResource(Res.string.besthours_strength)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strengthLabel, style = MaterialTheme.typography.labelLarge)
            KairosSegmentedRow(Modifier.fillMaxWidth().semantics { contentDescription = strengthLabel }) {
                LEVELS.forEachIndexed { index, text ->
                    KairosSegmentedButton(
                        selected = level == index,
                        onClick = { onChange(STRENGTH, index.toString()) },
                        shape = SegmentedButtonDefaults.itemShape(index, LEVELS.size),
                        label = { Text(stringResource(text), maxLines = 1) },
                    )
                }
            }
        }
        val hours = SettingsForm.parsePreferred(values[PREFERRED].orEmpty())
        val known = values["taskTypes"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
        val types = known + hours.keys.filter { h -> known.none { it.equals(h, ignoreCase = true) } }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.besthours_preferred_title), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(Res.string.besthours_preferred_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (types.isEmpty()) {
                Text(stringResource(Res.string.besthours_no_types), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val error = errors[PREFERRED]
            types.forEachIndexed { index, type ->
                val unknown = known.none { it.equals(type, ignoreCase = true) }
                val current = hours.entries.firstOrNull { it.key.equals(type, ignoreCase = true) }?.value.orEmpty()
                val label = stringResource(Res.string.besthours_hour_label, if (unknown) stringResource(Res.string.contexts_unknown_type, type) else type)
                OutlinedTextField(
                    value = current,
                    onValueChange = { typed ->
                        val next = LinkedHashMap<String, String>(hours.filterKeys { !it.equals(type, ignoreCase = true) })
                        next[type] = typed.filter { it.isDigit() }.take(2)
                        onChange(PREFERRED, SettingsForm.encodePreferred(next))
                    },
                    label = { Text(label) },
                    isError = error != null && index == 0,
                    supportingText = if (index == 0) error?.let { { Text(errorMessage(it, minExclusive = false)) } } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.widthIn(max = NUMBER_FIELD_MAX_WIDTH).fillMaxWidth()
                        .let { if (index == 0) it.bringIntoViewRequester(LocalErrorReveal.current.requester(PREFERRED)) else it },
                )
            }
        }
    }
}
