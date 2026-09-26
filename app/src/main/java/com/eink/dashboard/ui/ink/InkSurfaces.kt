package com.eink.dashboard.ui.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Ink UI surfaces: card (`.ink-card` + bands), headers, dividers and feedback
 * states. Each composable names the CSS class it ports so the two stacks can be
 * kept in sync.
 */

/** `.ink-icon` — a kit line icon tinted with the current content colour. */
@Composable
fun InkIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = InkSize.iconMd,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(imageVector = icon, contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

/**
 * `.ink-card` — paper surface in a thick ink frame. Put [InkCardHeader],
 * [InkCardBody] and [InkCardFooter] inside, in that order.
 *
 * @param flat hairline frame (`--flat`) for nested or secondary cards.
 * @param inverted black card with white text (`--inverted`).
 */
@Composable
fun InkCard(
    modifier: Modifier = Modifier,
    flat: Boolean = false,
    inverted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(InkRadius.sm)
    val bg = if (inverted) InkColors.Ink else InkColors.Paper
    val fg = if (inverted) InkColors.OnInk else InkColors.Ink
    CompositionLocalProvider(LocalContentColor provides fg) {
        Column(
            modifier = modifier
                .clip(shape)
                .background(bg, shape)
                .border(if (flat) InkStroke.hair else InkStroke.bold, InkColors.Ink, shape),
            content = content,
        )
    }
}

/** `.ink-card__header` — black band with a bold white title, optional icon and meta. */
@Composable
fun InkCardHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    meta: String? = null,
) {
    InkBand(modifier = modifier.heightIn(min = InkSize.controlMd), padding = PaddingValues(InkSpace.s2, InkSpace.s2)) {
        if (icon != null) InkIcon(icon)
        Text(
            text = title,
            style = InkType.band,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = true).padding(start = if (icon == null) InkSpace.s1 else 0.dp),
        )
        if (meta != null) Text(text = meta, style = InkType.meta, maxLines = 1)
    }
}

/** `.ink-card__tab` — small black label tab in the card's top-left (or top-right) corner. */
@Composable
fun ColumnScope.InkCardTab(text: String, end: Boolean = false) {
    val shape = if (end) {
        RoundedCornerShape(bottomStart = InkRadius.sm)
    } else {
        RoundedCornerShape(bottomEnd = InkRadius.sm)
    }
    Text(
        text = text,
        style = InkType.meta,
        color = InkColors.OnInk,
        maxLines = 1,
        modifier = Modifier
            .align(if (end) Alignment.End else Alignment.Start)
            .background(InkColors.Ink, shape)
            .padding(horizontal = InkSpace.s3, vertical = InkSpace.s1),
    )
}

/**
 * `.ink-card__body` — content area.
 * @param flush no inline padding, for ruled lists that run edge to edge (`--flush`).
 */
@Composable
fun ColumnScope.InkCardBody(
    modifier: Modifier = Modifier,
    flush: Boolean = false,
    fill: Boolean = false,
    spacing: Dp = InkSpace.s3,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fill) Modifier.weight(1f) else Modifier)
            .padding(if (flush) PaddingValues(vertical = InkSpace.s1) else PaddingValues(InkSpace.s4)),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** `.ink-card__footer` — black band: source label left, meta right. */
@Composable
fun InkCardFooter(
    start: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
) {
    InkBand(modifier = modifier.heightIn(min = InkSize.controlSm), padding = PaddingValues(InkSpace.s3, InkSpace.s1)) {
        Text(
            text = start,
            style = InkType.meta,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (meta != null) Text(text = meta, style = InkType.meta, maxLines = 1)
    }
}

/** Shared ink band row used by card headers/footers and `--band` section headers. */
@Composable
fun InkBand(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(InkSpace.s3, InkSpace.s2),
    content: @Composable RowScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides InkColors.OnInk) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(InkColors.Ink)
                .padding(padding),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * `.ink-page-header` — grey uppercase eyebrow over a heavy title, with an
 * optional action slot on the right and a grey description below.
 */
@Composable
fun InkPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    sub: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = InkSpace.s4, bottom = InkSpace.s3)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(InkSpace.s3)) {
            Column(modifier = Modifier.weight(1f)) {
                if (eyebrow != null) {
                    InkEyebrow(eyebrow, modifier = Modifier.padding(bottom = InkSpace.s1))
                }
                Text(text = title, style = InkType.h1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (action != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(InkSpace.s2),
                    verticalAlignment = Alignment.CenterVertically,
                    content = action,
                )
            }
        }
        if (sub != null) {
            Text(text = sub, style = InkType.small, color = InkColors.Ink3, modifier = Modifier.padding(top = InkSpace.s1))
        }
    }
}

