package com.eink.dashboard.dashboard.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Grayscale-only design tokens. Every colour is chroma-free (R == G == B) because
 * the M103 panel is a monochrome e-ink display (see `DeviceProfile.IS_GRAYSCALE`
 * and docs/adr/0001-architecture.md). No colour is allowed anywhere in the UI —
 * hue carries no information on this panel and only muddies contrast.
 *
 * The ramp is deliberately coarse: e-ink resolves a handful of grays cleanly but
 * smears fine gradients, so we use pure black/white for text and a few fixed
 * grays for structure.
 */
object EinkPalette {
    /** Primary text / strong marks. */
    val Ink = Color(0xFF000000)
    /** Page and block background. */
    val Paper = Color(0xFFFFFFFF)
    /** Secondary text (labels, timestamps). */
    val InkMuted = Color(0xFF4A4A4A)
    /** Borders and dividers. */
    val Line = Color(0xFF8C8C8C)
    /** Stale/disabled content and hairlines. */
    val Faint = Color(0xFFBDBDBD)
    /** Subtle fill to separate a block header from the page. */
    val Panel = Color(0xFFEDEDED)
}

/** Spacing scale in dp. Generous by default — touch/read targets on a 240dpi panel. */
object EinkSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    /** Border thickness for block/frame outlines — 1dp reads crisply on e-ink. */
    val hairline = 1.dp
}
