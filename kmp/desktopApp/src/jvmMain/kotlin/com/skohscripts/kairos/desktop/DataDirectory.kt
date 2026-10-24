package com.skohscripts.kairos.desktop

import java.io.File

/**
 * Dossier des données sur le bureau (docs/spec/distribution.md § Bureau) : la
 * racine `Kairos` de platformdirs (`user_data_dir("Kairos")`), sous-dossier
 * `v3`. Ce chemin ne change pas : c'est là que vivent les données des
 * utilisateurs.
 *
 * `KAIROS_DATA_DIR` prime (mode portable, tests) ; [resolveAll] ajoute `--data-dir=` et le dossier choisi dans les
 * Réglages (docs/spec/bureau-avance.md).
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

    /** D'où vient le dossier ouvert (docs/spec/bureau-avance.md). */
    enum class Source { ARGUMENT, ENVIRONMENT, CHOSEN, DEFAULT }

    /** Dossier ouvert, sa [source], et le dossier choisi qui n'a pas pu servir ([failed]). */
    data class Resolved(val dir: File, val source: Source, val default: File, val failed: File? = null)

    /** Option de lancement `--data-dir=<dossier>`. */
    const val ARGUMENT = "--data-dir="

    /**
     * Dossier des données au démarrage (docs/spec/bureau-avance.md) : `--data-dir=`, puis `KAIROS_DATA_DIR`, puis le dossier
     * choisi dans les Réglages (`data-location.txt` de la racine, avec la copie en attente faite ici, avant toute ouverture de
     * base), sinon le dossier par défaut.
     */
    fun resolveAll(
        args: List<String> = emptyList(),
        env: Map<String, String> = System.getenv(),
        osName: String = System.getProperty("os.name"),
        home: String = System.getProperty("user.home"),
    ): Resolved {
        val root = kairosRoot(env, osName, home)
        val default = File(root, "v3")
        args.firstOrNull { it.startsWith(ARGUMENT) }?.substringAfter(ARGUMENT)?.takeIf { it.isNotBlank() }
            ?.let { return Resolved(File(it), Source.ARGUMENT, default) }
        env["KAIROS_DATA_DIR"]?.takeIf { it.isNotBlank() }?.let { return Resolved(File(it), Source.ENVIRONMENT, default) }
        return DataFolder.applyPending(root, default)
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
