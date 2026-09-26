package com.eink.dashboard.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.settings.DashboardSettings
import com.eink.dashboard.settings.OrientationSetting
import com.eink.dashboard.ui.ink.InkCard
import com.eink.dashboard.ui.ink.InkCardBody
import com.eink.dashboard.ui.ink.InkCardHeader
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkDivider
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkLabel
import com.eink.dashboard.ui.ink.InkSegmented
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkSwitchRow
import com.eink.dashboard.ui.ink.InkType

/**
 * Settings screen: orientation lock, keep-screen-on, per-module visibility and
 * each module's own settings, every group in its own `.ink-card`. On the wide
 * landscape panel the cards flow into two columns. Pure display + callbacks —
 * persistence goes through `SettingsStore`, wired in the host. All controls are
 * static (no ripple/animation) for e-ink.
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
    val cards = buildList<@Composable () -> Unit> {
        add {
            SettingsCard(title = "Display", icon = InkIcons.Device) {
                InkLabel("Orientation")
                InkSegmented(
                    options = OrientationSetting.entries,
                    selected = settings.orientation,
                    onSelect = onOrientation,
                    label = { it.label() },
                    block = true,
                )
                InkDivider(color = InkColors.Ink4)
                InkSwitchRow(
                    label = "Keep screen on",
                    sub = "Stops the reader from sleeping while the board is open.",
                    checked = settings.keepScreenOn,
                    onToggle = onKeepScreenOn,
                )
            }
        }
        add {
            SettingsCard(title = "Blocks", icon = InkIcons.Grid, meta = "${modules.count { settings.isModuleVisible(it.id) }} / ${modules.size}") {
                if (modules.isEmpty()) {
                    Text(text = "No modules registered.", style = InkType.small, color = InkColors.Ink3)
                } else {
                    modules.forEachIndexed { index, module ->
                        val moduleState by module.state.collectAsStateWithLifecycle()
                        val visible = settings.isModuleVisible(module.id)
                        if (index > 0) InkDivider(color = InkColors.Ink4)
                        InkSwitchRow(
                            label = module.title + if (module.isDemo) " · demo" else "",
                            sub = if (visible && module.id in LAST_UPDATED_MODULE_IDS) {
                                SettingsFormat.lastUpdated(moduleState.lastUpdatedEpochMs)
                            } else {
                                null
                            },
                            checked = visible,
                            onToggle = { enabled -> onModuleVisible(module.id, enabled) },
                        )
                    }
                }
            }
        }
        // Per-module settings (additive hasSettings slot — see DashboardModule).
        // A module owns its own config UI; the shell just gives it a titled card.
        modules.filter { it.hasSettings }.forEach { module ->
            add {
                SettingsCard(title = module.title, icon = module.settingsIcon()) {
                    module.SettingsContent(Modifier.fillMaxWidth())
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val columns = if (maxWidth >= 1000.dp) 2 else 1
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = InkSpace.s6),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s5),
            verticalAlignment = Alignment.Top,
        ) {
            repeat(columns) { column ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(InkSpace.s5),
                ) {
                    cards.filterIndexed { index, _ -> index % columns == column }.forEach { it() }
                }
            }
        }
    }
}

private val LAST_UPDATED_MODULE_IDS = setOf("calendar", "todoist", "taskforge")

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    meta: String? = null,
    content: @Composable () -> Unit,
) {
    InkCard(modifier = Modifier.fillMaxWidth()) {
        InkCardHeader(title = title, icon = icon, meta = meta)
        InkCardBody(spacing = InkSpace.s2) { content() }
    }
}

private fun DashboardModule.settingsIcon(): ImageVector = when (id) {
    "calendar" -> InkIcons.Calendar
    "todoist", "taskforge" -> InkIcons.Todo
    "weather" -> InkIcons.Sun
    else -> InkIcons.Settings
}

private fun OrientationSetting.label(): String = when (this) {
    OrientationSetting.SYSTEM -> "System"
    OrientationSetting.PORTRAIT -> "Portrait"
    OrientationSetting.LANDSCAPE -> "Landscape"
}
