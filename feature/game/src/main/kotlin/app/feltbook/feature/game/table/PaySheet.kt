package app.feltbook.feature.game.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
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
import app.feltbook.core.ui.component.HoldButtonSize
import app.feltbook.core.ui.component.HoldToConfirmButton
import app.feltbook.core.ui.component.LocalCompactSheet
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.component.discardAmountBody
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.R

/** «Оплата · Борис» (обратимое) и «Выплатить · Саша» (деньги на руки, удержание). */
@Composable
fun PaySheet(ui: PayUi, onAction: (TableAction) -> Unit, snackbar: SnackbarHostState? = null) {
    FeltbookSheet(
        onDismiss = { onAction(TableAction.DismissSheet) },
        hasInput = ui.entry.value != ui.current && !ui.entry.isEmpty,
        discardBody = discardAmountBody(MoneyFormat.number(ui.entry.value)),
        contentSpacing = 12.dp,
        snackbarHostState = snackbar,
        bottomBar = { PayActions(ui = ui, onAction = onAction) },
    ) {
        PayContent(ui = ui, onAction = onAction)
    }
}

@Composable
fun ColumnScope.PayContent(ui: PayUi, onAction: (TableAction) -> Unit) {
    PayInput(
        title = stringResource(if (ui.payout) R.string.payout_title else R.string.pay_title, ui.name),
        subtitle =
            stringResource(
                if (ui.payout) R.string.payout_subtitle else R.string.pay_subtitle_debt,
                MoneyFormat.rub(ui.current),
            ),
        current = ui.current,
        entry = ui.entry,
        payout = ui.payout,
        error = ui.error?.resolve(),
        onAction = onAction,
    )
}

/** «Принять N ₽» / удержание «Выплатил N ₽» — закреплены внизу шторки. */
@Composable
fun ColumnScope.PayActions(ui: PayUi, onAction: (TableAction) -> Unit) {
    if (ui.payout) {
        HoldToConfirmButton(
            title = stringResource(R.string.payout_confirm, MoneyFormat.rub(ui.entry.value)),
            onConfirm = { onAction(TableAction.ConfirmPay) },
            size = HoldButtonSize.H68,
            enabled = ui.canConfirm,
        )
    } else {
        BrassButton(
            text =
                if (ui.entry.isEmpty) {
                    stringResource(R.string.pay_confirm_empty)
                } else {
                    stringResource(R.string.pay_confirm, MoneyFormat.rub(ui.entry.value))
                },
            onClick = { onAction(TableAction.ConfirmPay) },
            style = BrassButtonStyle.Hero64,
            enabled = ui.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Скелет шторки суммы оплаты: заголовок, сумма, «весь долг / половина», клавиатура, «Долг сейчас → Останется». */
@Composable
internal fun ColumnScope.PayInput(
    title: String,
    subtitle: String,
    current: Long,
    entry: AmountEntry,
    payout: Boolean,
    error: String?,
    onAction: (TableAction) -> Unit,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    SheetTitle(title = title, subtitle = subtitle)
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AmountText(
            text = MoneyFormat.rub(entry.value),
            style = if (LocalCompactSheet.current) type.amountInput else type.amountHero,
            color = if (entry.isEmpty) colors.muted else colors.ink,
            textAlign = TextAlign.Center,
        )
        if (error != null) Text(text = error, style = type.small, color = colors.owe)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        FeltFilterChip(
            text = stringResource(if (payout) R.string.payout_chip_all else R.string.pay_chip_all),
            selected = entry.value == current,
            onClick = { onAction(TableAction.PayAll) },
            role = Role.Button,
        )
        FeltFilterChip(
            text = stringResource(R.string.pay_chip_half),
            selected = entry.value == (current + 1) / 2 && entry.value != current,
            onClick = { onAction(TableAction.PayHalf) },
            role = Role.Button,
        )
    }
    BreakdownList(
        rows =
            listOf(
                BreakdownRow(
                    stringResource(if (payout) R.string.pay_row_owed_now else R.string.pay_row_debt_now),
                    MoneyFormat.rub(current),
                ),
            ),
        total = BreakdownRow(
            stringResource(R.string.pay_row_remaining),
            MoneyFormat.rub((current - entry.value).coerceAtLeast(0)),
        ),
    )
    AmountKeypad(entry = entry, onEntryChange = { onAction(TableAction.EntryChange(it)) })
}
