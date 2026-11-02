package app.feltbook.feature.settlement

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.GameSettlement
import app.feltbook.core.domain.engine.SettlementRow
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.AvatarSize
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.HoldButtonSize
import app.feltbook.core.ui.component.HoldToConfirmButton
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.MetricSize
import app.feltbook.core.ui.component.MetricTile
import app.feltbook.core.ui.component.MetricVariant
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.ShareButton
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.component.dashedBorder
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.launch

@Composable
fun SettlementRouteScreen(
    onBack: () -> Unit,
    onClosed: () -> Unit,
    onCashOut: (gameId: String, playerId: String) -> Unit,
    viewModel: SettlementViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettlementEvent.Closed -> closed()

                is SettlementEvent.Error -> scope.launch {
                    snackbar.showUndoSnackbar(resources.getString(event.messageRes), actionLabel = null)
                }
            }
        }
    }
    SettlementScreen(
        state = state,
        snackbarHostState = snackbar,
        onAction = { action ->
            when (action) {
                SettlementAction.Back -> onBack()

                SettlementAction.Share -> shareText(
                    context,
                    settlementShareText(resources, state, ZoneId.systemDefault()),
                )

                is SettlementAction.CashOut -> onCashOut(state.gameId, action.playerId)

                else -> viewModel.onAction(action)
            }
        },
    )
}

/** Android Sharesheet с текстом итога (картинкой — M4.6). */
fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/** Текст «Поделиться итогом» (m3-ui-spec §5.8). */
fun settlementShareText(resources: Resources, state: SettlementUiState, zone: ZoneId): String {
    val settlement = state.settlement ?: return ""
    val date = TimeFormat.localDate(state.createdAt, zone)
    val months = resources.getStringArray(UiR.array.months_short)
    val dateText = resources.getString(UiR.string.date_day_month, date.dayOfMonth, months[date.monthValue - 1])
    return buildList {
        add(resources.getString(R.string.settlement_share_header, state.title, dateText))
        settlement.rows.forEach { row ->
            val balance = row.balanceAtExit ?: 0L
            add(
                when {
                    balance < 0 -> resources.getString(
                        R.string.settlement_share_pays,
                        row.name,
                        MoneyFormat.rub(-balance),
                    )

                    balance > 0 -> resources.getString(
                        R.string.settlement_share_gets,
                        row.name,
                        MoneyFormat.rub(balance),
                    )

                    else -> resources.getString(R.string.settlement_share_even, row.name)
                },
            )
        }
        add(
            resources.getString(
                R.string.settlement_share_totals,
                MoneyFormat.rub(settlement.rakeAccrued),
                MoneyFormat.rub(settlement.bankerIncome),
            ),
        )
        add(
            if (settlement.chipsReconciled) {
                resources.getString(R.string.settlement_share_chips_ok, MoneyFormat.number(settlement.chipsIssued))
            } else {
                resources.getString(R.string.settlement_share_chips_diff, MoneyFormat.signedNumber(settlement.chipDiff))
            },
        )
    }.joinToString(separator = "\n")
}

/** Итог игры (макеты Settlement и DebtSettlement). */
@Composable
fun SettlementScreen(
    state: SettlementUiState,
    onAction: (SettlementAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val settlement = state.settlement
    Box(modifier = modifier.fillMaxSize().feltBackground(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
            Header(state = state, onAction = onAction)
            if (settlement != null) {
                Column(modifier = Modifier.weight(weight = 1f).verticalScroll(rememberScrollState())) {
                    Body(state = state, settlement = settlement, onAction = onAction)
                }
                Bottom(state = state, settlement = settlement, onAction = onAction)
            }
        }
        FeltbookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 96.dp),
        )
    }
    if (state.chipsDialog && settlement != null) {
        val diff = settlement.chipDiff
        FeltbookDialog(
            title = stringResource(R.string.settlement_chips_confirm_title),
            body =
                stringResource(
                    if (diff <
                        0
                    ) {
                        R.string.settlement_chips_confirm_body
                    } else {
                        R.string.settlement_chips_confirm_body_more
                    },
                    pluralStringResource(
                        UiR.plurals.chips_count,
                        pluralQuantity(abs(diff)),
                        MoneyFormat.number(abs(diff)),
                    ),
                ),
            safeLabel = stringResource(R.string.settlement_chips_confirm_keep),
            onSafe = { onAction(SettlementAction.DismissDialog) },
            confirmLabel = stringResource(R.string.settlement_close),
            onConfirm = { onAction(SettlementAction.ConfirmChipsMismatch) },
        )
    }
}

