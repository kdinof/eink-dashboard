package com.eink.dashboard.ui.ink

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// GENERATED from ui-kit/icons.svg by a one-off script — do not edit by hand.
// Regenerate when the kit's sprite changes so both stacks share one icon set.

/**
 * The Ink UI line icons (24×24 grid, 2px round strokes), ported 1:1 from the
 * web kit's SVG sprite. Tint them with [InkIcon]; the path colour here is a
 * placeholder that [androidx.compose.material3.Icon]-style tinting replaces.
 */
object InkIcons {

    /** `i-plus` */
    val Plus: ImageVector by lazy {
        icon("i-plus") {
            stroke("M12 5v14M5 12h14")
        }
    }

    /** `i-minus` */
    val Minus: ImageVector by lazy {
        icon("i-minus") {
            stroke("M5 12h14")
        }
    }

    /** `i-close` */
    val Close: ImageVector by lazy {
        icon("i-close") {
            stroke("M6 6l12 12M18 6L6 18")
        }
    }

    /** `i-check` */
    val Check: ImageVector by lazy {
        icon("i-check") {
            stroke("M5 12.5l4.5 4.5L19 7")
        }
    }

    /** `i-search` */
    val Search: ImageVector by lazy {
        icon("i-search") {
            stroke("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z")
            stroke("M20 20l-4.4-4.4")
        }
    }

    /** `i-more` */
    val More: ImageVector by lazy {
        icon("i-more") {
            stroke("M5 12h.01M12 12h.01M19 12h.01")
        }
    }

    /** `i-more-vertical` */
    val MoreVertical: ImageVector by lazy {
        icon("i-more-vertical") {
            stroke("M12 5v.01M12 12v.01M12 19v.01")
        }
    }

    /** `i-settings` */
    val Settings: ImageVector by lazy {
        icon("i-settings") {
            stroke("M5.5 12a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0z")
            stroke("M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z")
            stroke("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1")
        }
    }

    /** `i-edit` */
    val Edit: ImageVector by lazy {
        icon("i-edit") {
            stroke("M4 20h4L19 9l-4-4L4 16z")
            stroke("M13 7l4 4")
        }
    }

    /** `i-trash` */
    val Trash: ImageVector by lazy {
        icon("i-trash") {
            stroke("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5")
        }
    }

    /** `i-refresh` */
    val Refresh: ImageVector by lazy {
        icon("i-refresh") {
            stroke("M20 4v5h-5")
            stroke("M4 12a8 8 0 0 1 15.5-3")
            stroke("M4 20v-5h5")
            stroke("M20 12a8 8 0 0 1-15.5 3")
        }
    }

    /** `i-filter` */
    val Filter: ImageVector by lazy {
        icon("i-filter") {
            stroke("M4 5h16l-6 7.5V19l-4 2v-8.5z")
        }
    }

    /** `i-swap` */
    val Swap: ImageVector by lazy {
        icon("i-swap") {
            stroke("M4 8h15M15 4l4 4-4 4M20 16H5M9 12l-4 4 4 4")
        }
    }

    /** `i-link` */
    val Link: ImageVector by lazy {
        icon("i-link") {
            stroke("M10 14a4 4 0 0 0 5.66 0l3-3a4 4 0 0 0-5.66-5.66l-1 1")
            stroke("M14 10a4 4 0 0 0-5.66 0l-3 3a4 4 0 0 0 5.66 5.66l1-1")
        }
    }

    /** `i-download` */
    val Download: ImageVector by lazy {
        icon("i-download") {
            stroke("M12 4v11M7 10l5 5 5-5M5 20h14")
        }
    }

    /** `i-upload` */
    val Upload: ImageVector by lazy {
        icon("i-upload") {
            stroke("M12 15V4M7 9l5-5 5 5M5 20h14")
        }
    }

    /** `i-back` */
    val Back: ImageVector by lazy {
        icon("i-back") {
            stroke("M19 12H5M11 18l-6-6 6-6")
        }
    }

    /** `i-arrow-right` */
    val ArrowRight: ImageVector by lazy {
        icon("i-arrow-right") {
            stroke("M5 12h14M13 6l6 6-6 6")
        }
    }

    /** `i-arrow-up` */
    val ArrowUp: ImageVector by lazy {
        icon("i-arrow-up") {
            stroke("M12 19V5M6 11l6-6 6 6")
        }
    }

    /** `i-arrow-down` */
    val ArrowDown: ImageVector by lazy {
        icon("i-arrow-down") {
            stroke("M12 5v14M6 13l6 6 6-6")
        }
    }

    /** `i-chevron-left` */
    val ChevronLeft: ImageVector by lazy {
        icon("i-chevron-left") {
            stroke("M15 18l-6-6 6-6")
        }
    }

    /** `i-chevron-right` */
    val ChevronRight: ImageVector by lazy {
        icon("i-chevron-right") {
            stroke("M9 6l6 6-6 6")
        }
    }

