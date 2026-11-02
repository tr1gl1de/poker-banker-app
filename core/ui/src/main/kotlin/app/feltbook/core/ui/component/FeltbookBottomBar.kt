package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme

/** Значок вкладки: глиф шрифтом («♠») или векторная иконка. */
@Immutable
sealed interface BottomBarIcon {
    data class Glyph(val text: String) : BottomBarIcon

    data class Vector(val image: ImageVector) : BottomBarIcon
}

/** Пункт нижней панели. Подписи — строки `:app` (`nav_game`, `nav_history`, `nav_settings`). */
@Immutable
data class BottomBarItem(val label: String, val icon: BottomBarIcon)

/**
 * Нижняя панель «Игра / История / Настройки» (m3-ui-spec §4.7): 80 dp + `navigationBars`, фон `feltDeep`,
 * верхняя граница `brassContainer`. [onItemClick] вызывается на **каждое** нажатие, в том числе по уже
 * выбранной вкладке — `:app` считает по нему пасхалку (11 нажатий по «Настройкам», §8.3) и возврат к корню.
 */
@Composable
fun FeltbookBottomBar(
    items: List<BottomBarItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.navigationBars,
) {
    val colors = FeltbookTheme.colors
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.feltDeep)
                .drawBehind {
                    val line = 1.dp.toPx()
                    drawLine(
                        color = colors.brassContainer,
                        start = Offset(0f, line / 2),
                        end = Offset(size.width, line / 2),
                        strokeWidth = line,
                    )
                }.windowInsetsPadding(windowInsets)
                .heightIn(min = FeltbookDimens.bottomBarHeight)
                .padding(bottom = 8.dp)
                .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            BottomBarEntry(
                item = item,
                selected = index == selectedIndex,
                onClick = { onItemClick(index) },
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

@Composable
private fun BottomBarEntry(
    item: BottomBarItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val iconColor = if (selected) colors.brassLight else colors.muted
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .selectable(selected = selected, role = Role.Tab, onClick = onClick)
                .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterVertically),
    ) {
        Box(
            modifier =
                Modifier
                    .size(
                        width = FeltbookDimens.bottomBarIndicatorWidth,
                        height = FeltbookDimens.bottomBarIndicatorHeight,
                    )
                    .then(
                        if (selected) {
                            Modifier.background(color = colors.brassIndicator, shape = FeltbookTheme.shapes.card)
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            when (val icon = item.icon) {
                is BottomBarIcon.Glyph -> Text(text = icon.text, style = type.suitTab, color = iconColor)

                is BottomBarIcon.Vector ->
                    Icon(
                        imageVector = icon.image,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(size = FeltbookDimens.icon),
                    )
            }
        }
        Text(
            text = item.label,
            style = if (selected) type.navSelected else type.smallSemi,
            color = if (selected) colors.ink else colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Пункты для превью и скриншотов (в приложении подписи — из ресурсов `:app`). */
internal val PreviewBottomBarItems =
    listOf(
        BottomBarItem("Игра", BottomBarIcon.Glyph("♠")),
        BottomBarItem("История", BottomBarIcon.Vector(FeltbookIcons.History)),
        BottomBarItem("Настройки", BottomBarIcon.Vector(FeltbookIcons.Settings)),
    )

@Preview
@Composable
private fun FeltbookBottomBarPreview() {
    FeltbookTheme {
        FeltbookBottomBar(items = PreviewBottomBarItems, selectedIndex = 0, onItemClick = {})
    }
}