@Composable
private fun Header(state: SettlementUiState, onAction: (SettlementAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val settlement = state.settlement
    val subtitle = buildString {
        append(
            stringResource(
                R.string.settlement_subtitle,
                state.title,
                formatDuration(state.duration),
                pluralStringResource(
                    UiR.plurals.players_count,
                    settlement?.playerCount ?: 0,
                    settlement?.playerCount ?: 0,
                ),
            ),
        )
        val rebuys = settlement?.rebuyCount ?: 0
        if (rebuys > 0) {
            append(
                stringResource(
                    R.string.settlement_subtitle_rebuys,
                    pluralStringResource(UiR.plurals.rebuys_count, rebuys, rebuys),
                ),
            )
        }
    }
    Row(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeltIconButton(
            icon = FeltbookIcons.Back,
            contentDescription = stringResource(R.string.settlement_back),
            onClick = { onAction(SettlementAction.Back) },
            filled = false,
        )
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(text = stringResource(R.string.settlement_title), style = type.display, color = colors.ink)
            Text(
                text = subtitle,
                style = type.caption,
                color = colors.muted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Body(state: SettlementUiState, settlement: GameSettlement, onAction: (SettlementAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val debts = state.hasDebts
    if (debts) UnpaidBlock(settlement)
    if (!debts || !settlement.chipsReconciled) {
        InfoBanner(
            text =
                if (settlement.chipsReconciled) {
                    stringResource(
                        R.string.settlement_chips_ok,
                        MoneyFormat.number(settlement.chipsIssued),
                        MoneyFormat.number(settlement.chipsReturned),
                    )
                } else {
                    stringResource(
                        R.string.settlement_chips_diff,
                        MoneyFormat.number(settlement.chipsIssued),
                        MoneyFormat.number(settlement.chipsReturned),
                        MoneyFormat.signedNumber(settlement.chipDiff),
                    )
                },
            variant = if (settlement.chipsReconciled) BannerVariant.Ok else BannerVariant.Danger,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (debts) 8.dp else 4.dp),
        )
    }
    Tiles(settlement = settlement, debts = debts)
    CashBox(settlement = settlement)
    if (settlement.cashOnHand < 0) {
        Text(
            text = stringResource(R.string.settlement_cash_negative_note, MoneyFormat.rub(settlement.cashOnHand)),
            style = type.ledger,
            color = colors.ledger,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
        )
    }
    if (settlement.unpaidPayouts.isNotEmpty()) UnpaidPayouts(settlement)
    if (state.stillPlaying.isNotEmpty()) {
        StillPlaying(state = state, settlement = settlement, onAction = onAction)
    }
    if (debts) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(space = 6.dp),
        ) {
            settlement.rows.forEach { CompactRow(it) }
        }
    } else {
        Text(
            text = stringResource(R.string.settlement_players),
            style = type.sectionTitle,
            color = colors.ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            settlement.rows.forEach {
                DetailedRow(
                    row = it,
                    suit =
                        state.suits[it.playerId.value.toString()] ?: Suit.Spades,
                )
            }
        }
    }
    Box(Modifier.height(height = 16.dp))
}

@Composable
private fun UnpaidBlock(settlement: GameSettlement) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .fillMaxWidth()
                .background(color = colors.oweBackground, shape = shape)
                .border(width = 1.5.dp, color = colors.oweBorderStrong, shape = shape)
                .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = FeltbookIcons.Alert,
                contentDescription = null,
                tint = colors.owe,
                modifier = Modifier.size(size = 20.dp),
            )
            Text(text = stringResource(R.string.settlement_unpaid), style = type.sectionTitle, color = colors.oweTint)
        }
        settlement.unpaidDebts.forEach { debt ->
            AmountRowLayout(
                trailing = { AmountText(amount = debt.amount, style = type.amountTile, color = colors.owe) },
            ) {
                Text(
                    text = stringResource(R.string.settlement_left_with_debt, debt.name),
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = stringResource(R.string.settlement_unpaid_note, debt.name),
                style = type.ledger,
                color = colors.oweTint,
            )
        }
    }
}

