package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.language_en
import com.skohscripts.kairos.ui.generated.resources.language_fr
import com.skohscripts.kairos.ui.generated.resources.language_help
import com.skohscripts.kairos.ui.generated.resources.language_system
import com.skohscripts.kairos.ui.generated.resources.settings_language_more
import com.skohscripts.kairos.ui.generated.resources.settings_section_language
import com.skohscripts.kairos.ui.theme.KairosSegmentedButton
import com.skohscripts.kairos.ui.theme.KairosSegmentedRow
import com.skohscripts.kairos.ui.theme.KairosSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Langue de l'interface (docs/spec/i18n.md § Choix de la langue) : « Système », « Français », « English », liés au
 * réglage `language` du formulaire (enregistré avec le reste, par « Enregistrer »). Chaque langue porte son propre
 * nom, dans les deux langues. Le message du bas annonce d'autres langues : il disparaîtra avec la troisième.
 */
@Composable
internal fun LanguageCard(value: String, onChange: (String) -> Unit) {
    val chosen = UiLanguage.normalize(value)
    val title = stringResource(Res.string.settings_section_language)
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(title, stringResource(Res.string.language_help))
        KairosSegmentedRow(Modifier.fillMaxWidth().semantics { contentDescription = title }) {
            OPTIONS.forEachIndexed { index, (setting, label) ->
                KairosSegmentedButton(
                    selected = chosen == setting,
                    onClick = { onChange(setting) },
                    shape = SegmentedButtonDefaults.itemShape(index, OPTIONS.size),
                    label = { Text(stringResource(label), maxLines = 1) },
                )
            }
        }
        Text(
            stringResource(Res.string.settings_language_more),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val OPTIONS: List<Pair<String, StringResource>> = listOf(
    Settings.LANGUAGE_SYSTEM to Res.string.language_system,
    Settings.LANGUAGE_FR to Res.string.language_fr,
    Settings.LANGUAGE_EN to Res.string.language_en,
)
