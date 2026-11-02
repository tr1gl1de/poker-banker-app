package app.feltbook.feature.game.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.BuyInQuote
import app.feltbook.core.domain.engine.DomainWarning
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.component.AmountKeypad
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.BreakdownList
import app.feltbook.core.ui.component.BreakdownRow
import app.feltbook.core.ui.component.ChipDenominationRow
import app.feltbook.core.ui.component.DialogTextButton
import app.feltbook.core.ui.component.FeltFilterChip
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.IssueChipsButton
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.SegmentedToggle
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.component.chipColorFor
import app.feltbook.core.ui.component.discardAmountBody
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.LocalTimeZone

// ---------- Докуп / закуп / возврат (макет BuyIn) ----------

@Composable
fun BuyInSheet(ui: BuyInUi, onAction: (TableAction) -> Unit) {
    FeltbookSheet(
        onDismiss = { onAction(TableAction.DismissSheet) },
        hasInput = !ui.entry.isEmpty,
        discardBody = discardAmountBody(MoneyFormat.number(ui.entry.value)),
        contentSpacing = 12.dp,
    ) {
        BuyInContent(ui = ui, onAction = onAction)
    }
}

@Composable
fun ColumnScope.BuyInContent(ui: BuyInUi, onAction: (TableAction) -> Unit) {
    val subtitle = buildString {
        if (ui.isReturn) append(stringResource(R.string.buy_in_subtitle_return)).append(" · ")
        append(stringResource(R.string.buy_in_subtitle_bought, MoneyFormat.number(ui.chipsBought)))
        when {
            ui.debt > 0 -> append(stringResource(R.string.buy_in_subtitle_debt, MoneyFormat.rub(ui.debt)))

            ui.owedByBanker > 0 ->
                append(stringResource(R.string.buy_in_subtitle_banker_owes, MoneyFormat.rub(ui.owedByBanker)))

            ui.chipsBought > 0 -> append(stringResource(R.string.buy_in_subtitle_paid))
        }
    }
    SheetHeader(
        name = ui.name,
        suit = ui.suit,
        title = stringResource(if (ui.isFirst) R.string.buy_in_title_first else R.string.buy_in_title_rebuy, ui.name),
        subtitle = subtitle,
    )
    PurchaseInput(
        entry = ui.entry,
        minBuyIn = ui.minBuyIn,
        belowMin = ui.belowMin,
        quote = ui.quote,
        lastAmount = ui.lastAmount,
        rakePercent = ui.rakePercent,
        split = ui.split,
        payment = ui.payment,
        onAction = onAction,
    )
    IssueButton(quote = ui.quote, payment = ui.payment, enabled = ui.canConfirm, onAction = onAction)
}

@Composable
internal fun SheetHeader(name: String, suit: Suit, title: String, subtitle: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(name = name, suit = suit)
        SheetTitle(title = title, subtitle = subtitle, modifier = Modifier.weight(weight = 1f))
    }
}

