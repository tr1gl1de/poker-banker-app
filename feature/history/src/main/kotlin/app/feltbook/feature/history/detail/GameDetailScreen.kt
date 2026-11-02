package app.feltbook.feature.history.detail

import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.domain.engine.GameSettlement
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.AvatarSize
import app.feltbook.core.ui.component.BankerBadge
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.MetricTile
import app.feltbook.core.ui.component.MetricVariant
import app.feltbook.core.ui.component.NameWithBadges
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.SectionHeader
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import app.feltbook.feature.history.common.PreviewHistoryData
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** Маршрут карточки игры: события [GameDetailViewModel] → навигация, snackbar, «Поделиться». */
@Composable
fun GameDetailRouteScreen(
    onBack: () -> Unit,
    onOpenTable: (gameId: String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: GameDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    val zone = LocalHistoryTimeZone.current
    val back by rememberUpdatedState(onBack)
    val openTable by rememberUpdatedState(onOpenTable)
    val deleted by rememberUpdatedState(onDeleted)
    val current by rememberUpdatedState(state)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                GameDetailEvent.Back -> back()

                is GameDetailEvent.OpenTable -> openTable(event.gameId)

                GameDetailEvent.Deleted -> deleted()

                GameDetailEvent.Share -> {
                    val text = detailShareText(current, resources, zone)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, null))
                }

                is GameDetailEvent.Message -> scope.launch {
                    snackbar.showUndoSnackbar(event.text.resolve(resources), actionLabel = null)
                }
            }
        }
    }
    LaunchedEffect(state.missing) { if (state.missing) back() }
    GameDetailScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbar)
}

/** Карточка игры только для чтения [без макета]: шапка как у Итога, блоки итога, игроки, полный журнал. */
@Composable
fun GameDetailScreen(
    state: GameDetailUiState,
    onAction: (GameDetailAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier = modifier.fillMaxSize().feltBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") { DetailHeader(state = state, onAction = onAction) }
            val settlement = state.settlement
            if (!state.loading && settlement != null) {
                item(key = "summary") { Summary(settlement = settlement) }
                item(key = "players-title") { SectionHeader(title = stringResource(R.string.game_detail_players)) }
                items(items = state.players, key = { "player-${it.playerId}" }) { player ->
                    PlayerRow(player = player, modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp))
                }
                item(key = "journal-title") {
                    SectionHeader(
                        title = stringResource(R.string.detail_journal_title),
                        trailing = if (state.handedOver) null else stringResource(R.string.detail_journal_hold_hint),
                    )
                }
                item(key = "journal") {
                    JournalList(
                        rows = state.journal,
                        now = state.now,
                        onHold = { onAction(GameDetailAction.JournalHold(it)) },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
        FeltbookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
    DetailDialogs(state = state, onAction = onAction)
}

@Composable
private fun DetailHeader(state: GameDetailUiState, onAction: (GameDetailAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeltIconButton(
            icon = FeltbookIcons.Back,
            contentDescription = stringResource(UiR.string.common_back),
            onClick = { onAction(GameDetailAction.Back) },
            filled = false,
        )
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(
                text = state.title,
                style = type.display,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (!state.loading) {
                Text(text = detailSubtitle(state), style = type.caption, color = colors.muted)
            }
        }
        if (!state.loading) DetailMenu(state = state, onAction = onAction)
    }
}

@Composable
private fun DetailMenu(state: GameDetailUiState, onAction: (GameDetailAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    var open by remember { mutableStateOf(false) }
    Box {
        FeltIconButton(
            icon = FeltbookIcons.MoreVert,
            contentDescription = stringResource(R.string.game_detail_menu_a11y),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.dialog) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.game_detail_menu_share), style = type.bodyL, color = colors.ink)
                },
                onClick = {
                    open = false
                    onAction(GameDetailAction.Share)
                },
            )
            if (state.canReopen) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.game_detail_menu_reopen), style = type.bodyL, color = colors.ink)
                    },
                    onClick = {
                        open = false
                        onAction(GameDetailAction.Reopen)
                    },
                )
            }
            if (state.canDelete) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.game_detail_menu_delete), style = type.bodyL, color = colors.owe)
                    },
                    onClick = {
                        open = false
                        onAction(GameDetailAction.Delete)
                    },
                )
            }
        }
    }
}

