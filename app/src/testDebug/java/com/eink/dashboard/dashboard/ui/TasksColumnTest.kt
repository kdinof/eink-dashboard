package com.eink.dashboard.dashboard.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.eink.dashboard.modules.taskforge.data.TaskForgeParser
import com.eink.dashboard.modules.taskforge.model.TaskForgeTask
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/**
 * Tap-target contract of the board task card: the whole row completes
 * a task (a bare checkbox alone is unusable on the e-ink touch layer), while
 * recurring rows stay inert, and completion failures are shown on the board.
 */
@RunWith(RobolectricTestRunner::class)
class TasksColumnTest {

    @get:Rule val compose = createComposeRule()

    private val today = LocalDate.of(2026, 8, 18)
    private val tasks: List<TaskForgeTask> = TaskForgeParser.parse(
        """
        - [ ] Оплатить счёт 📅 2026-08-18
        - [/] Пробежка 📅 2026-08-18
        - [ ] Friday review 🔁 every Friday
        """.trimIndent().toByteArray(),
    )

    @Test fun tappingAnywhereOnARowCompletesThatTask() {
        val completed = mutableListOf<TaskForgeTask>()
        compose.setContent {
            TasksCard(tasks = tasks, total = 3, state = null, today = today, notice = null, onComplete = { completed += it })
        }

        // The tap lands on the title text — nowhere near the checkbox.
        compose.onNodeWithText("Оплатить счёт").assertHasClickAction()
        compose.onNodeWithText("Оплатить счёт").performClick()

        assertThat(completed.map { it.title }).containsExactly("Оплатить счёт")
    }

    @Test fun inProgressTasksAreCompletableToo() {
        val completed = mutableListOf<TaskForgeTask>()
        compose.setContent {
            TasksCard(tasks = tasks, total = 3, state = null, today = today, notice = null, onComplete = { completed += it })
        }

        compose.onNodeWithText("Пробежка").performClick()

        assertThat(completed.map { it.title }).containsExactly("Пробежка")
    }

    @Test fun recurringRowsHaveNoClickAction() {
        val completed = mutableListOf<TaskForgeTask>()
        compose.setContent {
            TasksCard(tasks = tasks, total = 3, state = null, today = today, notice = null, onComplete = { completed += it })
        }

        compose.onNode(hasText("Friday review") and hasClickAction()).assertDoesNotExist()
        assertThat(completed).isEmpty()
    }

    @Test fun completionFailureNoticeIsRenderedOnTheBoard() {
        compose.setContent {
            TasksCard(
                tasks = tasks,
                total = 3,
                state = null,
                today = today,
                notice = "Read-only file — re-pick it in Settings via Internal storage",
                onComplete = {},
            )
        }

        compose.onNodeWithText("READ-ONLY FILE — RE-PICK IT IN SETTINGS VIA INTERNAL STORAGE").assertExists()
    }
}
