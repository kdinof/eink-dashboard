package com.eink.dashboard.modules.battery

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.time.SystemTimeSource
import com.eink.dashboard.core.time.TimeSource
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.DataWidgetRefreshPolicy
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.modules.battery.data.AndroidBatterySource
import com.eink.dashboard.modules.battery.data.BatterySource
import com.eink.dashboard.modules.battery.data.BatteryStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Battery block (T05). On each [refresh] it reads the current battery state through
 * [BatterySource] (a synchronous read of the sticky battery broadcast — no live
 * receiver, no background work) and maps it onto the frozen [ModuleState]. Cadence is
 * the shell's shared ten-minute data-widget policy, without any module-owned loop.
 */
class BatteryModule(
    private val source: BatterySource,
    private val timeSource: TimeSource = SystemTimeSource,
) : DashboardModule {

    override val id: String = "battery"
    override val title: String = "Battery"
    override val refreshPolicy: RefreshPolicy = DataWidgetRefreshPolicy

    private val _state = MutableStateFlow<ModuleState>(ModuleState.Loading)
    override val state: StateFlow<ModuleState> = _state.asStateFlow()

    private val _status = MutableStateFlow<BatteryStatus?>(null)
    val status: StateFlow<BatteryStatus?> = _status.asStateFlow()

    override suspend fun refresh(reason: RefreshReason) {
        val reading = source.read()
        if (reading == null) {
            _state.value = ModuleState.Error(
                message = "Battery unavailable",
                lastUpdatedEpochMs = _state.value.lastUpdatedEpochMs,
            )
            return
        }
        _status.value = reading
        _state.value = ModuleState.Ok(lastUpdatedEpochMs = timeSource.nowMs())
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val reading by status.collectAsStateWithLifecycle()
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(EinkSpacing.md),
        ) {
            BatteryGauge(percent = reading?.percent ?: 0)
            Text(
                text = reading?.let { "${it.percent}%" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                color = EinkPalette.Ink,
            )
            Text(
                text = reading?.let { if (it.charging) "CHARGING" else "BATTERY" } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = EinkPalette.InkMuted,
            )
        }
    }

    @Composable
    private fun BatteryGauge(percent: Int) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(28.dp)
                    .border(EinkSpacing.hairline, EinkPalette.Ink, RoundedCornerShape(3.dp))
                    .padding(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(EinkPalette.Ink),
                )
            }
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .background(EinkPalette.Ink),
            )
        }
    }

    companion object {
        /** Wires the real Android battery source. Used by the composition root. */
        fun create(context: Context): BatteryModule =
            BatteryModule(AndroidBatterySource(context.applicationContext))
    }
}