    /** `i-chevron-down` */
    val ChevronDown: ImageVector by lazy {
        icon("i-chevron-down") {
            stroke("M6 9l6 6 6-6")
        }
    }

    /** `i-chevron-up` */
    val ChevronUp: ImageVector by lazy {
        icon("i-chevron-up") {
            stroke("M6 15l6-6 6 6")
        }
    }

    /** `i-trend-up` */
    val TrendUp: ImageVector by lazy {
        icon("i-trend-up") {
            stroke("M3 17l6-6 4 4 8-8M15 7h6v6")
        }
    }

    /** `i-trend-down` */
    val TrendDown: ImageVector by lazy {
        icon("i-trend-down") {
            stroke("M3 7l6 6 4-4 8 8M15 17h6v-6")
        }
    }

    /** `i-home` */
    val Home: ImageVector by lazy {
        icon("i-home") {
            stroke("M4 10.5L12 4l8 6.5V20H4z")
            stroke("M10 20v-5h4v5")
        }
    }

    /** `i-todo` */
    val Todo: ImageVector by lazy {
        icon("i-todo") {
            stroke("M7 4h10a2 2 0 0 1 2 2v13a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-13a2 2 0 0 1 2 -2z")
            stroke("M10 2.5h4a1 1 0 0 1 1 1v1a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-1a1 1 0 0 1 1 -1z")
            stroke("M9 13l2 2 4-4")
        }
    }

    /** `i-cards` */
    val Cards: ImageVector by lazy {
        icon("i-cards") {
            stroke("M4.5 7h11a1.5 1.5 0 0 1 1.5 1.5v11a1.5 1.5 0 0 1 -1.5 1.5h-11a1.5 1.5 0 0 1 -1.5 -1.5v-11a1.5 1.5 0 0 1 1.5 -1.5z")
            stroke("M7 3h12a2 2 0 0 1 2 2v12")
        }
    }

    /** `i-device` */
    val Device: ImageVector by lazy {
        icon("i-device") {
            stroke("M6 3h12a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z")
            stroke("M8 7h8v7H8zM10 17.5h4")
        }
    }

    /** `i-user` */
    val User: ImageVector by lazy {
        icon("i-user") {
            stroke("M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z")
            stroke("M4 21a8 8 0 0 1 16 0")
        }
    }

    /** `i-calendar` */
    val Calendar: ImageVector by lazy {
        icon("i-calendar") {
            stroke("M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z")
            stroke("M3 10h18M8 3v4M16 3v4")
        }
    }

    /** `i-bell` */
    val Bell: ImageVector by lazy {
        icon("i-bell") {
            stroke("M6 16v-5a6 6 0 0 1 12 0v5l2 2H4z")
            stroke("M10 21h4")
        }
    }

    /** `i-clock` */
    val Clock: ImageVector by lazy {
        icon("i-clock") {
            stroke("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z")
            stroke("M12 7v5l3 2")
        }
    }

    /** `i-info` */
    val Info: ImageVector by lazy {
        icon("i-info") {
            stroke("M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0z")
            stroke("M12 11v5M12 7.5v.01")
        }
    }

    /** `i-alert` */
    val Alert: ImageVector by lazy {
        icon("i-alert") {
            stroke("M12 3.5L2.5 20h19z")
            stroke("M12 10v4M12 17v.01")
        }
    }

    /** `i-empty` */
    val Empty: ImageVector by lazy {
        icon("i-empty") {
            stroke("M3 13l3-8h12l3 8v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z")
            stroke("M3 13h5l1 2h6l1-2h5")
        }
    }

    /** `i-battery` */
    val Battery: ImageVector by lazy {
        icon("i-battery") {
            stroke("M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z")
            stroke("M22 11v2")
        }
    }

    /** `i-battery-charging` */
    val BatteryCharging: ImageVector by lazy {
        icon("i-battery-charging") {
            stroke("M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-13a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z")
            stroke("M22 11v2M11.5 9.5L9 12h3.5L10 14.5")
        }
    }

    /** `i-wifi` */
    val Wifi: ImageVector by lazy {
        icon("i-wifi") {
            stroke("M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0M12 19.5v.01")
        }
    }

    /** `i-lock` */
    val Lock: ImageVector by lazy {
        icon("i-lock") {
            stroke("M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-6a2 2 0 0 1 2 -2z")
            stroke("M8 11V7a4 4 0 0 1 8 0v4")
        }
    }

    /** `i-eye` */
    val Eye: ImageVector by lazy {
        icon("i-eye") {
            stroke("M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z")
            stroke("M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z")
        }
    }

    /** `i-pin` */
    val Pin: ImageVector by lazy {
        icon("i-pin") {
            stroke("M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z")
            stroke("M9.5 9.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z")
        }
    }

    /** `i-sun` */
    val Sun: ImageVector by lazy {
        icon("i-sun") {
            stroke("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0z")
            stroke("M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4")
        }
    }

