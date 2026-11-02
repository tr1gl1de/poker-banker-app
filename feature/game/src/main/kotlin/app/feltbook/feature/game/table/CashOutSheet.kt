package app.feltbook.feature.game.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.feltbook.core.domain.engine.DomainWarning
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountField
import app.feltbook.core.ui.component.AmountKeypad
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.BreakdownList
import app.feltbook.core.ui.component.BreakdownRow
import app.feltbook.core.ui.component.DangerOutlineButton
import app.feltbook.core.ui.component.DialogTextButton
import app.feltbook.core.ui.component.FeltFilterChip
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.HoldButtonSize
import app.feltbook.core.ui.component.HoldToConfirmButton
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.ResultPanel
import app.feltbook.core.ui.component.ResultVariant
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.component.SmallToggle
import app.feltbook.core.ui.component.discardAmountBody
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yBankerPays
import app.feltbook.core.ui.format.a11yEven
import app.feltbook.core.ui.format.a11yPlayerPays
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.R
import kotlin.math.abs

/** Выход: вид определяется `CashOutQuote.balance` (pay / owe / even, m3-ui-spec §5.6). */
@Composable
fun CashOutSheet(ui: CashOutUi, onAction: (TableAction) -> Unit) {
    FeltbookSheet(
        onDismiss = { onAction(TableAction.DismissSheet) },
        hasInput = !ui.chips.isEmpty,
        discardBody = discardAmountBody(MoneyFormat.number(ui.chips.value)),
    ) {
        when {
            ui.partial != null -> {
                BackHandler { onAction(TableAction.ClosePartial) }
                PartialContent(ui = ui, onAction = onAction)
            }

            ui.recipients != null -> {
                BackHandler { onAction(TableAction.CancelPicker) }
                TipRecipientContent(ui = ui, onAction = onAction)
            }

            else -> CashOutContent(ui = ui, onAction = onAction)
        }
    }
}

@Composable
private fun cashOutSubtitle(ui: CashOutUi): String = buildString {
    append(
        stringResource(
            R.string.cash_out_subtitle,
            ui.playedFor?.let { formatDuration(it) } ?: stringResource(UiR.string.common_dash),
            MoneyFormat.number(ui.chipsBought),
        ),
    )
    if (ui.debtBefore > 0) append(stringResource(R.string.cash_out_subtitle_debt, MoneyFormat.rub(ui.debtBefore)))
}

