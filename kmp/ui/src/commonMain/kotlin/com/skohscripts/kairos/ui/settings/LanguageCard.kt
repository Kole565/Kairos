package com.skohscripts.kairos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.UiLanguage
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.language_de
import com.skohscripts.kairos.ui.generated.resources.language_en
import com.skohscripts.kairos.ui.generated.resources.language_es
import com.skohscripts.kairos.ui.generated.resources.language_fr
import com.skohscripts.kairos.ui.generated.resources.language_help
import com.skohscripts.kairos.ui.generated.resources.language_it
import com.skohscripts.kairos.ui.generated.resources.language_ru
import com.skohscripts.kairos.ui.generated.resources.language_system
import com.skohscripts.kairos.ui.generated.resources.settings_section_language
import com.skohscripts.kairos.ui.icons.KairosIcons
import com.skohscripts.kairos.ui.theme.KairosSpacing
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Langue de l'interface (docs/spec/i18n.md § Choix de la langue) : « Système » puis les six langues, en liste de choix
 * exclusifs (sept options ne tiennent pas dans une rangée segmentée à 360 dp), liés au réglage `language` du formulaire
 * (enregistré avec le reste, par « Enregistrer »). Chaque langue porte son propre nom, jamais traduit.
 */
@Composable
internal fun LanguageCard(value: String, onChange: (String) -> Unit) {
    val chosen = UiLanguage.normalize(value)
    val title = stringResource(Res.string.settings_section_language)
    SettingsCard(spacing = KairosSpacing.m) {
        SettingsCardHeader(KairosIcons.Translate, title, stringResource(Res.string.language_help))
        Column(Modifier.fillMaxWidth().selectableGroup().semantics { contentDescription = title }) {
            OPTIONS.forEach { (setting, label) ->
                val name = stringResource(label)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .selectable(selected = chosen == setting, onClick = { onChange(setting) }, role = Role.RadioButton),
                ) {
                    RadioButton(selected = chosen == setting, onClick = null)
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private val OPTIONS: List<Pair<String, StringResource>> = listOf(
    Settings.LANGUAGE_SYSTEM to Res.string.language_system,
    Settings.LANGUAGE_FR to Res.string.language_fr,
    Settings.LANGUAGE_EN to Res.string.language_en,
    Settings.LANGUAGE_ES to Res.string.language_es,
    Settings.LANGUAGE_DE to Res.string.language_de,
    Settings.LANGUAGE_RU to Res.string.language_ru,
    Settings.LANGUAGE_IT to Res.string.language_it,
)
