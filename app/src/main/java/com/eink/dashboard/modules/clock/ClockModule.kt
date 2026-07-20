package com.eink.dashboard.modules.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import java.time.ZoneId

/**
 * On-device clock block (T05). It shows the wall-clock time (no seconds) and the
 * date, and re-renders on the shell's shared minute ticker — the module holds no
 * timer of its own ([RefreshPolicy.EveryMinute] means the coordinator calls
 * [refresh] on every minute boundary). E-ink never animates a seconds counter, so
 * minute granularity is deliberate (see `core/time/MinuteTicker`).
 *
 * All time is read through the injected [TimeSource] and formatted by the pure
 * [ClockFormat], so the display is unit-tested without an Android runtime.
 */
class ClockModule(
    private val timeSource: TimeSource = SystemTimeSource,
    private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() },
) : DashboardModule {

    override val id: String = "clock"
    override val title: String = "Clock"
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.EveryMinute

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    private val _face = MutableStateFlow(ClockFace("--:--", ""))
    val face: StateFlow<ClockFace> = _face.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val now = timeSource.nowMs()
        _face.value = ClockFormat.face(now, zoneProvider())
        _state.value = ModuleState.Ok(lastUpdatedEpochMs = now)
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val value by face.collectAsStateWithLifecycle()
        Column(
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = value.time,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 76.sp,
                    lineHeight = 78.sp,
                    fontWeight = FontWeight.Normal,
                ),
                textAlign = TextAlign.Start,
                color = EinkPalette.Ink,
            )
            Text(
                text = value.date,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal),
                textAlign = TextAlign.Start,
                color = EinkPalette.InkMuted,
            )
        }
    }
}
