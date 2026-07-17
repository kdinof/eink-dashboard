package com.eink.dashboard.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing

/**
 * E-ink-native interactive controls, built from plain `clickable` + borders
 * instead of Material `Button`/`Switch`. This avoids every animated affordance
 * (ripple, thumb slide, elevation) — the app-wide [com.eink.dashboard.dashboard.theme.NoIndication]
 * removes press feedback, and a filled/outlined swap communicates state statically.
 */

/** A bordered pill that fills solid when [selected]. Used for nav and option choices. */
@Composable
fun EinkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) EinkPalette.Ink else EinkPalette.Paper
    val fg = if (selected) EinkPalette.Paper else EinkPalette.Ink
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = fg,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = modifier
            .border(EinkSpacing.hairline, EinkPalette.Ink, RoundedCornerShape(4.dp))
            .background(bg, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = EinkSpacing.md, vertical = EinkSpacing.sm),
    )
}

/** A full-width row: a label on the left, an [ON]/[OFF] state box on the right. */
@Composable
fun EinkToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = EinkSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        val bg = if (checked) EinkPalette.Ink else EinkPalette.Paper
        val fg = if (checked) EinkPalette.Paper else EinkPalette.Ink
        Text(
            text = if (checked) "ON" else "OFF",
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(EinkSpacing.hairline, EinkPalette.Ink, RoundedCornerShape(4.dp))
                .background(bg, RoundedCornerShape(4.dp))
                .padding(horizontal = EinkSpacing.md, vertical = 6.dp),
        )
    }
}
