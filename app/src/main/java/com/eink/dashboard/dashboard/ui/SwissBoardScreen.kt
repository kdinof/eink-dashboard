package com.eink.dashboard.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.DeviceProfile
import com.eink.dashboard.core.time.TimeFormat
import com.eink.dashboard.dashboard.DashboardModuleRegistry
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.modules.battery.BatteryModule
import com.eink.dashboard.modules.calendar.CalendarAgenda
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
import com.eink.dashboard.modules.weather.model.WmoCondition
import com.eink.dashboard.modules.weather.ui.WeatherFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Landscape board matching the 1248 × 936 "E-Ink / D · Swiss Board" reference.
 * The geometry intentionally uses the reference's dp values: the M103's
 * 1872 × 1404 panel at 240 dpi is exactly 1248 × 936 logical pixels in landscape.
 */
@Composable
fun SwissBoardScreen(
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
    val moduleStates = listOfNotNull(
        clockModule?.state?.collectAsStateWithLifecycle()?.value,
        batteryModule?.state?.collectAsStateWithLifecycle()?.value,
        weatherModule?.state?.collectAsStateWithLifecycle()?.value,
        calendarState,
        taskForgeModule?.state?.collectAsStateWithLifecycle()?.value,
    )
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        NowBand(
            clock = clockFace?.time ?: TimeFormat.clock(epochMs),
            batteryPercent = battery?.percent,
            charging = battery?.charging == true,
            weather = weather,
        )
        Rule(height = 1)
        ForecastRibbon(weather?.forecast.orEmpty(), Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate())
        Rule(height = 2, color = EinkPalette.Ink)
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AgendaColumn(
                agenda = agenda,
                today = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                modifier = Modifier.width(662.dp).fillMaxHeight(),
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(top = 20.dp)
                    .width(1.dp)
                    .background(EinkPalette.Line),
            )
            TasksColumn(
                tasks = board?.tasks.orEmpty(),
                total = board?.totalMatches ?: 0,
                today = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate(),
                onComplete = { task -> taskForgeModule?.let { scope.launch { it.complete(task) } } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Rule(height = 1)
        Footer(
            updatedEpochMs = moduleStates.mapNotNull { it.lastUpdatedEpochMs }.maxOrNull() ?: epochMs,
            calendarState = calendarState,
        )
    }
}

@Composable
private fun NowBand(
    clock: String,
    batteryPercent: Int?,
    charging: Boolean,
    weather: com.eink.dashboard.modules.weather.model.WeatherSnapshot?,
) {
    Row(modifier = Modifier.fillMaxWidth().height(243.dp)) {
        Column(
            modifier = Modifier.width(705.dp).fillMaxHeight().padding(top = 7.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = clock,
                color = EinkPalette.Ink,
                fontSize = 167.sp,
                lineHeight = 192.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-7).sp,
                maxLines = 1,
            )
            BatteryRow(percent = batteryPercent, charging = charging)
        }
        Box(
            modifier = Modifier
                .padding(top = 22.dp)
                .width(1.dp)
                .height(194.dp)
                .background(EinkPalette.Line),
        )
        WeatherNow(weather = weather, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun BatteryRow(percent: Int?, charging: Boolean) {
    val safePercent = percent?.coerceIn(0, 100) ?: 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(text = "BATTERY", fontSize = 11.5f, letterSpacing = 1.2f)
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(11.dp)
                .border(1.dp, EinkPalette.Ink)
                .padding(1.5.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(safePercent / 100f)
                    .background(EinkPalette.Ink),
            )
        }
        Text(
            text = percent?.let { "$it%" } ?: "—",
            color = EinkPalette.Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Label(
            text = if (charging) "CHARGING" else "ON BATTERY",
            fontSize = 11.5f,
            letterSpacing = 1.2f,
            weight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WeatherNow(
    weather: com.eink.dashboard.modules.weather.model.WeatherSnapshot?,
    modifier: Modifier,
) {
    val current = weather?.current
    Column(
        modifier = modifier.padding(top = 22.dp, start = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label(
            text = weather?.location?.label?.uppercase(Locale.ENGLISH) ?: "WEATHER",
            fontSize = 11f,
            letterSpacing = 2f,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = current?.let { WeatherFormat.temperature(it.temperatureC) } ?: "—°",
                color = EinkPalette.Ink,
                fontSize = 70.sp,
                lineHeight = 70.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = (-1).sp,
            )
            Text(
                text = current?.condition?.label?.uppercase(Locale.ENGLISH) ?: "UNAVAILABLE",
                modifier = Modifier.padding(bottom = 10.dp),
                color = EinkPalette.Ink,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                maxLines = 1,
            )
        }
        Text(
            text = weatherMeta(current),
            color = EinkPalette.InkMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.5.sp,
            maxLines = 1,
        )
    }
}

private fun weatherMeta(current: com.eink.dashboard.modules.weather.model.CurrentConditions?): String {
    if (current == null) return "FEELS — · HUM — · WIND —"
    val feels = current.apparentTemperatureC?.let(WeatherFormat::temperature) ?: "—"
    return "FEELS $feels · HUM ${WeatherFormat.humidity(current.humidityPercent)} · WIND ${WeatherFormat.wind(current.windKmh).uppercase(Locale.ENGLISH)}"
}

@Composable
private fun ForecastRibbon(forecast: List<DailyConditions>, today: LocalDate) {
    val cells = forecast.take(7).map { day ->
        ForecastCell(
            label = WeatherFormat.dayLabel(day.date, today).uppercase(Locale.ENGLISH),
            condition = day.condition,
            temperatures = WeatherFormat.highLow(day),
        )
    }.ifEmpty {
        (0L..6L).map { offset ->
            val date = today.plusDays(offset)
            ForecastCell(
                label = if (offset == 0L) "TODAY" else FORECAST_DAY.format(date).uppercase(Locale.ENGLISH),
                condition = WmoCondition.UNKNOWN,
                temperatures = "—° / —°",
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                Box(Modifier.width(1.dp).height(68.dp).background(EinkPalette.Line))
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = if (index == 0) 0.dp else 17.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Label(
                    text = cell.label,
                    fontSize = 11f,
                    letterSpacing = 1.4f,
                    weight = if (index == 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (index == 0) EinkPalette.Ink else EinkPalette.InkMuted,
                )
                Box(modifier = Modifier.width(18.dp).height(18.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        text = cell.condition.symbol,
                        color = EinkPalette.Ink,
                        fontSize = 17.sp,
                        lineHeight = 18.sp,
                    )
                }
                Text(
                    text = cell.temperatures,
                    color = EinkPalette.Ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private data class ForecastCell(
    val label: String,
    val condition: WmoCondition,
    val temperatures: String,
)

@Composable
private fun AgendaColumn(agenda: CalendarAgenda?, today: LocalDate, modifier: Modifier) {
    val days = visibleAgendaDays(agenda)
    Column(modifier = modifier.padding(top = 19.dp)) {
        if (days.isEmpty()) {
            SectionLabel("TODAY · AGENDA")
            Text(
                text = "NO UPCOMING EVENTS",
                modifier = Modifier.padding(top = 24.dp),
                color = EinkPalette.Faint,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
            )
            return@Column
        }

        days.forEachIndexed { dayIndex, visibleDay ->
            if (dayIndex > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(EinkPalette.Ink))
                SectionLabel(
                    text = agendaDayLabel(visibleDay.day.date, today),
                    modifier = Modifier.padding(top = 13.dp),
                )
            } else {
                SectionLabel(agendaDayLabel(visibleDay.day.date, today, includeAgenda = true))
            }
            visibleDay.events.forEachIndexed { eventIndex, event ->
                AgendaEventRow(event)
                val isLast = eventIndex == visibleDay.events.lastIndex
                if (!isLast) Rule(height = 1)
            }
        }
    }
}

@Composable
private fun AgendaEventRow(event: AgendaEvent) {
    val zone = ZoneId.systemDefault()
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 15.5.dp, bottom = 12.dp),
    ) {
        Text(
            text = CalendarFormat.timeLabel(event.event, zone).uppercase(Locale.ENGLISH),
            modifier = Modifier.width(126.dp).padding(top = 7.5.dp, start = 1.dp),
            color = EinkPalette.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = CalendarFormat.titleLabel(event),
                color = EinkPalette.Ink,
                fontSize = 25.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = agendaMeta(event, zone)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    color = EinkPalette.InkMuted,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun agendaMeta(event: AgendaEvent, zone: ZoneId): String = buildList {
    if (!event.event.isAllDay) add("UNTIL ${EVENT_TIME.format(Instant.ofEpochMilli(event.event.endMs).atZone(zone))}")
    event.event.location?.trim()?.takeIf(String::isNotEmpty)?.let { add(it.uppercase(Locale.getDefault())) }
    if (event.duplicateCount > 1) add("${event.duplicateCount} CALENDARS")
}.joinToString(" · ")

private data class VisibleAgendaDay(val day: AgendaDay, val events: List<AgendaEvent>)

private fun visibleAgendaDays(agenda: CalendarAgenda?, limit: Int = 4): List<VisibleAgendaDay> {
    var remaining = limit
    return agenda?.days.orEmpty().mapNotNull { day ->
        if (remaining == 0) return@mapNotNull null
        val events = day.events.take(remaining)
        remaining -= events.size
        events.takeIf { it.isNotEmpty() }?.let { VisibleAgendaDay(day, it) }
    }
}

private fun agendaDayLabel(date: LocalDate, today: LocalDate, includeAgenda: Boolean = false): String {
    val base = when (date) {
        today -> "TODAY"
        today.plusDays(1) -> "TOMORROW"
        else -> AGENDA_DAY.format(date).uppercase(Locale.ENGLISH)
    }
    return if (includeAgenda) "$base · AGENDA" else base
}

@Composable
private fun TasksColumn(
    tasks: List<TaskForgeTask>,
    total: Int,
    today: LocalDate,
    onComplete: (TaskForgeTask) -> Unit,
    modifier: Modifier,
) {
    val visible = tasks.take(4)
    Column(modifier = modifier.padding(top = 19.dp, start = 36.dp)) {
        SectionLabel("TASKFORGE · $total OPEN")
        if (visible.isEmpty()) {
            Text(
                text = "NO MATCHING TASKS",
                modifier = Modifier.padding(top = 24.dp),
                color = EinkPalette.Faint,
                fontSize = 12.sp,
                letterSpacing = 1.4.sp,
            )
            return@Column
        }
        visible.forEachIndexed { index, task ->
            TaskRow(
                task = task,
                today = today,
                onComplete = { onComplete(task) },
                topPadding = if (index == 0) 17.dp else 13.dp,
            )
            Rule(height = 1)
        }
        val remaining = (total - visible.size).coerceAtLeast(0)
        if (remaining > 0) {
            SectionLabel(
                text = "+ $remaining MORE",
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskForgeTask,
    today: LocalDate,
    onComplete: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = topPadding, bottom = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (task.isRecurring) {
            Box(
                modifier = Modifier.size(15.dp).border(1.dp, EinkPalette.Faint),
                contentAlignment = Alignment.Center,
            ) {
                Text("↻", color = EinkPalette.InkMuted, fontSize = 9.sp, lineHeight = 9.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .border(1.5.dp, if (task.canComplete) EinkPalette.Ink else EinkPalette.Faint)
                    .then(if (task.canComplete) Modifier.clickable(onClick = onComplete) else Modifier),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(top = 1.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = task.title,
                color = EinkPalette.Ink,
                fontSize = 16.5.sp,
                lineHeight = 19.sp,
                fontWeight = if (task.priority.rank >= TaskForgePriority.HIGH.rank) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = taskMeta(task, today)
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    color = EinkPalette.InkMuted,
                    fontSize = 12.sp,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun taskMeta(task: TaskForgeTask, today: LocalDate): String {
    if (task.isRecurring) {
        return "REPEATS ${task.recurrence.orEmpty().uppercase(Locale.getDefault())} · IN TASKFORGE"
    }
    return buildList {
        if (task.priority != TaskForgePriority.NORMAL) add(task.priority.name)
        task.due?.let { due ->
            add(
                when (due) {
                    today -> "DUE TODAY"
                    today.plusDays(1) -> "DUE TOMORROW"
                    else -> "DUE ${TASK_DATE.format(due).uppercase(Locale.ENGLISH)}"
                },
            )
        }
        task.subsection?.takeIf(String::isNotBlank)?.let { add(it.uppercase(Locale.getDefault())) }
            ?: task.section?.takeIf(String::isNotBlank)?.let { add(it.uppercase(Locale.getDefault())) }
        addAll(task.tags.sorted().map { "#${it.uppercase(Locale.getDefault())}" })
    }.joinToString(" · ")
}

@Composable
private fun Footer(updatedEpochMs: Long, calendarState: ModuleState?) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        val calendarSuffix = when (calendarState) {
            is ModuleState.Ok -> if (calendarState.isStale) " · CALENDAR STALE" else ""
            is ModuleState.Error -> " · CALENDAR ERROR"
            else -> ""
        }
        FooterText("UPDATED ${TimeFormat.clock(updatedEpochMs)}$calendarSuffix")
        FooterText(DeviceProfile.MODEL.uppercase(Locale.ENGLISH))
    }
}

@Composable
private fun FooterText(text: String) {
    Text(
        text = text,
        color = EinkPalette.Faint,
        fontSize = 11.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Label(
        text = text,
        modifier = modifier,
        fontSize = 11f,
        letterSpacing = 2f,
        weight = FontWeight.SemiBold,
    )
}

@Composable
private fun Label(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: Float,
    letterSpacing: Float,
    weight: FontWeight = FontWeight.SemiBold,
    color: androidx.compose.ui.graphics.Color = EinkPalette.InkMuted,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = fontSize.sp,
        lineHeight = fontSize.sp,
        fontWeight = weight,
        letterSpacing = letterSpacing.sp,
        maxLines = 1,
    )
}

@Composable
private fun Rule(height: Int, color: androidx.compose.ui.graphics.Color = EinkPalette.Line) {
    Box(Modifier.fillMaxWidth().height(height.dp).background(color))
}

private val FORECAST_DAY = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)
private val AGENDA_DAY = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val EVENT_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val TASK_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