@Composable
private fun Tiles(settlement: GameSettlement, debts: Boolean) {
    val type = FeltbookTheme.typography
    val colors = FeltbookTheme.colors
    val size = if (debts) MetricSize.Large else MetricSize.XL
    val rakeFootnote = when {
        settlement.rakePaid > 0 -> stringResource(R.string.settlement_rake_paid, MoneyFormat.rub(settlement.rakePaid))
        settlement.shortage > 0 -> stringResource(R.string.settlement_rake_incl_debt)
        else -> stringResource(R.string.settlement_rake_single)
    }
    val bankerFootnote = if (debts) {
        stringResource(R.string.settlement_banker_after_debt)
    } else {
        buildString {
            append(
                stringResource(
                    R.string.settlement_banker_breakdown,
                    MoneyFormat.number(settlement.bankerFees),
                    MoneyFormat.number(settlement.tipsToBanker),
                ),
            )
            if (settlement.forgiven > 0) {
                append(stringResource(R.string.settlement_banker_forgiven, MoneyFormat.number(settlement.forgiven)))
            }
        }
    }
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (debts) 10.dp else 12.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        val rake = settlement.rakeDue.takeIf { it > 0 } ?: settlement.rakeAccrued
        MetricTile(
            label = stringResource(R.string.settlement_rake_title),
            value = MoneyFormat.rub(rake),
            contentDescription = stringResource(R.string.settlement_rake_title) + ", " + a11yRubles(rake),
            variant = MetricVariant.NeutralOutlined,
            size = size,
            footnote = rakeFootnote,
            modifier = Modifier.weight(weight = 1f),
        )
        MetricTile(
            label = stringResource(R.string.settlement_banker_title),
            value = MoneyFormat.rub(settlement.bankerIncome),
            contentDescription =
                stringResource(R.string.settlement_banker_title) + ", " + a11yRubles(settlement.bankerIncome),
            variant = MetricVariant.Brass,
            size = size,
            footnote = bankerFootnote,
            modifier = Modifier.weight(weight = 1f),
        )
    }
    if (settlement.incomeByBanker.size > 1) {
        Text(
            text =
                settlement.incomeByBanker.joinToString(separator = " · ") {
                    "${it.bankerName} ${MoneyFormat.rub(it.net)}"
                },
            style = type.ledger,
            color = colors.ledger,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
        )
    }
}

@Composable
private fun CashBox(settlement: GameSettlement) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .fillMaxWidth()
                .background(color = colors.feltDeep, shape = FeltbookTheme.shapes.field)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        CashLine(
            stringResource(R.string.settlement_expected_cash),
            MoneyFormat.rub(settlement.expectedCash),
            colors.ink,
            big = true,
        )
        if (settlement.unpaidDebts.isNotEmpty()) {
            settlement.unpaidDebts.forEach {
                CashLine(
                    stringResource(R.string.settlement_not_received, it.name),
                    MoneyFormat.rub(-it.amount),
                    colors.owe,
                )
            }
            Box(Modifier.fillMaxWidth().height(height = 1.dp).background(color = colors.divider))
            CashLine(stringResource(R.string.settlement_cash_now), MoneyFormat.rub(settlement.cashOnHand), colors.ink)
        } else if (settlement.cashOnHand != settlement.expectedCash) {
            CashLine(stringResource(R.string.settlement_cash_now), MoneyFormat.rub(settlement.cashOnHand), colors.ink)
        }
    }
}

