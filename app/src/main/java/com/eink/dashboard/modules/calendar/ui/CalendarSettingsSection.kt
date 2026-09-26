package com.eink.dashboard.modules.calendar.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.calendar.CalendarModule
import com.eink.dashboard.modules.calendar.CalendarSettingsStore
import com.eink.dashboard.modules.calendar.READ_CALENDAR_PERMISSION
import com.eink.dashboard.modules.calendar.CalendarSourceMode
import com.eink.dashboard.modules.calendar.model.CalendarRangeMode
import com.eink.dashboard.ui.ink.InkAlert
import com.eink.dashboard.ui.ink.InkButton
import com.eink.dashboard.ui.ink.InkButtonSize
import com.eink.dashboard.ui.ink.InkButtonVariant
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkDivider
import com.eink.dashboard.ui.ink.InkHint
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkLabel
import com.eink.dashboard.ui.ink.InkSegmented
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkSwitchRow
import com.eink.dashboard.ui.ink.InkType
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
    val catalog by module.catalog.collectAsStateWithLifecycle()
    val calendars = if (catalog.source == settings.source) catalog.calendars else emptyList()
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
        verticalArrangement = Arrangement.spacedBy(InkSpace.s3),
    ) {
        InkLabel("Source")
        InkSegmented(
            options = CalendarSourceMode.entries,
            selected = settings.source,
            onSelect = { source ->
                scope.launch {
                    settingsStore.setSource(source)
                    module.refresh(RefreshReason.SETTINGS_CHANGED)
                }
            },
            label = { if (it == CalendarSourceMode.GOOGLE) "Google API" else "Reader" },
            block = true,
        )
        InkHint(
            if (module.googleConnected) "Google Calendar connected. Manage OAuth from Remote setup."
            else "Connect Google Calendar from the phone web panel.",
        )

        if (settings.source == CalendarSourceMode.DEVICE && !granted) {
            InkAlert(
                title = "Calendar access is not granted",
                text = "The reader needs permission to read its calendar accounts.",
                icon = InkIcons.Lock,
                outline = true,
            )
            InkButton(
                text = "Grant calendar access",
                icon = InkIcons.Lock,
                onClick = { permissionLauncher.launch(READ_CALENDAR_PERMISSION) },
            )
            return@Column
        }

        InkLabel("Range")
        InkSegmented(
            options = CalendarRangeMode.entries,
            selected = settings.range,
            onSelect = { mode ->
                scope.launch {
                    settingsStore.setRange(mode)
                    module.refresh(RefreshReason.SETTINGS_CHANGED)
                }
            },
            label = { it.label() },
            block = true,
        )

        InkLabel("Calendars", meta = if (calendars.isEmpty()) null else "${calendars.count { settings.isSelected(it.id) }} selected")
        if (calendars.isEmpty()) {
            Text(text = "No calendars found on this device.", style = InkType.small, color = InkColors.Ink3)
        } else {
            Column {
                calendars.forEachIndexed { index, calendar ->
                    if (index > 0) InkDivider(color = InkColors.Ink4)
                    InkSwitchRow(
                        label = calendar.displayName.ifBlank { calendar.accountName },
                        sub = calendar.accountName.takeIf { calendar.displayName.isNotBlank() && it != calendar.displayName },
                        checked = settings.isSelected(calendar.id),
                        onToggle = { selected ->
                            scope.launch {
                                settingsStore.setCalendarSelected(settings.source, calendar.id, selected)
                                module.refresh(RefreshReason.SETTINGS_CHANGED)
                            }
                        },
                    )
                }
            }
        }
        InkButton(
            text = "Refresh calendars",
            icon = InkIcons.Refresh,
            variant = InkButtonVariant.Outline,
            size = InkButtonSize.Sm,
            onClick = { scope.launch { module.refresh(RefreshReason.SETTINGS_CHANGED) } },
        )
    }
}

private fun CalendarRangeMode.label(): String = when (this) {
    CalendarRangeMode.TODAY -> "Today"
    CalendarRangeMode.TODAY_TOMORROW -> "+Tomorrow"
    CalendarRangeMode.WEEK -> "Week"
}
