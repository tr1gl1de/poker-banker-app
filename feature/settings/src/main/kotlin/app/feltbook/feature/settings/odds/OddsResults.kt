package app.feltbook.feature.settings.odds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.feltbook.core.odds.EquityResult
import app.feltbook.core.odds.OutsResult
import app.feltbook.core.odds.Position
import app.feltbook.core.odds.PotOddsVerdict
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.settings.R

/** Прозрачность прежнего / промежуточного итога, пока идёт расчёт (m3-ui-spec §5.14). */
private const val CALCULATING_ALPHA = 0.5f

@Composable
internal fun EquitySection(state: OddsUiState, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val equity = state.equity
    val against = pluralStringResource(UiR.plurals.against_players, state.opponents, state.opponents)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FeltbookTheme.shapes.cardLarge)
                .background(color = colors.card)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            Text(
                text = stringResource(R.string.odds_win_chance, against),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.weight(weight = 1f).alignByBaseline(),
            )
            if (equity != null) {
                Text(
                    text =
                        if (equity.exact) stringResource(R.string.odds_exact) else OddsFormat.margin(equity.winMargin),
                    style = type.ledgerS,
                    color = colors.ledger,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        when {
            state.status != EquityStatus.READY ->
                Text(text = stringResource(statusHint(state.status)), style = type.body, color = colors.inkSoft)

            equity == null ->
                Text(
                    text = stringResource(R.string.odds_calculating),
                    style = type.amountStat,
                    color = colors.brassBright,
                    maxLines = 1,
                    modifier = Modifier.alpha(alpha = CALCULATING_ALPHA),
                )

            else -> EquityValues(equity = equity, calculating = state.calculating)
        }
    }
}

private fun statusHint(status: EquityStatus): Int = when (status) {
    EquityStatus.NEED_HOLE, EquityStatus.READY -> R.string.odds_need_hole
    EquityStatus.INCOMPLETE_BOARD -> R.string.odds_need_board
    EquityStatus.TOO_MANY_DEAD -> R.string.odds_too_many_dead
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EquityValues(equity: EquityResult, calculating: Boolean) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val alpha = if (calculating) CALCULATING_ALPHA else 1f
    val win = OddsFormat.percentNumber(equity.win)
    val tie = OddsFormat.percentNumber(equity.tie)
    val lose = OddsFormat.percentNumber(equity.lose)
    AmountText(
        text = OddsFormat.percent(equity.win),
        style = type.amountEquity,
        color = colors.brassBright,
        modifier = Modifier.alpha(alpha = alpha),
    )
    val barDescription = stringResource(R.string.odds_bar_a11y, win, tie, lose)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color = colors.feltDeep)
                .clearAndSetSemantics { contentDescription = barDescription },
    ) {
        BarPart(weight = equity.win, color = colors.pay)
        BarPart(weight = equity.tie, color = colors.brassLight)
        BarPart(weight = equity.lose, color = colors.oweBar)
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(space = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        modifier = Modifier.clearAndSetSemantics {},
    ) {
        Text(text = stringResource(R.string.odds_legend_win, win), style = type.small, color = colors.inkSoft)
        Text(text = stringResource(R.string.odds_legend_tie, tie), style = type.small, color = colors.brassLight)
        Text(text = stringResource(R.string.odds_legend_lose, lose), style = type.small, color = colors.oweSoft)
    }
}

@Composable
private fun RowScope.BarPart(weight: Double, color: Color) {
    if (weight > 0.0) {
        Box(modifier = Modifier.weight(weight = weight.toFloat()).fillMaxHeight().background(color = color))
    }
}

/** Плитки «Шансы банка» и «Ауты» / «Сила руки»; на ривере (и без своих карт) — только шансы банка. */
@Composable
internal fun ResultTiles(state: OddsUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        val tile = Modifier.weight(weight = 1f).fillMaxHeight()
        PotOddsTile(state = state, modifier = tile)
        val preflop = state.preflop
        val outs = state.outs
        when {
            preflop != null -> PreflopTile(preflop = preflop, modifier = tile)
            outs != null -> OutsTile(outs = outs, pocketPair = state.pocketPair, modifier = tile)
        }
    }
}

private enum class TileTone { Neutral, Pay, Owe }

