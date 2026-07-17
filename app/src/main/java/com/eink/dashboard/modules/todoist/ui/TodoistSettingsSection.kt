package com.eink.dashboard.modules.todoist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eink.dashboard.dashboard.theme.EinkPalette
import com.eink.dashboard.dashboard.theme.EinkSpacing
import com.eink.dashboard.dashboard.ui.EinkChip
import com.eink.dashboard.modules.todoist.TodoistModule
import com.eink.dashboard.modules.todoist.TodoistSettingsStore
import com.eink.dashboard.modules.todoist.model.TodoistView
import kotlinx.coroutines.launch

/**
 * Per-module settings surfaced in the shell's Settings screen (via `hasSettings`):
 * enter/replace the Todoist personal token (masked, verified on save), clear it, and
 * pick the Today / Upcoming view. The token is written straight to the Keystore-backed
 * store and never held in persisted UI state beyond the transient input field.
 */
@Composable
fun TodoistSettingsSection(
    module: TodoistModule,
    settingsStore: TodoistSettingsStore,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by settingsStore.settings.collectAsStateWithLifecycle(
        initialValue = com.eink.dashboard.modules.todoist.TodoistSettings.DEFAULT,
    )
    val hasToken by module.hasToken.collectAsStateWithLifecycle()

    var tokenInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(EinkSpacing.sm),
    ) {
        Text(
            text = if (hasToken) "Token: configured" else "Token: not set",
            style = MaterialTheme.typography.bodyMedium,
            color = EinkPalette.InkMuted,
        )

        BasicTextField(
            value = tokenInput,
            onValueChange = { tokenInput = it; status = null },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = TextStyle(color = EinkPalette.Ink),
            modifier = Modifier
                .fillMaxWidth()
                .border(EinkSpacing.hairline, EinkPalette.Line, RoundedCornerShape(4.dp))
                .background(EinkPalette.Paper, RoundedCornerShape(4.dp))
                .padding(horizontal = EinkSpacing.sm, vertical = EinkSpacing.sm),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            EinkChip(
                label = "Save & verify",
                selected = false,
                onClick = {
                    val entered = tokenInput.trim()
                    if (entered.isEmpty()) {
                        status = "Enter a token first"
                        return@EinkChip
                    }
                    scope.launch {
                        val error = module.saveAndVerifyToken(entered)
                        status = if (error == null) "Verified" else "Rejected — check the token"
                        // Never keep the raw token in UI state once handed to the store.
                        tokenInput = ""
                    }
                },
            )
            if (hasToken) {
                EinkChip(
                    label = "Clear token",
                    selected = false,
                    onClick = { scope.launch { module.clearToken(); status = "Cleared" } },
                )
            }
        }

        status?.let {
            Text(text = it, style = MaterialTheme.typography.labelMedium, color = EinkPalette.InkMuted)
        }

        Text(text = "View", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(EinkSpacing.sm)) {
            TodoistView.entries.forEach { view ->
                EinkChip(
                    label = view.label(),
                    selected = settings.view == view,
                    onClick = {
                        scope.launch {
                            settingsStore.setView(view)
                            module.refresh(com.eink.dashboard.dashboard.RefreshReason.SETTINGS_CHANGED)
                        }
                    },
                )
            }
        }
    }
}

private fun TodoistView.label(): String = when (this) {
    TodoistView.TODAY -> "Today"
    TodoistView.UPCOMING -> "Upcoming 7d"
}
