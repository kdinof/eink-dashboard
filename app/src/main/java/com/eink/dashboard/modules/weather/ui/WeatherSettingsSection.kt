package com.eink.dashboard.modules.weather.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.ui.EinkChip
import com.eink.dashboard.modules.weather.LocationMode
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.modules.weather.WeatherSettings
import com.eink.dashboard.modules.weather.WeatherSettingsStore
import com.eink.dashboard.modules.weather.data.COARSE_LOCATION_PERMISSION
import kotlinx.coroutines.launch

/**
 * Per-module Weather settings surfaced in the shell's Settings screen (via
 * `hasSettings`): choose the location source. The Tashkent preset is the shipped
 * default and needs no permission; Fixed accepts manual lat/long (also permission-
 * free); Device is opt-in and requests `ACCESS_COARSE_LOCATION` once, reading only a
 * last-known fix (no continuous GPS). Any change triggers a `SETTINGS_CHANGED` reload
 * so the block updates at once instead of waiting for the next periodic refresh.
 */
@Composable
fun WeatherSettingsSection(
    module: WeatherModule,
    settingsStore: WeatherSettingsStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = WeatherSettings.DEFAULT,
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // On grant switch to device mode and reload; on denial fall back to preset.
        scope.launch {
            settingsStore.setLocationMode(if (granted) LocationMode.DEVICE else LocationMode.PRESET_TASHKENT)
            module.refresh(RefreshReason.SETTINGS_CHANGED)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        Text(text = "Location", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            LocationMode.entries.forEach { mode ->
                EinkChip(
                    label = mode.label(),
                    selected = settings.locationMode == mode,
                    onClick = {
                        if (mode == LocationMode.DEVICE) {
                            // Opt-in: ask for the coarse permission, then wire it up in the callback.
                            permissionLauncher.launch(COARSE_LOCATION_PERMISSION)
                        } else {
                            scope.launch {
                                settingsStore.setLocationMode(mode)
                                module.refresh(RefreshReason.SETTINGS_CHANGED)
                            }
                        }
                    },
                )
            }
        }

        if (settings.locationMode == LocationMode.FIXED) {
            FixedLocationEditor(settings = settings, settingsStore = settingsStore, module = module)
        }

        Text(
            text = "Tashkent works with no permission. Device location is opt-in and reads only a last-known fix.",
            style = MaterialTheme.typography.labelMedium,
            color = EinkPalette.InkMuted,
        )
    }
}

@Composable
private fun FixedLocationEditor(
    settings: WeatherSettings,
    settingsStore: WeatherSettingsStore,
    module: WeatherModule,
) {
    val scope = rememberCoroutineScope()
    var lat by remember { mutableStateOf(settings.fixedLatitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(settings.fixedLongitude?.toString() ?: "") }
    var label by remember { mutableStateOf(settings.fixedLabel) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(EinkSpacing.xs)) {
        CoordinateField(value = label, onChange = { label = it }, hint = "Label", keyboard = KeyboardType.Text)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            CoordinateField(
                value = lat, onChange = { lat = it; status = null }, hint = "Latitude",
                keyboard = KeyboardType.Number, modifier = Modifier.weight(1f),
            )
            CoordinateField(
                value = lon, onChange = { lon = it; status = null }, hint = "Longitude",
                keyboard = KeyboardType.Number, modifier = Modifier.weight(1f),
            )
        }
        EinkChip(
            label = "Save location",
            selected = false,
            onClick = {
                val latValue = lat.trim().toDoubleOrNull()
                val lonValue = lon.trim().toDoubleOrNull()
                if (latValue == null || lonValue == null ||
                    latValue !in -90.0..90.0 || lonValue !in -180.0..180.0
                ) {
                    status = "Enter a valid lat (-90..90) and long (-180..180)"
                } else {
                    scope.launch {
                        settingsStore.setFixedLocation(latValue, lonValue, label.trim())
                        module.refresh(RefreshReason.SETTINGS_CHANGED)
                        status = "Saved"
                    }
                }
            },
        )
        status?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = EinkPalette.InkMuted)
        }
    }
}

@Composable
private fun CoordinateField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = hint, style = MaterialTheme.typography.labelMedium, color = EinkPalette.InkMuted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(color = EinkPalette.Ink),
            modifier = Modifier
                .fillMaxWidth()
                .border(EinkSpacing.hairline, EinkPalette.Line, RoundedCornerShape(4.dp))
                .background(EinkPalette.Paper, RoundedCornerShape(4.dp))
                .padding(horizontal = EinkSpacing.sm, vertical = EinkSpacing.sm),
        )
    }
}

private fun LocationMode.label(): String = when (this) {
    LocationMode.PRESET_TASHKENT -> "Tashkent"
    LocationMode.FIXED -> "Fixed"
    LocationMode.DEVICE -> "Device"
}
