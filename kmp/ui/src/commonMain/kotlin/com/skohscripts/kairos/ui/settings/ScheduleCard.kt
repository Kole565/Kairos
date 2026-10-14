package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
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
import com.skohscripts.kairos.ui.generated.resources.schedule_custom
import com.skohscripts.kairos.ui.generated.resources.schedule_day_0
import com.skohscripts.kairos.ui.generated.resources.schedule_day_1
import com.skohscripts.kairos.ui.generated.resources.schedule_day_2
import com.skohscripts.kairos.ui.generated.resources.schedule_day_3
import com.skohscripts.kairos.ui.generated.resources.schedule_day_4
import com.skohscripts.kairos.ui.generated.resources.schedule_day_5
import com.skohscripts.kairos.ui.generated.resources.schedule_day_6
import com.skohscripts.kairos.ui.generated.resources.schedule_end
import com.skohscripts.kairos.ui.generated.resources.schedule_free
import com.skohscripts.kairos.ui.generated.resources.schedule_start
import com.skohscripts.kairos.ui.generated.resources.schedule_title
import com.skohscripts.kairos.ui.generated.resources.schedule_usual
import com.skohscripts.kairos.ui.generated.resources.settings_schedule_body
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import com.skohscripts.kairos.ui.theme.KairosSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Horaires par jour de la semaine (docs/spec/planning-personnalise.md § Lot B1) : pour chaque jour, « Comme d'habitude » (la
 * journée de travail ci-dessus), « Personnalisé » (début et fin, en heures) ou « Libre » (rien n'est planifié). Liés aux
 * champs `schedule.dN.mode`, `.start` et `.end` du formulaire, enregistrés par « Enregistrer » comme le reste.
 */
@Composable
internal fun ScheduleCard(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(stringResource(Res.string.schedule_title), stringResource(Res.string.settings_schedule_body))
        DAYS.forEachIndexed { day, name ->
            val mode = values["schedule.d$day.mode"] ?: SettingsForm.SCHEDULE_DEFAULT
            val label = stringResource(name)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(label, style = MaterialTheme.typography.labelLarge)
                KairosSegmentedRow(Modifier.fillMaxWidth().semantics { contentDescription = label }) {
                    MODES.forEachIndexed { index, (value, text) ->
                        KairosSegmentedButton(
                            selected = mode == value,
                            onClick = { onChange("schedule.d$day.mode", value) },
                            shape = SegmentedButtonDefaults.itemShape(index, MODES.size),
                            label = { Text(stringResource(text), maxLines = 1) },
                        )
                    }
                }
                if (mode == SettingsForm.SCHEDULE_CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        HourField("schedule.d$day.start", Res.string.schedule_start, values, errors, onChange, Modifier.weight(1f))
                        HourField("schedule.d$day.end", Res.string.schedule_end, values, errors, onChange, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HourField(
    key: String,
    label: StringResource,
    values: Map<String, String>,
    errors: Map<String, FieldError>,
    onChange: (String, String) -> Unit,
    modifier: Modifier,
) {
    val error = errors[key]
    val reveal: BringIntoViewRequester = LocalErrorReveal.current.requester(key)
    OutlinedTextField(
        value = values[key].orEmpty(),
        onValueChange = { onChange(key, it) },
        label = { Text(stringResource(label)) },
        isError = error != null,
        supportingText = error?.let { { Text(errorMessage(it, minExclusive = false)) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.widthIn(max = NUMBER_FIELD_MAX_WIDTH).bringIntoViewRequester(reveal),
    )
}

private val DAYS: List<StringResource> = listOf(
    Res.string.schedule_day_0, Res.string.schedule_day_1, Res.string.schedule_day_2, Res.string.schedule_day_3,
    Res.string.schedule_day_4, Res.string.schedule_day_5, Res.string.schedule_day_6,
)

private val MODES: List<Pair<String, StringResource>> = listOf(
    SettingsForm.SCHEDULE_DEFAULT to Res.string.schedule_usual,
    SettingsForm.SCHEDULE_CUSTOM to Res.string.schedule_custom,
    SettingsForm.SCHEDULE_FREE to Res.string.schedule_free,
)
