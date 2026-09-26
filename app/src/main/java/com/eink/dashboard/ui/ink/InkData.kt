package com.eink.dashboard.ui.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Ink UI data display: badges and tags, lists, key/value, meters, progress,
 * calendar leaf, stats and the compact weather row.
 */

/** `.ink-badge` — solid ink rectangle with white bold text; [outline] = framed paper variant. */
@Composable
fun InkBadge(
    text: String,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(InkRadius.xs)
    val fg = if (outline) InkColors.Ink else InkColors.OnInk
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            modifier = modifier
                .heightIn(min = InkSize.badgeH)
                .background(if (outline) InkColors.Paper else InkColors.Ink, shape)
                .border(InkStroke.regular, InkColors.Ink, shape)
                .padding(horizontal = InkSpace.s2, vertical = InkSpace.half),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.half * 1.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) InkIcon(icon, size = InkSize.iconSm)
            Text(text = text, style = InkType.meta.copy(lineHeight = InkText.xs), maxLines = 1)
        }
    }
}

/** `.ink-tag` — small outline pill in grey; [solid] = inverted tag. */
@Composable
fun InkTag(text: String, modifier: Modifier = Modifier, solid: Boolean = false) {
    Box(
        modifier = modifier
            .height(InkSize.badgeH)
            .background(if (solid) InkColors.Ink else InkColors.Paper, CircleShape)
            .border(InkStroke.regular, if (solid) InkColors.Ink else InkColors.Ink4, CircleShape)
            .padding(horizontal = InkSpace.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = InkType.eyebrow.copy(letterSpacing = 0.sp, fontWeight = FontWeight.Medium),
            color = if (solid) InkColors.OnInk else InkColors.Ink3,
            maxLines = 1,
        )
    }
}

/**
 * `.ink-list__item` — one row: optional leading slot, title + grey sub-line,
 * trailing slot. Wrap rows in [InkRuledList] for notebook separators.
 */
@Composable
fun InkListItem(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    inset: Dp = 0.dp,
    titleMaxLines: Int = 1,
    strong: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = InkSize.controlMd)
            .padding(horizontal = inset, vertical = InkSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.half)) {
            Text(
                text = title,
                style = InkType.small.copy(
                    fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
                    lineHeight = InkText.sm * 1.15f,
                ),
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            if (!sub.isNullOrEmpty()) {
                Text(text = sub, style = InkType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            CompositionLocalProvider(LocalContentColor provides InkColors.Ink2) { trailing() }
        }
    }
}

/**
 * `.ink-list--ruled` — renders [items] with a grey hairline between rows, like
 * notebook paper. [inset] pads rows inside flush card bodies (`--inset`).
 */
@Composable
fun <T> InkRuledList(
    items: List<T>,
    modifier: Modifier = Modifier,
    row: @Composable (T) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            if (index > 0) InkDivider(color = InkColors.Ink3)
            row(item)
        }
    }
}

/** `.ink-list--numbered` marker — solid black circle with a white digit. */
@Composable
fun InkNumberDisc(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(InkSize.badgeH).background(InkColors.Ink, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = number.toString(), style = InkType.meta.copy(lineHeight = InkText.xs), color = InkColors.OnInk)
    }
}

/** `.ink-kv` — key/value rows with hairline separators. */
@Composable
fun InkKv(rows: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { (key, value) -> InkKvRow(key, value) }
    }
}

/** `.ink-kv__row` — grey key left, bold tabular value right. */
@Composable
fun InkKvRow(key: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = InkSize.controlSm).padding(vertical = InkSpace.s2),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = key,
                style = InkType.small.copy(lineHeight = InkText.sm * 1.15f),
                color = InkColors.Ink2,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = InkType.smallStrong,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        InkDivider(color = InkColors.Ink4)
    }
}

/**
 * `.ink-meter` — battery-like gauge: outlined body with a nub, solid ink level,
 * label next to it. [large] = `--lg` (×1.6 / ×1.5 with a bold frame).
 */