/** Сумма, подсказка, фишки, статус оплаты, расшифровка и клавиатура — общая часть закупа и нового игрока. */
@Composable
internal fun ColumnScope.PurchaseInput(
    entry: AmountEntry,
    minBuyIn: Long,
    belowMin: Boolean,
    quote: BuyInQuote,
    lastAmount: Long?,
    rakePercent: Long?,
    split: Boolean,
    payment: BuyInPayment,
    onAction: (TableAction) -> Unit,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        AmountText(
            text = MoneyFormat.number(entry.value),
            style = type.amountHero,
            color = if (entry.isEmpty) colors.muted else colors.ink,
            textAlign = TextAlign.Center,
        )
        val minText = stringResource(R.string.buy_in_hint_min, MoneyFormat.number(minBuyIn))
        val hint = stringResource(R.string.buy_in_hint, minText)
        Text(
            text =
                buildAnnotatedString {
                    val start = hint.indexOf(minText)
                    if (belowMin && start >= 0) {
                        append(hint.substring(0, start))
                        withStyle(SpanStyle(color = colors.owe)) { append(minText) }
                        append(hint.substring(start + minText.length))
                    } else {
                        append(hint)
                    }
                },
            style = type.caption,
            color = colors.muted,
            textAlign = TextAlign.Center,
        )
    }
    quote.warnings.filterIsInstance<DomainWarning.SuspiciousBuyIn>().firstOrNull()?.let { warning ->
        InfoBanner(
            text =
                warning.typical?.let { typical ->
                    stringResource(
                        R.string.buy_in_suspicious,
                        MoneyFormat.number(warning.chips),
                        MoneyFormat.number(typical),
                    )
                } ?: run {
                    stringResource(R.string.buy_in_suspicious_no_typical, MoneyFormat.number(warning.chips))
                },
            variant = BannerVariant.Warn,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
    ChipDenominationRow(
        currentAmount = entry.value,
        onSelect = { onAction(TableAction.ChipSelect(it)) },
        lastAmount = lastAmount,
    )
    PaymentSelector(split = split, payment = payment, onAction = onAction)
    PurchaseBreakdown(quote = quote, rakePercent = rakePercent, payment = payment)
    AmountKeypad(entry = entry, onEntryChange = { onAction(TableAction.EntryChange(it)) })
}

@Composable
internal fun PaymentSelector(split: Boolean, payment: BuyInPayment, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    if (!split) {
        SegmentedToggle(
            options = listOf(stringResource(R.string.buy_in_debt), stringResource(R.string.buy_in_paid_now)),
            selectedIndex = if (payment.chipsPaid) 1 else 0,
            onSelect = { onAction(TableAction.PaymentChange(if (it == 1) BuyInPayment.PAID else BuyInPayment.DEBT)) },
        )
    } else {
        val options = listOf(stringResource(R.string.buy_in_debt), stringResource(R.string.buy_in_split_paid))
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.buy_in_split_chips),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.width(width = 96.dp),
            )
            SegmentedToggle(
                options = options,
                selectedIndex = if (payment.chipsPaid) 1 else 0,
                onSelect = { onAction(TableAction.PaymentChange(payment.copy(chipsPaid = it == 1))) },
                modifier = Modifier.weight(weight = 1f),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.buy_in_split_fees),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.width(width = 96.dp),
            )
            SegmentedToggle(
                options = options,
                selectedIndex = if (payment.feesPaid) 1 else 0,
                onSelect = { onAction(TableAction.PaymentChange(payment.copy(feesPaid = it == 1))) },
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

/** «Фишки 2 000 + рейк 200 + сбор 100 = 2 300 ₽» строками. */
@Composable
internal fun PurchaseBreakdown(quote: BuyInQuote, rakePercent: Long?, payment: BuyInPayment) {
    val fee = when {
        quote.feeAlreadyTaken -> stringResource(R.string.buy_in_row_fee_taken)
        quote.feeWaivedForBanker -> stringResource(R.string.buy_in_row_fee_waived)
        else -> MoneyFormat.rub(quote.bankerFee)
    }
    val rows = buildList {
        add(BreakdownRow(stringResource(R.string.buy_in_row_chips), MoneyFormat.rub(quote.chips)))
        add(
            BreakdownRow(
                rakePercent?.let { stringResource(R.string.buy_in_row_rake_percent, MoneyFormat.percent(it.toInt())) }
                    ?: stringResource(R.string.buy_in_row_rake),
                MoneyFormat.rub(quote.rake),
            ),
        )
        add(BreakdownRow(stringResource(R.string.buy_in_row_fee), fee))
        val paidNow = quote.paidNow(payment)
        if (!payment.isSingleStatus && paidNow > 0) {
            add(BreakdownRow(stringResource(R.string.buy_in_total_paid_now), MoneyFormat.rub(paidNow)))
        }
    }
    val toDebt = quote.toDebt(payment)
    val total = if (toDebt > 0) {
        BreakdownRow(
            stringResource(R.string.buy_in_total_debt),
            MoneyFormat.rub(toDebt),
            valueColor = FeltbookTheme.colors.owe,
        )
    } else {
        BreakdownRow(stringResource(R.string.buy_in_total_due), MoneyFormat.rub(quote.total))
    }
    BreakdownList(rows = rows, total = total)
}

@Composable
internal fun IssueButton(quote: BuyInQuote, payment: BuyInPayment, enabled: Boolean, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val chips = quote.chips
    val title = if (chips > 0) {
        pluralStringResource(R.plurals.buy_in_issue, pluralQuantity(chips), MoneyFormat.number(chips))
    } else {
        stringResource(R.string.buy_in_issue_empty)
    }
    val paidNow = quote.paidNow(payment)
    val toDebt = quote.toDebt(payment)
    val subtitle = when {
        paidNow == 0L -> stringResource(R.string.buy_in_issue_sub_debt, MoneyFormat.rub(quote.total))
        toDebt == 0L -> stringResource(R.string.buy_in_issue_sub_paid, MoneyFormat.rub(quote.total))
        else -> stringResource(R.string.buy_in_issue_sub_split, MoneyFormat.rub(paidNow), MoneyFormat.rub(toDebt))
    }
    IssueChipsButton(
        title = title,
        subtitle = subtitle,
        chipColor = chipColorFor(chips, colors.chips),
        onClick = { onAction(TableAction.ConfirmBuyIn) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------- Новый игрок (макет DebtWarning) ----------

@Composable
fun NewPlayerSheet(ui: NewPlayerUi, onAction: (TableAction) -> Unit) {
    FeltbookSheet(
        onDismiss = { onAction(TableAction.DismissSheet) },
        hasInput = ui.name.isNotBlank() || !ui.entry.isEmpty,
        discardBody = if (ui.entry.isEmpty) {
            stringResource(UiR.string.sheet_discard_body_generic)
        } else {
            discardAmountBody(MoneyFormat.number(ui.entry.value))
        },
    ) {
        if (ui.debtPay != null && ui.debt != null) {
            BackHandler { onAction(TableAction.DebtPayCancel) }
            DebtPayContent(debt = ui.debt, entry = ui.debtPay, busy = ui.busy, onAction = onAction)
        } else {
            NewPlayerContent(ui = ui, onAction = onAction)
        }
    }
}

@Suppress("CyclomaticComplexMethod") // Два режима фокуса из m3-ui-spec §5.4.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColumnScope.NewPlayerContent(ui: NewPlayerUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusAmount = {
        focusManager.clearFocus()
        keyboard?.hide()
        onAction(TableAction.FocusAmount)
    }
    SheetTitle(title = stringResource(R.string.new_player_title))
    FeltTextField(
        value = ui.name,
        onValueChange = { onAction(TableAction.NameChange(it)) },
        label = stringResource(R.string.new_player_name),
        large = true,
        error = if (ui.nameProblem == NameProblem.TooLong) stringResource(R.string.new_player_name_too_long) else null,
        onImeAction = focusAmount,
        modifier = Modifier.onFocusChanged { if (it.hasFocus && ui.amountFocused) onAction(TableAction.FocusName) },
    )
    if (ui.suggestions.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            ui.suggestions.forEach { name ->
                FeltFilterChip(
                    text = name,
                    selected = false,
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onAction(TableAction.SuggestionPick(name))
                    },
                    role = Role.Button,
                )
            }
        }
    }
    (ui.nameProblem as? NameProblem.Taken)?.let { taken ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text =
                    stringResource(
                        if (taken.seated) R.string.new_player_name_taken else R.string.new_player_name_taken_left,
                        taken.name,
                    ),
                style = type.small,
                color = colors.owe,
                modifier = Modifier.weight(weight = 1f),
            )
            DialogTextButton(
                text =
                    stringResource(if (taken.seated) R.string.new_player_open_card else R.string.new_player_return),
                onClick = {
                    onAction(
                        if (taken.seated) {
                            TableAction.OpenExisting(taken.playerId)
                        } else {
                            TableAction.ReturnExisting(taken.playerId)
                        },
                    )
                },
                destructive = false,
            )
        }
    }
    ui.debt?.let { debt ->
        if (ui.debtCollapsed) {
            Text(
                text = stringResource(R.string.debt_warning_collapsed, MoneyFormat.rub(debt.total)),
                style = type.small,
                color = colors.owe,
            )
        } else {
            DebtWarningCard(debt = debt, onAction = onAction)
        }
    }
    if (ui.amountFocused) {
        PurchaseInput(
            entry = ui.entry,
            minBuyIn = ui.minBuyIn,
            belowMin = ui.belowMin,
            quote = ui.quote,
            lastAmount = null,
            rakePercent = ui.rakePercent,
            split = ui.split,
            payment = ui.payment,
            onAction = onAction,
        )
        if (ui.debt != null) DebtDefaultHint()
        IssueButton(quote = ui.quote, payment = ui.payment, enabled = ui.canConfirm, onAction = onAction)
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = focusAmount),
            verticalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            Text(text = stringResource(R.string.new_player_first_buy_in), style = type.caption, color = colors.inkSoft)
            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp), verticalAlignment = Alignment.Bottom) {
                AmountText(
                    text = MoneyFormat.number(ui.entry.value),
                    style = type.amountInput,
                    color = if (ui.entry.isEmpty) colors.muted else colors.ink,
                )
                Text(
                    text =
                        if (ui.entry.isEmpty) {
                            stringResource(R.string.new_player_amount_hint_min, MoneyFormat.number(ui.minBuyIn))
                        } else {
                            stringResource(R.string.new_player_amount_hint, MoneyFormat.rub(ui.quote.total))
                        },
                    style = type.caption,
                    color = colors.ledger,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        PaymentSelector(split = ui.split, payment = ui.payment, onAction = onAction)
        if (ui.debt != null) DebtDefaultHint()
        val chips = ui.entry.value
        BrassButton(
            text =
                if (chips > 0) {
                    pluralStringResource(R.plurals.buy_in_issue, pluralQuantity(chips), MoneyFormat.number(chips))
                } else {
                    stringResource(R.string.buy_in_issue_empty)
                },
            onClick = { onAction(TableAction.ConfirmBuyIn) },
            style = BrassButtonStyle.Hero64,
            enabled = ui.canConfirm,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DebtDefaultHint() {
    Text(
        text = stringResource(R.string.debt_warning_default_paid),
        style = FeltbookTheme.typography.small,
        color = FeltbookTheme.colors.ledger,
    )
}

/** Предупреждение о старом долге: `role = alert`, `liveRegion = Polite`. */
@Composable
internal fun DebtWarningCard(debt: DebtWarningUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(18.dp)
    val since = formatShortDate(TimeFormat.localDate(debt.since, LocalTimeZone.current))
    val meta = if (debt.games > 1) {
        stringResource(
            R.string.debt_warning_meta_many,
            since,
            pluralStringResource(UiR.plurals.games_count, debt.games, debt.games),
        )
    } else {
        stringResource(
            R.string.debt_warning_meta,
            since,
            debt.oldestGameTitle,
            if (debt.days <= 0) {
                stringResource(R.string.debt_warning_today)
            } else {
                pluralStringResource(UiR.plurals.days_count, pluralQuantity(debt.days), debt.days.toInt())
            },
        )
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.oweBackground, shape = shape)
                .border(width = 1.5.dp, color = colors.oweBorderStrong, shape = shape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 10.dp)) {
            Icon(
                imageVector = FeltbookIcons.Alert,
                contentDescription = null,
                tint = colors.owe,
                modifier = Modifier.padding(top = 1.dp).size(size = FeltbookDimens.icon),
            )
            Column(verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
                Text(
                    text = stringResource(R.string.debt_warning_title, debt.name, MoneyFormat.rub(debt.total)),
                    style = type.emphasisM,
                    color = colors.ink,
                )
                Text(text = meta, style = type.ledger, color = colors.oweTint)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            BrassButton(
                text = stringResource(R.string.debt_warning_pay_now),
                onClick = { onAction(TableAction.DebtPayNow) },
                style = BrassButtonStyle.Flat48,
                modifier = Modifier.weight(weight = 1f),
            )
            OutlineButton(
                text = stringResource(R.string.debt_warning_seat_with_debt),
                onClick = { onAction(TableAction.DebtSeatWithDebt) },
                height = FeltbookDimens.button48,
                emphasized = true,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

/** «Погасить · Влад» поверх нового игрока: запись — в старую игру (m3-ui-spec §5.7). */
@Composable
internal fun ColumnScope.DebtPayContent(
    debt: DebtWarningUi,
    entry: AmountEntry,
    busy: Boolean,
    onAction: (TableAction) -> Unit,
) {
    val since = formatShortDate(TimeFormat.localDate(debt.since, LocalTimeZone.current))
    PayInput(
        title = stringResource(R.string.debt_pay_title, debt.name),
        subtitle = stringResource(R.string.debt_pay_subtitle, debt.oldestGameTitle, since, MoneyFormat.rub(debt.total)),
        current = debt.total,
        entry = entry,
        payout = false,
        error = if (entry.value >
            debt.total
        ) {
            stringResource(R.string.pay_exceeds_debt, MoneyFormat.rub(debt.total))
        } else {
            null
        },
        onAction = onAction,
    )
    BrassButton(
        text = stringResource(R.string.pay_confirm, MoneyFormat.rub(entry.value)),
        onClick = { onAction(TableAction.DebtPayConfirm) },
        style = BrassButtonStyle.Hero64,
        enabled = !busy && entry.value in 1..debt.total,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlineButton(
        text = stringResource(UiR.string.common_cancel),
        onClick = { onAction(TableAction.DebtPayCancel) },
        icon = FeltbookIcons.Close,
        modifier = Modifier.fillMaxWidth(),
    )
}