    /** `i-moon` */
    val Moon: ImageVector by lazy {
        icon("i-moon") {
            stroke("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
        }
    }

    /** `i-cloud` */
    val Cloud: ImageVector by lazy {
        icon("i-cloud") {
            stroke("M7 19a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.7 10.1 4.5 4.5 0 0 1 17 19z")
        }
    }

    /** `i-rain` */
    val Rain: ImageVector by lazy {
        icon("i-rain") {
            stroke("M7 15a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.7 6.1 4.5 4.5 0 0 1 17 15z")
            stroke("M8 18l-1 2.5M12 18l-1 2.5M16 18l-1 2.5")
        }
    }

    /** `i-snow` */
    val Snow: ImageVector by lazy {
        icon("i-snow") {
            stroke("M7 15a4 4 0 0 1-.5-7.97A6 6 0 0 1 17.7 6.1 4.5 4.5 0 0 1 17 15z")
            stroke("M8 18.5v.01M12 18.5v.01M16 18.5v.01M10 21.5v.01M14 21.5v.01")
        }
    }

    /** `i-wind` */
    val Wind: ImageVector by lazy {
        icon("i-wind") {
            stroke("M3 8h10a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7")
        }
    }

    /** `i-note` */
    val Note: ImageVector by lazy {
        icon("i-note") {
            stroke("M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2z")
            stroke("M9 8h6M9 12h6M9 16h4")
        }
    }

    /** `i-image` */
    val Image: ImageVector by lazy {
        icon("i-image") {
            stroke("M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-12a2 2 0 0 1 2 -2z")
            stroke("M7 10a2 2 0 1 0 4 0a2 2 0 1 0 -4 0z")
            stroke("M21 16l-5-5-9 9")
        }
    }

    /** `i-book` */
    val Book: ImageVector by lazy {
        icon("i-book") {
            stroke("M12 7v13M12 7a3 3 0 0 0-3-2H3v13h6a3 3 0 0 1 3 2M12 7a3 3 0 0 1 3-2h6v13h-6a3 3 0 0 0-3 2")
        }
    }

    /** `i-quote` */
    val Quote: ImageVector by lazy {
        icon("i-quote") {
            stroke("M5 18c2-1.5 3-3.5 3-6V7H4v5h4M15 18c2-1.5 3-3.5 3-6V7h-4v5h4")
        }
    }

    /** `i-list` */
    val List: ImageVector by lazy {
        icon("i-list") {
            stroke("M9 6h11M9 12h11M9 18h11M4.5 6v.01M4.5 12v.01M4.5 18v.01")
        }
    }

    /** `i-grid` */
    val Grid: ImageVector by lazy {
        icon("i-grid") {
            stroke("M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z")
            stroke("M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z")
            stroke("M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z")
            stroke("M15 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z")
        }
    }

    /** `i-star` */
    val Star: ImageVector by lazy {
        icon("i-star") {
            stroke("M12 3l2.8 5.8 6.2.9-4.5 4.4 1.1 6.3L12 17.4l-5.6 3 1.1-6.3L3 9.7l6.2-.9z")
        }
    }

    /** `i-heart` */
    val Heart: ImageVector by lazy {
        icon("i-heart") {
            stroke("M12 20s-8-4.8-8-11a4.5 4.5 0 0 1 8-2.8A4.5 4.5 0 0 1 20 9c0 6.2-8 11-8 11z")
        }
    }

    /** `i-sparkle` */
    val Sparkle: ImageVector by lazy {
        icon("i-sparkle") {
            stroke("M12 3c.8 5 4 8.2 9 9-5 .8-8.2 4-9 9-.8-5-4-8.2-9-9 5-.8 8.2-4 9-9z")
        }
    }

    /** `i-star-fill` */
    val StarFill: ImageVector by lazy {
        icon("i-star-fill") {
            fill("M12 1.9l3.4 6.9 7.6 1.1-5.5 5.4 1.3 7.6L12 19.3l-6.8 3.6 1.3-7.6L1 9.9l7.6-1.1z")
        }
    }

    /** `i-heart-fill` */
    val HeartFill: ImageVector by lazy {
        icon("i-heart-fill") {
            fill("M12 21.2l-.5-.3C11.2 20.7 3 15.9 3 9a5.5 5.5 0 0 1 9-4.2A5.5 5.5 0 0 1 21 9c0 6.9-8.2 11.7-8.5 11.9z")
        }
    }

    /** `i-circle-fill` */
    val CircleFill: ImageVector by lazy {
        icon("i-circle-fill") {
            fill("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z")
        }
    }
}

private class IconScope(private val builder: ImageVector.Builder) {
    fun stroke(d: String) {
        builder.addPath(
            pathData = addPathNodes(d),
            stroke = SolidColor(InkColors.Ink),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    fun fill(d: String) {
        builder.addPath(pathData = addPathNodes(d), fill = SolidColor(InkColors.Ink))
    }
}

private inline fun icon(name: String, block: IconScope.() -> Unit): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
    IconScope(builder).block()
    return builder.build()
}
