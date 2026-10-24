package com.skohscripts.kairos.core.updates

/**
 * D'où vient l'application Android (docs/spec/mises-a-jour.md § Android, issue #143) : installée **à la main** (APK ouvert
 * depuis un navigateur ou un gestionnaire de fichiers, ou `adb`), ou par un magasin, qui s'occupe alors des mises à jour.
 * Pur : le nom du paquet installateur est lu par la plateforme.
 */
object InstallSource {
    /** Installateurs du système : l'APK a été ouvert à la main. */
    val SYSTEM_INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller", "com.android.shell")

    /**
     * Vrai si [installer] est absent ou un installateur du système. Tout autre installateur est un magasin (F-Droid et ses
     * clients, Play, un gestionnaire de mises à jour) : une liste de magasins à tenir aurait tôt ou tard montré le lien dans
     * l'un d'eux.
     */
    fun installedByHand(installer: String?): Boolean = installer.isNullOrBlank() || installer in SYSTEM_INSTALLERS
}
