package com.eink.dashboard.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.DashboardViewModel
import com.eink.dashboard.dashboard.Screen
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkTheme
import com.eink.dashboard.diagnostics.ui.DiagnosticsScreen
import com.eink.dashboard.settings.ui.SettingsScreen
import com.eink.dashboard.remote.RemotePermission
import com.eink.dashboard.remote.ui.RemoteSetupScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.eink.dashboard.modules.calendar.READ_CALENDAR_PERMISSION
import com.eink.dashboard.modules.weather.data.COARSE_LOCATION_PERMISSION
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Root of the single-Activity UI. Draws the Swiss date/navigation header and
 * swaps the body by [Screen] with a plain `when` — no crossfade, no
 * navigation animation, because any transition repaints the whole e-ink panel.
 */
@Composable
fun DashboardHost(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    EinkTheme {
        Surface(modifier = modifier.fillMaxSize(), color = EinkPalette.Paper) {
            var screen by remember { mutableStateOf(Screen.DASHBOARD) }
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val lastTick by viewModel.lastTick.collectAsStateWithLifecycle()
            val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            val pendingPermission by viewModel.permissions.pending.collectAsStateWithLifecycle()
            val calendarLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.permissions.pending.value?.let { viewModel.completeRemotePermission(it.id, it.permission) } }
            val locationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { viewModel.permissions.pending.value?.let { viewModel.completeRemotePermission(it.id, it.permission) } }
            val taskForgeFileLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                viewModel.permissions.pending.value?.let {
                    viewModel.completeTaskForgeFileSelection(it.id, uri)
                }
            }

            LaunchedEffect(pendingPermission?.id) {
                when (pendingPermission?.permission) {
                    RemotePermission.CALENDAR -> calendarLauncher.launch(READ_CALENDAR_PERMISSION)
                    RemotePermission.LOCATION -> locationLauncher.launch(COARSE_LOCATION_PERMISSION)
                    RemotePermission.TASKFORGE_FILE -> taskForgeFileLauncher.launch(
                        arrayOf("text/markdown", "text/plain", "application/octet-stream"),
                    )
                    null -> Unit
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp),
            ) {
                SwissHeader(
                    epochMs = lastTick,
                    current = screen,
                    onSelect = { screen = it },
                )
                HeavyDivider()
                when (screen) {
                    Screen.DASHBOARD -> if (isLandscape) {
                        SwissBoardScreen(
                            registry = viewModel.registry,
                            epochMs = lastTick,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        DashboardScreen(
                            registry = viewModel.registry,
                            visibleModuleIds = settings.visibleAmong(viewModel.registry.ids),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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
private fun SwissHeader(epochMs: Long, current: Screen, onSelect: (Screen) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = HEADER_DATE.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())).uppercase(Locale.ENGLISH),
            color = EinkPalette.Ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            SwissNavItem("BOARD", current == Screen.DASHBOARD) { onSelect(Screen.DASHBOARD) }
            SwissNavItem("SETTINGS", current == Screen.SETTINGS) { onSelect(Screen.SETTINGS) }
            SwissNavItem("REMOTE", current == Screen.REMOTE) { onSelect(Screen.REMOTE) }
            SwissNavItem("DIAG", current == Screen.DIAGNOSTICS) { onSelect(Screen.DIAGNOSTICS) }
        }
    }
}

@Composable
private fun SwissNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(
                when (label) {
                    "BOARD" -> 52.dp
                    "SETTINGS" -> 76.dp
                    "REMOTE" -> 64.dp
                    else -> 42.dp
                },
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) EinkPalette.Ink else EinkPalette.InkMuted,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 1.7.sp,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(EinkPalette.Ink),
            )
        }
    }
}

@Composable
private fun HeavyDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(EinkPalette.Ink),
    )
}

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
