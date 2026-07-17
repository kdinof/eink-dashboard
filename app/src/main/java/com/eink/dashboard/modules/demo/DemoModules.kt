package com.eink.dashboard.modules.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.eink.dashboard.core.time.SystemTimeSource
import com.eink.dashboard.core.time.TimeSource
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.minutes

/**
 * Sample modules used to exercise the shell layout without any real data source.
 *
 * Every module here sets `isDemo = true`. The registry refuses to register a
 * demo module in a release build (`allowDemo = BuildConfig.DEBUG`), and the
 * shell paints a visible "DEMO" tag on demo blocks — so sample content can never
 * be mistaken for the user's real calendar/tasks/weather in a shipped build.
 * These exist only to prove portrait/landscape layout and the refresh pipeline;
 * T03–T05 replace them with real modules and do not touch this file.
 */

/** Big clock that re-renders on every minute tick — proves the minute cadence end to end. */
class DemoClockModule(private val timeSource: TimeSource = SystemTimeSource) : DashboardModule {
    override val id: String = "demo.clock"
    override val title: String = "Clock"
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.EveryMinute
    override val isDemo: Boolean = true

    private val formatter = DateTimeFormatter.ofPattern("HH:mm")
    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    private val _time = MutableStateFlow("--:--")
    private val time: StateFlow<String> = _time.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val now = timeSource.nowMs()
        _time.value = formatter.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))
        _state.value = ModuleState.Ok(lastUpdatedEpochMs = now)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val value by time.collectAsState()
        Text(
            text = value,
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.sm),
            style = MaterialTheme.typography.displaySmall,
            textAlign = TextAlign.Center,
        )
    }
}

/** Static sample agenda. Periodic policy, but the data never changes — it is fake. */
class DemoAgendaModule(private val timeSource: TimeSource = SystemTimeSource) : DashboardModule {
    override val id: String = "demo.agenda"
    override val title: String = "Agenda"
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.Periodic(15.minutes)
    override val isDemo: Boolean = true

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    private val items = listOf(
        "09:00  Stand-up",
        "11:30  Design review",
        "14:00  1:1 with Nastya",
        "18:00  Pick up Roma",
    )

    override suspend fun refresh(reason: RefreshReason) {
        _state.value = ModuleState.Ok(lastUpdatedEpochMs = timeSource.nowMs())
    }

    @Composable
    override fun Content(modifier: Modifier) {
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs),
        ) {
            items.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(text = line, style = MaterialTheme.typography.bodyLarge, color = EinkPalette.Ink)
                }
            }
        }
    }
}

/**
 * All demo modules in canonical order. Only registered when `allowDemo` is true
 * (debug builds). Kept as a single provider so wiring stays in one place.
 */
fun demoModules(timeSource: TimeSource = SystemTimeSource): List<DashboardModule> = listOf(
    DemoClockModule(timeSource),
    DemoAgendaModule(timeSource),
)
