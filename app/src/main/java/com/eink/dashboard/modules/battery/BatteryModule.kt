package com.eink.dashboard.modules.battery

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.time.SystemTimeSource
import com.eink.dashboard.core.time.TimeSource
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.DataWidgetRefreshPolicy
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.battery.data.AndroidBatterySource
import com.eink.dashboard.modules.battery.data.BatterySource
import com.eink.dashboard.modules.battery.data.BatteryStatus
import com.eink.dashboard.ui.ink.InkBadge
import com.eink.dashboard.ui.ink.InkEyebrow
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkMeter
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkType
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
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s4),
        ) {
            InkMeter(percent = reading?.percent, label = null, large = true)
            Text(text = reading?.let { "${it.percent}%" } ?: "—", style = InkType.big)
            if (reading?.charging == true) {
                InkBadge("Charging", icon = InkIcons.BatteryCharging)
            } else if (reading != null) {
                InkEyebrow("On battery")
            }
        }
    }

    companion object {
        /** Wires the real Android battery source. Used by the composition root. */
        fun create(context: Context): BatteryModule =
            BatteryModule(AndroidBatterySource(context.applicationContext))
    }
}
