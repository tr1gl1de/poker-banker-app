package app.feltbook.feature.game.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11ySignedRubles
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.R

/** «Расшифровка» цифры табло (m3-ui-spec §5.3.4). */
@Composable
fun BreakdownSheet(ui: BreakdownUi, onAction: (TableAction) -> Unit) {
    FeltbookSheet(onDismiss = { onAction(TableAction.DismissSheet) }, contentSpacing = 12.dp) {
        BreakdownContent(ui)
    }
}

@Composable
fun breakdownTitle(kind: BreakdownKind): String = when (kind) {
    BreakdownKind.CHIPS -> stringResource(R.string.breakdown_title_chips)

    BreakdownKind.CASH ->
        stringResource(
            R.string.breakdown_title_format,
            stringResource(UiR.string.suit_diamonds),
            stringResource(UiR.string.scoreboard_cash_on_hand),
        )

    BreakdownKind.DEBTS ->
        stringResource(
            R.string.breakdown_title_format,
            stringResource(UiR.string.suit_spades),
            stringResource(UiR.string.scoreboard_players_owe),
        )

    BreakdownKind.RAKE ->
        stringResource(
            R.string.breakdown_title_format,
            stringResource(UiR.string.suit_clubs),
            stringResource(UiR.string.scoreboard_rake),
        )

    BreakdownKind.BANKER ->
        stringResource(
            R.string.breakdown_title_format,
            stringResource(UiR.string.suit_hearts),
            stringResource(UiR.string.scoreboard_banker_income),
        )
}

@Composable
fun ColumnScope.BreakdownContent(ui: BreakdownUi) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val currency = ui.kind != BreakdownKind.CHIPS
    Text(text = breakdownTitle(ui.kind), style = type.displayS, color = colors.ink)
    AmountText(
        text = if (currency) MoneyFormat.rub(ui.total) else MoneyFormat.number(ui.total),
        style = type.amountStat,
        color = colors.ink,
    )
    if (ui.lines.isEmpty()) {
        Text(text = stringResource(R.string.breakdown_empty), style = type.caption, color = colors.muted)
    }
    BreakdownLines(ui.lines)
    if (ui.secondaryTitle != null && ui.secondary.isNotEmpty()) {
        Text(
            text = ui.secondaryTitle.resolve(),
            style = type.sectionTitle,
            color = colors.ink,
            modifier = Modifier.padding(top = 8.dp),
        )
        BreakdownLines(ui.secondary)
    }
}

@Composable
private fun BreakdownLines(lines: List<BreakdownLine>) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 0.dp)) {
        lines.forEach { line ->
            HorizontalDivider(thickness = 1.dp, color = colors.hairline)
            AmountRowLayout(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                trailing = {
                    AmountText(
                        text = if (line.currency) MoneyFormat.rub(line.amount) else MoneyFormat.number(line.amount),
                        style = type.amountRow,
                        color =
                            when (line.tone) {
                                Tone.INK -> colors.ink
                                Tone.OWE -> colors.owe
                                Tone.PAY -> colors.pay
                                Tone.BRASS -> colors.brassLight
                                Tone.MUTED -> colors.muted
                            },
                        contentDescription = if (line.currency) a11ySignedRubles(line.amount) else null,
                    )
                },
            ) {
                Text(
                    text = line.label.resolve(),
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