/** «25 окт · 4 ч 05 мин · 3 игрока» + пометки «изменено после закрытия», «передана», «долг». */
@Composable
private fun detailSubtitle(state: GameDetailUiState): String {
    val zone = LocalHistoryTimeZone.current
    val separator = stringResource(UiR.string.separator_dot)
    val parts = buildList {
        add(formatShortDate(TimeFormat.localDate(state.startedAt, zone)))
        state.duration?.let { add(formatDuration(it)) }
        add(pluralStringResource(UiR.plurals.players_count, state.playerCount, state.playerCount))
    }
    return buildString {
        append(parts.joinToString(separator))
        if (state.hasOpenDebt) append(stringResource(R.string.history_meta_debt))
        if (state.editedAfterClose) append(stringResource(R.string.history_meta_edited))
        if (state.handedOver) append(stringResource(R.string.history_meta_handed_over))
    }
}

/** Блоки итога только для чтения: сверка фишек, рейк, банкиру, касса, не оплачено. */
@Composable
private fun Summary(settlement: GameSettlement) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        val issued = MoneyFormat.number(settlement.chipsIssued)
        val returned = MoneyFormat.number(settlement.chipsReturned)
        if (settlement.chipsReconciled) {
            InfoBanner(
                text = stringResource(R.string.game_detail_chips_ok, issued, returned),
                variant = BannerVariant.Ok,
            )
        } else {
            InfoBanner(
                text = stringResource(
                    R.string.game_detail_chips_diff,
                    issued,
                    returned,
                    MoneyFormat.signedNumber(settlement.chipDiff),
                ),
                variant = BannerVariant.Danger,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            Tile(
                label = stringResource(R.string.game_detail_rake),
                amount = settlement.rakeAccrued,
                modifier = Modifier.weight(weight = 1f),
            )
            Tile(
                label = stringResource(R.string.game_detail_banker),
                amount = settlement.bankerIncome,
                variant = MetricVariant.Brass,
                modifier = Modifier.weight(weight = 1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            Tile(
                label = stringResource(R.string.game_detail_expected_cash),
                amount = settlement.expectedCash,
                modifier = Modifier.weight(weight = 1f),
            )
            Tile(
                label = stringResource(R.string.game_detail_unpaid),
                amount = settlement.shortage,
                variant = if (settlement.shortage > 0) MetricVariant.Owe else MetricVariant.Neutral,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

@Composable
private fun Tile(
    label: String,
    amount: Long,
    modifier: Modifier = Modifier,
    variant: MetricVariant = MetricVariant.Neutral,
) {
    MetricTile(
        label = label,
        value = MoneyFormat.rub(amount),
        contentDescription = "$label, ${a11yRubles(amount)}",
        variant = variant,
        modifier = modifier,
    )
}

/** «Андрей · начислено 2 300 · оплачено 0 · вывод 0» | «получил 2 400 ✓» / «долг 1 053». */
@Composable
private fun PlayerRow(player: DetailPlayerUi, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val (statusText, statusColor) = playerStatus(player)
    AmountRowLayout(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 6.dp),
        leading = { PlayerAvatar(name = player.name, suit = player.suit, size = AvatarSize.S) },
        trailing = { AmountText(text = statusText, style = type.labelBold, color = statusColor) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            NameWithBadges(name = player.name) { if (player.isBanker) BankerBadge() }
            Text(
                text = stringResource(
                    R.string.game_detail_row_meta,
                    MoneyFormat.number(player.charged),
                    MoneyFormat.number(player.paid),
                    MoneyFormat.number(player.withdrawal),
                ),
                style = type.ledger,
                color = colors.ledger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun playerStatus(player: DetailPlayerUi): Pair<String, Color> {
    val colors = FeltbookTheme.colors
    val balance = player.balanceAtExit ?: 0L
    return when {
        player.outstanding < 0 ->
            stringResource(R.string.game_detail_row_debt, MoneyFormat.rub(-player.outstanding)) to colors.owe

        player.outstanding > 0 ->
            stringResource(R.string.game_detail_row_banker_owes, MoneyFormat.rub(player.outstanding)) to colors.pay

        balance < 0 -> stringResource(R.string.game_detail_row_paid, MoneyFormat.rub(-balance)) to colors.ink

        balance > 0 -> stringResource(R.string.game_detail_row_received, MoneyFormat.rub(balance)) to colors.pay

        else -> stringResource(R.string.game_detail_row_even) to colors.muted
    }
}

@Composable
private fun DetailDialogs(state: GameDetailUiState, onAction: (GameDetailAction) -> Unit) {
    when (val dialog = state.dialog) {
        DetailDialog.Reopen -> FeltbookDialog(
            title = stringResource(R.string.game_detail_reopen_title),
            body = stringResource(R.string.game_detail_reopen_body),
            safeLabel = stringResource(R.string.history_delete_keep),
            onSafe = { onAction(GameDetailAction.DismissDialog) },
            confirmLabel = stringResource(R.string.game_detail_reopen_confirm),
            onConfirm = { onAction(GameDetailAction.ConfirmReopen) },
            destructive = false,
        )

        DetailDialog.Delete -> FeltbookDialog(
            title = pluralStringResource(R.plurals.history_delete_title, 1, 1),
            body = stringResource(R.string.history_delete_body_one),
            safeLabel = stringResource(R.string.history_delete_keep),
            onSafe = { onAction(GameDetailAction.DismissDialog) },
            confirmLabel = pluralStringResource(R.plurals.history_delete_confirm, 1, 1),
            onConfirm = { onAction(GameDetailAction.ConfirmDelete) },
        )

        is DetailDialog.Reverse -> FeltbookDialog(
            title = stringResource(R.string.detail_journal_reverse_title, dialog.title.resolve()),
            body = stringResource(R.string.detail_journal_reverse_body),
            safeLabel = stringResource(R.string.detail_journal_reverse_keep),
            onSafe = { onAction(GameDetailAction.DismissDialog) },
            confirmLabel = stringResource(R.string.detail_journal_reverse_confirm),
            onConfirm = { onAction(GameDetailAction.ConfirmReverse) },
        ) {
            FeltTextField(
                value = dialog.reason,
                onValueChange = { onAction(GameDetailAction.ReverseReason(it)) },
                label = stringResource(R.string.detail_journal_reverse_reason),
            )
        }

        null -> Unit
    }
}

/** Текст «Поделиться итогом»: шапка, кто сколько платит/получает, рейк и доход банкира. */
internal fun detailShareText(state: GameDetailUiState, resources: Resources, zone: ZoneId): String {
    val settlement = state.settlement ?: return state.title
    val date = resources.formatShortDate(TimeFormat.localDate(state.startedAt, zone))
    val header = resources.getString(R.string.game_detail_share_header, state.title, date)
    val lines = state.players.map { player ->
        val balance = player.balanceAtExit ?: 0L
        when {
            balance < 0 -> resources.getString(R.string.game_detail_share_pays, player.name, MoneyFormat.rub(-balance))
            balance > 0 -> resources.getString(R.string.game_detail_share_gets, player.name, MoneyFormat.rub(balance))
            else -> resources.getString(R.string.game_detail_share_even, player.name)
        }
    }
    val duration = state.duration?.let { resources.formatDuration(it) }
    val totals = resources.getString(
        R.string.game_detail_share_totals,
        MoneyFormat.rub(settlement.rakeAccrued),
        MoneyFormat.rub(settlement.bankerIncome),
    )
    return (listOfNotNull(header, duration) + lines + totals).joinToString(separator = "\n")
}

/** Превью: закрытая игра с частичным погашением долга. */
internal object PreviewDetailStates {
    fun detail(): GameDetailUiState =
        buildGameDetailUiState(PreviewHistoryData.thursdayPartial(), PreviewHistoryData.now, null, busy = false)

    fun tournament(): GameDetailUiState =
        buildGameDetailUiState(PreviewHistoryData.saturdayTournament(), PreviewHistoryData.now, null, busy = false)
}

@Preview(widthDp = 390, heightDp = 1400)
@Composable
private fun GameDetailPreview() {
    FeltbookTheme {
        CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) {
            GameDetailScreen(state = PreviewDetailStates.detail(), onAction = {})
        }
    }
}
