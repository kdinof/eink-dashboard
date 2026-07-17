package com.eink.dashboard.modules.calendar.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo

/**
 * Reads calendars and expanded event instances from the system
 * `CalendarContract` provider (the plan's chosen approach — no Google REST API,
 * no OAuth, no Cloud project; it reuses the account already synced on the device
 * and works offline for already-synced events).
 *
 * Only `READ_CALENDAR` is used. The module guarantees the permission is held
 * before these methods run. Queries use `Instances.query`, which expands recurring
 * events into individual occurrences within the window, so this class never has to
 * interpret RRULEs.
 */
class AndroidCalendarDataSource(context: Context) : CalendarDataSource {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    override fun listCalendars(): List<CalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val out = mutableListOf<CalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            while (cursor.moveToNext()) {
                out += CalendarInfo(
                    id = cursor.getLong(idCol),
                    displayName = cursor.getStringOrEmpty(nameCol),
                    accountName = cursor.getStringOrEmpty(accountCol),
                )
            }
        }
        return out
    }

    override fun queryInstances(
        startMs: Long,
        endMs: Long,
        calendarIds: Set<Long>?,
    ): List<CalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
        )

        // Instances are queried by appending the [start, end] window to the URI.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, startMs)
            ContentUris.appendId(this, endMs)
        }.build()

        // Restrict to selected calendars in SQL when a subset is chosen.
        val selection: String?
        val selectionArgs: Array<String>?
        if (calendarIds.isNullOrEmpty()) {
            selection = null
            selectionArgs = null
        } else {
            val placeholders = calendarIds.joinToString(",") { "?" }
            selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
            selectionArgs = calendarIds.map { it.toString() }.toTypedArray()
        }

        val out = mutableListOf<CalendarEvent>()
        resolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val eventIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
            val calendarIdCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
            val titleCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val beginCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val endCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val allDayCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val locationCol = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
            while (cursor.moveToNext()) {
                out += CalendarEvent(
                    eventId = cursor.getLong(eventIdCol),
                    calendarId = cursor.getLong(calendarIdCol),
                    title = cursor.getStringOrEmpty(titleCol).ifBlank { "(no title)" },
                    beginMs = cursor.getLong(beginCol),
                    endMs = cursor.getLong(endCol),
                    isAllDay = cursor.getInt(allDayCol) == 1,
                    location = cursor.getString(locationCol)?.takeIf { it.isNotBlank() },
                )
            }
        }
        return out
    }

    private fun Cursor.getStringOrEmpty(column: Int): String =
        if (isNull(column)) "" else getString(column) ?: ""
}