/**
 * `.ink-section-header` — bold section title, optional right meta and grey
 * description. [band] renders the black `--band` variant.
 */
@Composable
fun InkSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    sub: String? = null,
    band: Boolean = false,
) {
    if (band) {
        InkBand(modifier = modifier.clip(RoundedCornerShape(InkRadius.xs))) {
            Text(text = title, style = InkType.band, modifier = Modifier.weight(1f), maxLines = 1)
            if (meta != null) Text(text = meta, style = InkType.caption, color = InkColors.OnInk)
        }
        return
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = InkSpace.s3, bottom = InkSpace.s2)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = title, style = InkType.title, modifier = Modifier.weight(1f))
            if (meta != null) Text(text = meta, style = InkType.meta)
        }
        if (sub != null) {
            Text(text = sub, style = InkType.caption, modifier = Modifier.padding(top = InkSpace.s1))
        }
    }
}

/** `.ink-eyebrow` — tiny uppercase letter-spaced grey label. */
@Composable
fun InkEyebrow(text: String, modifier: Modifier = Modifier, color: Color = InkColors.Ink3) {
    Text(text = text.uppercase(), style = InkType.eyebrow, color = color, maxLines = 1, modifier = modifier)
}

/** `.ink-divider` — hairline ink rule; [bold] = `--bold`, [color] for the grey list rule. */
@Composable
fun InkDivider(
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    color: Color = InkColors.Ink,
    vertical: Boolean = false,
) {
    val thickness = if (bold) InkStroke.bold else InkStroke.hair
    Box(
        modifier = modifier
            .then(
                if (vertical) {
                    Modifier.width(thickness).fillMaxHeight()
                } else {
                    Modifier.fillMaxWidth().height(thickness)
                },
            )
            .background(color),
    )
}

/**
 * `.ink-empty` — centred empty state: framed line icon, bold short title, grey hint.
 */
@Composable
fun InkEmpty(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = InkIcons.Empty,
    hint: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = InkSpace.s10, horizontal = InkSpace.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(InkSpace.s2, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = InkSpace.s2)
                .size(InkSpace.s12)
                .border(InkStroke.hair, InkColors.Ink4, RoundedCornerShape(InkRadius.xs)),
            contentAlignment = Alignment.Center,
        ) {
            InkIcon(icon, size = InkSize.iconLg, tint = InkColors.Ink3)
        }
        Text(text = title, style = InkType.bodyStrong, textAlign = TextAlign.Center)
        if (hint != null) {
            Text(
                text = hint,
                style = InkType.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }
        if (action != null) Box(Modifier.padding(top = InkSpace.s2)) { action() }
    }
}

/**
 * `.ink-alert` — inverted banner with icon, bold title and text; [outline] is the
 * paper variant in a thick frame. Monochrome: urgency is inversion, never red.
 */
@Composable
fun InkAlert(
    title: String,
    modifier: Modifier = Modifier,
    text: String? = null,
    icon: ImageVector = InkIcons.Alert,
    outline: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(InkRadius.sm)
    val fg = if (outline) InkColors.Ink else InkColors.OnInk
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(if (outline) InkColors.Paper else InkColors.Ink, shape)
                .border(InkStroke.bold, InkColors.Ink, shape)
                .padding(horizontal = InkSpace.s4, vertical = InkSpace.s3),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
            verticalAlignment = Alignment.Top,
        ) {
            InkIcon(icon)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.half)) {
                Text(text = title, style = InkType.smallStrong)
                if (text != null) {
                    Text(
                        text = text,
                        style = InkType.caption,
                        color = if (outline) InkColors.Ink2 else InkColors.OnInk,
                    )
                }
            }
            if (action != null) Box(Modifier.align(Alignment.CenterVertically)) { action() }
        }
    }
}

/** `.ink-loading` — static "Loading…" label with three square dots in fading ink steps. */
@Composable
fun InkLoading(modifier: Modifier = Modifier, text: String = "Loading") {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s1),
    ) {
        Text(text = text, style = InkType.small, color = InkColors.Ink3)
        listOf(InkColors.Ink, InkColors.Ink3, InkColors.Ink4).forEach { dot ->
            Box(Modifier.size(InkSpace.s1).background(dot))
        }
    }
}