@Composable
fun ColumnScope.CashOutContent(ui: CashOutUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    SheetHeader(
        name = ui.name,
        suit = ui.suit,
        title = stringResource(R.string.cash_out_title, ui.name),
        subtitle = cashOutSubtitle(ui),
    )
    AmountField(
        label = stringResource(R.string.cash_out_chips_on_hand),
        amount = ui.chips.value,
        focused = ui.focus == CashOutFocus.CHIPS,
        onClick = { onAction(TableAction.FocusChips) },
    )
    if (ui.focus == CashOutFocus.TIP) {
        AmountField(
            label = stringResource(R.string.cash_out_tips),
            amount = ui.customTip.value,
            focused = true,
            onClick = {},
        )
    }
    if (ui.focus != CashOutFocus.NONE) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            DialogTextButton(
                text = stringResource(UiR.string.common_done),
                onClick = { onAction(TableAction.KeypadDone) },
                destructive = false,
            )
        }
        AmountKeypad(
            entry = if (ui.focus == CashOutFocus.TIP) ui.customTip else ui.chips,
            onEntryChange = { onAction(TableAction.EntryChange(it)) },
        )
    }
    ui.quote.warnings.filterIsInstance<DomainWarning.ChipsExceedInPlay>().firstOrNull()?.let {
        InfoBanner(
            text = stringResource(R.string.cash_out_exceeds_in_play, MoneyFormat.number(it.chipsInPlay)),
            variant = BannerVariant.Warn,
        )
    }
    if (ui.showEarlyBanner) {
        InfoBanner(
            text =
                stringResource(
                    R.string.cash_out_early_banner,
                    (ui.quote.minutesPlayed ?: 0L).toInt(),
                    ui.minPlayMinutes,
                ),
            variant = BannerVariant.Warn,
        )
    }
    if (ui.focus == CashOutFocus.NONE) {
        if (ui.tipsExpanded) TipsBlock(ui = ui, onAction = onAction) else CollapsedTips(onAction = onAction)
        CashOutBreakdown(ui)
    }
    ui.blocker?.let { Text(text = it.resolve(), style = type.small, color = colors.owe) }
    CashOutResult(ui)
    Text(
        text =
            pluralStringResource(
                R.plurals.cash_out_result_game,
                pluralQuantity(ui.quote.resultThisGame),
                MoneyFormat.signedNumber(ui.quote.resultThisGame),
            ),
        style = type.ledgerTight,
        color = colors.ledger,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    CashOutActions(ui = ui, onAction = onAction)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TipsBlock(ui: CashOutUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.card, shape = FeltbookTheme.shapes.card)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.cash_out_tips),
                style = type.sectionTitle,
                color = colors.ink,
                modifier = Modifier.weight(weight = 1f),
            )
            Text(text = stringResource(R.string.cash_out_tips_hint), style = type.caption, color = colors.ledger)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            FeltFilterChip(
                text = stringResource(R.string.cash_out_no_tips),
                selected = ui.tipChoice == TipChoice.NONE,
                onClick = { onAction(TableAction.TipSelect(TipChoice.NONE)) },
            )
            if (ui.round100 > 0) {
                FeltFilterChip(
                    text = stringResource(R.string.cash_out_tip_round_100, MoneyFormat.number(ui.round100)),
                    selected = ui.tipChoice == TipChoice.ROUND_100,
                    onClick = { onAction(TableAction.TipSelect(TipChoice.ROUND_100)) },
                )
            }
            if (ui.round500 > 0 && ui.round500 != ui.round100) {
                FeltFilterChip(
                    text = stringResource(R.string.cash_out_tip_round_500, MoneyFormat.number(ui.round500)),
                    selected = ui.tipChoice == TipChoice.ROUND_500,
                    onClick = { onAction(TableAction.TipSelect(TipChoice.ROUND_500)) },
                )
            }
            FeltFilterChip(
                text =
                    if (ui.tipChoice == TipChoice.CUSTOM && !ui.customTip.isEmpty) {
                        stringResource(R.string.cash_out_tip_custom_value, MoneyFormat.number(ui.customTip.value))
                    } else {
                        stringResource(R.string.cash_out_tip_custom)
                    },
                selected = ui.tipChoice == TipChoice.CUSTOM,
                onClick = { onAction(TableAction.TipSelect(TipChoice.CUSTOM)) },
            )
        }
        val enabled = ui.tipChoice != TipChoice.NONE
        Row(
            modifier = Modifier.alpha(alpha = if (enabled) 1f else 0.5f),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(R.string.cash_out_tip_to), style = type.caption, color = colors.muted)
            SmallToggle(
                text = stringResource(R.string.cash_out_tip_banker),
                selected = ui.tipRecipient == null,
                onClick = { if (enabled) onAction(TableAction.TipToBanker) },
            )
            SmallToggle(
                text =
                    ui.tipRecipient?.let { stringResource(R.string.cash_out_tip_player_selected, it.name) }
                        ?: stringResource(R.string.cash_out_tip_player),
                selected = ui.tipRecipient != null,
                onClick = { if (enabled) onAction(TableAction.PickTipRecipient) },
            )
        }
    }
}

@Composable
private fun CollapsedTips(onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    AmountRowLayout(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(FeltbookTheme.shapes.card)
                .background(color = colors.card)
                .clickable(role = Role.Button, onClick = { onAction(TableAction.ExpandTips) })
                .padding(horizontal = 14.dp),
        trailing = {
            Text(text = stringResource(R.string.cash_out_tips_collapsed), style = type.caption, color = colors.muted)
        },
    ) {
        Text(text = stringResource(R.string.cash_out_tips), style = type.title, color = colors.ink)
    }
}

