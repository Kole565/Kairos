package com.skohscripts.kairos.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.CrashReportScreen
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Seule activité : l'interface Compose commune, bord à bord (docs/spec/distribution.md § Android). */
class MainActivity : ComponentActivity(), FileService {
    /** Rapport du plantage précédent affiché à la place de l'application (`null` : lancement normal). */
    private var crashReport: String? = null
    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    // Sélecteurs du système (Storage Access Framework) : aucune permission de stockage.
    private val createDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        pendingSave?.complete(it)
    }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        pendingOpen?.complete(it)
    }
    private var pendingPermission: CompletableDeferred<Boolean>? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingPermission?.complete(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        KairosProcess.activity = this
        // Un plantage au lancement précédent : son rapport remplace l'application, base fermée (issue #65).
        crashReport = KairosCrash.pending(this)
        crashReport?.let { report ->
            setContent {
                KairosTheme {
                    CrashReportScreen(report, onShare = { share(report) }, onContinue = { KairosCrash.clear(this); recreate() })
                }
            }
            return
        }
        val services = KairosProcess.services(this)
        setContent {
            // Couleurs du fond d'écran (Material You), proposées dans Réglages → Apparence
            // à partir d'Android 12 (docs/spec/apparence.md).
            val system = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(LocalContext.current) else null
            KairosApp(Platform.ANDROID, systemColorScheme = system, splash = true) { services.await() }
        }
    }

    override fun onResume() {
        super.onResume()
        KairosProcess.activity = this
        if (crashReport != null) return
        // L'utilisateur a pu changer l'autorisation dans les réglages du système.
        KairosProcess.scope.launch { (KairosProcess.services(this@MainActivity).await().notifier as? AndroidNotifier)?.refresh() }
    }

    override fun onDestroy() {
        if (KairosProcess.activity === this) KairosProcess.activity = null
        super.onDestroy()
    }

    /** Confie le rapport de plantage au partage du système : rien ne part sans le geste de l'utilisateur. */
    private fun share(report: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
        startActivity(Intent.createChooser(send, null))
    }

    /** Demande `POST_NOTIFICATIONS` (Android 13+) ; `true` si accordée. */
    suspend fun requestNotificationPermission(): Boolean = withContext(Dispatchers.Main) {
        val result = CompletableDeferred<Boolean>().also { pendingPermission = it }
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        result.await()
    }

    override suspend fun saveText(suggestedName: String, text: String): Boolean {
        val result = CompletableDeferred<Uri?>().also { pendingSave = it }
        createDocument.launch(suggestedName)
        val uri = result.await() ?: return false
        withContext(Dispatchers.IO) {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }
        return true
    }

    override suspend fun openText(): String? {
        val result = CompletableDeferred<Uri?>().also { pendingOpen = it }
        openDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        val uri = result.await() ?: return null
        return withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        }
    }
}
