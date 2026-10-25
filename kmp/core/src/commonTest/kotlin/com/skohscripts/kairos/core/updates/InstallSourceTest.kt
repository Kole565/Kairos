package com.skohscripts.kairos.core.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Installation à la main ou par un magasin (docs/spec/mises-a-jour.md § Android, issue #143). */
class InstallSourceTest {
    @Test
    fun aHandInstalledApkHasNoInstallerOrTheSystemOne() {
        assertTrue(InstallSource.installedByHand(null))
        assertTrue(InstallSource.installedByHand(""))
        assertTrue(InstallSource.installedByHand("com.google.android.packageinstaller"))
        assertTrue(InstallSource.installedByHand("com.android.packageinstaller"))
        assertTrue(InstallSource.installedByHand("com.android.shell"))
    }

    @Test
    fun anyOtherInstallerIsAStore() {
        listOf("org.fdroid.fdroid", "org.fdroid.basic", "com.looker.droidify", "com.android.vending", "dev.imranr.obtainium", "some.unknown.store")
            .forEach { assertFalse(InstallSource.installedByHand(it), it) }
    }

    @Test
    fun theDownloadPageFollowsTheLanguage() {
        assertEquals("https://skohscripts.github.io/Kairos/", UpdateCheck.downloadPage("fr"))
        assertEquals("https://skohscripts.github.io/Kairos/en/", UpdateCheck.downloadPage("en"))
        assertEquals("https://skohscripts.github.io/Kairos/es/", UpdateCheck.downloadPage("es"))
        assertEquals("https://skohscripts.github.io/Kairos/ru/", UpdateCheck.downloadPage("ru-RU"))
        assertEquals("https://skohscripts.github.io/Kairos/en/", UpdateCheck.downloadPage("zh"))
    }
}
