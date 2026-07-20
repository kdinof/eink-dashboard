package com.eink.dashboard.settings.ui

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.ui.EinkChip
import com.eink.dashboard.dashboard.ui.EinkToggleRow
import com.eink.dashboard.settings.DashboardSettings
import com.eink.dashboard.settings.OrientationSetting

/**
 * Settings screen: orientation lock, keep-screen-on, and per-module visibility.
 * Pure display + callbacks — persistence goes through `SettingsStore`, wired in
 * the host. All controls are static (no ripple/animation) for e-ink.
 */
@Composable
fun SettingsScreen(
    settings: DashboardSettings,
    modules: List<DashboardModule>,
    onOrientation: (OrientationSetting) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onModuleVisible: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(EinkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.lg),
    ) {
        Section(title = "Orientation") {
            Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
                OrientationSetting.entries.forEach { option ->
                    EinkChip(
                        label = option.label(),
                        selected = settings.orientation == option,
                        onClick = { onOrientation(option) },
                    )
                }
            }
        }

        Section(title = "Display") {
            EinkToggleRow(
                label = "Keep screen on",
                checked = settings.keepScreenOn,
                onToggle = onKeepScreenOn,
            )
        }

        Section(title = "Blocks") {
            if (modules.isEmpty()) {
                Text(
                    text = "No modules registered.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EinkPalette.InkMuted,
                )
            } else {
                modules.forEach { module ->
                    val moduleState by module.state.collectAsStateWithLifecycle()
                    val visible = settings.isModuleVisible(module.id)
                    EinkToggleRow(
                        label = module.title + if (module.isDemo) "  (demo)" else "",
                        checked = visible,
                        onToggle = { enabled -> onModuleVisible(module.id, enabled) },
                    )
                    if (visible && module.id in LAST_UPDATED_MODULE_IDS) {
                        Text(
                            text = SettingsFormat.lastUpdated(moduleState.lastUpdatedEpochMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = EinkPalette.InkMuted,
                            modifier = Modifier.padding(start = EinkSpacing.sm),
                        )
                    }
                }
            }
        }

        // Per-module settings (additive hasSettings slot — see DashboardModule).
        // A module owns its own config UI; the shell just gives it a titled section.
        modules.filter { it.hasSettings }.forEach { module ->
            Section(title = module.title) {
                module.SettingsContent(Modifier.fillMaxWidth())
            }
        }
    }
}

private val LAST_UPDATED_MODULE_IDS = setOf("calendar", "todoist", "taskforge")

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

private fun OrientationSetting.label(): String = when (this) {
    OrientationSetting.SYSTEM -> "System"
    OrientationSetting.PORTRAIT -> "Portrait"
    OrientationSetting.LANDSCAPE -> "Landscape"
}
