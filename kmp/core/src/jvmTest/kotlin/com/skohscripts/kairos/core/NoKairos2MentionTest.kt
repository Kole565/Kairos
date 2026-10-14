package com.skohscripts.kairos.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Garde du retrait de l'ancienne version (docs/spec/retrait-kairos2.md) : aucune mention de la version 2 dans le
 * code, les ressources, les specs, les README, la page du site et les fiches des magasins. L'histoire reste
 * (notes de version publiées, plan de la réécriture) : liste blanche ci-dessous.
 */
class NoKairos2MentionTest {
    // « retrait-kairos2.md » (le nom de la spec) n'est pas une mention.
    private val mention = Regex("""Kairos ?2(?![0-9])|(?<!retrait-)kairos2|KAIROS2""")

    private fun root(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "CLAUDE.md").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "racine du dépôt introuvable" }
    }

    private val allowed = setOf(
        "docs/spec/retrait-kairos2.md",
        "docs/plan-v3-kotlin.md",
        "kmp/core/src/jvmTest/kotlin/com/skohscripts/kairos/core/NoKairos2MentionTest.kt",
    )
    private val scanned = listOf("kmp", "docs", "site", "fdroid", "fastlane", ".github", ".claude", "README.md", "README.fr.md", "CLAUDE.md")
    private val textual = setOf("kt", "kts", "xml", "sq", "sqm", "md", "html", "txt", "yml", "yaml", "py", "sh", "json", "properties", "toml", "js", "css")

    @Test
    fun noMentionOfTheOldVersionRemains() {
        val root = root()
        val hits = scanned.map { File(root, it) }.filter { it.exists() }.flatMap { it.walkTopDown().onEnter { d -> d.name != "build" && d.name != ".gradle" && d.name != ".git" }.toList() }
            .filter { it.isFile && it.extension in textual }
            .filter { it.relativeTo(root).invariantSeparatorsPath !in allowed }
            // Les notes de version déjà publiées font partie de l'histoire.
            .filterNot { it.parentFile.name == "changelogs" }
            .flatMap { file -> file.readLines().withIndex().filter { mention.containsMatchIn(it.value) }.map { "${file.relativeTo(root).invariantSeparatorsPath}:${it.index + 1}" } }
        assertTrue(hits.isEmpty(), "mentions de l'ancienne version :\n" + hits.joinToString("\n"))
    }
}
