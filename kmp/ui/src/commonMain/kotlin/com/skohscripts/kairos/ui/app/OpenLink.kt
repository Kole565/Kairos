package com.skohscripts.kairos.ui.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.link_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Ouvre un lien dans le navigateur. Sans navigateur (`xdg-open` absent, web sans autorisation…) l'ouverture échoue :
 * jamais de plantage, et un message bref le dit au lieu de ne rien faire (docs/spec/soutien.md).
 */
@Composable
fun rememberLinkOpener(message: (String) -> Unit = LocalMessages.current): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val failure = stringResource(Res.string.link_unavailable)
    return remember(uriHandler, message, failure) { { url -> runCatching { uriHandler.openUri(url) }.onFailure { message(failure) } } }
}
