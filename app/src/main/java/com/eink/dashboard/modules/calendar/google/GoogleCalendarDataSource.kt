package com.eink.dashboard.modules.calendar.google

import com.eink.dashboard.modules.calendar.data.CalendarDataSource
import com.eink.dashboard.modules.calendar.model.CalendarEvent
import com.eink.dashboard.modules.calendar.model.CalendarInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

class GoogleCalendarApiException(val statusCode: Int, val reason: String?) :
    IllegalStateException("Google Calendar API HTTP $statusCode")

@Serializable private data class CalendarListPage(
    val items: List<GoogleCalendarDto> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)
@Serializable private data class GoogleCalendarDto(
    val id: String,
    val summary: String = "",
)
@Serializable private data class EventPage(
    val items: List<GoogleEventDto> = emptyList(),
    @SerialName("nextPageToken") val nextPageToken: String? = null,
)
@Serializable private data class GoogleEventDto(
    val id: String,
    val summary: String = "(untitled)",
    val location: String? = null,
    val start: GoogleEventTime,
    val end: GoogleEventTime,
)
@Serializable private data class GoogleEventTime(val date: String? = null, val dateTime: String? = null)

/** Read-only Google Calendar API v3 source using the reader's OAuth access token. */
class GoogleCalendarDataSource(
    private val auth: GoogleAccessTokenProvider,
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val apiBase: HttpUrl = DEFAULT_API,
) : CalendarDataSource {
    private val rawIds = ConcurrentHashMap<Long, String>()

    override fun listCalendars(): List<CalendarInfo> {
        val result = mutableListOf<CalendarInfo>()
        var page: String? = null
        do {
            val url = apiBase.newBuilder().addPathSegments("users/me/calendarList")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val parsed = json.decodeFromString<CalendarListPage>(get(url.toString()))
            parsed.items.forEach { item ->
                val stable = stableId(item.id)
                rawIds[stable] = item.id
                result += CalendarInfo(stable, item.summary, "Google")
            }
            page = parsed.nextPageToken
        } while (page != null)
        return result
    }

    override fun queryInstances(startMs: Long, endMs: Long, calendarIds: Set<Long>?): List<CalendarEvent> {
        val selected = calendarIds.orEmpty().mapNotNull { id -> rawIds[id]?.let { id to it } }
        return selected.flatMap { (stableCalendarId, rawCalendarId) ->
            loadEvents(rawCalendarId, stableCalendarId, startMs, endMs)
        }
    }

    private fun loadEvents(rawCalendarId: String, calendarId: Long, startMs: Long, endMs: Long): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        var page: String? = null
        do {
            val url = apiBase.newBuilder()
                .addPathSegment("calendars").addPathSegment(rawCalendarId).addPathSegment("events")
                .addQueryParameter("timeMin", Instant.ofEpochMilli(startMs).toString())
                .addQueryParameter("timeMax", Instant.ofEpochMilli(endMs).toString())
                .addQueryParameter("singleEvents", "true")
                .addQueryParameter("orderBy", "startTime")
                .apply { page?.let { addQueryParameter("pageToken", it) } }
                .build()
            val parsed = json.decodeFromString<EventPage>(get(url.toString()))
            parsed.items.mapNotNullTo(result) { it.toModel(calendarId) }
            page = parsed.nextPageToken
        } while (page != null)
        return result
    }

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${auth.accessToken()}").build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw GoogleCalendarApiException(response.code, errorReason(body))
            body
        }
    }

    private fun errorReason(body: String): String? = runCatching {
        val error = json.parseToJsonElement(body).jsonObject["error"]?.jsonObject ?: return@runCatching null
        val legacy = error["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
        val detail = error["details"]?.jsonArray?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
        (legacy ?: detail ?: error["status"]?.jsonPrimitive?.contentOrNull)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,80}")) }
    }.getOrNull()

    private fun GoogleEventDto.toModel(calendarId: Long): CalendarEvent? {
        val allDay = start.date != null
        val begin = start.epochMs() ?: return null
        val finish = end.epochMs() ?: return null
        return CalendarEvent(
            eventId = stableId("$id:$begin"),
            calendarId = calendarId,
            title = summary,
            beginMs = begin,
            endMs = finish,
            isAllDay = allDay,
            location = location,
        )
    }

    private fun GoogleEventTime.epochMs(): Long? = when {
        dateTime != null -> OffsetDateTime.parse(dateTime).toInstant().toEpochMilli()
        date != null -> LocalDate.parse(date).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        else -> null
    }

    private fun stableId(value: String): Long =
        // The local web API serializes ids as JSON numbers, so keep them inside
        // JavaScript's exact integer range while retaining 53 deterministic bits.
        ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(value.toByteArray())).long and ((1L shl 53) - 1)

    private companion object { val DEFAULT_API = "https://www.googleapis.com/calendar/v3/".toHttpUrl() }
}
