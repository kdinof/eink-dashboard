package com.eink.dashboard.remote.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.eink.dashboard.remote.PairingManager
import com.eink.dashboard.remote.RemoteServerState
import com.eink.dashboard.remote.TrustedSession
import com.eink.dashboard.ui.ink.InkAlert
import com.eink.dashboard.ui.ink.InkButton
import com.eink.dashboard.ui.ink.InkButtonSize
import com.eink.dashboard.ui.ink.InkButtonVariant
import com.eink.dashboard.ui.ink.InkCard
import com.eink.dashboard.ui.ink.InkCardBody
import com.eink.dashboard.ui.ink.InkCardFooter
import com.eink.dashboard.ui.ink.InkCardHeader
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkEmpty
import com.eink.dashboard.ui.ink.InkEyebrow
import com.eink.dashboard.ui.ink.InkHint
import com.eink.dashboard.ui.ink.InkIcon
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkListItem
import com.eink.dashboard.ui.ink.InkRadius
import com.eink.dashboard.ui.ink.InkRuledList
import com.eink.dashboard.ui.ink.InkSpace
import com.eink.dashboard.ui.ink.InkStat
import com.eink.dashboard.ui.ink.InkStroke
import com.eink.dashboard.ui.ink.InkText
import com.eink.dashboard.ui.ink.InkType
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

    val connectCard: @Composable (Modifier) -> Unit = { cardModifier ->
        InkCard(modifier = cardModifier) {
            InkCardHeader(title = "Connect a phone", icon = InkIcons.Wifi, meta = if (url != null) "Local Wi-Fi" else null)
            InkCardBody(spacing = InkSpace.s4) {
                when (val current = state) {
                    RemoteServerState.Stopped -> InkEmpty(title = "Server stopped", icon = InkIcons.Wifi, hint = "Reopen the app to start remote setup.")
                    RemoteServerState.NoWifi -> InkAlert(
                        title = "No Wi-Fi",
                        text = "Connect the reader to Wi-Fi to enable remote setup.",
                        icon = InkIcons.Wifi,
                        outline = true,
                    )
                    is RemoteServerState.Failed -> InkAlert(title = "Server failed to start", text = current.message)
                    is RemoteServerState.Running -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(InkSpace.s5),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            QrCode(current.url)
                            Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s4)) {
                                InkStat(label = "Pairing PIN", value = pin.value.chunked(3).joinToString(" "), valueStyle = InkType.big)
                                InkHint("Single-use. Expires after 10 minutes.")
                                InkButton(
                                    text = "New PIN",
                                    icon = InkIcons.Refresh,
                                    variant = InkButtonVariant.Outline,
                                    onClick = onRegeneratePin,
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(InkSpace.s1)) {
                            InkEyebrow("Or open on the phone")
                            Text(current.url, style = InkType.bodyStrong.copy(fontFamily = InkText.mono))
                        }
                    }
                }
            }
            if (url != null) {
                InkCardFooter(start = "Trusted home Wi-Fi only", meta = "Closing the app stops the server")
            }
        }
    }
    val sessionsCard: @Composable (Modifier) -> Unit = { cardModifier ->
        InkCard(modifier = cardModifier) {
            InkCardHeader(title = "Trusted browsers", icon = InkIcons.Lock, meta = "${sessions.size}")
            if (sessions.isEmpty()) {
                InkCardBody {
                    InkEmpty(title = "No paired browsers", icon = InkIcons.Lock, hint = "Scan the QR code and enter the PIN on your phone.")
                }
            } else {
                InkCardBody(flush = true, spacing = 0.dp) {
                    InkRuledList(sessions) { SessionRow(it, onRevokeSession) }
                }
                InkCardBody {
                    InkButton(
                        text = "Revoke all",
                        icon = InkIcons.Trash,
                        variant = InkButtonVariant.Outline,
                        onClick = onRevokeAll,
                    )
                }
            }
        }
    }

    // Side by side on the wide landscape panel, stacked in portrait.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= 1000.dp
        val scroll = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = InkSpace.s6)
        if (wide) {
            Row(modifier = scroll, horizontalArrangement = Arrangement.spacedBy(InkSpace.s5), verticalAlignment = Alignment.Top) {
                connectCard(Modifier.weight(1f))
                sessionsCard(Modifier.weight(1f))
            }
        } else {
            Column(modifier = scroll, verticalArrangement = Arrangement.spacedBy(InkSpace.s5)) {
                connectCard(Modifier.fillMaxWidth())
                sessionsCard(Modifier.fillMaxWidth())
            }
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
    Image(bitmap.asImageBitmap(), contentDescription = "Remote setup QR code", modifier = Modifier.size(240.dp).border(InkStroke.bold, InkColors.Ink, RoundedCornerShape(InkRadius.sm)).padding(InkSpace.s2))
}

@Composable
private fun SessionRow(session: TrustedSession, onRevoke: (String) -> Unit) {
    InkListItem(
        title = session.clientName,
        sub = "Last used ${DateFormat.getDateTimeInstance().format(Date(session.lastUsedAtEpochMs))}",
        strong = true,
        inset = InkSpace.s3,
        leading = { InkIcon(InkIcons.Device) },
        trailing = {
            InkButton(
                text = "Revoke",
                variant = InkButtonVariant.Outline,
                size = InkButtonSize.Sm,
                onClick = { onRevoke(session.id) },
            )
        },
    )
}
