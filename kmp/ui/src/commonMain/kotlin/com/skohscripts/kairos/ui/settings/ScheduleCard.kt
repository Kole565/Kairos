package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import com.skohscripts.kairos.ui.generated.resources.contexts_body
import com.skohscripts.kairos.ui.generated.resources.contexts_name
import com.skohscripts.kairos.ui.generated.resources.contexts_no_types
import com.skohscripts.kairos.ui.generated.resources.contexts_title
import com.skohscripts.kairos.ui.generated.resources.contexts_types
import com.skohscripts.kairos.ui.generated.resources.contexts_unknown_type
import com.skohscripts.kairos.ui.generated.resources.schedule_add_window
import com.skohscripts.kairos.ui.generated.resources.schedule_context_any
import com.skohscripts.kairos.ui.generated.resources.schedule_context_label
import com.skohscripts.kairos.ui.generated.resources.schedule_remove_window
import com.skohscripts.kairos.ui.generated.resources.schedule_window
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosButtonIconPadding
import com.skohscripts.kairos.ui.theme.KairosFilterChip
import com.skohscripts.kairos.ui.theme.KairosTextButton
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
 * Horaires par jour de la semaine (docs/spec/planning-personnalise.md § Lots B1 et B2) : pour chaque jour, « Comme d'habitude » (la
 * journée de travail ci-dessus), « Personnalisé » (une ou plusieurs plages, chacune avec son début, sa fin en heures et, s'il y
 * a des contextes, celui à qui elle est réservée) ou « Libre » (rien n'est planifié). Liés aux champs `schedule.dN.*` du
 * formulaire, enregistrés par « Enregistrer » comme le reste.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleCard(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    val contextNames = contextNames(values)
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
                    val count = (values["schedule.d$day.count"]?.toIntOrNull() ?: 1).coerceIn(1, SettingsForm.MAX_WINDOWS)
                    for (index in 0 until count) {
                        val prefix = if (index == 0) "schedule.d$day." else "schedule.d$day.w${index + 1}."
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (count > 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(Res.string.schedule_window, index + 1), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                    if (index > 0 && index == count - 1) {
                                        val remove = stringResource(Res.string.schedule_remove_window, index + 1)
                                        IconButton(onClick = { onChange("schedule.d$day.count", (count - 1).toString()) }, modifier = Modifier.semantics { contentDescription = remove }) {
                                            Icon(KairosIcons.Close, contentDescription = null)
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                HourField(prefix + "start", Res.string.schedule_start, values, errors, onChange, Modifier.weight(1f))
                                HourField(prefix + "end", Res.string.schedule_end, values, errors, onChange, Modifier.weight(1f))
                            }
                            // Le contexte de la plage : seulement quand il y en a ; une plage qui cite un contexte disparu le garde, signalé.
                            val current = values[prefix + "ctx"].orEmpty()
                            if (contextNames.isNotEmpty() || current.isNotEmpty()) {
                                Text(stringResource(Res.string.schedule_context_label), style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    KairosFilterChip(selected = current.isEmpty(), onClick = { onChange(prefix + "ctx", "") }, label = { Text(stringResource(Res.string.schedule_context_any)) })
                                    (contextNames + listOfNotNull(current.takeIf { it.isNotEmpty() && contextNames.none { n -> n.equals(it, ignoreCase = true) } })).forEach { name ->
                                        KairosFilterChip(selected = name.equals(current, ignoreCase = true), onClick = { onChange(prefix + "ctx", name) }, label = { Text(name) })
                                    }
                                }
                                errors[prefix + "ctx"]?.let {
                                    Text(errorMessage(it, minExclusive = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.bringIntoViewRequester(LocalErrorReveal.current.requester(prefix + "ctx")))
                                }
                            }
                        }
                    }
                    if (count < SettingsForm.MAX_WINDOWS) {
                        KairosTextButton(onClick = { onChange("schedule.d$day.count", (count + 1).toString()) }, contentPadding = KairosButtonIconPadding) {
                            Icon(KairosIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(Res.string.schedule_add_window), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Les noms des contextes définis dans le formulaire (emplacements remplis), dans l'ordre. */
private fun contextNames(values: Map<String, String>): List<String> =
    (1..SettingsForm.MAX_CONTEXTS).mapNotNull { values["schedule.c$it.name"]?.trim()?.takeIf { name -> name.isNotEmpty() } }

/**
 * Contextes (docs/spec/planning-personnalise.md § Lot B2) : un nom et les types de tâche qu'il regroupe. Une plage réservée à un
 * contexte n'accueille que ses tâches ; une tâche d'aucun contexte va dans n'importe quelle plage. Quatre emplacements au plus ;
 * on en voit un vide de plus que ceux qui sont remplis. Un type qui n'est plus dans « Types de tâche » reste listé, signalé
 * « type inconnu » : jamais perdu en silence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContextsCard(values: Map<String, String>, errors: Map<String, FieldError>, onChange: (String, String) -> Unit) {
    val known = values["taskTypes"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val typesOf = (1..SettingsForm.MAX_CONTEXTS).map { slot -> values["schedule.c$slot.types"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() } }
    val lastFilled = (1..SettingsForm.MAX_CONTEXTS).lastOrNull { slot -> !values["schedule.c$slot.name"].isNullOrBlank() || typesOf[slot - 1].isNotEmpty() } ?: 0
    val shown = minOf(SettingsForm.MAX_CONTEXTS, lastFilled + 1)
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(stringResource(Res.string.contexts_title), stringResource(Res.string.contexts_body))
        for (slot in 1..shown) {
            val nameKey = "schedule.c$slot.name"
            val typesKey = "schedule.c$slot.types"
            val selected = typesOf[slot - 1]
            val takenElsewhere = typesOf.filterIndexed { i, _ -> i != slot - 1 }.flatten().map { it.lowercase() }.toSet()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = values[nameKey].orEmpty(),
                    onValueChange = { onChange(nameKey, it) },
                    label = { Text(stringResource(Res.string.contexts_name)) },
                    isError = errors[nameKey] != null,
                    supportingText = errors[nameKey]?.let { { Text(errorMessage(it, minExclusive = false)) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().bringIntoViewRequester(LocalErrorReveal.current.requester(nameKey)),
                )
                val options = known + selected.filter { s -> known.none { it.equals(s, ignoreCase = true) } }
                if (options.isEmpty()) {
                    Text(stringResource(Res.string.contexts_no_types), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(stringResource(Res.string.contexts_types), style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { type ->
                            val isSelected = selected.any { it.equals(type, ignoreCase = true) }
                            val unknown = known.none { it.equals(type, ignoreCase = true) }
                            KairosFilterChip(
                                selected = isSelected,
                                enabled = isSelected || type.lowercase() !in takenElsewhere,
                                onClick = {
                                    val next = if (isSelected) selected.filterNot { it.equals(type, ignoreCase = true) } else selected + type
                                    onChange(typesKey, next.joinToString(","))
                                },
                                leadingIcon = if (unknown) ({ Icon(KairosIcons.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) }) else null,
                                label = { Text(if (unknown) stringResource(Res.string.contexts_unknown_type, type) else type) },
                            )
                        }
                    }
                }
                errors[typesKey]?.let {
                    Text(errorMessage(it, minExclusive = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.bringIntoViewRequester(LocalErrorReveal.current.requester(typesKey)))
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
