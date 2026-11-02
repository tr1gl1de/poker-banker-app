package app.feltbook.feature.history.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.component.AmountKeypad
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.BreakdownList
import app.feltbook.core.ui.component.BreakdownRow
import app.feltbook.core.ui.component.FeltFilterChip
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.LocalCompactSheet
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.component.discardAmountBody
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import java.time.ZoneOffset

/**
 * «Погасить · Влад» (m3-ui-spec §5.7): сумма (по умолчанию весь долг), «весь долг» / «половина», клавиатура,
 * «Долг сейчас → Останется», «Принять 1 053 ₽». Запись — PAYMENT в старую игру; обратимое (snackbar).
 */
@Composable
fun DebtPaySheet(sheet: DebtPaySheetUi, busy: Boolean, onAction: (DebtsAction) -> Unit) {
    FeltbookSheet(
        onDismiss = { onAction(DebtsAction.DismissSheet) },
        hasInput = !sheet.entry.isEmpty && sheet.amount != sheet.card.amount,
        discardBody = discardAmountBody(MoneyFormat.number(sheet.amount)),
        contentSpacing = 12.dp,
        // Главная кнопка закреплена внизу: на низких экранах (Redmi 6A) она иначе уходит под навигацию.
        bottomBar = { DebtPayConfirmButton(sheet = sheet, busy = busy, onAction = onAction) },
    ) {
        DebtPayContent(sheet = sheet, busy = busy, onAction = onAction, showConfirm = false)
    }
}

@Composable
internal fun ColumnScope.DebtPayContent(
    sheet: DebtPaySheetUi,
    busy: Boolean,
    onAction: (DebtsAction) -> Unit,
    showConfirm: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val zone = LocalHistoryTimeZone.current
    val card = sheet.card
    SheetTitle(
        title = stringResource(R.string.debts_pay_title, card.playerName),
        subtitle = stringResource(
            R.string.debts_pay_subtitle,
            card.gameTitle,
            formatShortDate(TimeFormat.localDate(card.gameCreatedAt, zone)),
            MoneyFormat.rub(card.amount),
        ),
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AmountText(
            text = MoneyFormat.rub(sheet.amount),
            style = if (LocalCompactSheet.current) type.amountInput else type.amountHero,
            color = if (sheet.entry.isEmpty) colors.muted else colors.ink,
            textAlign = TextAlign.Center,
        )
        if (sheet.exceedsDebt) {
            Text(
                text = stringResource(R.string.debts_pay_exceeds_debt, MoneyFormat.rub(card.amount)),
                style = type.small,
                color = colors.owe,
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        FeltFilterChip(
            text = stringResource(R.string.debts_pay_chip_all),
            selected = sheet.amount == card.amount,
            onClick = { onAction(DebtsAction.PayAll) },
            role = Role.Button,
        )
        FeltFilterChip(
            text = stringResource(R.string.debts_pay_chip_half),
            selected = sheet.amount == (card.amount + 1) / 2 && sheet.amount != card.amount,
            onClick = { onAction(DebtsAction.PayHalf) },
            role = Role.Button,
        )
    }
    BreakdownList(
        rows = listOf(BreakdownRow(stringResource(R.string.debts_pay_row_debt_now), MoneyFormat.rub(card.amount))),
        total = BreakdownRow(stringResource(R.string.debts_pay_row_remaining), MoneyFormat.rub(sheet.remaining)),
    )
    AmountKeypad(entry = sheet.entry, onEntryChange = { onAction(DebtsAction.PayEntry(it)) })
    if (showConfirm) DebtPayConfirmButton(sheet = sheet, busy = busy, onAction = onAction)
}

@Composable
private fun DebtPayConfirmButton(sheet: DebtPaySheetUi, busy: Boolean, onAction: (DebtsAction) -> Unit) {
    BrassButton(
        text = if (sheet.entry.isEmpty) {
            stringResource(R.string.debts_pay_confirm_empty)
        } else {
            stringResource(R.string.debts_pay_confirm, MoneyFormat.rub(sheet.amount))
        },
        onClick = { onAction(DebtsAction.ConfirmPay) },
        style = BrassButtonStyle.Hero64,
        enabled = sheet.canConfirm && !busy,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(widthDp = 390)
@Composable
private fun DebtPayPreview() {
    FeltbookTheme {
        CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) {
            val card = PreviewDebtsStates.debts().cards.first()
            Column(
                modifier = Modifier.padding(all = 20.dp),
                verticalArrangement = Arrangement.spacedBy(space = 12.dp),
            ) {
                DebtPayContent(
                    sheet = DebtPaySheetUi(card, AmountEntry.preset(card.amount)),
                    busy = false,
                    onAction = {},
                )
            }
        }
    }
}
