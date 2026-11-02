package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/**
 * Чип-фильтр (m3-ui-spec §4.10): чаевые (h36, r18), фильтр истории (h34, r17, [brightSelected]).
 * Выбран — фон `brassIndicator` + рамка 1.5 `brass`; нет — рамка `chipBorder`. Цель касания 48.
 */
@Composable
fun FeltFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = FeltbookDimens.filterChipHeight,
    brightSelected: Boolean = false,
    role: Role = Role.RadioButton,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.pill
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = height)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier
                            .background(color = colors.brassIndicator)
                            .border(width = 1.5.dp, color = colors.brass, shape = shape)
                    } else {
                        Modifier.border(width = 1.dp, color = colors.chipBorder, shape = shape)
                    },
                ).selectable(selected = selected, role = role, onClick = onClick)
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(
            text = text,
            style = if (selected) type.chipBold else type.chip,
            color =
                when {
                    !selected -> colors.inkSoft
                    brightSelected -> colors.brassBright
                    else -> colors.brassLight
                },
        )
    }
}

/** Малый переключатель «Кому: Банкиру / Игроку…»: h32 r10, выбран — фон `feltDeep`. */
@Composable
fun SmallToggle(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.smallToggle
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = FeltbookDimens.smallToggleHeight)
                .clip(shape)
                .then(if (selected) Modifier.background(color = colors.feltDeep) else Modifier)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(
            text = text,
            style = FeltbookTheme.typography.chip,
            color = if (selected) colors.ink else colors.muted,
        )
    }
}

/** «За 3 месяца ▾»: h36 r18, рамка `brassBorderStrong`. Текст передаётся вместе с «▾». */
@Composable
fun DropdownChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.pill
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = FeltbookDimens.dropdownChipHeight)
                .clip(shape)
                .border(width = 1.dp, color = colors.brassBorderStrong, shape = shape)
                .clickable(role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text = text, style = FeltbookTheme.typography.chip, color = colors.brassBright)
    }
}

@Preview
@Composable
private fun ChipsPreview() {
    FeltbookTheme {
        Row(
            modifier = Modifier.feltBackground().padding(all = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeltFilterChip(text = "Нет", selected = true, onClick = {})
            FeltFilterChip(text = "до 100", selected = false, onClick = {})
            SmallToggle(text = "Банкиру", selected = true, onClick = {})
            DropdownChip(text = "За 3 месяца ▾", onClick = {})
        }
    }
}
