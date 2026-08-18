package com.eink.dashboard.dashboard.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.DashboardViewModel
import com.eink.dashboard.dashboard.Screen
import com.eink.dashboard.diagnostics.ui.DiagnosticsScreen
import com.eink.dashboard.ui.ink.InkChip
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkPageHeader
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkTheme
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
 * Root of the single-Activity UI. Draws the Ink page header (section eyebrow,
 * title, screen chips) and swaps the body by [Screen] with a plain `when` — no crossfade, no
 * navigation animation, because any transition repaints the whole e-ink panel.
 */
@Composable
fun DashboardHost(viewModel: DashboardViewModel, modifier: Modifier = Modifier) {
    InkTheme {
        Surface(modifier = modifier.fillMaxSize(), color = InkColors.Paper) {
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
                com.eink.dashboard.modules.taskforge.ui.OpenWritableDocument(),
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
                    .padding(horizontal = InkSpace.s6),
            ) {
                InkShellHeader(
                    epochMs = lastTick,
                    compact = !isLandscape,
                    current = screen,
                    onSelect = { screen = it },
                )
                when (screen) {
                    Screen.DASHBOARD -> if (isLandscape) {
                        InkBoardScreen(
                            registry = viewModel.registry,
                            epochMs = lastTick,
                            modifier = Modifier.fillMaxSize().padding(bottom = InkSpace.s6),
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

/**
 * `.ink-page-header` for the whole shell: a grey eyebrow naming the section, a
 * heavy title (today's date on the board) and the screen switcher as `.ink-chip`s
 * in the action slot — the active screen is the inverted pill.
 */
@Composable
private fun InkShellHeader(epochMs: Long, compact: Boolean, current: Screen, onSelect: (Screen) -> Unit) {
    val title = when (current) {
        Screen.DASHBOARD -> (if (compact) HEADER_DATE_SHORT else HEADER_DATE)
            .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))
        Screen.SETTINGS -> "Settings"
        Screen.REMOTE -> "Remote setup"
        Screen.DIAGNOSTICS -> "Diagnostics"
    }
    InkPageHeader(
        title = title,
        eyebrow = current.eyebrow,
        action = {
            Screen.entries.forEach { target ->
                InkChip(
                    label = target.navLabel,
                    icon = target.navIcon,
                    selected = target == current,
                    onClick = { onSelect(target) },
                )
            }
        },
    )
}

private val Screen.eyebrow: String
    get() = when (this) {
        Screen.DASHBOARD -> "DAILY BOARD"
        Screen.SETTINGS -> "CUSTOMIZATION"
        Screen.REMOTE -> "PHONE PAIRING"
        Screen.DIAGNOSTICS -> "HARDWARE MANAGEMENT"
    }

private val Screen.navLabel: String
    get() = when (this) {
        Screen.DASHBOARD -> "Board"
        Screen.SETTINGS -> "Settings"
        Screen.REMOTE -> "Remote"
        Screen.DIAGNOSTICS -> "Diag"
    }

private val Screen.navIcon: ImageVector
    get() = when (this) {
        Screen.DASHBOARD -> InkIcons.Grid
        Screen.SETTINGS -> InkIcons.Settings
        Screen.REMOTE -> InkIcons.Wifi
        Screen.DIAGNOSTICS -> InkIcons.Info
    }

private val HEADER_DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val HEADER_DATE_SHORT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
