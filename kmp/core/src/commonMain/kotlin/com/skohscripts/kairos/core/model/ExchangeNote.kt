package com.skohscripts.kairos.core.model

import kotlin.time.Instant

/**
 * Note reçue de l'autre côté d'un échange d'équipe (docs/spec/equipe-echanges.md § Notes, issue #120) : son [text], la date du
 * paquet ou du rapport qui l'a portée ([at]) et son auteur ([from], nom du manager ou du membre).
 */
data class ExchangeNote(val text: String, val at: Instant, val from: String) {
    /** Colonne `task.note_in` : « instant␟auteur␟texte » (séparateur U+001F, retiré de l'auteur). */
    fun encode(): String = "$at$SEPARATOR${from.replace(SEPARATOR, "")}$SEPARATOR$text"

    companion object {
        /** Longueur maximale d'une note : au-delà, un texte reçu est tronqué. */
        const val MAX_LENGTH = 500

        private const val SEPARATOR = "\u001F"

        /** Texte d'une note à envoyer : espaces retirés aux bords, borné à [MAX_LENGTH] ; `null` s'il est vide. */
        fun clean(text: String?): String? = text?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }

        /** Lecture de la colonne ; `null` si absente ou illisible. */
        fun decode(text: String?): ExchangeNote? {
            val parts = text?.split(SEPARATOR, limit = 3)?.takeIf { it.size == 3 } ?: return null
            val at = runCatching { Instant.parse(parts[0]) }.getOrNull() ?: return null
            return ExchangeNote(parts[2], at, parts[1])
        }
    }
}
