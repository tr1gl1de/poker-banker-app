package app.feltbook.feature.history.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.history.R

/** Высота самого высокого столбца (макет: 800 → 100 dp). */
private val BarMaxHeight = 100.dp

/** Столбец, когда доход ≤ 0 или максимум ≤ 0. */
private val BarMinHeight = 2.dp

/** Столбец r(6 6 2 2). */
private val BarShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 2.dp, bottomEnd = 2.dp)

/** Высота области столбцов с подписями значений (макет: h120). */
private val ChartHeight = 120.dp

/**
 * «Доход банкира, последние N игр» (макет Stats): столбцы по геометрии макета — значение над столбцом
 * (`tiny ledgerLight`, последний — `tinyBold brassBright`), столбец `focusBorder` (последний — `brass`),
 * подписи дат `dd.MM`. Свой компонент, а не Vico: макет задаёт подпись над каждым столбцом и выделение
 * последней игры, а высоты — долей от максимума; так совпадение с макетом точное и без новой зависимости.
 */
@Composable
fun IncomeChart(bars: List<ChartBarUi>, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val max = bars.maxOfOrNull { it.value } ?: 0L
    val description = chartDescription(bars)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(color = colors.card, shape = RoundedCornerShape(size = 18.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Text(
            text = pluralStringResource(R.plurals.stats_chart_title, bars.size, bars.size),
            style = type.labelBold,
            color = colors.ink,
            modifier = Modifier.padding(bottom = 12.dp).clearAndSetSemantics {},
        )
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = ChartHeight).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEachIndexed { index, bar ->
                val last = index == bars.lastIndex
                Column(
                    modifier = Modifier.weight(weight = 1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    Text(
                        text = MoneyFormat.number(bar.value),
                        style = if (last) type.tinyBold else type.tiny,
                        color = if (last) colors.brassBright else colors.ledgerLight,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(height = barHeight(bar.value, max))
                                .background(
                                    color = if (last) colors.brass else colors.focusBorder,
                                    shape = BarShape,
                                ),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            bars.forEach { bar ->
                Text(
                    text = bar.label,
                    style = type.ledgerS,
                    color = colors.ledger,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(weight = 1f),
                )
            }
        }
    }
}

/** Доля от максимума × 100 dp; доход ≤ 0 или максимум ≤ 0 — 2 dp. */
internal fun barHeight(value: Long, max: Long): Dp =
    if (max <= 0L || value <= 0L) BarMinHeight else maxOf(BarMinHeight, BarMaxHeight * (value.toFloat() / max))

/** «Доход банкира по играм: 13.09 — 500 рублей, …». */
@Composable
private fun chartDescription(bars: List<ChartBarUi>): String {
    val resources = LocalResources.current
    val points = bars.joinToString(separator = ", ") { bar ->
        resources.getString(R.string.stats_chart_point_a11y, bar.label, resources.a11yRubles(bar.value))
    }
    return stringResource(R.string.stats_chart_a11y, points)
}
