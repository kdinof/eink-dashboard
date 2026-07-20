package com.eink.dashboard.dashboard

import com.eink.dashboard.dashboard.DashboardLayoutSpec.Orientation
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Golden baselines for the two-orientation dashboard layout. The placement is a
 * pure function, so the "screenshot" here is a deterministic text rendering of
 * the grid — committed under `src/test/resources/golden/` and diffed on every
 * run. A layout regression shows up as a readable text diff, with no device and
 * no flaky image comparison.
 *
 * To regenerate after an intentional change: run with `-DupdateGolden=true` and
 * copy the printed block into the matching resource file.
 */
class DashboardLayoutSpecTest {

    // Represents the intended real-module order once T03–T05 land.
    private val moduleIds = listOf("calendar", "todoist", "taskforge", "weather", "clock")

    @Test
    fun portrait_singleColumn_matchesGolden() {
        val rendered = DashboardLayoutSpec.render(
            DashboardLayoutSpec.layout(Orientation.PORTRAIT, moduleIds),
        )
        assertThat(rendered.trim()).isEqualTo(readGolden("dashboard_portrait.txt"))
    }

    @Test
    fun landscape_twoColumns_matchesGolden() {
        val rendered = DashboardLayoutSpec.render(
            DashboardLayoutSpec.layout(Orientation.LANDSCAPE, moduleIds),
        )
        assertThat(rendered.trim()).isEqualTo(readGolden("dashboard_landscape.txt"))
    }

    @Test
    fun landscape_oddCount_lastRowLeavesTrailingEmptyCell() {
        val layout = DashboardLayoutSpec.layout(Orientation.LANDSCAPE, listOf("a", "b", "c"))
        assertThat(layout.rows).isEqualTo(2)
        // c is alone on the second row, column 0; column 1 is empty.
        assertThat(layout.slots).contains(DashboardLayoutSpec.Slot("c", row = 1, column = 0))
        assertThat(layout.slots.none { it.row == 1 && it.column == 1 }).isTrue()
    }

    @Test
    fun emptyModules_rendersEmptyMarker() {
        val rendered = DashboardLayoutSpec.render(
            DashboardLayoutSpec.layout(Orientation.PORTRAIT, emptyList()),
        )
        assertThat(rendered).contains("empty")
    }

    @Test
    fun updatedLandscape_placesGlanceableModulesAboveLists() {
        val plan = DashboardLayoutSpec.updatedLandscapePlan(
            listOf("calendar", "todoist", "clock", "weather", "battery"),
        )

        assertThat(plan).isEqualTo(
            DashboardLayoutSpec.UpdatedLandscapePlan(
                topLeft = listOf("clock", "battery"),
                topRight = "weather",
                bottomLeft = "calendar",
                bottomRight = "todoist",
            ),
        )
    }

    @Test
    fun updatedLandscape_rejectsUnknownModulesForGenericFallback() {
        assertThat(DashboardLayoutSpec.updatedLandscapePlan(listOf("clock", "future-module"))).isNull()
    }

    @Test
    fun updatedLandscape_usesCompactFallbackWhenAModuleIsHidden() {
        assertThat(
            DashboardLayoutSpec.updatedLandscapePlan(
                listOf("calendar", "todoist", "clock", "weather"),
            ),
        ).isNull()
    }

    private fun readGolden(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("golden/$name")
            ?: error("Missing golden resource: golden/$name")
        return stream.bufferedReader().use { it.readText() }.trim()
    }
}
