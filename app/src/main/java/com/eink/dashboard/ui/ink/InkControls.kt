package com.eink.dashboard.ui.ink

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/*
 * Ink UI controls, built from plain `clickable` + borders instead of Material
 * Button/Switch so nothing animates (ripple, thumb slide, elevation). The
 * app-wide NoIndication removes press feedback; state is a static inversion.
 */

enum class InkButtonVariant { Solid, Outline, Ghost }
enum class InkButtonSize(val height: Dp, val paddingX: Dp) {
    Sm(InkSize.controlSm, InkSpace.s3),
    Md(InkSize.controlMd, InkSpace.s4),
    Lg(InkSize.controlLg, InkSpace.s5),
}

/**
 * `.ink-button` — solid ink pill with bold white text; `--outline` is a thick
 * frame on paper, `--ghost` text only. [square] swaps the pill for `--square`.
 * Disabled buttons get the kit's dashed-grey look (drawn as a grey frame).
 */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: InkButtonVariant = InkButtonVariant.Solid,
    size: InkButtonSize = InkButtonSize.Md,
    icon: ImageVector? = null,
    square: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = if (square) RoundedCornerShape(InkRadius.sm) else CircleShape
    val (bg, fg, frame) = when {
        !enabled -> Triple(InkColors.Paper, InkColors.Ink4, InkColors.Ink4)
        variant == InkButtonVariant.Solid -> Triple(InkColors.Ink, InkColors.OnInk, InkColors.Ink)
        variant == InkButtonVariant.Outline -> Triple(InkColors.Paper, InkColors.Ink, InkColors.Ink)
        else -> Triple(InkColors.Paper, InkColors.Ink, InkColors.Paper)
    }
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            modifier = modifier
                .heightIn(min = size.height)
                .background(bg, shape)
                .border(InkStroke.bold, frame, shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = size.paddingX),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s1, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) InkIcon(icon, size = InkSize.iconSm)
            Text(
                text = text,
                style = InkType.smallStrong.copy(fontWeight = FontWeight.Black),
                maxLines = 1,
            )
        }
    }
}

/** `.ink-button--icon` — square icon-only button. */
@Composable
fun InkIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: InkButtonVariant = InkButtonVariant.Outline,
) {
    val shape = RoundedCornerShape(InkRadius.sm)
    val solid = variant == InkButtonVariant.Solid
    Box(
        modifier = modifier
            .size(InkSize.controlMd)
            .background(if (solid) InkColors.Ink else InkColors.Paper, shape)
            .border(InkStroke.bold, if (variant == InkButtonVariant.Ghost) InkColors.Paper else InkColors.Ink, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        InkIcon(icon, tint = if (solid) InkColors.OnInk else InkColors.Ink, contentDescription = contentDescription)
    }
}

/**
 * `.ink-chip` — plain text label; selected = solid black pill with white bold
 * text. [outline] adds the thin grey frame of `--outline`.
 */
@Composable
fun InkChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outline: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val frame = when {
        selected -> InkColors.Ink
        outline -> InkColors.Ink4
        else -> InkColors.Paper
    }
    val fg = when {
        !enabled -> InkColors.Ink4
        selected -> InkColors.OnInk
        else -> InkColors.Ink
    }
    CompositionLocalProvider(LocalContentColor provides fg) {
        Row(
            modifier = modifier
                .heightIn(min = InkSize.controlMd)
                .widthIn(min = InkSize.controlMd + InkSpace.s6)
                .background(if (selected) InkColors.Ink else InkColors.Paper, CircleShape)
                .border(if (outline && !selected) InkStroke.regular else InkStroke.bold, frame, CircleShape)
                .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
                .padding(horizontal = InkSpace.s4),
            horizontalArrangement = Arrangement.spacedBy(InkSpace.s1, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) InkIcon(icon, size = InkSize.iconSm)
            Text(
                text = label,
                style = InkType.small.copy(
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
                    lineHeight = InkText.sm,
                ),
                maxLines = 1,
            )
        }
    }
}

/** `.ink-chips--wrap` — a row of chips that wraps onto several lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkChips(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s1),
        verticalArrangement = Arrangement.spacedBy(InkSpace.s1),
    ) { content() }
}

/**
 * `.ink-segmented` — joined segments in one pill frame; the selected segment is
 * inverted. [block] stretches to full width with equal segments.
 */
@Composable
fun <T> InkSegmented(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    block: Boolean = false,
) {
    Row(
        modifier = modifier
            .then(if (block) Modifier.fillMaxWidth() else Modifier)
            .height(IntrinsicSize.Min)
            .border(InkStroke.bold, InkColors.Ink, CircleShape)
            .padding(InkStroke.bold)
            .background(InkColors.Paper, CircleShape),
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) Box(Modifier.width(InkStroke.regular).fillMaxHeight().background(InkColors.Ink))
            val isSelected = option == selected
            val shape = when (index) {
                0 -> RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
                options.lastIndex -> RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
                else -> RoundedCornerShape(0)
            }
            Box(
                modifier = Modifier
                    .then(if (block) Modifier.weight(1f) else Modifier)
                    .heightIn(min = InkSize.controlMd - InkStroke.bold * 2)
                    .background(if (isSelected) InkColors.Ink else InkColors.Paper, shape)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = InkSpace.s4),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = InkType.smallStrong.copy(
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        lineHeight = InkText.sm,
                    ),
                    color = if (isSelected) InkColors.OnInk else InkColors.Ink,
                    maxLines = 1,
                )
            }
        }
    }
}

