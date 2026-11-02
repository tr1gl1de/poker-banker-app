// Файл назван по главному компоненту; enum вариантов — его параметр.
@file:Suppress("MatchingDeclarationName")

package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/** Варианты сегментов (m3-ui-spec §4.9). */
enum class SegmentedToggleStyle(val segmentHeight: Dp, val padding: Dp, val gap: Dp) {
    /** «В долг / Оплачено сейчас», «Отдать / Принять игру»: контейнер r14 p4, сегмент h44 r10. */
    Regular(segmentHeight = 44.dp, padding = 4.dp, gap = 4.dp),

    /** Правила в «Новой игре»: r12 p3, сегмент h40 r9. */
    Compact(segmentHeight = 40.dp, padding = 3.dp, gap = 3.dp),

    /** Позиции калькулятора: r12 p3 gap 4, сегмент h36 r9. */
    Position(segmentHeight = 36.dp, padding = 3.dp, gap = 4.dp),
}

/**
 * Сегментированный переключатель: равные колонки, роль RadioButton в `selectableGroup`. Нажатие засчитывается
 * по всей высоте контейнера; контейнер не ниже 48 dp (цель касания).
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: SegmentedToggleStyle = SegmentedToggleStyle.Regular,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val shapes = FeltbookTheme.shapes
    val containerShape = if (style == SegmentedToggleStyle.Regular) shapes.field else shapes.key
    val segmentShape = if (style == SegmentedToggleStyle.Regular) shapes.segment else shapes.segmentCompact
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .background(color = colors.feltDeep, shape = containerShape)
                .padding(horizontal = style.padding)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(space = style.gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier =
                    Modifier
                        .weight(weight = 1f)
                        .heightIn(min = FeltbookDimens.minTouchTarget)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) },
                        ).padding(vertical = style.padding),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = style.segmentHeight)
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(color = colors.card, shape = segmentShape)
                                        .border(width = 1.5.dp, color = colors.selectedBorder, shape = segmentShape)
                                } else {
                                    Modifier
                                },
                            ).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ButtonLabel(text = label, style = labelStyle(style, selected), color = labelColor(style, selected))
                }
            }
        }
    }
}

@Composable
private fun labelStyle(style: SegmentedToggleStyle, selected: Boolean): TextStyle {
    val type = FeltbookTheme.typography
    return when (style) {
        SegmentedToggleStyle.Regular -> if (selected) type.labelBold else type.labelSemi
        SegmentedToggleStyle.Compact -> if (selected) type.chipBold else type.chip
        SegmentedToggleStyle.Position -> if (selected) type.positionLabelSelected else type.positionLabel
    }
}

@Composable
private fun labelColor(style: SegmentedToggleStyle, selected: Boolean) = when {
    !selected -> FeltbookTheme.colors.muted
    style == SegmentedToggleStyle.Position -> FeltbookTheme.colors.brassBright
    else -> FeltbookTheme.colors.ink
}

@Preview
@Composable
private fun SegmentedTogglePreview() {
    FeltbookTheme {
        Column(
            modifier = Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            SegmentedToggle(options = listOf("В долг", "Оплачено сейчас"), selectedIndex = 0, onSelect = {})
            SegmentedToggle(
                options = listOf("Нет", "С закупа", "С игрока"),
                selectedIndex = 1,
                onSelect = {},
                style = SegmentedToggleStyle.Compact,
            )
            SegmentedToggle(
                options = listOf("UTG", "MP", "CO", "BTN", "SB", "BB"),
                selectedIndex = 3,
                onSelect = {},
                style = SegmentedToggleStyle.Position,
            )
        }
    }
}
