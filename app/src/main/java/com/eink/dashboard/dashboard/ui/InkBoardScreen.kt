package com.eink.dashboard.dashboard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.DeviceProfile
import com.eink.dashboard.core.time.TimeFormat
import com.eink.dashboard.dashboard.DashboardModuleRegistry
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.modules.battery.BatteryModule
import com.eink.dashboard.modules.battery.data.BatteryStatus
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.model.AgendaDay
import com.eink.dashboard.modules.calendar.model.AgendaEvent
import com.eink.dashboard.modules.calendar.ui.CalendarFormat
import com.eink.dashboard.modules.clock.ClockModule
import com.eink.dashboard.modules.taskforge.TaskForgeModule
import com.eink.dashboard.modules.taskforge.model.TaskForgePriority
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.modules.weather.model.DailyConditions
import com.eink.dashboard.modules.weather.model.WeatherSnapshot
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.eink.dashboard.modules.weather.ui.WeatherFormat
import com.eink.dashboard.modules.weather.ui.icon
import com.eink.dashboard.ui.ink.InkBadge
import com.eink.dashboard.ui.ink.InkCard
import com.eink.dashboard.ui.ink.InkCardBody
import com.eink.dashboard.ui.ink.InkCardFooter
import com.eink.dashboard.ui.ink.InkCardHeader
import com.eink.dashboard.ui.ink.InkCardTab
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkDateBlock
import com.eink.dashboard.ui.ink.InkDivider
import com.eink.dashboard.ui.ink.InkEmpty
import com.eink.dashboard.ui.ink.InkEyebrow
import com.eink.dashboard.ui.ink.InkIcon
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkKv
import com.eink.dashboard.ui.ink.InkListItem
import com.eink.dashboard.ui.ink.InkMeter
import com.eink.dashboard.ui.ink.InkRadius
import com.eink.dashboard.ui.ink.InkSize
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkStatGrid
import com.eink.dashboard.ui.ink.InkStroke
import com.eink.dashboard.ui.ink.InkTaskRow
import com.eink.dashboard.ui.ink.InkType
import com.eink.dashboard.ui.ink.InkWeatherSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Landscape board for the M103 (1248 × 936dp), composed from Ink UI cards the
 * way `ui-kit/examples/dashboard.html` is: a 2 × 2 grid of framed cards with
 * black header bands. Top row: "Now" (hero clock + calendar leaf + battery) and
 * Weather (current + 7-day strip). Bottom row: Calendar agenda and TaskForge.
 * Everything fits one screen — no scrolling on a wall-mounted panel.
 */
@Composable
fun InkBoardScreen(
    registry: DashboardModuleRegistry,
    epochMs: Long,
    modifier: Modifier = Modifier,
) {
    val clockModule = registry.byId("clock") as? ClockModule
    val batteryModule = registry.byId("battery") as? BatteryModule
    val weatherModule = registry.byId("weather") as? WeatherModule
    val calendarModule = registry.byId("calendar") as? CalendarModule
    val taskForgeModule = registry.byId("taskforge") as? TaskForgeModule

    val clockFace = clockModule?.face?.collectAsStateWithLifecycle()?.value
    val battery = batteryModule?.status?.collectAsStateWithLifecycle()?.value
    val weather = weatherModule?.snapshot?.collectAsStateWithLifecycle()?.value
    val agenda = calendarModule?.agenda?.collectAsStateWithLifecycle()?.value
    val board = taskForgeModule?.board?.collectAsStateWithLifecycle()?.value
    val calendarState = calendarModule?.state?.collectAsStateWithLifecycle()?.value
    val weatherState = weatherModule?.state?.collectAsStateWithLifecycle()?.value
    val taskForgeState = taskForgeModule?.state?.collectAsStateWithLifecycle()?.value
    val moduleStates = listOfNotNull(
        clockModule?.state?.collectAsStateWithLifecycle()?.value,
        batteryModule?.state?.collectAsStateWithLifecycle()?.value,
        weatherState,
        calendarState,
        taskForgeState,
    )
    val scope = rememberCoroutineScope()

    InkBoard(
        epochMs = epochMs,
        clock = clockFace?.time ?: TimeFormat.clock(epochMs),
        battery = battery,
        weather = weather,
        weatherState = weatherState,
        agendaDays = agenda?.days.orEmpty(),
        calendarState = calendarState,
        tasks = board?.tasks.orEmpty(),
        totalTasks = board?.totalMatches ?: 0,
        taskForgeState = taskForgeState,
        updatedEpochMs = moduleStates.mapNotNull { it.lastUpdatedEpochMs }.maxOrNull() ?: epochMs,
        onComplete = { task -> taskForgeModule?.let { scope.launch { it.complete(task) } } },
        modifier = modifier,
    )
}

