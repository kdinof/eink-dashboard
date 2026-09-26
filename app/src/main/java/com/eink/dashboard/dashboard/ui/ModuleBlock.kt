package com.eink.dashboard.dashboard.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.core.time.TimeFormat
import com.eink.dashboard.dashboard.DashboardModule
import com.eink.dashboard.dashboard.ModuleState
import com.eink.dashboard.ui.ink.InkAlert
import com.eink.dashboard.ui.ink.InkCard
import com.eink.dashboard.ui.ink.InkCardBody
import com.eink.dashboard.ui.ink.InkCardHeader
import com.eink.dashboard.ui.ink.InkColors
import com.eink.dashboard.ui.ink.InkIcons
import com.eink.dashboard.ui.ink.InkType

/**
 * Shell chrome drawn around every [DashboardModule]: an `.ink-card` whose black
 * header band carries the module title and a status meta derived from
 * [ModuleState] (last-updated time, "stale", DEMO). The module never draws its
 * own frame or title — the shell owns block appearance so all blocks look uniform.
 */
@Composable
fun ModuleBlock(module: DashboardModule, modifier: Modifier = Modifier) {
    val state by module.state.collectAsStateWithLifecycle()
    InkCard(modifier = modifier) {
        InkCardHeader(title = module.title, meta = statusMeta(module, state))
        InkCardBody {
            ModuleBody(module, state)
        }
    }
}

/** Right-aligned band meta: DEMO tag, last-updated time, "stale", or an error marker. */
private fun statusMeta(module: DashboardModule, state: ModuleState): String? {
    val parts = buildList {
        if (module.isDemo) add("DEMO")
        when (state) {
            ModuleState.Loading -> add("…")
            is ModuleState.Ok -> {
                state.lastUpdatedEpochMs?.let { add(TimeFormat.clock(it)) }
                if (state.isStale) add("stale")
            }
            is ModuleState.Empty -> Unit
            is ModuleState.Error -> add("error")
        }
    }
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun ModuleBody(module: DashboardModule, state: ModuleState) {
    when (state) {
        is ModuleState.Error -> InkAlert(title = "Update failed", text = state.message, outline = true, icon = InkIcons.Alert)
        is ModuleState.Empty -> Text(text = "Nothing to show", style = InkType.small, color = InkColors.Ink3)
        // Loading and Ok both let the module render; a module may show a
        // placeholder while Loading. The shell does not spin — no animation.
        else -> module.Content(Modifier.fillMaxWidth())
    }
}
