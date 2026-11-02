package app.feltbook.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.a11ySignedRubles
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.TABULAR_NUMBERS
import app.feltbook.core.ui.theme.feltBackground

/** Нижняя граница автоуменьшения сумм (ТЗ §5.1). */
val AmountMinFontSize: TextUnit = 12.sp

/** Доля исходного кегля, до которой сумма может уменьшиться. */
const val AMOUNT_MIN_FONT_FRACTION: Float = 0.7f

/**
 * Сумма, которая никогда не переносится (железное правило №7): одна строка, `tnum`,
 * `TextAutoSize.StepBased` от кегля [style] до `max(12 sp, 70 %)`. Обычные пробелы в [text] заменяются
 * на неразрывные: «1 053 ₽» не разрывается, даже если строку собрали без [MoneyFormat].
 * [contentDescription] — фраза для TalkBack со знаком и словом («Банкир выплачивает 353 рубля»);
 * без неё TalkBack прочтёт [text].
 */
@Composable
fun AmountText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val maxSize = style.fontSize
    val minSize = minOf(maxSize.value, maxOf(AmountMinFontSize.value, maxSize.value * AMOUNT_MIN_FONT_FRACTION)).sp
    BasicText(
        text = text.replace(' ', MoneyFormat.NBSP),
        modifier =
            if (contentDescription != null) {
                modifier.semantics { this.contentDescription = contentDescription }
            } else {
                modifier
            },
        style = style.merge(color = color, fontFeatureSettings = TABULAR_NUMBERS, textAlign = textAlign),
        maxLines = 1,
        // softWrap = true: иначе TextAutoSize не видит переполнения по ширине и обрезает текст. Перенос
        // невозможен (maxLines = 1), а суммы склеены неразрывными пробелами — число не разрывается.
        softWrap = true,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

/**
 * Денежная сумма в рублях: `2 300 ₽` (или `+353 ₽` при [signed]). По умолчанию TalkBack читает
 * «2 300 рублей» / «плюс 353 рубля»; фразу по смыслу задаёт [contentDescription].
 */
@Composable
fun AmountText(
    amount: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    currency: Boolean = true,
    contentDescription: String? = if (signed) a11ySignedRubles(amount) else a11yRubles(amount),
    textAlign: TextAlign = TextAlign.Unspecified,
) {
    val text =
        when {
            signed && currency -> MoneyFormat.signedRub(amount)
            signed -> MoneyFormat.signedNumber(amount)
            currency -> MoneyFormat.rub(amount)
            else -> MoneyFormat.number(amount)
        }
    AmountText(text, style, color, modifier, contentDescription, textAlign)
}

/** Наибольшая доля ширины строки под правую колонку сумм (ТЗ §5.1). */
const val AMOUNT_COLUMN_MAX_FRACTION: Float = 0.5f

/**
 * Строка с суммой справа (m3-ui-spec §4.17): [leading] (аватар/чекбокс/дата) + [content] (имя и мета,
 * обрезается многоточием) + [trailing] (суммы). Правый слот меряется с `maxWidth = 50 %` ширины строки
 * и никогда не обрезается — уступает левая часть.
 *
 * Имя и значки («БАНКИР», «докуп ×N») в [content] — через [NameWithBadges]: значки уступают место имени
 * и при крупном шрифте переходят на следующую строку, а не съедают имя.
 */
@Composable
fun AmountRowLayout(
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    leadingGap: Dp = 12.dp,
    trailingGap: Dp = 12.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable () -> Unit,
) {
    val leadingContent: @Composable () -> Unit = leading ?: {}
    Layout(
        contents = listOf(leadingContent, content, trailing),
        modifier = modifier,
    ) { (leadingMeasurables, contentMeasurables, trailingMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val bounded = constraints.hasBoundedWidth
        val leadingPlaceables = leadingMeasurables.map { it.measure(loose) }
        val leadingWidth = leadingPlaceables.maxWidth()
        val leadingGapPx = if (leadingWidth > 0) leadingGap.roundToPx() else 0

        val trailingMax =
            if (bounded) (constraints.maxWidth * AMOUNT_COLUMN_MAX_FRACTION).toInt() else Constraints.Infinity
        val trailingPlaceables = trailingMeasurables.map { it.measure(loose.copy(maxWidth = trailingMax)) }
        val trailingWidth = trailingPlaceables.maxWidth()
        val trailingGapPx = if (trailingWidth > 0) trailingGap.roundToPx() else 0

        val sides = leadingWidth + leadingGapPx + trailingWidth + trailingGapPx
        val available = if (bounded) (constraints.maxWidth - sides).coerceAtLeast(0) else null
        val contentConstraints = available?.let { loose.copy(minWidth = it, maxWidth = it) } ?: loose
        val contentPlaceables = contentMeasurables.map { it.measure(contentConstraints) }

        val width = if (bounded) constraints.maxWidth else sides + contentPlaceables.maxWidth()
        val all = leadingPlaceables + contentPlaceables + trailingPlaceables
        val rowHeight = (all.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, rowHeight) {
            fun Placeable.y() = verticalAlignment.align(height, rowHeight)
            leadingPlaceables.forEach { it.placeRelative(0, it.y()) }
            contentPlaceables.forEach { it.placeRelative(leadingWidth + leadingGapPx, it.y()) }
            trailingPlaceables.forEach { it.placeRelative(width - it.width, it.y()) }
        }
    }
}

private fun List<Placeable>.maxWidth(): Int = maxOfOrNull { it.width } ?: 0

@Preview
@Composable
private fun AmountRowLayoutPreview() {
    FeltbookTheme {
        val colors = FeltbookTheme.colors
        val type = FeltbookTheme.typography
        Column(Modifier.feltBackground().width(width = 360.dp).padding(all = 16.dp)) {
            AmountRowLayout(
                leading = { PlayerAvatar(name = "Дима", suit = Suit.Spades) },
                trailing = {
                    AmountText(amount = -125_400L, style = type.amountRow, color = colors.owe, signed = true)
                },
            ) {
                Column {
                    NameWithBadges(name = "Константин Константинопольский") { RebuyBadge(count = 2) }
                    Text(text = "закуп 3 000 · 1 ч 52 мин", style = type.ledgerTight, color = colors.ledger)
                }
            }
            AmountText(
                text = MoneyFormat.number(value = 12_500L),
                style = type.amountBoard,
                color = colors.ink,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