/** `.ink-switch` — framed pill; checked = solid ink track with a paper knob on the right. */
@Composable
fun InkSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val frame = if (enabled) InkColors.Ink else InkColors.Ink4
    val inset = InkStroke.regular * 2
    val knob = InkSize.controlSm - InkStroke.bold * 2 - inset * 2
    Box(
        modifier = modifier
            .size(width = InkSpace.s12, height = InkSize.controlSm)
            .background(if (checked && enabled) InkColors.Ink else InkColors.Paper, CircleShape)
            .border(InkStroke.bold, frame, CircleShape)
            .padding(InkStroke.bold + inset),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .size(knob)
                .background(if (!enabled) InkColors.Ink4 else if (checked) InkColors.OnInk else InkColors.Ink, CircleShape),
        )
    }
}

/** `.ink-checkbox` — square frame; checked = solid ink with a white check. */
@Composable
fun InkCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(InkRadius.xs)
    Box(
        modifier = modifier
            .size(InkSize.controlXs)
            .background(if (checked) InkColors.Ink else InkColors.Paper, shape)
            .border(InkStroke.bold, if (enabled) InkColors.Ink else InkColors.Ink4, shape)
            .then(
                if (onClick != null && enabled) {
                    Modifier.clickable(role = Role.Checkbox, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) InkIcon(InkIcons.Check, size = InkSize.iconSm, tint = InkColors.OnInk)
    }
}

/**
 * `.ink-check--between` with an `.ink-switch` — a full-width settings row: text
 * (and optional grey sub-line) left, switch right. The whole row is the tap target.
 */
@Composable
fun InkSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sub: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = InkSize.controlLg)
            .clickable(enabled = enabled, role = Role.Switch) { onToggle(!checked) }
            .padding(vertical = InkSpace.s2),
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.half)) {
            Text(
                text = label,
                style = InkType.body.copy(fontWeight = FontWeight.Medium),
                color = if (enabled) InkColors.Ink else InkColors.Ink4,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) Text(text = sub, style = InkType.caption, maxLines = 2)
        }
        InkSwitch(checked = checked, enabled = enabled)
    }
}

/**
 * `.ink-check--task` — to-do row: checkbox + text; checked reads as done
 * (struck through, muted). [onCheck] null renders a read-only box.
 */
@Composable
fun InkTaskRow(
    title: String,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    sub: String? = null,
    strong: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onCheck: (() -> Unit)? = null,
) {
    // The whole row is the tap target: a bare controlXs checkbox cannot be hit
    // reliably on the e-ink touch layer. The board doesn't scroll, so a row tap
    // is always a deliberate completion gesture.
    val tappable = onCheck != null && enabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (tappable) Modifier.clickable(role = Role.Checkbox, onClick = onCheck!!) else Modifier)
            .heightIn(min = InkSize.controlMd),
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
        } else {
            InkCheckbox(checked = checked, enabled = tappable)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(InkSpace.half)) {
            Text(
                text = title,
                style = InkType.small.copy(
                    fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                ),
                color = if (checked) InkColors.Ink3 else InkColors.Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (sub != null) {
                Text(text = sub, style = InkType.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** `.ink-label` — bold small field label. */
@Composable
fun InkLabel(text: String, modifier: Modifier = Modifier, meta: String? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(text = text, style = InkType.smallStrong, modifier = Modifier.weight(1f))
        if (meta != null) Text(text = meta, style = InkType.caption)
    }
}

/** `.ink-hint` — grey helper text. */
@Composable
fun InkHint(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = InkType.caption.copy(lineHeight = InkText.xs * 1.45f), modifier = modifier)
}

/** `.ink-error` — error text in ink with an inverted "!" marker (no red). */
@Composable
fun InkError(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(InkSpace.s4).background(InkColors.Ink, RoundedCornerShape(InkRadius.xs)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "!", style = InkType.meta, color = InkColors.OnInk)
        }
        Text(text = text, style = InkType.meta, color = InkColors.Ink)
    }
}

/**
 * `.ink-field` + `.ink-input` — label, framed text input and optional hint or
 * error. The frame thickens (`--stroke-heavy`) while [error] is set.
 */
@Composable
fun InkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    hint: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    val shape = RoundedCornerShape(InkRadius.sm)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(InkSpace.s1)) {
        if (label != null) InkLabel(label)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = InkType.body.copy(fontWeight = FontWeight.Medium, color = InkColors.Ink),
            cursorBrush = SolidColor(InkColors.Ink),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = InkSize.controlLg)
                .background(InkColors.Paper, shape)
                .border(if (error != null) InkStroke.heavy else InkStroke.bold, InkColors.Ink, shape),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.padding(horizontal = InkSpace.s3, vertical = InkSpace.s2),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = InkType.body, color = InkColors.Ink4, maxLines = 1)
                    }
                    inner()
                }
            },
        )
        when {
            error != null -> InkError(error)
            hint != null -> InkHint(hint)
        }
    }
}

/**
 * `.ink-menu` — framed vertical list of actions (settings list). Items are
 * separated by hairlines; see [InkMenuItem].
 */
@Composable
fun InkMenu(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(InkRadius.md)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(InkColors.Paper, shape)
            .border(InkStroke.bold, InkColors.Ink, shape)
            .padding(InkStroke.bold),
    ) { content() }
}

/** `.ink-menu__item` — icon + label + trailing grey value / chevron. */
@Composable
fun InkMenuItem(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    chevron: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    if (divider) InkDivider(color = InkColors.Ink4)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = InkSize.controlLg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = InkSpace.s4, vertical = InkSpace.s3),
        horizontalArrangement = Arrangement.spacedBy(InkSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) InkIcon(icon)
        Text(text = label, style = InkType.body, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (value != null) Text(text = value, style = InkType.small, color = InkColors.Ink3, maxLines = 1)
        if (chevron) InkIcon(InkIcons.ChevronRight, size = InkSize.iconSm, tint = InkColors.Ink3)
    }
}
