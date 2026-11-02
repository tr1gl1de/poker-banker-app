package app.feltbook.feature.game.table

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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.BrassFab
import app.feltbook.core.ui.component.DialogTextButton
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.ScoreboardCell
import app.feltbook.core.ui.component.ScoreboardState
import app.feltbook.core.ui.component.SectionHeader
import app.feltbook.core.ui.component.TableScoreboard
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.LocalConfirmHaptics
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.game.R
import kotlinx.coroutines.launch

/** Маршрут стола: состояние и события [TableViewModel], snackbar «Отменить» 5 с. */
@Composable
fun TableRouteScreen(
    onOpenSettlement: () -> Unit,
    onOpenHandover: () -> Unit,
    onGameGone: () -> Unit,
    viewModel: TableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val haptic = LocalHapticFeedback.current
    val haptics = LocalConfirmHaptics.current
    val undoLabel = stringResource(UiR.string.common_undo)
    val settlement by rememberUpdatedState(onOpenSettlement)
    val handover by rememberUpdatedState(onOpenHandover)
    val gone by rememberUpdatedState(onGameGone)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TableEvent.Undoable -> scope.launch {
                    val result = snackbar.showUndoSnackbar(event.message.resolve(resources), undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onAction(TableAction.Undo(event.token))
                }

                is TableEvent.Message -> scope.launch {
                    snackbar.showUndoSnackbar(event.message.resolve(resources), actionLabel = null)
                }

                TableEvent.OpenSettlement -> settlement()

                TableEvent.OpenHandover -> handover()

                TableEvent.GameGone -> gone()

                TableEvent.ConfirmHaptic -> if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            }
        }
    }
    TableScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbar)
}

/** Стол (m3-ui-spec §5.3): на телефоне — список и шторки, на ширине ≥ 600 dp — список + карточка игрока. */
@Composable
fun TableScreen(
    state: TableUiState,
    onAction: (TableAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
    forceSinglePane: Boolean = false,
) {
    if (!forceSinglePane && isTwoPane()) {
        TableTwoPane(state = state, onAction = onAction, snackbarHostState = snackbarHostState, modifier = modifier)
    } else {
        Box(modifier = modifier.fillMaxSize().feltBackground()) {
            TableList(
                state = state,
                onAction = onAction,
                onRowClick = { onAction(TableAction.RowClick(it)) },
                listState = listState,
            )
            TableFabAndSnackbar(state = state, onAction = onAction, snackbarHostState = snackbarHostState)
        }
    }
    TableSheets(state = state, onAction = onAction)
    TableDialogs(state = state, onAction = onAction)
}

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.TableFabAndSnackbar(
    state: TableUiState,
    onAction: (TableAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding(),
        horizontalAlignment = Alignment.End,
    ) {
        FeltbookSnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 16.dp))
        if (state.canAddPlayer && !state.loading) {
            BrassFab(
                text = stringResource(R.string.table_fab_player),
                contentDescription = stringResource(R.string.table_fab_player_a11y),
                onClick = { onAction(TableAction.AddPlayer) },
                modifier = Modifier.padding(end = 16.dp, bottom = 20.dp),
            )
        }
    }
}

