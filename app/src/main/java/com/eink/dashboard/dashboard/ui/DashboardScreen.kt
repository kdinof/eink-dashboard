package com.eink.dashboard.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.eink.dashboard.dashboard.DashboardLayoutSpec
import com.eink.dashboard.dashboard.DashboardLayoutSpec.Orientation
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.DashboardModuleRegistry
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing

/**
 * The dashboard grid. Blocks are placed by the pure [DashboardLayoutSpec] — one
 * column in portrait, two in landscape — filtered to the modules the user has
 * kept visible. This composable is a thin renderer over that spec; the placement
 * rules themselves are golden-tested off-device.
 */
@Composable
fun DashboardScreen(
    registry: DashboardModuleRegistry,
    visibleModuleIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val orientation = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) {
        Orientation.LANDSCAPE
    } else {
        Orientation.PORTRAIT
    }

    if (visibleModuleIds.isEmpty()) {
        EmptyDashboard(modifier)
        return
    }

    val layout = DashboardLayoutSpec.layout(orientation, visibleModuleIds)
    val byId: (String) -> DashboardModule? = registry::byId

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EinkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.md),
    ) {
        (0 until layout.rows).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(EinkSpacing.md),
            ) {
                (0 until layout.columns).forEach { col ->
                    val slot = layout.slots.firstOrNull { it.row == row && it.column == col }
                    val module = slot?.let { byId(it.moduleId) }
                    if (module != null) {
                        ModuleBlock(module = module, modifier = Modifier.weight(1f))
                    } else {
                        // Keep the grid aligned when the last row is short.
                        Box(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboard(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(EinkSpacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No modules enabled",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = "Enable blocks in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.InkMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
