// Файл назван по главному компоненту; enum вариантов — его параметр.
@file:Suppress("MatchingDeclarationName")

package app.feltbook.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.theme.FeltbookChipColors
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import kotlin.math.PI

/** Быстрые суммы закупа (ТЗ §5.1): 500, 1 000, 2 000, 5 000. */
enum class ChipDenom(val amount: Long, @StringRes val label: Int) {
    C500(amount = 500L, label = R.string.chip_500),
    C1000(amount = 1_000L, label = R.string.chip_1000),
    C2000(amount = 2_000L, label = R.string.chip_2000),
    C5000(amount = 5_000L, label = R.string.chip_5000),
    ;

    fun color(chips: FeltbookChipColors): Color = when (this) {
        C500 -> chips.chip500
        C1000 -> chips.chip1000
        C2000 -> chips.chip2000
        C5000 -> chips.chip5000
    }
}

/**
 * Цвет мини-фишки кнопки «Выдать» по сумме: ≥ 5 000 — фиолетовая, ≥ 2 000 — красная, ≥ 1 000 — чёрная,
 * иначе зелёная.
 */
fun chipColorFor(amount: Long, chips: FeltbookChipColors): Color = when {
    amount >= ChipDenom.C5000.amount -> chips.chip5000
    amount >= ChipDenom.C2000.amount -> chips.chip2000
    amount >= ChipDenom.C1000.amount -> chips.chip1000
    else -> chips.chip500
}

/** Число штрихов пунктирного канта по окружности (скважность 1:1). */
internal const val CHIP_EDGE_DASHES = 10

/** Пунктирная окружность радиусом [radius] по середине штриха; [dashes] штрихов равной длины. */
internal fun DrawScope.dashedCircle(color: Color, radius: Float, strokeWidth: Float, dashes: Int) {
    val segment = (2 * PI * radius / (dashes * 2)).toFloat()
    drawCircle(
        color = color,
        radius = radius,
        style =
            Stroke(
                width = strokeWidth,
                cap = StrokeCap.Butt,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(segment, segment), 0f),
            ),
    )
}

/**
 * Фишка быстрой суммы (m3-ui-spec §4.2): 64 dp, пунктирный кант `ivory`, номинал в центре.
 * Выбранная — без тени, с кольцом 3 dp `feltDeep` + 2 dp `brass` снаружи габарита (раскладку не меняет).
 * Нажатие **устанавливает** сумму. TalkBack: «500 фишек», роль RadioButton, состояние выбора.
 */
@Composable
fun ChipDenomination(denom: ChipDenom, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val chipColor = denom.color(colors.chips)
    val description =
        pluralStringResource(R.plurals.chips_count, pluralQuantity(denom.amount), MoneyFormat.number(denom.amount))
    Box(
        modifier =
            modifier
                .size(size = FeltbookDimens.chip)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.feltShadow(CircleShape, offsetY = 4.dp, blur = 10.dp, alpha = 0.35f)
                    },
                ).drawBehind {
                    val radius = size.minDimension / 2
                    if (selected) {
                        val gap = FeltbookDimens.chipSelectionGap.toPx()
                        val ring = FeltbookDimens.chipSelectionRing.toPx()
                        drawCircle(colors.feltDeep, radius = radius + gap / 2, style = Stroke(width = gap))
                        drawCircle(colors.brass, radius = radius + gap + ring / 2, style = Stroke(width = ring))
                    }
                    drawCircle(chipColor, radius = radius)
                    val edge = FeltbookDimens.chipEdgeStroke.toPx()
                    dashedCircle(
                        color = colors.ivory,
                        radius = FeltbookDimens.chipEdge.toPx() / 2 - edge / 2,
                        strokeWidth = edge,
                        dashes = CHIP_EDGE_DASHES,
                    )
                    val inner = FeltbookDimens.chipInnerStroke.toPx()
                    val centerRadius = FeltbookDimens.chipCenter.toPx() / 2
                    drawCircle(chipColor, radius = centerRadius)
                    drawCircle(colors.chipInnerRing, radius = centerRadius - inner / 2, style = Stroke(width = inner))
                }.clip(CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(denom.label),
            style = FeltbookTheme.typography.chipDenomination,
            color = colors.ivory,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** «как прошлый»: последняя покупка этого игрока. Скрывается, если покупок не было. */
@Composable
fun LastAmountChip(amount: Long, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val description = stringResource(R.string.chip_last_a11y, MoneyFormat.number(amount))
    Box(
        modifier =
            modifier
                .size(size = FeltbookDimens.chip)
                .drawBehind {
                    val radius = size.minDimension / 2
                    if (selected) {
                        val gap = FeltbookDimens.chipSelectionGap.toPx()
                        val ring = FeltbookDimens.chipSelectionRing.toPx()
                        drawCircle(colors.feltDeep, radius = radius + gap / 2, style = Stroke(width = gap))
                        drawCircle(colors.brass, radius = radius + gap + ring / 2, style = Stroke(width = ring))
                    }
                    val stroke = FeltbookDimens.chipInnerStroke.toPx()
                    dashedCircle(colors.selectedBorder, radius - stroke / 2, stroke, dashes = LAST_CHIP_DASHES)
                }.clip(CircleShape)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
            Text(stringResource(R.string.chip_last_line1), style = type.tinyBold, color = colors.brassLight)
            Text(
                text = stringResource(R.string.chip_last_line2),
                style = type.tinyBold,
                color = colors.brassLight,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val LAST_CHIP_DASHES = 24

/**
 * Ряд фишек: 500 / 1K / 2K / 5K и «как прошлый» (если [lastAmount] не `null`), `SpaceBetween`.
 * Фишка выбрана, когда [currentAmount] равна её номиналу.
 */
@Composable
fun ChipDenominationRow(
    currentAmount: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lastAmount: Long? = null,
    selectionEnabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val lastSelected = selectionEnabled && lastAmount != null && currentAmount == lastAmount
        ChipDenom.entries.forEach { denom ->
            ChipDenomination(
                denom = denom,
                selected = selectionEnabled && !lastSelected && currentAmount == denom.amount,
                onClick = { onSelect(denom.amount) },
            )
        }
        if (lastAmount != null) {
            LastAmountChip(amount = lastAmount, selected = lastSelected, onClick = { onSelect(lastAmount) })
        }
    }
}

/** Мини-фишка 44 dp кнопки «Выдать» (без номинала). */
@Composable
internal fun MiniChip(color: Color, modifier: Modifier = Modifier, size: Dp = FeltbookDimens.miniChip) {
    val ivory = FeltbookTheme.colors.ivory
    Box(
        modifier =
            modifier.size(size = size).drawBehind {
                drawCircle(color)
                val edge = FeltbookDimens.miniChipEdgeStroke.toPx()
                dashedCircle(ivory, FeltbookDimens.miniChipEdge.toPx() / 2 - edge / 2, edge, CHIP_EDGE_DASHES)
            },
    )
}

@Preview
@Composable
private fun ChipDenominationRowPreview() {
    FeltbookTheme {
        Box(Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp)) {
            ChipDenominationRow(currentAmount = 2_000L, lastAmount = 1_000L, onSelect = {})
        }
    }
}
