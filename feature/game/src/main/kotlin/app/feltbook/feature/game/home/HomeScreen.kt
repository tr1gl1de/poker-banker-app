package app.feltbook.feature.game.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.BrassFab
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.SectionHeader
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.icons.FeltbookMark
import app.feltbook.core.ui.theme.FeltbookGradients
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.core.ui.theme.feltTable
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.LocalTimeZone
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Маршрут Главной: состояние из [HomeViewModel], навигация — колбэками `:app`. */
@Composable
fun HomeRouteScreen(
    onNewGame: () -> Unit,
    onOpenTable: (gameId: String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenGame: (gameId: String) -> Unit,
    onOpenDebts: () -> Unit,
    onReceiveGame: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onAction = { action ->
            when (action) {
                HomeAction.NewGame -> if (state.active == null) onNewGame() else viewModel.onAction(action)

                HomeAction.BackToTable -> {
                    viewModel.onAction(HomeAction.DismissDialog)
                    state.active?.let { onOpenTable(it.gameId) }
                }

                is HomeAction.OpenPastGame -> onOpenGame(action.gameId)

                HomeAction.AllHistory -> onOpenHistory()

                HomeAction.OpenDebts -> onOpenDebts()

                HomeAction.ReceiveGame -> onReceiveGame()

                HomeAction.DismissDialog -> viewModel.onAction(action)
            }
        },
    )
}

/** Главная (m3-ui-spec §5.1). Без логики: всё — из [state], действия — в [onAction]. */
@Composable
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().feltBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Низ — под FAB: 20 над панелью + 56 FAB + 16.
            contentPadding = PaddingValues(bottom = 92.dp),
        ) {
            item(key = "header") { HomeHeader(onReceiveGame = { onAction(HomeAction.ReceiveGame) }) }
            state.active?.let { active ->
                item(key = "active") {
                    ActiveGameCard(active = active, onBack = { onAction(HomeAction.BackToTable) })
                }
            }
            state.openDebts?.let { debts ->
                item(key = "debts") { OpenDebtsBlock(debts = debts, onClick = { onAction(HomeAction.OpenDebts) }) }
            }
            item(key = "past_header") {
                SectionHeader(
                    title = stringResource(R.string.home_past_games),
                    trailing = stringResource(R.string.home_all_history),
                    onTrailingClick = { onAction(HomeAction.AllHistory) },
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
                )
            }
            if (state.pastGames.isEmpty() && !state.loading) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.home_empty_history),
                        style = FeltbookTheme.typography.caption,
                        color = FeltbookTheme.colors.muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
            items(items = state.pastGames, key = { it.gameId }) { game ->
                PastGameRow(
                    game = game,
                    onClick = { onAction(HomeAction.OpenPastGame(game.gameId)) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
            }
        }
        BrassFab(
            text = stringResource(R.string.home_fab_new_game),
            onClick = { onAction(HomeAction.NewGame) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 20.dp),
        )
    }
    if (state.alreadyRunningDialog && state.active != null) {
        FeltbookDialog(
            title = stringResource(R.string.home_game_already_running_title),
            body = stringResource(R.string.home_game_already_running_body, state.active.title),
            safeLabel = stringResource(UiR.string.common_ok),
            onSafe = { onAction(HomeAction.DismissDialog) },
            confirmLabel = stringResource(R.string.home_game_already_running_go),
            onConfirm = { onAction(HomeAction.BackToTable) },
            destructive = false,
            bottomInset = 96.dp,
        )
    }
}

