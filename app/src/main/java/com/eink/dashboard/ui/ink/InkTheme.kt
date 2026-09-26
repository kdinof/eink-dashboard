package com.eink.dashboard.ui.ink

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.drawscope.ContentDrawScope

/**
 * E-ink-safe application theme built on the Ink UI tokens.
 *
 * Two deliberate departures from a normal Material app:
 *  1. **Grayscale scheme.** The whole [lightColorScheme] is mapped onto
 *     [InkColors] so no stray Material component can introduce a hue.
 *  2. **No ripple / no press animation.** [NoIndication] is the app-wide
 *     [LocalIndication], matching the kit's `--motion: 0ms`: any animation on
 *     e-ink is a visible full-screen repaint. State is shown by static inversion.
 */
@Composable
fun InkTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = InkColors.Ink,
        onPrimary = InkColors.OnInk,
        secondary = InkColors.Ink2,
        onSecondary = InkColors.OnInk,
        background = InkColors.Paper,
        onBackground = InkColors.Ink,
        surface = InkColors.Paper,
        onSurface = InkColors.Ink,
        surfaceVariant = InkColors.Paper2,
        onSurfaceVariant = InkColors.Ink2,
        outline = InkColors.Ink,
        outlineVariant = InkColors.Ink4,
        error = InkColors.Ink,
        onError = InkColors.OnInk,
    )
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalIndication provides NoIndication,
            LocalContentColor provides InkColors.Ink,
            LocalTextStyle provides InkType.body,
            content = content,
        )
    }
}

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
