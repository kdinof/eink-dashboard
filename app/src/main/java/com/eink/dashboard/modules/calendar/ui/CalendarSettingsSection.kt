package com.eink.dashboard.modules.calendar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.ui.EinkChip
import com.eink.dashboard.dashboard.ui.EinkToggleRow
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.CalendarSettingsStore
import com.eink.dashboard.modules.calendar.READ_CALENDAR_PERMISSION
import com.eink.dashboard.modules.calendar.CalendarSourceMode
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Per-module settings surfaced inside the shell's Settings screen (via the additive
 * `DashboardModule.hasSettings` slot): grant the `READ_CALENDAR` permission, pick
 * the display window (Today / +Tomorrow / Week), and choose which calendars show.
 *
 * All state is persisted in the module's own [CalendarSettingsStore]; changing a
 * setting immediately triggers a `SETTINGS_CHANGED` reload so the block updates at
 * once instead of waiting for the next tick. The permission request uses the
 * Compose ActivityResult launcher, so nothing here needs to touch the frozen
 * `MainActivity`.
 */
@Composable
fun CalendarSettingsSection(
    module: CalendarModule,
    settingsStore: CalendarSettingsStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = com.eink.dashboard.modules.calendar.CalendarSettings.DEFAULT,
    )
    val calendars by module.calendars.collectAsStateWithLifecycle()
    val granted by module.permissionGranted.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        module.refreshPermissionFlag()
        // Reload after a grant so calendars populate without waiting for a tick.
        scope.launch { module.refresh(RefreshReason.SETTINGS_CHANGED) }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        Text(text = "Source", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            CalendarSourceMode.entries.forEach { source ->
                EinkChip(
                    label = if (source == CalendarSourceMode.GOOGLE) "Google API" else "Reader",
                    selected = settings.source == source,
                    onClick = {
                        scope.launch {
                            settingsStore.setSource(source)
                            module.refresh(RefreshReason.SETTINGS_CHANGED)
                        }
                    },
                )
            }
            EinkChip(
                label = "Refresh calendars",
                selected = false,
                onClick = { scope.launch { module.refresh(RefreshReason.SETTINGS_CHANGED) } },
            )
        }
        Text(
            text = if (module.googleConnected) "Google Calendar connected. Manage OAuth from Remote setup."
            else "Connect Google Calendar from the phone web panel.",
            style = MaterialTheme.typography.labelMedium,
            color = EinkPalette.InkMuted,
        )

        if (settings.source == CalendarSourceMode.DEVICE && !granted) {
            Text(
                text = "Calendar access is not granted.",
                style = MaterialTheme.typography.bodyMedium,
                color = EinkPalette.InkMuted,
            )
            EinkChip(
                label = "Grant calendar access",
                selected = false,
                onClick = { permissionLauncher.launch(READ_CALENDAR_PERMISSION) },
            )
            return@Column
        }

        Text(text = "Range", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            CalendarRangeMode.entries.forEach { mode ->
                EinkChip(
                    label = mode.label(),
                    selected = settings.range == mode,
                    onClick = {
                        scope.launch {
                            settingsStore.setRange(mode)
                            module.refresh(RefreshReason.SETTINGS_CHANGED)
                        }
                    },
                )
            }
        }

        Text(text = "Calendars", style = MaterialTheme.typography.titleMedium)
        if (calendars.isEmpty()) {
            Text(
                text = "No calendars found on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = EinkPalette.InkMuted,
            )
        } else {
            calendars.forEach { calendar ->
                EinkToggleRow(
                    label = calendar.displayName.ifBlank { calendar.accountName },
                    checked = settings.isSelected(calendar.id),
                    onToggle = { selected ->
                        scope.launch {
                            settingsStore.setCalendarSelected(calendar.id, selected)
                            module.refresh(RefreshReason.SETTINGS_CHANGED)
                        }
                    },
                )
            }
        }
    }
}

private fun CalendarRangeMode.label(): String = when (this) {
    CalendarRangeMode.TODAY -> "Today"
    CalendarRangeMode.TODAY_TOMORROW -> "+Tomorrow"
    CalendarRangeMode.WEEK -> "Week"
}
