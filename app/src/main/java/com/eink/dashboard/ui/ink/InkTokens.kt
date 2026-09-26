package com.eink.dashboard.ui.ink

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * Ink UI design tokens, ported 1:1 from ui-kit/tokens.css. CSS px map to dp/sp:
 * the kit's `ink-page--wide` layout targets a 936px-wide tablet, which is exactly
 * the M103's 936dp short side (1404px @ 240dpi), so values carry over unscaled.
 *
 * Every component in this package reads only these objects. No hues anywhere —
 * the panel is monochrome and hierarchy comes from weight, inversion and stroke.
 */

/** Ink on paper: a few luminance steps that survive a 16-level grayscale panel. */
object InkColors {
    /** Primary text, borders, filled surfaces. */
    val Ink = Color(0xFF0A0A0A)
    /** Secondary text. */
    val Ink2 = Color(0xFF3D3D3D)
    /** Tertiary text, eyebrows, meta. */
    val Ink3 = Color(0xFF7A7A7A)
    /** Disabled text, hairlines. */
    val Ink4 = Color(0xFFB8B8B8)
    /** Page and card background. */
    val Paper = Color(0xFFFFFFFF)
    /** Subtle fill: pressed, skeleton, input background. */
    val Paper2 = Color(0xFFF2F2F2)
    /** Dividers on [Paper2]. */
    val Paper3 = Color(0xFFE4E4E4)
    /** Text on filled ink surfaces. */
    val OnInk = Color(0xFFFFFFFF)
}

/** 4dp grid. */
object InkSpace {
    val half = 2.dp
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
}

/** The thick frame is the signature of the style. */
object InkStroke {
    val hair = 1.dp
    val regular = 1.5.dp
    val bold = 2.5.dp
    val heavy = 4.dp
}

object InkRadius {
    val xs = 2.dp
    val sm = 4.dp
    val md = 8.dp
    val lg = 14.dp
}

object InkSize {
    val controlXs = 22.dp
    val controlSm = 28.dp
    val controlMd = 36.dp
    val controlLg = 44.dp
    val badgeH = 20.dp
    val iconSm = 14.dp
    val iconMd = 18.dp
    val iconLg = 24.dp
    val iconXl = 40.dp
    val meterW = 30.dp
    val meterH = 15.dp
}

/** Type scale from tokens.css (`--text-*`). */
object InkText {
    val xs2: TextUnit = 10.sp
    val xs: TextUnit = 12.sp
    val sm: TextUnit = 14.sp
    val md: TextUnit = 16.sp
    val lg: TextUnit = 20.sp
    val xl: TextUnit = 26.sp
    val xl2: TextUnit = 34.sp
    val xl3: TextUnit = 48.sp
    val hero: TextUnit = 120.sp

    /** `--tracking-eyebrow: 0.16em`. */
    val trackingEyebrow = 0.16.em

    /**
     * The kit asks for Golos Text; the device ships Roboto, whose Black weight
     * (sans-serif-black) gives the same heavy-title contrast without bundling fonts.
     */
    val sans: FontFamily = FontFamily.SansSerif
    val mono: FontFamily = FontFamily.Monospace
    val serif: FontFamily = FontFamily.Serif
}

/**
 * Ready-made text styles matching the kit's typography classes (`.ink-h1`,
 * `.ink-eyebrow`, `.ink-caption` …). Tight leading, trimmed so bands and badges
 * centre their text the way the CSS `line-height: 1` does. Styles without an
 * explicit colour inherit LocalContentColor, so they flip on inverted surfaces.
 */
object InkType {
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
    private fun style(size: TextUnit, weight: FontWeight, lineHeight: Float = 1.15f, color: Color = Color.Unspecified) =
        TextStyle(
            fontFamily = InkText.sans,
            fontSize = size,
            fontWeight = weight,
            lineHeight = size * lineHeight,
            lineHeightStyle = trim,
            color = color,
        )

    val hero = style(InkText.hero, FontWeight.Black, lineHeight = 1f).copy(letterSpacing = (-0.03).em)
    val h1 = style(InkText.xl2, FontWeight.Black).copy(letterSpacing = (-0.01).em)
    val h2 = style(InkText.xl, FontWeight.Bold)
    val h3 = style(InkText.lg, FontWeight.Bold)
    val title = style(InkText.lg, FontWeight.Black)
    val body = style(InkText.md, FontWeight.Normal, lineHeight = 1.45f)
    val bodyStrong = style(InkText.md, FontWeight.Bold, lineHeight = 1.45f)
    val small = style(InkText.sm, FontWeight.Normal, lineHeight = 1.45f)
    val smallStrong = style(InkText.sm, FontWeight.Bold)
    val caption = style(InkText.xs, FontWeight.Normal, color = InkColors.Ink3)
    val eyebrow = style(InkText.xs2, FontWeight.Bold, lineHeight = 1f, color = InkColors.Ink3)
        .copy(letterSpacing = InkText.trackingEyebrow)
    val band = style(InkText.md, FontWeight.Black)
    val meta = style(InkText.xs, FontWeight.Bold)
    val big = style(InkText.xl3, FontWeight.Black, lineHeight = 1f).copy(letterSpacing = (-0.02).em)
    val number = style(InkText.xl2, FontWeight.Black, lineHeight = 1f)
}