@Composable
private fun CashOutBreakdown(ui: CashOutUi) {
    val quote = ui.quote
    val tip = quote.tip?.amount ?: 0L
    val rows = buildList {
        if (ui.balance >= 0) {
            add(BreakdownRow(stringResource(R.string.cash_out_row_chips), MoneyFormat.number(quote.chips)))
            add(
                BreakdownRow(
                    stringResource(R.string.cash_out_row_tips),
                    if (tip > 0) MoneyFormat.number(-tip) else stringResource(UiR.string.common_dash),
                ),
            )
            add(
                BreakdownRow(
                    stringResource(R.string.cash_out_row_withdrawal),
                    MoneyFormat.rub(quote.withdrawal),
                    bold = true,
                ),
            )
            if (quote.carriedOver == 0L) {
                when {
                    quote.outstandingBefore < 0 ->
                        add(
                            BreakdownRow(
                                stringResource(R.string.cash_out_row_player_debt),
                                MoneyFormat.rub(quote.outstandingBefore),
                                valueColor = FeltbookTheme.colors.owe,
                            ),
                        )

                    quote.outstandingBefore > 0 ->
                        add(
                            BreakdownRow(
                                stringResource(R.string.cash_out_row_banker_owes),
                                MoneyFormat.signedRub(quote.outstandingBefore),
                                valueColor = FeltbookTheme.colors.pay,
                            ),
                        )
                }
            } else {
                add(BreakdownRow(stringResource(R.string.cash_out_row_charged), MoneyFormat.rub(quote.sessionCharged)))
                add(BreakdownRow(stringResource(R.string.cash_out_row_paid), MoneyFormat.rub(quote.sessionPaid)))
            }
        } else {
            if (tip > 0) add(BreakdownRow(stringResource(R.string.cash_out_row_tips), MoneyFormat.number(-tip)))
            add(BreakdownRow(stringResource(R.string.cash_out_row_withdrawal), MoneyFormat.rub(quote.withdrawal)))
            add(BreakdownRow(stringResource(R.string.cash_out_row_charged), MoneyFormat.rub(quote.sessionCharged)))
            add(BreakdownRow(stringResource(R.string.cash_out_row_paid), MoneyFormat.rub(quote.sessionPaid)))
        }
        when {
            quote.carriedOver < 0 ->
                add(
                    BreakdownRow(
                        stringResource(R.string.cash_out_row_carried_debt),
                        MoneyFormat.rub(quote.carriedOver),
                        valueColor = FeltbookTheme.colors.owe,
                    ),
                )

            quote.carriedOver > 0 ->
                add(
                    BreakdownRow(
                        stringResource(R.string.cash_out_row_carried_credit),
                        MoneyFormat.signedRub(quote.carriedOver),
                    ),
                )
        }
    }
    BreakdownList(rows = rows, card = false, ledgerLabels = ui.balance < 0)
}

@Composable
private fun CashOutResult(ui: CashOutUi) {
    val balance = ui.balance
    when {
        balance > 0 -> ResultPanel(
            label = stringResource(R.string.cash_out_result_pay, ui.name),
            amount = MoneyFormat.signedRub(balance),
            contentDescription = a11yBankerPays(balance),
            variant = ResultVariant.Pay,
        )

        balance < 0 -> ResultPanel(
            label = stringResource(R.string.cash_out_result_owe, ui.name),
            amount = MoneyFormat.rub(-balance),
            contentDescription = a11yPlayerPays(ui.name, -balance),
            variant = ResultVariant.Owe,
        )

        else -> ResultPanel(
            label = stringResource(R.string.cash_out_result_even),
            amount = MoneyFormat.rub(0),
            contentDescription = a11yEven(),
            variant = ResultVariant.Even,
        )
    }
}

