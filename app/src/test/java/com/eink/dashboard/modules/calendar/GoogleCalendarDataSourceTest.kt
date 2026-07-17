package com.eink.dashboard.modules.calendar

import com.eink.dashboard.modules.calendar.google.GoogleAccessTokenProvider
import com.eink.dashboard.modules.calendar.google.GoogleCalendarDataSource
import com.google.common.truth.Truth.assertThat
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.Instant

class GoogleCalendarDataSourceTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().also { it.start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun listsCalendarsAndMapsTimedAndAllDayEvents() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items":[{"id":"primary@example.com","summary":"Personal"}]}
        """.trimIndent()))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""
            {"items":[
              {"id":"timed","summary":"Call","start":{"dateTime":"2026-07-17T10:00:00+05:00"},"end":{"dateTime":"2026-07-17T11:00:00+05:00"}},
              {"id":"all-day","summary":"Holiday","start":{"date":"2026-07-18"},"end":{"date":"2026-07-19"}}
            ]}
        """.trimIndent()))
        val source = GoogleCalendarDataSource(
            auth = GoogleAccessTokenProvider { "access" },
            apiBase = server.url("/calendar/v3/"),
        )

        val calendar = source.listCalendars().single()
        val events = source.queryInstances(0, Long.MAX_VALUE, setOf(calendar.id))

        assertThat(calendar.displayName).isEqualTo("Personal")
        assertThat(events.map { it.title }).containsExactly("Call", "Holiday")
        assertThat(events.first { it.title == "Call" }.beginMs)
            .isEqualTo(Instant.parse("2026-07-17T05:00:00Z").toEpochMilli())
        assertThat(events.first { it.title == "Holiday" }.isAllDay).isTrue()
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer access")
    }
}