@Composable
private fun Tile(
    tone: TileTone,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.card
    val (background, border) = when (tone) {
        TileTone.Neutral -> colors.card to null
        TileTone.Pay -> colors.payContainer to colors.payBorderMedium
        TileTone.Owe -> colors.oweBackground to colors.oweBorder
    }
    Column(
        modifier =
            modifier
                .alpha(alpha = if (dimmed) CALCULATING_ALPHA else 1f)
                .clip(shape)
                .background(color = background)
                .then(if (border != null) Modifier.border(width = 1.dp, color = border, shape = shape) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(space = 3.dp),
    ) {
        content()
    }
}

@Composable
private fun PotOddsTile(state: OddsUiState, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val odds = state.potOdds
    val verdict = state.verdict
    val tone = when (verdict) {
        PotOddsVerdict.PAYS_OFF -> TileTone.Pay
        PotOddsVerdict.DOES_NOT_PAY -> TileTone.Owe
        null -> TileTone.Neutral
    }
    Tile(tone = tone, modifier = modifier, dimmed = state.calculating && odds != null) {
        Text(text = stringResource(R.string.odds_pot_odds), style = type.small, color = colors.inkSoft)
        if (odds == null) {
            Text(text = stringResource(R.string.odds_pot_missing), style = type.smallSemi, color = colors.inkSoft)
        } else {
            // Вердикт сравнивает именно это число (победа + доля дележа) с нужной долей — показываем оба.
            val equity = state.equity?.equity
            val needed = OddsFormat.percent(odds.required)
            val (text, description) =
                if (equity != null) {
                    stringResource(R.string.odds_pot_equity_needed, OddsFormat.percent(equity), needed) to
                        stringResource(
                            R.string.odds_pot_equity_needed_a11y,
                            OddsFormat.percentNumber(equity),
                            OddsFormat.percentNumber(odds.required),
                        )
                } else {
                    stringResource(R.string.odds_pot_needed, needed) to
                        stringResource(R.string.odds_pot_needed_a11y, OddsFormat.percentNumber(odds.required))
                }
            // Переносится по « · », числа склеены неразрывными пробелами — не обрезается многоточием.
            Text(
                text = text,
                style = type.sectionTitle,
                color = colors.ink,
                modifier = Modifier.semantics { contentDescription = description },
            )
            VerdictLine(state = state)
        }
    }
}

@Composable
private fun VerdictLine(state: OddsUiState) {
    val colors = FeltbookTheme.colors
    val (text, a11y, color) = when (state.verdict) {
        PotOddsVerdict.PAYS_OFF -> Triple(R.string.odds_call_ok, R.string.odds_call_ok_a11y, colors.pay)

        PotOddsVerdict.DOES_NOT_PAY -> Triple(R.string.odds_call_bad, R.string.odds_call_bad_a11y, colors.owe)

        null -> {
            val hint = when {
                state.status == EquityStatus.INCOMPLETE_BOARD -> R.string.odds_call_need_board
                state.status == EquityStatus.TOO_MANY_DEAD -> R.string.odds_call_too_many_dead
                state.status == EquityStatus.READY -> R.string.odds_calculating
                else -> R.string.odds_call_no_equity
            }
            Triple(hint, hint, colors.inkSoft)
        }
    }
    val description = stringResource(a11y)
    Text(
        text = stringResource(text),
        style = FeltbookTheme.typography.smallSemi,
        color = color,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun OutsTile(outs: OutsResult, pocketPair: Boolean, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val main = outs.mainDraw()
    Tile(tone = TileTone.Neutral, modifier = modifier) {
        if (main == null) {
            Text(text = stringResource(R.string.odds_outs_none), style = type.small, color = colors.inkSoft)
            Text(text = stringResource(R.string.odds_outs_none_hint), style = type.ledgerS, color = colors.ledger)
        } else {
            Text(
                text =
                    stringResource(
                        R.string.odds_outs_title,
                        drawName(main.first, outs, pocketPair),
                        outsCount(outs.total),
                    ),
                style = type.small,
                color = colors.inkSoft,
            )
            AmountText(
                text = outsValue(outs),
                style = type.sectionTitle,
                color = colors.ink,
                contentDescription = outsValueA11y(outs),
            )
            Text(text = outsHint(outs), style = type.ledgerS, color = colors.ledger)
            outsKinds(outs, pocketPair)?.let { Text(text = it, style = type.ledgerS, color = colors.ledger) }
        }
        if (outs.overcards.isNotEmpty()) {
            Text(
                text = pluralStringResource(R.plurals.odds_overcards, outs.overcards.size, outs.overcards.size),
                style = type.ledgerS,
                color = colors.ledger,
            )
        }
    }
}

/** Флоп: «19,1 % / 35,0 %» (тёрн / к риверу); тёрн: шанс к риверу. */
@Composable
private fun outsValue(outs: OutsResult): String {
    val byRiver = outs.byRiver
    return if (byRiver != null) {
        stringResource(R.string.odds_outs_value, OddsFormat.percent(outs.nextCard), OddsFormat.percent(byRiver))
    } else {
        OddsFormat.percent(outs.nextCard)
    }
}

/** TalkBack: «на тёрне 19,1 процента, к риверу 35,0 процента». */
@Composable
private fun outsValueA11y(outs: OutsResult): String {
    val byRiver = outs.byRiver
    val next = OddsFormat.percentNumber(outs.nextCard)
    return if (byRiver != null) {
        stringResource(R.string.odds_outs_value_a11y, next, OddsFormat.percentNumber(byRiver))
    } else {
        stringResource(R.string.odds_outs_value_turn_a11y, next)
    }
}

/** Быстрая оценка как в макете: «≈36 % по правилу 4» на флопе, «≈18 % по правилу 2» на тёрне. */
@Composable
private fun outsHint(outs: OutsResult): String {
    val ruleOfFour = outs.ruleOfFour
    return if (ruleOfFour != null) {
        stringResource(R.string.odds_outs_hint, MoneyFormat.percent(ruleOfFour))
    } else {
        stringResource(R.string.odds_outs_hint_turn, MoneyFormat.percent(outs.ruleOfTwo))
    }
}

@Composable
private fun PreflopTile(preflop: PreflopUi, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Tile(tone = TileTone.Neutral, modifier = modifier) {
        Text(
            text = stringResource(R.string.odds_hand_strength, preflop.strength.handClass.label),
            style = type.small,
            color = colors.inkSoft,
        )
        AmountText(
            text = stringResource(R.string.odds_top_percent, OddsFormat.percentValue(preflop.strength.topPercent)),
            style = type.sectionTitle,
            color = colors.ink,
        )
        Text(
            text = stringResource(
                R.string.odds_guide,
                preflop.guide.position.label,
                OddsFormat.percentValue(preflop.guide.openTopPercent),
                stringResource(if (preflop.inGuide) R.string.odds_guide_in else R.string.odds_guide_out),
            ),
            style = type.ledgerS,
            color = colors.ledger,
        )
    }
}

/** «Сейчас: старшая карта, туз · 150 000 раздач · офлайн». */
@Composable
internal fun StatusLine(state: OddsUiState, modifier: Modifier = Modifier) {
    val hand = state.hand ?: return
    val handName = handText(hand)
    val equity = state.equity
    val text = when {
        state.calculating -> stringResource(R.string.odds_status_calculating, handName)
        equity == null -> stringResource(R.string.odds_status_hand, handName)
        equity.exact -> stringResource(R.string.odds_status_exact, handName)
        else -> stringResource(R.string.odds_status, handName, MoneyFormat.number(equity.samples))
    }
    Text(
        text = text,
        style = FeltbookTheme.typography.ledgerS,
        color = FeltbookTheme.colors.ledger,
        modifier = modifier,
    )
}

/** Памятка «кто есть кто» — только позиции текущего числа игроков. */
@Composable
internal fun MemoSection(positions: List<Position>, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.sheet)
                .border(width = 1.dp, color = colors.brassBorderMedium, shape = shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.odds_memo_title),
                style = type.labelBold,
                color = colors.ink,
                modifier = Modifier.weight(weight = 1f).semantics { heading() },
            )
            Text(text = stringResource(R.string.odds_memo_hint), style = type.small, color = colors.ledger)
        }
        Column(verticalArrangement = Arrangement.spacedBy(space = 5.dp)) {
            positions.forEach { position ->
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                ) {
                    Text(
                        text = position.label,
                        style = type.positionLabel,
                        color = colors.brassBright,
                        modifier = Modifier.widthIn(min = 40.dp),
                    )
                    Text(
                        text = positionMemo(position.label),
                        style = type.small,
                        color = colors.inkSoft,
                        modifier = Modifier.weight(weight = 1f),
                    )
                }
            }
        }
        Text(text = stringResource(R.string.odds_memo_footer), style = type.tiny, color = colors.ledger)
    }
}