/** Список стола: шапка, табло, игроки — одним `LazyColumn` (шапка прокручивается вместе со списком). */
@Composable
internal fun TableList(
    state: TableUiState,
    onAction: (TableAction) -> Unit,
    onRowClick: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    selectedId: String? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "header") { TableHeader(state = state, onAction = onAction) }
        item(key = "board") {
            TableScoreboard(
                state = state.board.toScoreboard(),
                onOvalClick = { onAction(TableAction.OpenBreakdown(BreakdownKind.CHIPS)) },
                onCellClick = { cell -> onAction(TableAction.OpenBreakdown(cell.toKind())) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        item(key = "players_header") {
            SectionHeader(
                title = stringResource(R.string.table_players),
                trailing =
                    if (state.playersLeft > 0) {
                        stringResource(R.string.table_players_meta, state.playersAtTable, state.playersLeft)
                    } else {
                        stringResource(R.string.table_players_meta_none_left, state.playersAtTable)
                    },
            )
        }
        if (state.settling) {
            item(key = "settling") {
                Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    InfoBanner(text = stringResource(R.string.table_settling_banner), variant = BannerVariant.Warn)
                    DialogTextButton(
                        text = stringResource(R.string.table_to_settlement),
                        onClick = { onAction(TableAction.ToSettlement) },
                        destructive = false,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
        if (state.rows.isEmpty() && !state.loading) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.table_empty),
                    style = type.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
        items(items = state.rows, key = { it.id }) { row ->
            PlayerRow(
                row = row,
                onClick = { onRowClick(row.id) },
                onSwipeBuyIn = { onAction(TableAction.SwipeBuyIn(row.id)) },
                onSwipeCashOut = { onAction(TableAction.SwipeCashOut(row.id)) },
                selected = row.id == selectedId,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun TableHeader(state: TableUiState, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    var menu by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(
                text = state.title,
                style = type.displayTable,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        if (state.settling) R.string.table_subtitle_settling else R.string.table_subtitle_running,
                        formatDuration(state.elapsed),
                        state.bankerName,
                    ),
                style = type.caption,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FeltIconButton(
            icon = FeltbookIcons.Handover,
            contentDescription = stringResource(R.string.table_handover),
            onClick = { onAction(TableAction.Handover) },
        )
        Box {
            FeltIconButton(
                icon = FeltbookIcons.MoreVert,
                contentDescription = stringResource(R.string.table_menu),
                onClick = { menu = true },
            )
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                containerColor = colors.dialog,
            ) {
                if (state.settling) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.table_menu_resume), style = type.bodyL, color = colors.ink)
                        },
                        onClick = {
                            menu = false
                            onAction(TableAction.Resume)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.table_to_settlement), style = type.bodyL, color = colors.ink)
                        },
                        onClick = {
                            menu = false
                            onAction(TableAction.ToSettlement)
                        },
                    )
                } else {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.table_menu_finish), style = type.bodyL, color = colors.owe)
                        },
                        onClick = {
                            menu = false
                            onAction(TableAction.Finish)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardUi.toScoreboard(): ScoreboardState = ScoreboardState(
    chipsInPlay = chipsInPlay,
    chipsMeta =
        stringResource(
            R.string.table_chips_meta,
            MoneyFormat.number(chipsIssued),
            MoneyFormat.number(chipsReturned),
            rebuyCount,
        ),
    cashOnHand = cashOnHand,
    playersOwe = playersOwe,
    rakeDue = rakeDue,
    bankerIncome = bankerIncome,
    bankerOwes = bankerOwes,
)

private fun ScoreboardCell.toKind(): BreakdownKind = when (this) {
    ScoreboardCell.Cash -> BreakdownKind.CASH
    ScoreboardCell.Debts -> BreakdownKind.DEBTS
    ScoreboardCell.Rake -> BreakdownKind.RAKE
    ScoreboardCell.Banker -> BreakdownKind.BANKER
}

/** Шторки — по состоянию экрана. */
@Composable
internal fun TableSheets(state: TableUiState, onAction: (TableAction) -> Unit) {
    when (val sheet = state.sheet) {
        is PlayerCardUi -> PlayerCardSheet(card = sheet, now = state.now, onAction = onAction)
        is BuyInUi -> BuyInSheet(ui = sheet, onAction = onAction)
        is NewPlayerUi -> NewPlayerSheet(ui = sheet, onAction = onAction)
        is PayUi -> PaySheet(ui = sheet, onAction = onAction)
        is CashOutUi -> CashOutSheet(ui = sheet, onAction = onAction)
        is BreakdownUi -> BreakdownSheet(ui = sheet, onAction = onAction)
        null -> Unit
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TableScreenPreview() {
    FeltbookTheme { TableScreen(state = PreviewTableData.table(), onAction = {}, forceSinglePane = true) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TableScreenSettlingPreview() {
    FeltbookTheme { TableScreen(state = PreviewTableData.settling(), onAction = {}, forceSinglePane = true) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun TableScreenEmptyPreview() {
    FeltbookTheme { TableScreen(state = PreviewTableData.empty(), onAction = {}, forceSinglePane = true) }
}
