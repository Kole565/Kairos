package com.skohscripts.kairos.ui.reminders

import com.skohscripts.kairos.core.reminders.Reminder
import com.skohscripts.kairos.core.reminders.ReminderKind
import com.skohscripts.kairos.ui.chrono.ChronoTexts
import com.skohscripts.kairos.ui.generated.resources.Res
import com.skohscripts.kairos.ui.generated.resources.notif_channel_reminders
import com.skohscripts.kairos.ui.generated.resources.reminder_clock
import com.skohscripts.kairos.ui.generated.resources.reminder_fixed_body
import com.skohscripts.kairos.ui.generated.resources.reminder_fixed_now_body
import com.skohscripts.kairos.ui.generated.resources.reminder_next_body
import kotlinx.datetime.LocalDateTime
import org.jetbrains.compose.resources.getString

/**
 * Textes des rappels hors composition (docs/spec/rappels.md), dans la langue de l'appareil : pour la veille de l'application
 * et pour les alarmes Android, construites hors de l'interface.
 */
object ReminderTexts {
    /** « 14h00 », « 14:00 ». */
    suspend fun clock(t: LocalDateTime): String =
        getString(Res.string.reminder_clock, t.hour.toString().padStart(2, '0'), t.minute.toString().padStart(2, '0'))

    /** « Commence à 14h00 (dans 10 min) », « Commence à 14h00 », « C'est l'heure : prévue jusqu'à 11h30 ». */
    suspend fun body(reminder: Reminder): String = when (reminder.kind) {
        ReminderKind.FIXED -> {
            val lead = ((reminder.start.hour * 60 + reminder.start.minute) - (reminder.at.hour * 60 + reminder.at.minute)).let { if (it < 0) it + 24 * 60 else it }
            if (lead == 0) getString(Res.string.reminder_fixed_now_body, clock(reminder.start))
            else getString(Res.string.reminder_fixed_body, clock(reminder.start), ChronoTexts.duration(lead))
        }
        ReminderKind.NEXT -> getString(Res.string.reminder_next_body, clock(reminder.end))
    }

    suspend fun channel(): String = getString(Res.string.notif_channel_reminders)
}