@Composable
private fun HomeHeader(onReceiveGame: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeltbookMark(size = 40.dp)
        Column(modifier = Modifier.weight(weight = 1f)) {
            Text(text = stringResource(R.string.home_brand), style = type.brand, color = colors.ink)
            Text(
                text = stringResource(R.string.home_tagline).uppercase(),
                style = type.taglineCaps,
                color = colors.brass,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        FeltIconButton(
            icon = FeltbookIcons.Handover,
            contentDescription = stringResource(R.string.home_receive_game),
            onClick = onReceiveGame,
        )
    }
}

@Composable
private fun ActiveGameCard(active: ActiveGameUi, onBack: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.board
    Column(
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .fillMaxWidth()
                .clip(shape)
                .feltTable(shape = shape, geometry = FeltbookGradients.ActiveGame)
                .border(width = 1.dp, color = colors.brassBorder, shape = shape)
                .padding(all = 18.dp),
        verticalArrangement = Arrangement.spacedBy(space = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(size = 8.dp)
                    .background(
                        color = if (active.settling) colors.brass else colors.pay,
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
            Text(
                text =
                    stringResource(
                        if (active.settling) R.string.home_game_settling else R.string.home_game_running,
                    ).uppercase(),
                style = type.overline,
                color = colors.onFeltTableSoft,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(
                text = active.title,
                style = type.display,
                color = colors.onFeltTable,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    stringResource(
                        R.string.home_active_meta,
                        formatDuration(active.elapsed),
                        pluralStringResource(UiR.plurals.players_count, active.playersAtTable, active.playersAtTable),
                        active.bankerName,
                    ),
                style = type.ledger,
                color = colors.onFeltTableSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            ActiveMetric(
                label = stringResource(R.string.home_metric_in_play),
                value = MoneyFormat.number(active.chipsInPlay),
                description = MoneyFormat.number(active.chipsInPlay),
                modifier = Modifier.weight(weight = 1f),
            )
            ActiveMetric(
                label = stringResource(R.string.home_metric_debts),
                value = MoneyFormat.rub(active.playersOwe),
                description = a11yRubles(active.playersOwe),
                owe = active.playersOwe > 0,
                modifier = Modifier.weight(weight = 1f),
            )
            ActiveMetric(
                label = stringResource(R.string.home_metric_rake),
                value = MoneyFormat.rub(active.rakeDue),
                description = a11yRubles(active.rakeDue),
                modifier = Modifier.weight(weight = 1f),
            )
        }
        BrassButton(
            text = stringResource(R.string.home_back_to_table),
            onClick = onBack,
            style = BrassButtonStyle.Flat52,
            trailingIcon = FeltbookIcons.ArrowForward,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActiveMetric(
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
    owe: Boolean = false,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $description" },
        verticalArrangement = Arrangement.spacedBy(space = 2.dp),
    ) {
        Text(
            text = label,
            style = type.tiny,
            color = colors.onFeltTableSoft,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        AmountText(
            text = value,
            style = type.amountMetric,
            color = if (owe) colors.owe else colors.onFeltTable,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
private fun OpenDebtsBlock(debts: OpenDebtsUi, onClick: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(18.dp)
    Column {
        SectionHeader(
            title = stringResource(R.string.home_open_debts),
            trailing = stringResource(R.string.home_open_debts_all),
            onTrailingClick = onClick,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp),
        )
        AmountRowLayout(
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(color = colors.oweBackground)
                    .border(width = 1.dp, color = colors.oweBorder, shape = shape)
                    .clickable(role = Role.Button, onClick = onClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            trailing = {
                AmountText(amount = debts.total, style = type.amountTotal, color = colors.owe)
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                Text(text = stringResource(R.string.home_owed_to_you), style = type.caption, color = colors.oweTint)
                Text(
                    text =
                        stringResource(
                            R.string.home_debts_meta,
                            pluralStringResource(UiR.plurals.players_count, debts.people, debts.people),
                            pluralStringResource(UiR.plurals.games_count, debts.games, debts.games),
                        ),
                    style = type.ledger,
                    color = colors.oweTint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PastGameRow(game: PastGameUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    val date = TimeFormat.localDate(game.startedAt, LocalTimeZone.current)
    val shortDate = formatShortDate(date)
    val meta = buildString {
        append(
            stringResource(
                R.string.home_past_meta,
                game.duration?.let { formatDuration(it) } ?: stringResource(UiR.string.common_dash),
                pluralStringResource(UiR.plurals.players_count, game.playerCount, game.playerCount),
                if (game.rebuyCount == 0) {
                    stringResource(R.string.home_no_rebuys)
                } else {
                    pluralStringResource(UiR.plurals.rebuys_count, game.rebuyCount, game.rebuyCount)
                },
            ),
        )
        if (game.handedOver) append(stringResource(R.string.home_meta_handed_over))
    }
    AmountRowLayout(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.card)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        leading = {
            Column(
                modifier = Modifier.width(width = 44.dp).clearAndSetSemantics { contentDescription = shortDate },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = date.dayOfMonth.toString(), style = type.amountTile, color = colors.ink)
                Text(
                    text = shortDate.substringAfter(MoneyFormat.NBSP),
                    style = type.tiny,
                    color = colors.ledger,
                )
            }
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                AmountText(
                    text = stringResource(R.string.home_income, MoneyFormat.rub(game.bankerIncome)),
                    style = type.amountRow,
                    color = colors.brassLight,
                    contentDescription = a11yRubles(game.bankerIncome),
                )
                AmountText(
                    text = stringResource(R.string.home_rake_short, MoneyFormat.number(game.rake)),
                    style = type.tiny,
                    color = colors.ledger,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(
                text = game.title,
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    text = meta,
                    style = type.ledger,
                    color = colors.ledger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )
                if (game.hasOpenDebt) {
                    Text(
                        text = stringResource(R.string.home_meta_debt),
                        style = type.ledger,
                        color = colors.owe,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

internal val PreviewHomeState =
    HomeUiState(
        loading = false,
        active =
            ActiveGameUi(
                gameId = "a",
                title = "Пятничный стол",
                settling = false,
                elapsed = 134.minutes,
                playersAtTable = 6,
                bankerName = "Андрей",
                chipsInPlay = 8_300L,
                playersOwe = 9_200L,
                rakeDue = 1_250L,
            ),
        openDebts = OpenDebtsUi(total = 1_653L, people = 2, games = 2),
        pastGames =
            listOf(
                PastGameUi(
                    gameId = "p1",
                    title = "Субботний стол",
                    startedAt = Instant.parse("2026-10-25T17:05:00Z"),
                    duration = 245.minutes,
                    playerCount = 8,
                    rebuyCount = 11,
                    bankerIncome = 800L,
                    rake = 2_900L,
                    hasOpenDebt = true,
                    handedOver = false,
                ),
                PastGameUi(
                    gameId = "p2",
                    title = "Пятничный стол",
                    startedAt = Instant.parse("2026-10-18T17:00:00Z"),
                    duration = 220.minutes,
                    playerCount = 3,
                    rebuyCount = 0,
                    bankerIncome = 353L,
                    rake = 500L,
                    hasOpenDebt = false,
                    handedOver = false,
                ),
            ),
    )

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenPreview() {
    FeltbookTheme { HomeScreen(state = PreviewHomeState, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HomeScreenEmptyPreview() {
    FeltbookTheme { HomeScreen(state = HomeUiState(loading = false), onAction = {}) }
}
