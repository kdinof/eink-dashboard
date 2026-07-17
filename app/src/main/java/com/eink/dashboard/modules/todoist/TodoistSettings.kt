package com.eink.dashboard.modules.todoist

import com.eink.dashboard.modules.todoist.model.TodoistView

/**
 * User-configurable Todoist display settings. Intentionally tiny: only the chosen
 * view. **The token is never part of settings** — it lives solely in the
 * Keystore-backed store — so persisted preferences carry no secret.
 */
data class TodoistSettings(
    val view: TodoistView = TodoistView.TODAY,
) {
    companion object {
        val DEFAULT = TodoistSettings()
    }
}
