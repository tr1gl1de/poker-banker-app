package app.feltbook.feature.settings.odds

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.HandCategory
import app.feltbook.core.odds.OutsResult
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.feature.settings.R
import kotlin.math.roundToInt

/** Шансы — `Double` от 0 до 1 (не деньги). Проценты с одним знаком: `38,4 %`. */
internal object OddsFormat {
    private const val PERCENT = 100.0

    fun percent(share: Double): String = MoneyFormat.percentOneDecimal(share * PERCENT)

    /** Без знака «%» — для легенды «● победа 38,4». Значения ≤ 100: тысячных разделителей нет. */
    fun percentNumber(share: Double): String = percent(share).substringBefore(MoneyFormat.NBSP)

    /** «±0,3 %». */
    fun margin(share: Double): String = MoneyFormat.plusMinusPercent(share * PERCENT)

    /** Значение уже в процентах: целое — «45 %», иначе «4,2 %». */
    fun percentValue(value: Double): String {
        val whole = value.roundToInt()
        return if (value == whole.toDouble()) MoneyFormat.percent(whole) else MoneyFormat.percentOneDecimal(value)
    }
}

/** «старшая карта, туз», «пара», «флеш». */
@Composable
internal fun handText(hand: HandSummary): String = when (hand.category) {
    HandCategory.HIGH_CARD ->
        stringResource(
            R.string.odds_hand_high_card,
            stringArrayResource(R.array.odds_rank_names_lower)[hand.topRank.ordinal],
        )

    HandCategory.THREE_OF_A_KIND -> stringResource(
        when (hand.trips) {
            TripsKind.SET, null -> R.string.odds_hand_set
            TripsKind.TRIPS -> R.string.odds_hand_trips
            TripsKind.BOARD -> R.string.odds_hand_board_trips
        },
    )

    else -> categoryName(hand.category)
}

/** Название категории без уточнений (виды аутов). Старшей карты среди аутов не бывает. */
@Composable
internal fun categoryName(category: HandCategory, pocketPair: Boolean = true): String = stringResource(
    when (category) {
        HandCategory.HIGH_CARD, HandCategory.ONE_PAIR -> R.string.odds_hand_pair
        HandCategory.TWO_PAIR -> R.string.odds_hand_two_pair
        HandCategory.THREE_OF_A_KIND -> if (pocketPair) R.string.odds_hand_set else R.string.odds_hand_trips
        HandCategory.STRAIGHT -> R.string.odds_hand_straight
        HandCategory.FLUSH -> R.string.odds_hand_flush
        HandCategory.FULL_HOUSE -> R.string.odds_hand_full_house
        HandCategory.FOUR_OF_A_KIND -> R.string.odds_hand_quads
        HandCategory.STRAIGHT_FLUSH -> R.string.odds_hand_straight_flush
    },
)

/** Главный вид дро — тот, что даёт больше всего аутов (при равенстве — сильнейший). */
internal fun OutsResult.mainDraw(): Pair<HandCategory, Int>? = outs.entries
    .maxWithOrNull(compareBy<Map.Entry<HandCategory, List<Card>>>({ it.value.size }, { it.key }))
    ?.let { it.key to it.value.size }

/**
 * «Флеш-дро», «Гатшот» (стрит закрывает один ранг), «Двусторонний стрит-дро» (два ранга),
 * «На сет» (карманная пара) / «На трипс».
 */
@Composable
internal fun drawName(category: HandCategory, outs: OutsResult, pocketPair: Boolean): String = stringResource(
    when (category) {
        HandCategory.FLUSH -> R.string.odds_draw_flush

        HandCategory.STRAIGHT ->
            if (outs.straightRanks <= 1) R.string.odds_draw_gutshot else R.string.odds_draw_straight

        HandCategory.THREE_OF_A_KIND -> if (pocketPair) R.string.odds_draw_set else R.string.odds_draw_trips

        HandCategory.HIGH_CARD, HandCategory.ONE_PAIR -> R.string.odds_draw_pair

        HandCategory.TWO_PAIR -> R.string.odds_draw_two_pair

        HandCategory.FULL_HOUSE -> R.string.odds_draw_full_house

        HandCategory.FOUR_OF_A_KIND -> R.string.odds_draw_quads

        HandCategory.STRAIGHT_FLUSH -> R.string.odds_draw_straight_flush
    },
)

/** Разбивка по видам, если их больше одного: «флеш 9 · стрит 3». */
@Composable
internal fun outsKinds(outs: OutsResult, pocketPair: Boolean): String? {
    if (outs.outs.size < 2) return null
    val separator = stringResource(R.string.odds_outs_separator)
    return outs.outs.entries.map { (category, cards) ->
        stringResource(R.string.odds_outs_kind, categoryName(category, pocketPair), cards.size)
    }.joinToString(separator)
}

/** «9 аутов». */
@Composable
internal fun outsCount(count: Int): String = pluralStringResource(UiR.plurals.outs_count, count, count)

/** Памятка для метки позиции (ТЗ §4 п. 2). */
@Composable
internal fun positionMemo(label: String): String = stringResource(
    when {
        label == "SB/BTN" -> R.string.odds_memo_sb_btn
        label == "UTG" -> R.string.odds_memo_utg
        label.startsWith("UTG+") -> R.string.odds_memo_utg_plus
        label.startsWith("MP") -> R.string.odds_memo_mp
        label == "CO" -> R.string.odds_memo_co
        label == "BTN" -> R.string.odds_memo_btn
        label == "SB" -> R.string.odds_memo_sb
        else -> R.string.odds_memo_bb
    },
)
