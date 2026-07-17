package com.eink.dashboard.modules.battery

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.time.SystemTimeSource
import com.eink.dashboard.core.time.TimeSource
import com.eink.dashboard.dashboard.DashboardModule
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
 * the shell's minute ticker ([RefreshPolicy.EveryMinute]), so a charge/discharge
 * change is reflected within a minute without the module scheduling anything itself.
 */
class BatteryModule(
    private val source: BatterySource,
    private val timeSource: TimeSource = SystemTimeSource,
) : DashboardModule {

    override val id: String = "battery"
    override val title: String = "Battery"
    override val refreshPolicy: RefreshPolicy = RefreshPolicy.EveryMinute

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
            modifier = modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = reading?.let { "${it.percent}%" } ?: "—",
                style = MaterialTheme.typography.displaySmall,
                color = EinkPalette.Ink,
            )
            Spacer(Modifier.width(EinkSpacing.md))
            Text(
                text = reading?.let { if (it.charging) "charging" else "on battery" } ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = EinkPalette.InkMuted,
            )
        }
    }

    companion object {
        /** Wires the real Android battery source. Used by the composition root. */
        fun create(context: Context): BatteryModule =
            BatteryModule(AndroidBatterySource(context.applicationContext))
    }
}
