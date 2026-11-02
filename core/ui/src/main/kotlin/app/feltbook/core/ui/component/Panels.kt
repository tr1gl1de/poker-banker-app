package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookColors
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookGradients
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.FeltbookTypography
import app.feltbook.core.ui.theme.feltBackground

// ---------- MetricTile (§4.18) ----------

/** Цветовой вариант плитки. */
enum class MetricVariant { Neutral, Owe, Brass, Pay }

/** Размер плитки: радиус, отступы и стиль значения. */
enum class MetricSize(val radius: Dp, val horizontal: Dp, val vertical: Dp, val gap: Dp) {
    /** Метрики карточки игры/игрока: r14 p(10 12), `amountMetric`. */
    Small(radius = 14.dp, horizontal = 12.dp, vertical = 10.dp, gap = 2.dp),

    /** Плитки табло: r16 p(10 12), `amountTile`. */
    Medium(radius = 16.dp, horizontal = 12.dp, vertical = 10.dp, gap = 2.dp),

    /** Статистика: r16 p(12 14), `amountStat`. */
    Large(radius = 16.dp, horizontal = 14.dp, vertical = 12.dp, gap = 4.dp),

    /** Итог игры: r18 p14, `amountCard`. */
    XL(radius = 18.dp, horizontal = 14.dp, vertical = 14.dp, gap = 4.dp),
}

private fun MetricSize.valueStyle(type: FeltbookTypography): TextStyle = when (this) {
    MetricSize.Small -> type.amountMetric
    MetricSize.Medium -> type.amountTile
    MetricSize.Large -> type.amountStat
    MetricSize.XL -> type.amountCard
}

/** Цвета варианта: фон, рамка, подпись, значение, третья строка. */
@Immutable
private data class TileColors(
    val background: Color,
    val border: Color?,
    val label: Color,
    val value: Color,
    val footnote: Color,
)

private fun MetricVariant.colors(c: FeltbookColors): TileColors = when (this) {
    MetricVariant.Neutral -> TileColors(c.card, null, c.ledger, c.ink, c.ledger)
    MetricVariant.Owe -> TileColors(c.oweBackground, c.oweBorder, c.oweSoft, c.owe, c.oweSoft)
    MetricVariant.Brass -> TileColors(c.brassContainer, c.brassBorderStrong, c.brassLight, c.brassBright, c.brassMuted)
    MetricVariant.Pay -> TileColors(c.payContainer, c.payBorder, c.inkSoft, c.pay, c.inkSoft)
}

/**
 * Плитка метрики: подпись, сумма ([AmountText]), опц. третья строка. [contentDescription] — фраза TalkBack
 * («Касса на руках, 950 рублей»); [onClick] — плитка-кнопка.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    variant: MetricVariant = MetricVariant.Neutral,
    size: MetricSize = MetricSize.Small,
    footnote: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val type = FeltbookTheme.typography
    val tile = variant.colors(FeltbookTheme.colors)
    val shape = RoundedCornerShape(size = size.radius)
    Column(
        modifier =
            modifier
                .clip(shape)
                .background(color = tile.background)
                .then(
                    if (tile.border !=
                        null
                    ) {
                        Modifier.border(width = 1.dp, color = tile.border, shape = shape)
                    } else {
                        Modifier
                    },
                )
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .padding(horizontal = size.horizontal, vertical = size.vertical),
        verticalArrangement = Arrangement.spacedBy(space = size.gap),
    ) {
        Text(
            text = label,
            style = if (size == MetricSize.Small) type.tiny else type.small,
            color = tile.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
        AmountText(
            text = value,
            style = size.valueStyle(type),
            color = tile.value,
            modifier = Modifier.clearAndSetSemantics {
            },
        )
        if (footnote != null) {
            Text(
                text = footnote,
                style = type.tiny,
                color = tile.footnote,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

// ---------- ResultPanel (§4.19) ----------

/** Вариант итога: банкир выплачивает / игрок доплачивает / квиты. */
enum class ResultVariant { Pay, Owe, Even }

/**
 * Панель итога выхода: круглая плашка с иконкой 44, подпись и крупная сумма. [contentDescription] — фраза
 * со словом и знаком («Банкир выплачивает 353 рубля»).
 */
@Composable
fun ResultPanel(
    label: String,
    amount: String,
    contentDescription: String,
    variant: ResultVariant,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.hero
    val background: Brush
    val border: Color
    val icon: ImageVector
    val iconBackground: Color
    val labelColor: Color
    val valueColor: Color
    when (variant) {
        ResultVariant.Pay -> {
            background = FeltbookGradients.payPanel(colors)
            border = colors.payBorderStrong
            icon = FeltbookIcons.ArrowDown
            iconBackground = colors.payIcon
            labelColor = colors.inkSoft
            valueColor = colors.pay
        }

        ResultVariant.Owe -> {
            background = SolidColor(colors.oweBackground)
            border = colors.oweBorderStrong
            icon = FeltbookIcons.ArrowUp
            iconBackground = colors.oweIcon
            labelColor = colors.oweTint
            valueColor = colors.owe
        }

        ResultVariant.Even -> {
            background = SolidColor(colors.card)
            border = colors.chipBorder
            icon = FeltbookIcons.Check
            iconBackground = colors.cardInactive
            labelColor = colors.inkSoft
            valueColor = colors.ink
        }
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(brush = background)
                .border(width = 1.5.dp, color = border, shape = shape)
                .clearAndSetSemantics { this.contentDescription = contentDescription }
                .padding(all = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(
                size = FeltbookDimens.resultIconPlate,
            ).background(color = iconBackground, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.ivory,
                modifier = Modifier.size(size = FeltbookDimens.icon),
            )
        }
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(text = label, style = type.caption, color = labelColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            AmountText(text = amount, style = type.amountResult, color = valueColor)
        }
    }
}

