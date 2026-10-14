package com.skohscripts.kairos.desktop

import java.io.File

/**
 * Dossier des données sur le bureau (docs/spec/distribution.md § Bureau) : la
 * racine `Kairos` de platformdirs (`user_data_dir("Kairos")`), sous-dossier
 * `v3`. Ce chemin ne change pas : c'est là que vivent les données des
 * utilisateurs.
 *
 * `KAIROS_DATA_DIR` prime (mode portable, tests).
 */
object DataDirectory {
    fun resolve(
        env: Map<String, String> = System.getenv(),
        osName: String = System.getProperty("os.name"),
        home: String = System.getProperty("user.home"),
    ): File {
        env["KAIROS_DATA_DIR"]?.takeIf { it.isNotBlank() }?.let { return File(it) }
        return File(kairosRoot(env, osName, home), "v3")
    }

    /** Racine `Kairos` de platformdirs pour cet OS. */
    fun kairosRoot(env: Map<String, String>, osName: String, home: String): File {
        val os = osName.lowercase()
        return when {
            os.startsWith("windows") ->
                File(env["LOCALAPPDATA"] ?: "$home\\AppData\\Local", "Kairos")
            os.startsWith("mac") -> File(home, "Library/Application Support/Kairos")
            else -> File(env["XDG_DATA_HOME"]?.takeIf { it.isNotBlank() } ?: "$home/.local/share", "Kairos")
        }
    }
}
