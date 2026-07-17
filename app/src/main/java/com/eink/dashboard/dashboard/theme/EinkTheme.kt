package com.eink.dashboard.dashboard.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * E-ink-safe application theme.
 *
 * Two deliberate departures from a normal Material app:
 *  1. **Grayscale scheme.** The whole [lightColorScheme] is mapped onto
 *     [EinkPalette] so no component can introduce a hue.
 *  2. **No ripple / no press animation.** [NoIndication] is provided as the
 *     app-wide [LocalIndication]; any `clickable` (nav, toggles, cards) shows no
 *     animated feedback. Together with avoiding animated Material components and
 *     instant (non-crossfade) screen swaps, this keeps the panel from flashing —
 *     an animation on e-ink is a visible full-screen repaint.
 */
@Composable
fun EinkTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = EinkPalette.Ink,
        onPrimary = EinkPalette.Paper,
        secondary = EinkPalette.InkMuted,
        onSecondary = EinkPalette.Paper,
        background = EinkPalette.Paper,
        onBackground = EinkPalette.Ink,
        surface = EinkPalette.Paper,
        onSurface = EinkPalette.Ink,
        surfaceVariant = EinkPalette.Panel,
        onSurfaceVariant = EinkPalette.InkMuted,
        outline = EinkPalette.Line,
        error = EinkPalette.Ink,
        onError = EinkPalette.Paper,
    )
    CompositionLocalProvider(LocalIndication provides NoIndication) {
        MaterialTheme(
            colorScheme = scheme,
            typography = EinkTypography,
            content = content,
        )
    }
}

/**
 * Large, high-contrast type scale. E-ink favours a small number of clearly
 * distinct sizes over subtle steps; body text stays big for glanceability on a
 * wall-mounted panel.
 */
val EinkTypography = Typography(
    displaySmall = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, color = EinkPalette.Ink),
    headlineMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = EinkPalette.Ink),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = EinkPalette.Ink),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium, color = EinkPalette.Ink),
    bodyLarge = TextStyle(fontSize = 18.sp, color = EinkPalette.Ink),
    bodyMedium = TextStyle(fontSize = 16.sp, color = EinkPalette.Ink),
    labelMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = EinkPalette.InkMuted),
)

/**
 * An [Indication] that draws nothing — no ripple, no state overlay. Applied
 * app-wide via [LocalIndication] so no interaction animates the panel.
 */
object NoIndication : Indication {
    private object Instance : IndicationInstance {
        override fun ContentDrawScope.drawIndication() = drawContent()
    }

    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance = Instance
}

/** Convenience: a fully transparent colour for the rare place one is needed. */
val Transparent: Color get() = Color(0x00000000)
