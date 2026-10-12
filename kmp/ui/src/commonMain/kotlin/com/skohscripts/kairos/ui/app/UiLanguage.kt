package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.intl.Locale
import com.skohscripts.kairos.core.model.Settings
import com.skohscripts.kairos.core.settings.UiLanguage

/**
 * Langue de l'interface résolue (`fr` ou `en`), fournie par `KairosApp` une fois les réglages lus
 * (docs/spec/i18n.md § Choix de la langue). `null` hors de `KairosApp` (tests d'un écran seul) : [uiLanguage]
 * retombe alors sur la langue du système.
 */
val LocalUiLanguage = compositionLocalOf<String?> { null }

/**
 * La langue de l'interface, `fr` ou `en` : celle qu'on formate (dates, nombres, pluriels). À utiliser à la place de
 * `Locale.current.language`, qui ignorerait le choix de l’utilisateur et laisserait passer une troisième langue.
 */
@Composable
fun uiLanguage(): String = LocalUiLanguage.current ?: UiLanguage.resolve(Settings.LANGUAGE_SYSTEM, Locale.current.language)
