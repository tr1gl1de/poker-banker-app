package app.feltbook.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.core.ui.theme.feltTable

/** Ячейки табло под овалом: ♦ касса, ♠ долги, ♣ рейк, ♥ банкиру. */
enum class ScoreboardCell(val suit: Suit, @StringRes val label: Int) {
    Cash(Suit.Diamonds, R.string.scoreboard_cash_on_hand),
    Debts(Suit.Spades, R.string.scoreboard_players_owe),
    Rake(Suit.Clubs, R.string.scoreboard_rake),
    Banker(Suit.Hearts, R.string.scoreboard_banker_income),
}

/** Цифры табло — из `TableSummary` домена; [chipsMeta] — готовая строка «выдано 12 500 · сдано 4 200 · докупов 2». */
@Immutable
data class ScoreboardState(
    val chipsInPlay: Long,
    val chipsMeta: String,
    val cashOnHand: Long,
    val playersOwe: Long,
    val rakeDue: Long,
    val bankerIncome: Long,
    /** Банкир должен вышедшим (ТЗ §4); > 0 — третья строка под ♠. */
    val bankerOwes: Long = 0L,
)

/**
 * Табло стола (m3-ui-spec §4.6): овал-стадион с бортом `rail` 6 dp и внутренним кольцом, в центре фишки в игре;
 * ниже 2 × 2 плитки. Нажатие на овал или плитку — шторка «Расшифровка» ([onOvalClick], [onCellClick]).
 * TalkBack: каждая плитка — кнопка «Касса на руках, 950 рублей».
 */
@Composable
fun TableScoreboard(
    state: ScoreboardState,
    onOvalClick: () -> Unit,
    onCellClick: (ScoreboardCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 10.dp)) {
        ScoreboardOval(state = state, onClick = onOvalClick)
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(space = FeltbookDimens.cardGap),
        ) {
            ScoreboardTile(ScoreboardCell.Cash, state, onCellClick, Modifier.weight(weight = 1f))
            ScoreboardTile(ScoreboardCell.Debts, state, onCellClick, Modifier.weight(weight = 1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(space = FeltbookDimens.cardGap),
        ) {
            ScoreboardTile(ScoreboardCell.Rake, state, onCellClick, Modifier.weight(weight = 1f))
            ScoreboardTile(ScoreboardCell.Banker, state, onCellClick, Modifier.weight(weight = 1f))
        }
    }
}

@Composable
private fun ScoreboardOval(state: ScoreboardState, onClick: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.stadium
    val title = stringResource(R.string.scoreboard_chips_in_play)
    val description = "$title, ${MoneyFormat.number(state.chipsInPlay)}, ${state.chipsMeta}"
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FeltbookDimens.scoreboardOvalHeight)
                .clip(shape)
                .feltTable(shape)
                .border(width = FeltbookDimens.railWidth, color = colors.rail, shape = shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .padding(all = FeltbookDimens.railWidth)
                .border(width = FeltbookDimens.railInnerRing, color = colors.focusBorder, shape = shape),
        )
        Column(
            modifier =
                Modifier
                    .padding(horizontal = 40.dp, vertical = 16.dp)
                    .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(space = 2.dp),
        ) {
            Text(text = title.uppercase(), style = type.overlineBoard, color = colors.inkSoft, maxLines = 1)
            AmountText(
                text = MoneyFormat.number(state.chipsInPlay),
                style = type.amountBoard,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
            // При крупном шрифте мета-строка переносится, а не упирается в борт овала.
            Text(
                text = state.chipsMeta,
                style = type.small,
                color = colors.inkSoft,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ScoreboardTile(
    cell: ScoreboardCell,
    state: ScoreboardState,
    onCellClick: (ScoreboardCell) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    val value =
        when (cell) {
            ScoreboardCell.Cash -> state.cashOnHand
            ScoreboardCell.Debts -> state.playersOwe
            ScoreboardCell.Rake -> state.rakeDue
            ScoreboardCell.Banker -> state.bankerIncome
        }
    val valueColor: Color =
        when (cell) {
            ScoreboardCell.Cash, ScoreboardCell.Rake -> colors.ink
            ScoreboardCell.Debts -> if (value > 0) colors.owe else colors.ink
            ScoreboardCell.Banker -> colors.brassLight
        }
    val label = stringResource(cell.label)
    val bankerOwes =
        if (cell == ScoreboardCell.Debts && state.bankerOwes > 0) {
            stringResource(R.string.scoreboard_banker_owes, MoneyFormat.rub(state.bankerOwes))
        } else {
            null
        }
    val description =
        stringResource(R.string.a11y_scoreboard_cell, label, a11yRubles(value)) +
            (bankerOwes?.let { ", $it" } ?: "")
    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .clip(shape)
                .background(color = colors.card)
                .border(width = 1.dp, color = colors.brassHairline, shape = shape)
                .clickable(role = Role.Button, onClick = { onCellClick(cell) })
                .semantics { contentDescription = description }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        Text(
            text = "${stringResource(cell.suit.glyph)} $label",
            style = type.ledgerTight,
            color = colors.ledger,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
        AmountText(
            text = MoneyFormat.rub(value),
            style = type.amountTile,
            color = valueColor,
            modifier = Modifier.clearAndSetSemantics {},
        )
        if (bankerOwes != null) {
            Text(
                text = bankerOwes,
                style = type.ledgerTight,
                color = colors.pay,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Пример для превью и скриншотов (цифры макета Main). */
internal val PreviewScoreboardState =
    ScoreboardState(
        chipsInPlay = 8_300L,
        chipsMeta = "выдано 12 500 · сдано 4 200 · докупов 2",
        cashOnHand = 950L,
        playersOwe = 3_400L,
        rakeDue = 1_250L,
        bankerIncome = 800L,
    )

@Preview
@Composable
private fun TableScoreboardPreview() {
    FeltbookTheme {
        Box(Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp)) {
            TableScoreboard(
                state = PreviewScoreboardState.copy(bankerOwes = 300L),
                onOvalClick = {},
                onCellClick = {},
            )
        }
    }
}
