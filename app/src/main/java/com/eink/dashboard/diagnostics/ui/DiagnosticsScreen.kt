package com.eink.dashboard.diagnostics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.eink.dashboard.ui.ink.InkAlert
import com.eink.dashboard.ui.ink.InkBadge
import com.eink.dashboard.ui.ink.InkCard
import com.eink.dashboard.ui.ink.InkCardBody
import com.eink.dashboard.ui.ink.InkCardHeader
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkKv
import com.eink.dashboard.ui.ink.InkListItem
import com.eink.dashboard.ui.ink.InkRuledList
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkTag
import com.eink.dashboard.ui.ink.InkType

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
            .padding(bottom = InkSpace.s6),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s5),
    ) {
        if (hasDemoModules) {
            InkAlert(
                title = "Demo data",
                text = "Sample modules are active. This is not real data.",
                icon = InkIcons.Alert,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s5)) {
            InkCard(modifier = Modifier.weight(1f)) {
                InkCardHeader(title = "Device", icon = InkIcons.Device, meta = DeviceProfile.MODEL)
                InkCardBody(spacing = 0.dp) {
                    InkKv(
                        listOf(
                            "Model" to DeviceProfile.MODEL,
                            "API / minSdk" to DeviceProfile.MIN_SDK.toString(),
                            "ABI" to DeviceProfile.PRIMARY_ABI,
                            "Screen" to "${DeviceProfile.SCREEN_WIDTH_PX}×${DeviceProfile.SCREEN_HEIGHT_PX}px · " +
                                "${DeviceProfile.screenWidthDp}×${DeviceProfile.screenHeightDp}dp @ ${DeviceProfile.DENSITY_DPI}dpi",
                            "Grayscale" to DeviceProfile.IS_GRAYSCALE.toString(),
                        ),
                    )
                }
            }
            InkCard(modifier = Modifier.weight(1f)) {
                InkCardHeader(title = "Build", icon = InkIcons.Settings, meta = versionName)
                InkCardBody(spacing = 0.dp) {
                    InkKv(
                        listOf(
                            "Version" to versionName,
                            "Build type" to buildType,
                            "Refresh loop" to if (coordinatorRunning) "running (foreground)" else "stopped (background)",
                        ),
                    )
                }
            }
        }

        InkCard(modifier = Modifier.fillMaxWidth()) {
            InkCardHeader(title = "Modules", icon = InkIcons.Grid, meta = "${modules.size}")
            if (modules.isEmpty()) {
                InkCardBody { Text("None registered.", style = InkType.small, color = InkColors.Ink3) }
            } else {
                InkCardBody(flush = true, spacing = 0.dp) {
                    InkRuledList(modules) { ModuleRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ModuleRow(module: DashboardModule) {
    val state by module.state.collectAsStateWithLifecycle()
    InkListItem(
        title = module.id + if (module.isDemo) "  [demo]" else "",
        sub = "policy=${module.refreshPolicy.describe()}   state=${state.describe()}",
        strong = true,
        inset = InkSpace.s3,
        trailing = {
            when (val current = state) {
                is ModuleState.Ok -> InkBadge(if (current.isStale) "Stale" else "OK", outline = current.isStale)
                is ModuleState.Error -> InkBadge("Error")
                ModuleState.Loading -> InkTag("Loading")
                is ModuleState.Empty -> InkTag("Empty")
            }
        },
    )
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
