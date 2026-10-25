package com.skohscripts.kairos.core.calendar

/** Une propriété iCalendar : `NOM;PARAM=valeur:valeur` (noms et paramètres en majuscules). */
data class IcsProperty(val name: String, val params: Map<String, String>, val value: String) {
    fun param(key: String): String? = params[key]
}

/** Un composant `BEGIN:X … END:X` : ses propriétés dans l'ordre, et ses composants imbriqués. */
data class IcsComponent(val name: String, val properties: List<IcsProperty>, val children: List<IcsComponent>) {
    fun first(name: String): IcsProperty? = properties.firstOrNull { it.name == name }
    fun all(name: String): List<IcsProperty> = properties.filter { it.name == name }
    fun children(name: String): List<IcsComponent> = children.filter { it.name == name }
}

/**
 * Lecture d'un fichier iCalendar (RFC 5545) en arbre de composants (docs/spec/connexions-externes.md § Lecture iCalendar) : marque
 * d'ordre des octets, fins de ligne `CRLF` ou `LF`, lignes repliées, paramètres entre guillemets. Tolérante : une ligne
 * sans deux-points est ignorée, un `END` orphelin aussi, un composant non fermé est fermé à la fin du texte.
 */
object IcsParser {
    /** Le premier contenu non vide du texte est `BEGIN:VCALENDAR` : c'est un calendrier, pas un export Kairos. */
    fun isCalendar(text: String): Boolean =
        text.removePrefix("﻿").trimStart().take(15).equals("BEGIN:VCALENDAR", ignoreCase = true)

    /** Le premier `VCALENDAR` du texte ; `IllegalArgumentException` s'il n'y en a pas. */
    fun parse(text: String): IcsComponent {
        val root = Builder("ROOT", null)
        var current = root
        for (line in unfold(text)) {
            val property = property(line) ?: continue
            when (property.name) {
                "BEGIN" -> current = Builder(property.value.uppercase(), current).also { current.children += it }
                "END" -> if (current.parent != null && current.name == property.value.uppercase()) current = current.parent!!
                else -> current.properties += property
            }
        }
        val calendar = root.children.firstOrNull { it.name == "VCALENDAR" } ?: throw IllegalArgumentException("pas un calendrier")
        return calendar.build()
    }

    /** Lignes logiques : une ligne qui commence par une espace ou une tabulation prolonge la précédente (sans ce caractère). */
    internal fun unfold(text: String): List<String> {
        val out = mutableListOf<StringBuilder>()
        for (raw in text.removePrefix("﻿").split('\n')) {
            val line = raw.removeSuffix("\r")
            if ((line.startsWith(' ') || line.startsWith('\t')) && out.isNotEmpty()) out.last().append(line, 1, line.length)
            else out += StringBuilder(line)
        }
        return out.map { it.toString() }.filter { it.isNotBlank() }
    }

    /** `NOM;P1=a;P2="b:c":valeur` ; les deux-points et points-virgules entre guillemets ne coupent pas. */
    internal fun property(line: String): IcsProperty? {
        var quoted = false
        var nameEnd = -1
        var colon = -1
        for (i in line.indices) {
            when (line[i]) {
                '"' -> quoted = !quoted
                ';' -> if (!quoted && nameEnd < 0) nameEnd = i
                ':' -> if (!quoted) { colon = i; break }
            }
        }
        if (colon < 0) return null
        if (nameEnd < 0 || nameEnd > colon) nameEnd = colon
        val name = line.substring(0, nameEnd).trim().uppercase()
        if (name.isEmpty()) return null
        val params = LinkedHashMap<String, String>()
        if (nameEnd < colon) {
            for (part in splitOutsideQuotes(line.substring(nameEnd + 1, colon), ';')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                params[part.substring(0, eq).trim().uppercase()] = part.substring(eq + 1).trim().removeSurrounding("\"")
            }
        }
        return IcsProperty(name, params, line.substring(colon + 1))
    }

    /** Texte d'une valeur `TEXT` : `\\`, `\;`, `\,`, `\n` et `\N` défaits. */
    fun text(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    'n', 'N' -> out.append('\n')
                    else -> out.append(next)
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun splitOutsideQuotes(text: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        var quoted = false
        var start = 0
        for (i in text.indices) {
            when (text[i]) {
                '"' -> quoted = !quoted
                separator -> if (!quoted) { parts += text.substring(start, i); start = i + 1 }
            }
        }
        parts += text.substring(start)
        return parts
    }

    private class Builder(val name: String, val parent: Builder?) {
        val properties = mutableListOf<IcsProperty>()
        val children = mutableListOf<Builder>()
        fun build(): IcsComponent = IcsComponent(name, properties.toList(), children.map { it.build() })
    }
}