/** Stateless board body: everything it draws comes in as plain values (previewable, screenshot-testable). */
@Composable
internal fun InkBoard(
    epochMs: Long,
    clock: String,
    battery: BatteryStatus?,
    weather: WeatherSnapshot?,
    weatherState: ModuleState?,
    agendaDays: List<AgendaDay>,
    calendarState: ModuleState?,
    tasks: List<TaskForgeTask>,
    totalTasks: Int,
    taskForgeState: ModuleState?,
    updatedEpochMs: Long,
    onComplete: (TaskForgeTask) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val today = now.toLocalDate()

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s5),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.43f),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s5),
        ) {
            NowCard(
                clock = clock,
                today = today,
                batteryPercent = battery?.percent,
                charging = battery?.charging == true,
                sunTimes = weather?.today,
                updatedEpochMs = updatedEpochMs,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            WeatherCard(
                weather = weather,
                state = weatherState,
                today = today,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().weight(0.57f),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s5),
        ) {
            AgendaCard(
                agendaDays = agendaDays,
                state = calendarState,
                today = today,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            TasksCard(
                tasks = tasks,
                total = totalTasks,
                state = taskForgeState,
                today = today,
                onComplete = onComplete,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Now: hero clock + calendar leaf, battery meter, sun times in the footer band.
// ---------------------------------------------------------------------------

@Composable
private fun NowCard(
    clock: String,
    today: LocalDate,
    batteryPercent: Int?,
    charging: Boolean,
    sunTimes: DailyConditions?,
    updatedEpochMs: Long,
    modifier: Modifier,
) {
    InkCard(modifier = modifier) {
        InkCardTab("Now")
        InkCardBody(fill = true, spacing = InkSpace.s4) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(InkSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = clock,
                    style = InkType.hero,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                InkDateBlock(
                    month = MONTH.format(today),
                    day = today.dayOfMonth.toString(),
                    weekday = WEEKDAY.format(today).lowercase(Locale.ENGLISH),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InkEyebrow("Battery")
                InkMeter(percent = batteryPercent, label = batteryPercent?.let { "$it%" } ?: "—")
                if (charging) InkBadge("Charging", icon = InkIcons.BatteryCharging)
            }
        }
        val sun = listOfNotNull(
            sunTimes?.sunrise?.let { "Sunrise ${HOUR_MIN.format(it)}" },
            sunTimes?.sunset?.let { "Sunset ${HOUR_MIN.format(it)}" },
        ).joinToString(" · ").ifEmpty { null }
        InkCardFooter(
            start = "Week ${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · Updated ${TimeFormat.clock(updatedEpochMs)}",
            meta = sun ?: DeviceProfile.MODEL,
        )
    }
}

// ---------------------------------------------------------------------------
// Weather: current conditions + 7-day stat strip.
// ---------------------------------------------------------------------------

@Composable
private fun WeatherCard(
    weather: WeatherSnapshot?,
    state: ModuleState?,
    today: LocalDate,
    modifier: Modifier,
) {
    val current = weather?.current
    InkCard(modifier = modifier) {
        InkCardHeader(
            title = "Weather",
            icon = current?.condition?.icon ?: InkIcons.Sun,
            meta = listOfNotNull(weather?.location?.label, state.staleHint()).joinToString(" · ").ifEmpty { null },
        )
        InkCardBody(fill = true, spacing = InkSpace.s4) {
            if (current == null) {
                Text(
                    text = (state as? ModuleState.Error)?.message ?: "Waiting for the first forecast…",
                    style = InkType.small,
                    color = InkColors.Ink3,
                )
            } else {
                InkWeatherSummary(
                    temperature = WeatherFormat.temperature(current.temperatureC),
                    condition = current.condition.label,
                    meta = weatherMeta(weather.today),
                    icon = current.condition.icon,
                )
                InkKv(
                    listOf(
                        "Feels like" to (current.apparentTemperatureC?.let(WeatherFormat::temperature) ?: "—"),
                        "Humidity" to WeatherFormat.humidity(current.humidityPercent),
                        "Wind" to WeatherFormat.wind(current.windKmh),
                    ),
                )
            }
            Box(Modifier.weight(1f))
            ForecastStrip(weather?.forecast.orEmpty(), today)
        }
    }
}

private fun weatherMeta(day: DailyConditions?): String = listOfNotNull(
    day?.let { "High ${WeatherFormat.temperature(it.temperatureMaxC)} · Low ${WeatherFormat.temperature(it.temperatureMinC)}" },
    day?.precipitationProbabilityMaxPercent?.takeIf { it > 0 }?.let { "Rain $it%" },
).joinToString(" · ")

@Composable
private fun ForecastStrip(forecast: List<DailyConditions>, today: LocalDate) {
    val days = forecast.take(7).map { Triple(WeatherFormat.dayLabel(it.date, today), it.condition, WeatherFormat.highLow(it)) }
        .ifEmpty {
            (0L..6L).map { offset ->
                val date = today.plusDays(offset)
                Triple(WeatherFormat.dayLabel(date, today), WmoCondition.UNKNOWN, "—° / —°")
            }
        }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(InkStroke.hair, InkColors.Ink4, RoundedCornerShape(InkRadius.xs)),
    ) {
        InkStatGrid(cells = days.size, contentPadding = androidx.compose.foundation.layout.PaddingValues(InkSpace.s2)) { index ->
            val (label, condition, temps) = days[index]
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(InkSpace.s1),
            ) {
                InkEyebrow(label, color = if (index == 0) InkColors.Ink else InkColors.Ink3)
                InkIcon(condition.icon, size = InkSize.iconLg)
                Text(text = temps, style = InkType.meta, maxLines = 1, textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Calendar agenda: day groups of ruled rows with a time column.
// ---------------------------------------------------------------------------

@Composable
private fun AgendaCard(
    agendaDays: List<AgendaDay>,
    state: ModuleState?,
    today: LocalDate,
    modifier: Modifier,
) {
    val days = visibleAgendaDays(agendaDays)
    val shown = days.sumOf { it.events.size }
    val total = agendaDays.sumOf { it.events.size }
    InkCard(modifier = modifier) {
        InkCardHeader(
            title = "Calendar",
            icon = InkIcons.Calendar,
            meta = listOfNotNull(
                if (total == 1) "1 event" else "$total events",
                state.staleHint(),
            ).joinToString(" · "),
        )
        Column(modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
            if (days.isEmpty()) {
                InkEmpty(
                    title = if (state is ModuleState.Error) "Calendar unavailable" else "No upcoming events",
                    icon = InkIcons.Calendar,
                    hint = (state as? ModuleState.Error)?.message ?: "Your agenda is clear.",
                    modifier = Modifier.fillMaxHeight(),
                )
                return@Column
            }
            days.forEachIndexed { index, visibleDay ->
                if (index > 0) InkDivider(bold = true)
                DayLabel(visibleDay.day.date, today)
                visibleDay.events.forEachIndexed { eventIndex, event ->
                    if (eventIndex > 0) InkDivider(color = InkColors.Ink3, modifier = Modifier.padding(horizontal = InkSpace.s3))
                    AgendaRow(event)
                }
            }
        }
        if (total > shown) InkCardFooter(start = "+ ${total - shown} more")
    }
}

@Composable
private fun DayLabel(date: LocalDate, today: LocalDate) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = InkSpace.s3, end = InkSpace.s3, top = InkSpace.s3, bottom = InkSpace.s1),
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (date) {
            today -> InkBadge("Today")
            today.plusDays(1) -> InkBadge("Tomorrow", outline = true)
            else -> Unit
        }
        InkEyebrow(AGENDA_DAY.format(date))
    }
}

@Composable
private fun AgendaRow(event: AgendaEvent) {
    val zone = ZoneId.systemDefault()
    InkListItem(
        title = CalendarFormat.titleLabel(event),
        sub = agendaMeta(event, zone),
        inset = InkSpace.s3,
        strong = true,
        leading = {
            Text(
                text = CalendarFormat.timeLabel(event.event, zone),
                style = InkType.bodyStrong.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                modifier = Modifier.width(InkSpace.s12 + InkSpace.s8),
            )
        },
    )
}

private fun agendaMeta(event: AgendaEvent, zone: ZoneId): String = buildList {
    if (!event.event.isAllDay) add("until ${HOUR_MIN.format(Instant.ofEpochMilli(event.event.endMs).atZone(zone))}")
    event.event.location?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    if (event.duplicateCount > 1) add("${event.duplicateCount} calendars")
}.joinToString(" · ")

private data class VisibleAgendaDay(val day: AgendaDay, val events: List<AgendaEvent>)

private fun visibleAgendaDays(agendaDays: List<AgendaDay>, limit: Int = 6): List<VisibleAgendaDay> {
    var remaining = limit
    return agendaDays.mapNotNull { day ->
        if (remaining == 0) return@mapNotNull null
        val events = day.events.take(remaining)
        remaining -= events.size
        events.takeIf { it.isNotEmpty() }?.let { VisibleAgendaDay(day, it) }
    }
}

// ---------------------------------------------------------------------------
// TaskForge: ruled to-do rows with checkboxes.
// ---------------------------------------------------------------------------

@Composable
private fun TasksCard(
    tasks: List<TaskForgeTask>,
    total: Int,
    state: ModuleState?,
    today: LocalDate,
    onComplete: (TaskForgeTask) -> Unit,
    modifier: Modifier,
) {
    val visible = tasks.take(7)
    InkCard(modifier = modifier) {
        InkCardHeader(
            title = "TaskForge",
            icon = InkIcons.Todo,
            meta = listOfNotNull("$total open", state.staleHint()).joinToString(" · "),
        )
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds().padding(vertical = InkSpace.s1),
        ) {
            if (visible.isEmpty()) {
                InkEmpty(
                    title = if (state is ModuleState.Error) "TaskForge unavailable" else "No matching tasks",
                    icon = InkIcons.Todo,
                    hint = (state as? ModuleState.Error)?.message ?: "Nothing due in this view.",
                    modifier = Modifier.fillMaxHeight(),
                )
                return@Column
            }
            visible.forEachIndexed { index, task ->
                if (index > 0) InkDivider(color = InkColors.Ink3, modifier = Modifier.padding(horizontal = InkSpace.s3))
                InkTaskRow(
                    title = task.title,
                    sub = taskMeta(task, today).ifEmpty { null },
                    strong = task.priority.rank >= TaskForgePriority.HIGH.rank,
                    enabled = task.canComplete,
                    onCheck = if (task.canComplete) ({ onComplete(task) }) else null,
                    leading = if (task.isRecurring) ({ RecurringMark() }) else null,
                    modifier = Modifier.padding(horizontal = InkSpace.s3, vertical = InkSpace.s2),
                )
            }
        }
        val remaining = (total - visible.size).coerceAtLeast(0)
        if (remaining > 0) InkCardFooter(start = "+ $remaining more")
    }
}

/** Recurring tasks complete in TaskForge itself: a grey framed repeat glyph instead of a checkbox. */
@Composable
private fun RecurringMark() {
    Box(
        modifier = Modifier
            .size(InkSize.controlXs)
            .border(InkStroke.regular, InkColors.Ink4, RoundedCornerShape(InkRadius.xs)),
        contentAlignment = Alignment.Center,
    ) {
        InkIcon(InkIcons.Refresh, size = InkSize.iconSm, tint = InkColors.Ink3)
    }
}

private fun taskMeta(task: TaskForgeTask, today: LocalDate): String {
    if (task.isRecurring) {
        return "Repeats ${task.recurrence.orEmpty()} · complete in TaskForge"
    }
    return buildList {
        if (task.priority != TaskForgePriority.NORMAL) add(task.priority.name.lowercase().replaceFirstChar { it.uppercase() })
        task.due?.let { due ->
            add(
                when (due) {
                    today -> "Due today"
                    today.plusDays(1) -> "Due tomorrow"
                    else -> if (due.isBefore(today)) "Overdue · ${TASK_DATE.format(due)}" else "Due ${TASK_DATE.format(due)}"
                },
            )
        }
        (task.subsection?.takeIf(String::isNotBlank) ?: task.section?.takeIf(String::isNotBlank))?.let(::add)
        addAll(task.tags.sorted().map { "#$it" })
    }.joinToString(" · ")
}

private fun ModuleState?.staleHint(): String? = when (this) {
    is ModuleState.Ok -> if (isStale) "stale" else null
    is ModuleState.Error -> "error"
    else -> null
}

private val MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)
private val AGENDA_DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val HOUR_MIN = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val TASK_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
