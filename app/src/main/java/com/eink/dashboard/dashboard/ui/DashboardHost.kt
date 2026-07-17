package com.eink.dashboard.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.time.TimeFormat
import com.eink.dashboard.dashboard.DashboardViewModel
import com.eink.dashboard.dashboard.Screen
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.theme.EinkTheme
import com.eink.dashboard.diagnostics.ui.DiagnosticsScreen
import com.eink.dashboard.settings.ui.SettingsScreen
import com.eink.dashboard.remote.RemotePermission
import com.eink.dashboard.remote.ui.RemoteSetupScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.eink.dashboard.modules.calendar.READ_CALENDAR_PERMISSION
import com.eink.dashboard.modules.weather.data.COARSE_LOCATION_PERMISSION

/**
 * Root of the single-Activity UI. Draws a static top bar (clock + destination
 * chips) and swaps the body by [Screen] with a plain `when` — no crossfade, no
 * navigation animation, because any transition repaints the whole e-ink panel.
 */
@Composable
fun DashboardHost(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    EinkTheme {
        Surface(modifier = modifier.fillMaxSize(), color = EinkPalette.Paper) {
            var screen by remember { mutableStateOf(Screen.DASHBOARD) }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val lastTick by viewModel.lastTick.collectAsStateWithLifecycle()
            val pendingPermission by viewModel.permissions.pending.collectAsStateWithLifecycle()
            val calendarLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.permissions.pending.value?.let { viewModel.completeRemotePermission(it.id, it.permission) } }
            val locationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.permissions.pending.value?.let { viewModel.completeRemotePermission(it.id, it.permission) } }

            LaunchedEffect(pendingPermission?.id) {
                when (pendingPermission?.permission) {
                    RemotePermission.CALENDAR -> calendarLauncher.launch(READ_CALENDAR_PERMISSION)
                    RemotePermission.LOCATION -> locationLauncher.launch(COARSE_LOCATION_PERMISSION)
                    null -> Unit
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    clock = TimeFormat.clock(lastTick),
                    current = screen,
                    onSelect = { screen = it },
                )
                HairlineDivider()
                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        registry = viewModel.registry,
                        visibleModuleIds = settings.visibleAmong(viewModel.registry.ids),
                        modifier = Modifier.fillMaxSize(),
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        modules = viewModel.registry.all,
                        onOrientation = viewModel::setOrientation,
                        onKeepScreenOn = viewModel::setKeepScreenOn,
                        onModuleVisible = viewModel::setModuleVisible,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Screen.REMOTE -> RemoteSetupScreen(
                        serverState = viewModel.remoteServer.state,
                        pairing = viewModel.pairing,
                        onRegeneratePin = viewModel::regeneratePairingPin,
                        onRevokeSession = viewModel::revokeSession,
                        onRevokeAll = viewModel::revokeAllSessions,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Screen.DIAGNOSTICS -> {
                        val running by viewModel.coordinatorRunning.collectAsStateWithLifecycle()
                        DiagnosticsScreen(
                            modules = viewModel.registry.all,
                            coordinatorRunning = running,
                            buildType = viewModel.buildType,
                            versionName = viewModel.versionName,
                            hasDemoModules = viewModel.hasDemoModules,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(clock: String, current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = EinkSpacing.md, vertical = EinkSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = clock, style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            NavChip("Board", current == Screen.DASHBOARD) { onSelect(Screen.DASHBOARD) }
            NavChip("Settings", current == Screen.SETTINGS) { onSelect(Screen.SETTINGS) }
            NavChip("Remote", current == Screen.REMOTE) { onSelect(Screen.REMOTE) }
            NavChip("Diag", current == Screen.DIAGNOSTICS) { onSelect(Screen.DIAGNOSTICS) }
        }
    }
}

@Composable
private fun NavChip(label: String, selected: Boolean, onClick: () -> Unit) {
    EinkChip(label = label, selected = selected, onClick = onClick)
}

@Composable
private fun HairlineDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(EinkSpacing.hairline)
            .background(EinkPalette.Line),
    )
}