// ---------- InfoBanner (§4.20) ----------

/** Вариант баннера: «Фишки сошлись», опасность, предупреждение (подозрительная сумма). */
enum class BannerVariant { Ok, Danger, Warn }

/** Баннер r14 с иконкой 20: ok — зелёный, danger — красный, warn — латунный. */
@Composable
fun InfoBanner(text: String, variant: BannerVariant, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.field
    val background: Color
    val border: Color
    val iconTint: Color
    val textColor: Color
    val icon: ImageVector
    when (variant) {
        BannerVariant.Ok -> {
            background = colors.payContainer
            border = colors.payBorder
            iconTint = colors.pay
            textColor = colors.ink
            icon = FeltbookIcons.CheckThin
        }

        BannerVariant.Danger -> {
            background = colors.oweBackground
            border = colors.oweBorderStrong
            iconTint = colors.owe
            textColor = colors.ink
            icon = FeltbookIcons.Alert
        }

        BannerVariant.Warn -> {
            background = colors.brassContainer
            border = colors.brassBorderStrong
            iconTint = colors.brassBright
            textColor = colors.brassBright
            icon = FeltbookIcons.Alert
        }
    }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = background)
                .border(width = 1.dp, color = border, shape = shape)
                .semantics(mergeDescendants = true) {
                    // Предупреждения появляются по ходу ввода — TalkBack озвучивает их сам.
                    if (variant != BannerVariant.Ok) liveRegion = LiveRegionMode.Polite
                }.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(size = FeltbookDimens.iconM),
        )
        Text(
            text = text,
            style = FeltbookTheme.typography.caption,
            color = textColor,
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

// ---------- BreakdownList (§4.21) ----------

/** Строка расшифровки «Фишки 2 000 + рейк 200 = 2 300 ₽». [contentDescription] — для сумм со знаком. */
@Immutable
data class BreakdownRow(val label: String, val value: String, val contentDescription: String? = null)

/**
 * Расшифровка итога (`dl`): строки «подпись — значение» через [AmountRowLayout] (значение не переносится),
 * итоговая строка с чертой `divider`. [card] — контейнер `card r14 p(12 14)` (закуп), иначе без фона (выход).
 * [ledgerLabels] — подписи `ledger` (акт, выход с долгом) вместо `muted`.
 */
@Composable
fun BreakdownList(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier,
    total: BreakdownRow? = null,
    card: Boolean = true,
    ledgerLabels: Boolean = false,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val labelColor = if (ledgerLabels) colors.ledger else colors.muted
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (card) {
                        Modifier
                            .background(color = colors.card, shape = FeltbookTheme.shapes.field)
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    } else {
                        Modifier.padding(horizontal = 4.dp)
                    },
                ),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        rows.forEach { row ->
            BreakdownLine(row, labelStyle = type.body, labelColor = labelColor, valueStyle = type.body)
        }
        if (total != null) {
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(height = 1.dp).background(color = colors.divider))
                Spacer(Modifier.height(height = 6.dp))
                BreakdownLine(total, labelStyle = type.labelBold, labelColor = colors.ink, valueStyle = type.labelBold)
            }
        }
    }
}

@Composable
private fun BreakdownLine(row: BreakdownRow, labelStyle: TextStyle, labelColor: Color, valueStyle: TextStyle) {
    AmountRowLayout(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        trailing = {
            AmountText(
                text = row.value,
                style = valueStyle,
                color = FeltbookTheme.colors.ink,
                contentDescription = row.contentDescription,
            )
        },
    ) {
        Text(text = row.label, style = labelStyle, color = labelColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Preview
@Composable
private fun PanelsPreview() {
    FeltbookTheme {
        Column(
            modifier = Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            ResultPanel(
                label = "Банкир выплачивает",
                amount = "+353 ₽",
                contentDescription = "Банкир выплачивает 353 рубля",
                variant = ResultVariant.Pay,
            )
            ResultPanel(
                label = "Влад доплачивает банкиру",
                amount = "1 053 ₽",
                contentDescription = "Влад доплачивает 1 053 рубля",
                variant = ResultVariant.Owe,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                MetricTile(
                    label = "Долг",
                    value = "3 400 ₽",
                    contentDescription = "Долг 3 400 рублей",
                    variant = MetricVariant.Owe,
                    modifier = Modifier.weight(weight = 1f),
                )
                MetricTile(
                    label = "♥ Банкиру",
                    value = "800 ₽",
                    footnote = "сборы 300 · чаевые 53",
                    contentDescription = "Банкиру 800 рублей",
                    variant = MetricVariant.Brass,
                    size = MetricSize.XL,
                    modifier = Modifier.weight(weight = 1f),
                )
            }
            InfoBanner(text = "Фишки сошлись: 12 500 выдано = 12 500 сдано", variant = BannerVariant.Ok)
            InfoBanner(text = "20 000 фишек — точно? Обычно здесь берут 2 000", variant = BannerVariant.Warn)
            BreakdownList(
                rows = listOf(
                    BreakdownRow("Фишки", "2 000"),
                    BreakdownRow("Рейк 10 %", "200"),
                    BreakdownRow("Сбор банкира", "100"),
                ),
                total = BreakdownRow("Итого", "2 300 ₽"),
            )
        }
    }
}
