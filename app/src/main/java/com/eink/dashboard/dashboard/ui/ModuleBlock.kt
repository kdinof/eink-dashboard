package com.eink.dashboard.dashboard.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.time.TimeFormat
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing

/**
 * Shell chrome drawn around every [DashboardModule]: an outlined frame, a title
 * row (with a DEMO tag for sample modules), a status line derived from
 * [ModuleState], and the module's own body. The module never draws its own
 * border or title — the shell owns block appearance so all blocks look uniform.
 */
@Composable
fun ModuleBlock(module: DashboardModule, modifier: Modifier = Modifier) {
    val state by module.state.collectAsStateWithLifecycle()
    Column(
        modifier = modifier
            .border(EinkSpacing.hairline, EinkPalette.Line, RoundedCornerShape(2.dp))
            .padding(EinkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = module.title, style = MaterialTheme.typography.titleMedium)
                if (module.isDemo) {
                    Text(
                        text = "  DEMO",
                        style = MaterialTheme.typography.labelMedium,
                        color = EinkPalette.InkMuted,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            StatusHint(state)
        }
        ModuleBody(module, state)
    }
}

/** Right-aligned status hint: last-updated time, "stale", or an error marker. */
@Composable
private fun StatusHint(state: ModuleState) {
    val text = when (state) {
        ModuleState.Loading -> "…"
        is ModuleState.Ok -> buildString {
            state.lastUpdatedEpochMs?.let { append(TimeFormat.clock(it)) }
            if (state.isStale) append(if (isEmpty()) "stale" else " · stale")
        }
        is ModuleState.Empty -> ""
        is ModuleState.Error -> "!"
    }
    if (text.isNotEmpty()) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = EinkPalette.InkMuted)
    }
}

@Composable
private fun ModuleBody(module: DashboardModule, state: ModuleState) {
    when (state) {
        is ModuleState.Error -> Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.InkMuted,
        )
        is ModuleState.Empty -> Text(
            text = "Nothing to show",
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.Faint,
        )
        // Loading and Ok both let the module render; a module may show a
        // placeholder while Loading. The shell does not spin — no animation.
        else -> module.Content(Modifier.fillMaxWidth())
    }
}
