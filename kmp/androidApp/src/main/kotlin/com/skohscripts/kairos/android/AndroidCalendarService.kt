package com.skohscripts.kairos.android

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import com.skohscripts.kairos.core.calendar.DeviceCalendarData
import com.skohscripts.kairos.core.calendar.DeviceEvent
import com.skohscripts.kairos.core.calendar.ExternalBlocks
import com.skohscripts.kairos.ui.app.CalendarService
import com.skohscripts.kairos.ui.app.DeviceCalendar
import com.skohscripts.kairos.ui.app.DeviceCalendarState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * Calendriers du téléphone (docs/spec/connexions-externes.md § Android) : `READ_CALENDAR` demandée seulement à l'activation,
 * lecture de `CalendarContract.Instances` (le système déplie déjà répétitions, exceptions et fuseaux) d'il y a un mois à dans
 * 6 mois, hors du fil principal ; rafraîchie à la reprise de l'activité et quand un agenda change. Interrupteur et sélection
 * dans les préférences de l'application (propres à l'appareil), jamais dans les réglages ni dans l'export.
 */
class AndroidCalendarService(context: Context, private val scope: CoroutineScope, private val clock: Clock = Clock.System) : CalendarService {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow<DeviceCalendarState>(DeviceCalendarState.Off)
    private val mutableData = MutableStateFlow(DeviceCalendarData())
    override val state: StateFlow<DeviceCalendarState> = mutableState.asStateFlow()
    override val data: StateFlow<DeviceCalendarData> = mutableData.asStateFlow()

    private var observer: ContentObserver? = null
    private var pending: Job? = null

    private val enabled get() = prefs.getBoolean(KEY_ENABLED, false)
    private val selected get() = prefs.getStringSet(KEY_SELECTED, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()

    private fun granted() = app.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override suspend fun enable() {
        val ok = granted() || withContext(Dispatchers.Main) { KairosProcess.activity?.requestPermission(Manifest.permission.READ_CALENDAR) ?: false }
        // Refus : Kairos reste tel quel, l'interrupteur aussi.
        if (!ok) return
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        refresh()
    }

    override suspend fun disable() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        unobserve()
        mutableData.value = DeviceCalendarData()
        mutableState.value = DeviceCalendarState.Off
    }

    override suspend fun select(ids: Set<Long>) {
        prefs.edit().putStringSet(KEY_SELECTED, ids.map { it.toString() }.toSet()).apply()
        refresh()
    }

    override fun openSystemSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        app.startActivity(intent)
    }

    /** Relit les calendriers et les événements choisis ; autorisation retirée → `Denied`, plus aucun créneau. */
    suspend fun refresh() {
        if (!enabled) {
            mutableState.value = DeviceCalendarState.Off
            return
        }
        if (!granted()) {
            unobserve()
            mutableData.value = DeviceCalendarData()
            mutableState.value = DeviceCalendarState.Denied
            return
        }
        observe()
        val (calendars, events) = withContext(Dispatchers.IO) {
            val calendars = runCatching { calendars() }.getOrDefault(emptyList())
            val chosen = selected.filter { id -> calendars.any { it.id == id } }.toSet()
            calendars to runCatching { events(chosen) }.getOrDefault(emptyList())
        }
        mutableState.value = DeviceCalendarState.On(calendars, selected)
        mutableData.value = ExternalBlocks.map(events, TimeZone.currentSystemDefault())
    }

    private fun calendars(): List<DeviceCalendar> {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME)
        return app.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, "${CalendarContract.Calendars.VISIBLE} = 1", null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(DeviceCalendar(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty())) }
        }.orEmpty().sortedBy { it.name.lowercase() }
    }

    private fun events(ids: Set<Long>): List<DeviceEvent> {
        if (ids.isEmpty()) return emptyList()
        val now = clock.now()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, (now - LOOKBACK).toEpochMilliseconds())
            ContentUris.appendId(it, (now + AHEAD).toEpochMilliseconds())
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.AVAILABILITY, CalendarContract.Instances.STATUS,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS, CalendarContract.Instances.UID_2445,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME, CalendarContract.Instances.EVENT_ID,
        )
        val where = "${CalendarContract.Instances.CALENDAR_ID} IN (${ids.joinToString(",")})"
        return app.contentResolver.query(uri, projection, where, null, null)?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DeviceEvent(
                            calendarName = c.getString(8).orEmpty(),
                            // Sans UID iCalendar (agenda local) : l'identifiant de l'événement, stable sur l'appareil.
                            uid = c.getString(7)?.takeIf { it.isNotBlank() } ?: "event-${c.getLong(9)}",
                            title = c.getString(0).orEmpty().replace(Regex("\\s+"), " ").trim().take(200),
                            begin = Instant.fromEpochMilliseconds(c.getLong(1)),
                            end = Instant.fromEpochMilliseconds(c.getLong(2)),
                            allDay = c.getInt(3) == 1,
                            free = c.getInt(4) == CalendarContract.Events.AVAILABILITY_FREE,
                            cancelled = c.getInt(5) == CalendarContract.Events.STATUS_CANCELED,
                            declined = c.getInt(6) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    /** Un agenda a changé : relecture regroupée (1 s), pour qu'une synchronisation de cent événements ne relise qu'une fois. */
    private fun observe() {
        if (observer != null) return
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                pending?.cancel()
                pending = scope.launch {
                    delay(1_000)
                    refresh()
                }
            }
        }
        runCatching { app.contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, o) }.onSuccess { observer = o }
    }

    private fun unobserve() {
        observer?.let { runCatching { app.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    private companion object {
        const val PREFS = "phone-calendars"
        const val KEY_ENABLED = "enabled"
        const val KEY_SELECTED = "selected"
        val LOOKBACK = 31.days
        val AHEAD = 184.days
    }
}