@Composable
private fun CashOutActions(ui: CashOutUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val balance = ui.balance
    Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        when {
            balance > 0 -> {
                HoldToConfirmButton(
                    title = stringResource(R.string.cash_out_settled),
                    hint = stringResource(R.string.cash_out_settled_hint_pay, MoneyFormat.rub(balance)),
                    onConfirm = { onAction(TableAction.SettleFull) },
                    size = HoldButtonSize.H68,
                    enabled = ui.canConfirm,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    OutlineButton(
                        text = stringResource(R.string.cash_out_partial_paid_out),
                        onClick = { onAction(TableAction.OpenPartial) },
                        enabled = ui.canConfirm && balance > 1,
                        emphasized = true,
                        modifier = Modifier.weight(weight = 1f),
                    )
                    OutlineButton(
                        text = stringResource(UiR.string.common_cancel),
                        onClick = { onAction(TableAction.DismissSheet) },
                        icon = FeltbookIcons.Close,
                        modifier = Modifier.weight(weight = 1f),
                    )
                }
            }

            balance < 0 -> {
                HoldToConfirmButton(
                    title = stringResource(R.string.cash_out_received, MoneyFormat.rub(-balance)),
                    onConfirm = { onAction(TableAction.SettleFull) },
                    size = HoldButtonSize.H68,
                    enabled = ui.canConfirm,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                    OutlineButton(
                        text = stringResource(R.string.cash_out_partial_received),
                        onClick = { onAction(TableAction.OpenPartial) },
                        enabled = ui.canConfirm && -balance > 1,
                        emphasized = true,
                        modifier = Modifier.weight(weight = 1f),
                    )
                    DangerOutlineButton(
                        text = stringResource(R.string.cash_out_not_paid),
                        onClick = { onAction(TableAction.SettleNone) },
                        enabled = ui.canConfirm,
                        modifier = Modifier.weight(weight = 1f),
                    )
                }
                Text(
                    text = stringResource(R.string.cash_out_not_paid_hint, MoneyFormat.rub(-balance)),
                    style = type.small,
                    color = colors.ledger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                HoldToConfirmButton(
                    title = stringResource(R.string.cash_out_settled),
                    onConfirm = { onAction(TableAction.SettleFull) },
                    size = HoldButtonSize.H68,
                    enabled = ui.canConfirm,
                )
                OutlineButton(
                    text = stringResource(UiR.string.common_cancel),
                    onClick = { onAction(TableAction.DismissSheet) },
                    icon = FeltbookIcons.Close,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** «Отдал часть…» / «Выплатил часть…»: 0 < x < |balance|. */
@Composable
private fun ColumnScope.PartialContent(ui: CashOutUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val entry = ui.partial ?: return
    val receiving = ui.balance < 0
    val rest = (abs(ui.balance) - entry.value).coerceAtLeast(0)
    SheetTitle(
        title =
            if (receiving) {
                stringResource(R.string.cash_out_partial_title_received, ui.name)
            } else {
                stringResource(R.string.cash_out_partial_title_paid_out)
            },
        subtitle = stringResource(UiR.string.error_partial_out_of_range, MoneyFormat.rub(ui.partialMax)),
    )
    AmountText(
        text = MoneyFormat.rub(entry.value),
        style = type.amountHero,
        color = if (entry.isEmpty) colors.muted else colors.ink,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    AmountKeypad(entry = entry, onEntryChange = {
        onAction(TableAction.EntryChange(it))
    }, maxValue = ui.partialMax.coerceAtLeast(0))
    HoldToConfirmButton(
        title =
            stringResource(
                if (receiving) {
                    R.string.cash_out_partial_received_confirm
                } else {
                    R.string.cash_out_partial_paid_out_confirm
                },
                MoneyFormat.rub(entry.value),
            ),
        hint =
            stringResource(
                if (receiving) R.string.cash_out_partial_rest_debt else R.string.cash_out_partial_rest_banker,
                MoneyFormat.rub(rest),
            ),
        onConfirm = { onAction(TableAction.SettlePartial) },
        size = HoldButtonSize.H68,
        enabled = ui.canConfirmPartial,
    )
    if (!receiving) {
        DialogTextButton(
            text = stringResource(R.string.cash_out_pay_later),
            onClick = { onAction(TableAction.SettleNone) },
            destructive = false,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
    OutlineButton(
        text = stringResource(UiR.string.common_cancel),
        onClick = { onAction(TableAction.ClosePartial) },
        icon = FeltbookIcons.Close,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** «Кому чаевые»: за столом — сначала, затем вышедшие. */
@Composable
private fun ColumnScope.TipRecipientContent(ui: CashOutUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    SheetTitle(title = stringResource(R.string.cash_out_tip_picker_title))
    ui.recipients.orEmpty().forEach { recipient ->
        AmountRowLayout(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(FeltbookTheme.shapes.card)
                    .background(color = colors.card)
                    .clickable(role = Role.Button, onClick = {
                        onAction(TableAction.TipRecipientPicked(recipient.playerId))
                    })
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            leading = { PlayerAvatar(name = recipient.name, suit = recipient.suit, active = recipient.seated) },
            trailing = {
                Text(
                    text =
                        stringResource(
                            if (recipient.seated) {
                                R.string.cash_out_tip_picker_at_table
                            } else {
                                R.string.cash_out_tip_picker_left
                            },
                        ),
                    style = type.tiny,
                    color = colors.muted,
                )
            },
        ) {
            Text(text = recipient.name, style = type.title, color = colors.ink, maxLines = 1)
        }
    }
    OutlineButton(
        text = stringResource(UiR.string.common_cancel),
        onClick = { onAction(TableAction.CancelPicker) },
        icon = FeltbookIcons.Close,
        modifier = Modifier.fillMaxWidth(),
    )
}