@Composable
fun InkMeter(
    percent: Int?,
    modifier: Modifier = Modifier,
    label: String? = percent?.let { "$it%" },
    large: Boolean = false,
) {
    val w = if (large) InkSize.meterW * 1.6f else InkSize.meterW
    val h = if (large) InkSize.meterH * 1.5f else InkSize.meterH
    val frame = if (large) InkStroke.bold else InkStroke.regular
    val shape = RoundedCornerShape(InkRadius.xs)
    val fraction = (percent ?: 0).coerceIn(0, 100) / 100f
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = w, height = h)
                    .border(frame, InkColors.Ink, shape)
                    .padding(frame + InkSpace.half),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(InkColors.Ink))
            }
            Box(
                Modifier
                    .padding(start = InkSpace.half)
                    .size(width = if (large) InkSpace.half * 1.5f else InkSpace.half, height = h * 0.4f)
                    .background(InkColors.Ink),
            )
        }
        if (label != null) {
            Text(text = label, style = (if (large) InkType.bodyStrong else InkType.meta).copy(lineHeight = InkText.md))
        }
    }
}

/**
 * `.ink-progress` — thick bordered track with a solid ink fill. [segmented]
 * snaps the fill to ten discrete blocks.
 */
@Composable
fun InkProgress(fraction: Float, modifier: Modifier = Modifier, segmented: Boolean = false, small: Boolean = false) {
    val shape = RoundedCornerShape(InkRadius.xs)
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (small) InkSpace.s2 else InkSpace.s4)
            .border(if (small) InkStroke.regular else InkStroke.bold, InkColors.Ink, shape)
            .padding((if (small) InkStroke.regular else InkStroke.bold) + InkSpace.half),
    ) {
        if (segmented) {
            val filled = (value * 10).toInt()
            Row(Modifier.fillMaxWidth().fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(InkSpace.half)) {
                repeat(10) { i ->
                    Box(Modifier.weight(1f).fillMaxHeight().background(if (i < filled) InkColors.Ink else InkColors.Paper))
                }
            }
        } else {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value).background(InkColors.Ink))
        }
    }
}

/** `.ink-date-block` — calendar leaf: month band, big day, weekday. */
@Composable
fun InkDateBlock(month: String, day: String, weekday: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(InkRadius.sm)
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .widthIn(min = InkText.xl3.value.dp + InkSpace.s6 + InkSpace.s1)
            .background(InkColors.Paper, shape)
            .border(InkStroke.bold, InkColors.Ink, shape)
            .padding(InkStroke.bold),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = month,
            style = InkType.meta,
            color = InkColors.OnInk,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .background(InkColors.Ink)
                .padding(horizontal = InkSpace.s2, vertical = InkSpace.half + InkSpace.half),
        )
        Text(
            text = day,
            style = InkType.big,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = InkSpace.s2, end = InkSpace.s2, top = InkSpace.s2),
        )
        Text(
            text = weekday,
            style = InkType.caption.copy(fontWeight = FontWeight.Medium),
            color = InkColors.Ink2,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(start = InkSpace.s2, end = InkSpace.s2, top = InkSpace.s1, bottom = InkSpace.s2),
        )
    }
}

/** `.ink-stat` — eyebrow label, big tabular number (+ unit), optional delta line. */
@Composable
fun InkStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    delta: String? = null,
    icon: ImageVector? = null,
    valueStyle: androidx.compose.ui.text.TextStyle = InkType.number,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(InkSpace.s1)) {
        InkEyebrow(label)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(InkSpace.s1)) {
            if (icon != null) InkIcon(icon, size = InkSize.iconLg, modifier = Modifier.padding(end = InkSpace.s1))
            Text(text = value, style = valueStyle, maxLines = 1)
            if (unit != null) Text(text = unit, style = InkType.smallStrong, color = InkColors.Ink3)
        }
        if (delta != null) Text(text = delta, style = InkType.meta, color = InkColors.Ink2, maxLines = 1)
    }
}

/** `.ink-weather` — hero glyph, big temperature, condition and grey meta line. */
@Composable
fun InkWeatherSummary(
    temperature: String,
    condition: String,
    meta: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InkIcon(icon, size = InkSize.iconXl)
        Text(text = temperature, style = InkType.big, maxLines = 1)
        Column(verticalArrangement = Arrangement.spacedBy(InkSpace.half)) {
            Text(text = condition, style = InkType.smallStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = meta, style = InkType.caption, maxLines = 2)
        }
    }
}

/** Grid cell separators for `.ink-stat-grid--flush`: a vertical hairline between equal-weight cells. */
@Composable
fun InkStatGrid(
    cells: Int,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(InkSpace.s3),
    cell: @Composable (Int) -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        repeat(cells) { index ->
            if (index > 0) Box(Modifier.width(InkStroke.hair).fillMaxHeight().background(InkColors.Ink4))
            Box(Modifier.weight(1f).padding(contentPadding)) { cell(index) }
        }
    }
}