@Composable
private fun CashLine(label: String, value: String, color: Color, big: Boolean = false) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    AmountRowLayout(trailing = {
        AmountText(text = value, style = if (big) type.amountMetric else type.labelBold, color = color)
    }) {
        Text(text = label, style = type.body, color = colors.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UnpaidPayouts(settlement: GameSettlement) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(text = stringResource(R.string.settlement_unpaid_payouts), style = type.sectionTitle, color = colors.ink)
        Text(text = stringResource(R.string.settlement_unpaid_payouts_note), style = type.small, color = colors.ledger)
        settlement.unpaidPayouts.forEach { payout ->
            AmountRowLayout(trailing = {
                AmountText(text = MoneyFormat.signedRub(payout.amount), style = type.amountRow, color = colors.pay)
            }) {
                Text(
                    text = payout.name,
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StillPlaying(state: SettlementUiState, settlement: GameSettlement, onAction: (SettlementAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val ids = state.stillPlaying
    Column(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        InfoBanner(
            text =
                stringResource(
                    R.string.settlement_still_playing,
                    pluralStringResource(UiR.plurals.players_count, ids.size, ids.size),
                ),
            variant = BannerVariant.Warn,
        )
        settlement.rows.filter { it.playerId.value.toString() in ids }.forEach { row ->
            AmountRowLayout(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(color = colors.card, shape = FeltbookTheme.shapes.field)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                leading = {
                    PlayerAvatar(
                        name = row.name,
                        suit =
                            state.suits[row.playerId.value.toString()] ?: Suit.Spades,
                        size = AvatarSize.S,
                    )
                },
                trailing = {
                    OutlineButton(
                        text = stringResource(R.string.settlement_cash_out),
                        onClick = { onAction(SettlementAction.CashOut(row.playerId.value.toString())) },
                        height = FeltbookDimens.button48,
                        icon = FeltbookIcons.Exit,
                    )
                },
            ) {
                Text(
                    text = row.name,
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun DetailedRow(row: SettlementRow, suit: Suit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val balance = row.balanceAtExit ?: 0L
    val meta = buildString {
        append(
            stringResource(
                R.string.settlement_row_meta,
                MoneyFormat.number(row.charged),
                MoneyFormat.number(row.paidBeforeExit),
                MoneyFormat.number(row.withdrawal),
            ),
        )
        if (row.tipsGiven >
            0
        ) {
            append(stringResource(R.string.settlement_row_meta_tips, MoneyFormat.number(row.tipsGiven)))
        }
        if (row.earlyExit) append(stringResource(R.string.settlement_row_meta_early))
    }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.card, shape = FeltbookTheme.shapes.card)
                .padding(all = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        AmountRowLayout(
            leading = { PlayerAvatar(name = row.name, suit = suit, size = AvatarSize.S) },
            trailing = {
                when {
                    row.status == PlayerStatus.PLAYING ->
                        AmountText(
                            text = stringResource(R.string.settlement_row_playing),
                            style = type.amountRow,
                            color = colors.muted,
                        )

                    balance < 0 ->
                        AmountText(
                            text = stringResource(R.string.settlement_row_pays, MoneyFormat.rub(-balance)),
                            style = type.amountRow,
                            color = colors.owe,
                        )

                    balance > 0 ->
                        AmountText(
                            text = stringResource(R.string.settlement_row_gets, MoneyFormat.rub(balance)),
                            style = type.amountRow,
                            color = colors.pay,
                        )

                    else -> AmountText(
                        text = stringResource(R.string.settlement_row_even),
                        style = type.amountRow,
                        color = colors.muted,
                    )
                }
            },
        ) {
            Text(
                text = row.name,
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(text = meta, style = type.ledgerTight, color = colors.ledger)
    }
}

@Composable
private fun CompactRow(row: SettlementRow) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val balance = row.balanceAtExit ?: 0L
    val debtor = row.outstanding < 0
    val shape = FeltbookTheme.shapes.field
    val (text, color) = when {
        debtor -> stringResource(R.string.settlement_row_debt, MoneyFormat.number(-row.outstanding)) to colors.owe

        row.outstanding > 0 -> stringResource(
            R.string.settlement_row_banker_owes,
            MoneyFormat.number(row.outstanding),
        ) to
            colors.pay

        row.status == PlayerStatus.PLAYING -> stringResource(R.string.settlement_row_playing) to colors.muted

        balance < 0 -> stringResource(R.string.settlement_row_paid, MoneyFormat.number(-balance)) to colors.inkSoft

        balance > 0 -> stringResource(R.string.settlement_row_received, MoneyFormat.number(balance)) to colors.inkSoft

        else -> stringResource(R.string.settlement_row_even) to colors.muted
    }
    AmountRowLayout(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.card, shape = shape)
                .then(if (debtor) Modifier.dashedBorder(colors.oweBorderStrong, 14.dp) else Modifier)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        trailingGap = 10.dp,
        trailing = { AmountText(text = text, style = type.labelBold, color = color) },
    ) {
        Text(text = row.name, style = type.title, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Bottom(state: SettlementUiState, settlement: GameSettlement, onAction: (SettlementAction) -> Unit) {
    val debts = state.hasDebts
    val unpaid = settlement.unpaidPayouts.sumOf { it.amount }
    val title = when {
        CloseConfirmation.OPEN_DEBTS in settlement.requiredConfirmations -> stringResource(
            R.string.settlement_close_with_debt,
        )

        settlement.rakeDue > 0 -> stringResource(R.string.settlement_close_rake)

        else -> stringResource(R.string.settlement_close)
    }
    val hint = when {
        unpaid > 0 -> stringResource(R.string.settlement_close_unpaid_hint, MoneyFormat.rub(unpaid))
        debts -> stringResource(R.string.settlement_close_with_debt_hint, MoneyFormat.rub(settlement.rakeDue))
        else -> stringResource(UiR.string.common_hold)
    }
    Row(
        modifier =
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp)
                .then(if (state.stillPlaying.isNotEmpty()) Modifier.alpha(alpha = 0.5f) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShareButton(
            contentDescription = stringResource(R.string.settlement_share),
            onClick = { onAction(SettlementAction.Share) },
        )
        HoldToConfirmButton(
            title = title,
            hint = hint,
            onConfirm = { onAction(SettlementAction.Close) },
            size = if (debts) HoldButtonSize.H64 else HoldButtonSize.H56,
            enabled = state.canClose,
            modifier = Modifier.weight(weight = 1f),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettlementPreview() {
    FeltbookTheme { SettlementScreen(state = PreviewSettlementData.settled(), onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettlementDebtPreview() {
    FeltbookTheme { SettlementScreen(state = PreviewSettlementData.withDebt(), onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SettlementStillPlayingPreview() {
    FeltbookTheme { SettlementScreen(state = PreviewSettlementData.stillPlaying(), onAction = {}) }
}
