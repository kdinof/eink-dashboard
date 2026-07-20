package com.eink.dashboard.modules.taskforge

import com.eink.dashboard.modules.taskforge.model.TaskForgeView

data class TaskForgeSettings(
    val view: TaskForgeView = TaskForgeView.TODAY,
    val selectedTags: Set<String> = emptySet(),
    val limit: Int = 10,
    val fileUri: String? = null,
    val fileName: String? = null,
    val canWrite: Boolean = false,
) {
    companion object {
        val DEFAULT = TaskForgeSettings()
        val ALLOWED_LIMITS = setOf(5, 10, 20)
    }
}
