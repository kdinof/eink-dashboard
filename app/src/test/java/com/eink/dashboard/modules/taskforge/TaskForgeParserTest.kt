package com.eink.dashboard.modules.taskforge

import com.eink.dashboard.modules.taskforge.data.TaskForgeParser
import com.eink.dashboard.modules.taskforge.model.TaskForgePriority
import com.eink.dashboard.modules.taskforge.model.TaskForgeView
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class TaskForgeParserTest {
    private val fixture = """
        # TaskForge
        ## Backlog
        ### #consulting
        - [ ] 📅 ⏫ **Закрыть честно** 📅 2026-07-18 #consulting #commitment
          - [ ] Подзадача 🔼 📅 2026-07-19 #consulting
        - [ ] Friday review ⏫ 🔁 every Friday #consulting
        ### #life
        - [/] [[Health|Пробежка]] 🔽 📅 2026-07-18 #life
        - [x] Готово ✅ 2026-07-17 #life
    """.trimIndent().toByteArray()

    @Test fun parsesCurrentVaultSyntaxWithoutLeakingMarkdownMetadataIntoTitle() {
        val tasks = TaskForgeParser.parse(fixture)

        assertThat(tasks).hasSize(5)
        assertThat(tasks[0].title).isEqualTo("Закрыть честно")
        assertThat(tasks[0].due).isEqualTo(LocalDate.of(2026, 7, 18))
        assertThat(tasks[0].priority).isEqualTo(TaskForgePriority.HIGH)
        assertThat(tasks[0].tags).containsExactly("consulting", "commitment")
        assertThat(tasks[0].section).isEqualTo("Backlog")
        assertThat(tasks[0].subsection).isEqualTo("#consulting")
        assertThat(tasks[1].indentLevel).isEqualTo(1)
        assertThat(tasks[2].recurrence).isEqualTo("every Friday")
        assertThat(tasks[3].title).isEqualTo("Пробежка")
    }

    @Test fun filtersStrictTodayTagsWithOrSemanticsAndLimit() {
        val parsed = TaskForgeParser.parse(fixture)
        val board = TaskForgeParser.board(
            parsed,
            TaskForgeSettings(
                view = TaskForgeView.TODAY,
                selectedTags = setOf("commitment", "life"),
                limit = 5,
            ),
            LocalDate.of(2026, 7, 18),
        )

        assertThat(board.tasks.map { it.title }).containsExactly("Закрыть честно", "Пробежка").inOrder()
        assertThat(board.availableTags).containsAtLeast("consulting", "commitment", "life")
    }

    @Test fun nextSevenDaysExcludesOverdueAndDayEight() {
        val source = """
            - [ ] old 📅 2026-07-17
            - [ ] today 📅 2026-07-18
            - [ ] day seven 📅 2026-07-24
            - [ ] day eight 📅 2026-07-25
        """.trimIndent().toByteArray()
        val board = TaskForgeParser.board(
            TaskForgeParser.parse(source),
            TaskForgeSettings(view = TaskForgeView.NEXT_7_DAYS, limit = 10),
            LocalDate.of(2026, 7, 18),
        )
        assertThat(board.tasks.map { it.title }).containsExactly("today", "day seven").inOrder()
    }

    @Test fun locatorSurvivesLineMovementAndDistinguishesDuplicates() {
        val original = "- [ ] same\n- [ ] other\n- [ ] same\n".toByteArray()
        val second = TaskForgeParser.parse(original)[2].source
        val moved = "- [ ] other\n- [ ] same\n- [ ] same\n".toByteArray()

        val offset = TaskForgeParser.resolveCheckboxOffset(moved, second)

        assertThat(offset).isEqualTo(moved.toString(Charsets.UTF_8).lastIndexOf("[ ]").toLong() + 1)
    }
}
