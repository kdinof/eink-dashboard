package com.eink.dashboard.modules.calendar.model

/**
 * A grayscale, colour-free way to tell calendars apart on the e-ink panel.
 *
 * The card requires that calendars be distinguishable **without relying on colour**
 * (§8.1 of the plan: "оттенка; формы маркера; штриховки"). Hue carries no
 * information on a monochrome panel, so each calendar gets a combination of a
 * [shape] and a [shade] of gray. Shape is the primary signal (it survives any
 * contrast loss); shade is the secondary signal that multiplies the number of
 * distinct markers before any repeat.
 */
data class CalendarMarker(
    val shape: MarkerShape,
    /** Gray level 0f (black) … 1f (white). Kept coarse — e-ink smears fine grays. */
    val shade: Float,
) {
    /** A single crisp glyph to render before an event line. */
    val glyph: String get() = shape.glyph
}

enum class MarkerShape(val glyph: String) {
    FILLED_CIRCLE("●"), // ●
    HOLLOW_CIRCLE("○"), // ○
    SQUARE("■"),        // ■
    TRIANGLE("▲"),      // ▲
    DIAMOND("◆"),       // ◆
}

/**
 * Assigns a stable, distinct [CalendarMarker] to every calendar.
 *
 * Assignment is deterministic in a canonical order (display name, then id) so the
 * same calendar always gets the same marker across refreshes and the list is
 * stable to the eye. Shape varies fastest; shade steps only after all shapes are
 * used, giving `shapes × shades` unique markers before any pair collides.
 */
object CalendarMarkers {

    private val shades = listOf(0.0f, 0.4f, 0.65f) // black, dark gray, mid gray

    fun assign(calendars: List<CalendarInfo>): Map<Long, CalendarMarker> {
        val shapes = MarkerShape.entries
        val ordered = calendars.sortedWith(
            compareBy({ it.displayName.lowercase() }, { it.id }),
        )
        return ordered.mapIndexed { index, calendar ->
            val shape = shapes[index % shapes.size]
            val shade = shades[(index / shapes.size) % shades.size]
            calendar.id to CalendarMarker(shape, shade)
        }.toMap()
    }
}
