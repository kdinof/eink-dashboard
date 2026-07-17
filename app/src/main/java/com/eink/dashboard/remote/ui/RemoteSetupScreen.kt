package com.eink.dashboard.remote.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.ui.EinkChip
import com.eink.dashboard.remote.PairingManager
import com.eink.dashboard.remote.RemoteServerState
import com.eink.dashboard.remote.TrustedSession
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.DateFormat
import java.util.Date

@Composable
fun RemoteSetupScreen(
    serverState: kotlinx.coroutines.flow.StateFlow<RemoteServerState>,
    pairing: PairingManager,
    onRegeneratePin: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onRevokeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by serverState.collectAsState()
    val pin by pairing.pin.collectAsState()
    val sessions by pairing.sessions.collectAsState()
    val url = (state as? RemoteServerState.Running)?.url

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EinkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.md),
    ) {
        Text("Remote setup", style = MaterialTheme.typography.headlineMedium)
        when (val current = state) {
            RemoteServerState.Stopped -> Text("Server stopped")
            RemoteServerState.NoWifi -> Text("Connect the reader to Wi-Fi to enable remote setup.")
            is RemoteServerState.Failed -> Text(current.message)
            is RemoteServerState.Running -> {
                Text(current.url, style = MaterialTheme.typography.titleMedium)
                QrCode(current.url)
                Text("Pairing PIN: ${pin.value}", style = MaterialTheme.typography.headlineMedium)
                Text("The PIN is single-use and expires after 10 minutes.", color = EinkPalette.InkMuted)
                EinkChip("New PIN", selected = false, onClick = onRegeneratePin)
            }
        }

        Text("Trusted browsers", style = MaterialTheme.typography.titleLarge)
        if (sessions.isEmpty()) Text("No paired browsers.", color = EinkPalette.InkMuted)
        sessions.forEach { SessionRow(it, onRevokeSession) }
        if (sessions.isNotEmpty()) EinkChip("Revoke all", selected = false, onClick = onRevokeAll)
        if (url != null) {
            Text(
                "Local HTTP is intended only for a trusted home Wi-Fi network. Closing the app stops the server.",
                color = EinkPalette.InkMuted,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun QrCode(value: String) {
    val bitmap = remember(value) {
        val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 360, 360)
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).also { out ->
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                out.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
    }
    Image(bitmap.asImageBitmap(), contentDescription = "Remote setup QR code", modifier = Modifier.size(240.dp))
}

@Composable
private fun SessionRow(session: TrustedSession, onRevoke: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.clientName, style = MaterialTheme.typography.titleMedium)
            Text(
                "Last used ${DateFormat.getDateTimeInstance().format(Date(session.lastUsedAtEpochMs))}",
                style = MaterialTheme.typography.labelMedium,
                color = EinkPalette.InkMuted,
            )
        }
        EinkChip("Revoke", selected = false, onClick = { onRevoke(session.id) })
    }
}
