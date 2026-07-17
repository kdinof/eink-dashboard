package com.eink.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eink.dashboard.core.DeviceProfile

/**
 * Single Activity that hosts the whole app (single-activity + Compose
 * architecture — see docs/adr/0001-architecture.md).
 *
 * T01 scope: this only proves the toolchain and the single-activity shell build
 * and launch. Immersive fullscreen, the dashboard scaffold, the minute ticker
 * and the e-ink-safe theme are added by T02.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FoundationScreen()
        }
    }
}

/** Placeholder foundation screen. Grayscale, no animation — replaced by T02. */
@Composable
private fun FoundationScreen() {
    // Explicit black-on-white; the real e-ink theme is defined in T02.
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "E-Ink Dashboard",
                    color = Color.Black,
                    fontSize = 28.sp,
                )
                Text(
                    text = "Foundation build (T01)",
                    color = Color.Black,
                    fontSize = 16.sp,
                )
                Text(
                    text = "Target: ${DeviceProfile.MODEL} · API ${DeviceProfile.MIN_SDK} · " +
                        "${DeviceProfile.SCREEN_WIDTH_PX}×${DeviceProfile.SCREEN_HEIGHT_PX}px",
                    color = Color.Black,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FoundationScreenPreview() {
    FoundationScreen()
}
