package com.skohscripts.kairos.android

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.skohscripts.kairos.ui.KairosApp
import com.skohscripts.kairos.ui.Platform
import com.skohscripts.kairos.ui.app.CrashReportScreen
import com.skohscripts.kairos.ui.app.FileService
import com.skohscripts.kairos.ui.app.mimeOf
import com.skohscripts.kairos.ui.theme.KairosTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Seule activité : l'interface Compose commune, bord à bord (docs/spec/distribution.md § Android). */
class MainActivity : ComponentActivity(), FileService {
    /** Rapport du plantage précédent affiché à la place de l'application (`null` : lancement normal). */
    private var crashReport: String? = null
    private var pendingSave: CompletableDeferred<Uri?>? = null
    private var pendingOpen: CompletableDeferred<Uri?>? = null

    // Sélecteurs du système (Storage Access Framework) : aucune permission de stockage. Un par type de fichier : le
    // type fixe l'extension que le fournisseur ajoute au nom (un `.csv` enregistré en JSON devenait `.csv.json`).
    private val createDocument = listOf("application/json", "text/csv", "image/png").associateWith { mime ->
        registerForActivityResult(ActivityResultContracts.CreateDocument(mime)) { pendingSave?.complete(it) }
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
            // Couleurs du fond d'écran (Material You), claires ou sombres, proposées dans Réglages → Apparence
            // à partir d'Android 12 (docs/spec/apparence.md).
            val context = LocalContext.current
            val system = remember(context) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    { dark: Boolean -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context) }
                } else {
                    null
                }
            }
            KairosApp(Platform.ANDROID, systemColorScheme = system, onDarkTheme = ::applySystemBars, splash = true) { services.await() }
        }
    }

    /**
     * Icônes des barres du système selon le thème **affiché**, pas celui du système : un thème sombre choisi sur un
     * système clair garderait sinon des icônes sombres sur fond sombre (docs/spec/apparence.md § Thème sombre). Voiles
     * de la barre de navigation à trois boutons : ceux d'`enableEdgeToEdge` par défaut.
     */
    private fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(AndroidColor.argb(0xE6, 0xFF, 0xFF, 0xFF), AndroidColor.argb(0x80, 0x1B, 0x1B, 0x1B)) { dark },
        )
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

    override suspend fun saveText(suggestedName: String, text: String): Boolean = saveBytes(suggestedName) { text.toByteArray() }

    /** PNG par `Bitmap.compress` ; un bitmap matériel (rendu du calque) est d'abord copié en mémoire. */
    override suspend fun saveImage(suggestedName: String, image: ImageBitmap): Boolean = saveBytes(suggestedName) {
        val bitmap = image.asAndroidBitmap().let { if (it.config == Bitmap.Config.HARDWARE) it.copy(Bitmap.Config.ARGB_8888, false) else it }
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    private suspend fun saveBytes(suggestedName: String, bytes: () -> ByteArray): Boolean {
        val result = CompletableDeferred<Uri?>().also { pendingSave = it }
        createDocument.getValue(mimeOf(suggestedName)).launch(suggestedName)
        val uri = result.await() ?: return false
        withContext(Dispatchers.IO) {
            val data = bytes()
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(data) }
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
