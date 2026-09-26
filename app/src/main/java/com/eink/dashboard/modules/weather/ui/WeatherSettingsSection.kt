package com.eink.dashboard.modules.weather.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.RefreshReason
import com.eink.dashboard.modules.weather.LocationMode
import com.eink.dashboard.modules.weather.WeatherModule
import com.eink.dashboard.modules.weather.WeatherSettings
import com.eink.dashboard.modules.weather.WeatherSettingsStore
import com.eink.dashboard.modules.weather.data.COARSE_LOCATION_PERMISSION
import com.eink.dashboard.ui.ink.InkButton
import com.eink.dashboard.ui.ink.InkError
import com.eink.dashboard.ui.ink.InkHint
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkLabel
import com.eink.dashboard.ui.ink.InkSegmented
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkTextField
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
        verticalArrangement = Arrangement.spacedBy(InkSpace.s3),
    ) {
        InkLabel("Location")
        InkSegmented(
            options = LocationMode.entries,
            selected = settings.locationMode,
            onSelect = { mode ->
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
            label = { it.label() },
            block = true,
        )

        if (settings.locationMode == LocationMode.FIXED) {
            FixedLocationEditor(settings = settings, settingsStore = settingsStore, module = module)
        }

        InkHint("Tashkent works with no permission. Device location is opt-in and reads only a last-known fix.")
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

    Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s3)) {
        InkTextField(value = label, onValueChange = { label = it }, label = "Label")
        Row(horizontalArrangement = Arrangement.spacedBy(InkSpace.s3)) {
            InkTextField(
                value = lat, onValueChange = { lat = it; status = null }, label = "Latitude",
                keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
            )
            InkTextField(
                value = lon, onValueChange = { lon = it; status = null }, label = "Longitude",
                keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f),
            )
        }
        InkButton(
            text = "Save location",
            icon = InkIcons.Pin,
            onClick = {
                val latValue = lat.trim().toDoubleOrNull()
                val lonValue = lon.trim().toDoubleOrNull()
                if (latValue == null || lonValue == null ||
                    latValue !in -90.0..90.0 || lonValue !in -180.0..180.0
                ) {
                    status = INVALID_COORDINATES
                } else {
                    scope.launch {
                        settingsStore.setFixedLocation(latValue, lonValue, label.trim())
                        module.refresh(RefreshReason.SETTINGS_CHANGED)
                        status = "Saved"
                    }
                }
            },
        )
        status?.let { if (it == INVALID_COORDINATES) InkError(it) else InkHint(it) }
    }
}

private const val INVALID_COORDINATES = "Enter a valid lat (-90..90) and long (-180..180)"

private fun LocationMode.label(): String = when (this) {
    LocationMode.PRESET_TASHKENT -> "Tashkent"
    LocationMode.FIXED -> "Fixed"
    LocationMode.DEVICE -> "Device"
}
