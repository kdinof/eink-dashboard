package com.eink.dashboard.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.DeviceProfile
import com.eink.dashboard.core.time.TimeFormat
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.RefreshPolicy
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing

/**
 * On-device troubleshooting without a computer attached: confirmed device facts,
 * build info, whether the refresh loop is running (foreground), and every
 * registered module's id / policy / current state / last-updated time.
 *
 * When [hasDemoModules] is true a prominent banner marks the data as sample —
 * demo content must never be read as real user data.
 */
@Composable
fun DiagnosticsScreen(
    modules: List<DashboardModule>,
    coordinatorRunning: Boolean,
    buildType: String,
    versionName: String,
    hasDemoModules: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EinkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.lg),
    ) {
        if (hasDemoModules) {
            Text(
                text = "⚠ DEMO DATA — sample modules are active. This is not real data.",
                style = MaterialTheme.typography.titleMedium,
                color = EinkPalette.Ink,
            )
        }

        Section("Device") {
            KeyVal("Model", DeviceProfile.MODEL)
            KeyVal("API / minSdk", DeviceProfile.MIN_SDK.toString())
            KeyVal("ABI", DeviceProfile.PRIMARY_ABI)
            KeyVal(
                "Screen",
                "${DeviceProfile.SCREEN_WIDTH_PX}×${DeviceProfile.SCREEN_HEIGHT_PX}px · " +
                    "${DeviceProfile.screenWidthDp}×${DeviceProfile.screenHeightDp}dp @ ${DeviceProfile.DENSITY_DPI}dpi",
            )
            KeyVal("Grayscale", DeviceProfile.IS_GRAYSCALE.toString())
        }

        Section("Build") {
            KeyVal("Version", versionName)
            KeyVal("Build type", buildType)
            KeyVal("Refresh loop", if (coordinatorRunning) "running (foreground)" else "stopped (background)")
        }

        Section("Modules (${modules.size})") {
            if (modules.isEmpty()) {
                Text("None registered.", style = MaterialTheme.typography.bodyMedium, color = EinkPalette.InkMuted)
            } else {
                modules.forEach { ModuleRow(it) }
            }
        }
    }
}

@Composable
private fun ModuleRow(module: DashboardModule) {
    val state by module.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = EinkSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = module.id + if (module.isDemo) "  [demo]" else "",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "policy=${module.refreshPolicy.describe()}   state=${state.describe()}",
            style = MaterialTheme.typography.labelMedium,
            color = EinkPalette.InkMuted,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun KeyVal(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = key, style = MaterialTheme.typography.bodyMedium, color = EinkPalette.InkMuted)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun RefreshPolicy.describe(): String = when (this) {
    RefreshPolicy.Manual -> "manual"
    RefreshPolicy.EveryMinute -> "every-minute"
    is RefreshPolicy.Periodic -> "periodic(${minInterval})"
}

private fun ModuleState.describe(): String = when (this) {
    ModuleState.Loading -> "loading"
    is ModuleState.Ok -> "ok" +
        (lastUpdatedEpochMs?.let { " @${TimeFormat.clock(it)}" } ?: "") +
        (if (isStale) " stale" else "")
    is ModuleState.Empty -> "empty"
    is ModuleState.Error -> "error: $message"
}
