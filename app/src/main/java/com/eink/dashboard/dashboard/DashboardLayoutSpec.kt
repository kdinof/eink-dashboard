package com.eink.dashboard.dashboard

/**
 * Pure placement of dashboard blocks into a grid, independent of Compose. The
 * shell reads this to build the portrait/landscape layouts, and the golden
 * tests serialize it to lock the two-orientation baselines without rendering a
 * real screen (no device, no flaky image diffing).
 *
 * Rule: fixed column count per orientation, blocks filled row-major in registry
 * order. Portrait is a single tall column on the M103's 936dp width; landscape
 * splits into two columns across the 1248dp width. Hidden modules are removed
 * before placement so the grid never leaves a gap.
 */
object DashboardLayoutSpec {

    enum class Orientation(val columns: Int) {
        PORTRAIT(1),
        LANDSCAPE(2),
    }

    /** A block's position in the grid. */
    data class Slot(val moduleId: String, val row: Int, val column: Int)

    data class Layout(
        val orientation: Orientation,
        val columns: Int,
        val slots: List<Slot>,
    ) {
        val rows: Int get() = slots.maxOfOrNull { it.row + 1 } ?: 0
    }

    /**
     * Place [visibleModuleIds] (already filtered to visible, in canonical order)
     * into a grid for [orientation]. Column-major index → (row, column) row-major.
     */
    fun layout(orientation: Orientation, visibleModuleIds: List<String>): Layout {
        val cols = orientation.columns
        val slots = visibleModuleIds.mapIndexed { index, id ->
            Slot(moduleId = id, row = index / cols, column = index % cols)
        }
        return Layout(orientation, cols, slots)
    }

    /**
     * Deterministic text rendering of a layout — one line per grid row, columns
     * separated by " | ", empty cells shown as "·". This is the exact form
     * committed as the golden baseline, so a layout regression shows up as a
     * readable diff.
     */
    fun render(layout: Layout): String {
        if (layout.slots.isEmpty()) return "(empty: ${layout.orientation}, ${layout.columns} col)"
        val byCell = layout.slots.associateBy { it.row to it.column }
        val lines = (0 until layout.rows).map { row ->
            (0 until layout.columns).joinToString(" | ") { col ->
                byCell[row to col]?.moduleId ?: "·"
            }
        }
        return buildString {
            appendLine("orientation=${layout.orientation} columns=${layout.columns} rows=${layout.rows}")
            lines.forEach { appendLine(it) }
        }.trimEnd()
    }
}
