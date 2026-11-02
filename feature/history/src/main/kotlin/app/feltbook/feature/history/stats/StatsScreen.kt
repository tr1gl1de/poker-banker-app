package app.feltbook.feature.history.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.AvatarSize
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.DropdownChip
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.MetricSize
import app.feltbook.core.ui.component.MetricTile
import app.feltbook.core.ui.component.MetricVariant
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.SectionHeader
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.a11ySignedRubles
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import app.feltbook.feature.history.common.PreviewHistoryData
import java.time.ZoneOffset
import kotlinx.coroutines.launch

/** Маршрут статистики: события [StatsViewModel] → навигация и snackbar «Отменить». */
@Composable
fun StatsRouteScreen(onBack: () -> Unit, onOpenDebts: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoLabel = stringResource(UiR.string.common_undo)
    val back by rememberUpdatedState(onBack)
    val debts by rememberUpdatedState(onOpenDebts)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                StatsEvent.Back -> back()

                StatsEvent.OpenDebts -> debts()

                is StatsEvent.Undoable -> scope.launch {
                    val result = snackbar.showUndoSnackbar(event.message.resolve(resources), undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onAction(StatsAction.Undo(event.token))
                }

                is StatsEvent.Message -> scope.launch {
                    snackbar.showUndoSnackbar(event.text.resolve(resources), actionLabel = null)
                }
            }
        }
    }
    StatsScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbar)
}

/** Статистика (m3-ui-spec §5.12): нижняя панель скрыта. */
@Composable
fun StatsScreen(
    state: StatsUiState,
    onAction: (StatsAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Box(modifier = modifier.fillMaxSize().feltBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") { StatsHeader(state = state, onAction = onAction) }
            if (!state.loading) {
                item(key = "totals") { Totals(state = state) }
                if (state.chart.isNotEmpty()) {
                    item(key = "chart") {
                        IncomeChart(
                            bars = state.chart,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                        )
                    }
                }
                item(key = "players-title") {
                    SectionHeader(
                        title = stringResource(R.string.stats_players),
                        trailing = stringResource(R.string.stats_players_hint),
                    )
                }
                if (state.players.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = type.bodyL,
                            color = colors.muted,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
                items(items = state.players, key = { it.name }) { player ->
                    PlayerRow(
                        player = player,
                        onClick = { onAction(StatsAction.PlayerClick(player.name)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 3.dp),
                    )
                }
            }
        }
        FeltbookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
    state.sheet?.let { sheet -> PlayerStatsSheet(sheet = sheet, onAction = onAction) }
    state.mergeDialog?.let { dialog ->
        FeltbookDialog(
            title = stringResource(R.string.stats_merge_title, dialog.from, dialog.into),
            body = stringResource(R.string.stats_merge_body),
            safeLabel = stringResource(UiR.string.common_cancel),
            onSafe = { onAction(StatsAction.DismissDialog) },
            confirmLabel = stringResource(R.string.stats_merge_confirm),
            onConfirm = { onAction(StatsAction.ConfirmMerge) },
            destructive = false,
        )
    }
}

@Composable
private fun StatsHeader(state: StatsUiState, onAction: (StatsAction) -> Unit) {
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
            contentDescription = stringResource(R.string.stats_back),
            onClick = { onAction(StatsAction.Back) },
            filled = false,
        )
        Text(
            text = stringResource(R.string.stats_title),
            style = type.display,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f).semantics { heading() },
        )
        Box {
            DropdownChip(
                text = stringResource(R.string.stats_period_chip, stringResource(state.period.label())),
                onClick = { onAction(StatsAction.PeriodMenu(open = true)) },
            )
            DropdownMenu(
                expanded = state.periodMenu,
                onDismissRequest = { onAction(StatsAction.PeriodMenu(open = false)) },
                containerColor = colors.dialog,
            ) {
                StatsPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(period.label()),
                                style = if (period == state.period) type.labelBold else type.bodyL,
                                color = if (period == state.period) colors.brassBright else colors.ink,
                            )
                        },
                        onClick = { onAction(StatsAction.SetPeriod(period)) },
                    )
                }
            }
        }
    }
}

private fun StatsPeriod.label(): Int = when (this) {
    StatsPeriod.MONTH -> R.string.stats_period_month
    StatsPeriod.THREE_MONTHS -> R.string.stats_period_3_months
    StatsPeriod.YEAR -> R.string.stats_period_year
    StatsPeriod.ALL -> R.string.stats_period_all
}

/** Плитки 2 × 2: «Игр», «Оборот фишек», «♣ Рейк заведению», «♥ Доход банкира». */
@Composable
private fun Totals(state: StatsUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            val games = stringResource(R.string.stats_games)
            MetricTile(
                label = games,
                value = MoneyFormat.number(state.gameCount.toLong()),
                contentDescription = "$games, ${state.gameCount}",
                size = MetricSize.Large,
                modifier = Modifier.weight(weight = 1f),
            )
            val turnover = stringResource(R.string.stats_turnover)
            MetricTile(
                label = turnover,
                value = MoneyFormat.number(state.turnover),
                contentDescription = "$turnover, ${MoneyFormat.number(state.turnover)}",
                size = MetricSize.Large,
                modifier = Modifier.weight(weight = 1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            val rake = stringResource(R.string.stats_rake)
            MetricTile(
                label = rake,
                value = MoneyFormat.rub(state.rake),
                contentDescription = "$rake, ${a11yRubles(state.rake)}",
                size = MetricSize.Large,
                modifier = Modifier.weight(weight = 1f),
            )
            val income = stringResource(R.string.stats_banker_income)
            MetricTile(
                label = income,
                value = MoneyFormat.rub(state.bankerIncome),
                contentDescription = "$income, ${a11yRubles(state.bankerIncome)}",
                variant = MetricVariant.Brass,
                size = MetricSize.Large,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

@Composable
private fun resultColor(result: Long): Color = when {
    result > 0 -> FeltbookTheme.colors.pay
    result < 0 -> FeltbookTheme.colors.owe
    else -> FeltbookTheme.colors.muted
}

/** «11 игр · 1,4 докупа за игру» — среднее из целых счётчиков (`MoneyFormat.ratioOneDecimal`). */
@Composable
private fun playerMeta(games: Int, rebuys: Int): String = stringResource(
    R.string.stats_player_meta,
    pluralStringResource(UiR.plurals.games_count, games, games),
    MoneyFormat.ratioOneDecimal(rebuys.toLong(), games.coerceAtLeast(1).toLong()),
)

@Composable
private fun PlayerRow(player: StatsPlayerRowUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.field
    AmountRowLayout(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.card)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        leading = { PlayerAvatar(name = player.name, suit = player.suit, size = AvatarSize.XS) },
        trailing = {
            AmountText(
                amount = player.totalResult,
                style = type.amountRow,
                color = resultColor(player.totalResult),
                signed = true,
                currency = false,
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 1.dp)) {
            Text(
                text = player.name,
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playerMeta(player.gamesPlayed, player.rebuyCount),
                style = type.ledger,
                color = colors.ledger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Шторка «Игрок» [без макета]: плитки 2 × 2, долг, «Последняя игра», «Объединить с…» / «Разъединить». */
@Composable
private fun PlayerStatsSheet(sheet: PlayerSheetUi, onAction: (StatsAction) -> Unit) {
    FeltbookSheet(onDismiss = { onAction(StatsAction.DismissSheet) }, contentSpacing = 14.dp) {
        PlayerSheetContent(sheet = sheet, onAction = onAction)
    }
}

@Composable
internal fun PlayerSheetContent(sheet: PlayerSheetUi, onAction: (StatsAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(space = 14.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(name = sheet.name, suit = sheet.suit, size = AvatarSize.L)
            Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                Text(
                    text = sheet.name,
                    style = type.display,
                    color = colors.ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                // Список игроков — за выбранный период, шторка — по всем играм (ТЗ §4 п. 4): подпись это говорит.
                Text(text = stringResource(R.string.stats_player_all_time), style = type.ledger, color = colors.ledger)
            }
        }
        if (sheet.picking) {
            MergePicker(sheet = sheet, onAction = onAction)
        } else {
            PlayerSheetDetails(sheet = sheet, onAction = onAction)
        }
    }
}

@Composable
private fun PlayerSheetDetails(sheet: PlayerSheetUi, onAction: (StatsAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val zone = LocalHistoryTimeZone.current
    Column(verticalArrangement = Arrangement.spacedBy(space = 14.dp)) {
        SheetTiles(sheet = sheet)
        if (sheet.openDebt > 0) {
            val debt = stringResource(R.string.stats_player_debt)
            val since = sheet.debtSince?.let {
                stringResource(R.string.stats_player_debt_since, formatShortDate(TimeFormat.localDate(it, zone)))
            }
            MetricTile(
                label = debt,
                value = MoneyFormat.rub(sheet.openDebt),
                contentDescription = listOfNotNull(debt, a11yRubles(sheet.openDebt), since).joinToString(", "),
                variant = MetricVariant.Owe,
                footnote = since,
                modifier = Modifier.fillMaxWidth(),
            )
            BrassButton(
                text = stringResource(R.string.stats_player_open_debts),
                onClick = { onAction(StatsAction.OpenDebts) },
                style = BrassButtonStyle.Flat48,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        sheet.lastPlayedAt?.let {
            Text(
                text = stringResource(
                    R.string.stats_player_last,
                    formatShortDate(TimeFormat.localDate(it, zone)),
                ),
                style = type.ledger,
                color = colors.ledger,
            )
        }
        sheet.aliases.forEach { alias ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.stats_player_alias, alias),
                    style = type.body,
                    color = colors.inkSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f),
                )
                OutlineButton(
                    text = stringResource(R.string.stats_player_unmerge),
                    onClick = { onAction(StatsAction.Unmerge(alias)) },
                    height = FeltbookDimens.button48,
                )
            }
        }
        OutlineButton(
            text = stringResource(R.string.stats_player_merge),
            onClick = { onAction(StatsAction.StartMerge) },
            enabled = sheet.mergeCandidates.isNotEmpty(),
            height = FeltbookDimens.button48,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetTiles(sheet: PlayerSheetUi) {
    val gamesLabel = stringResource(R.string.stats_player_games)
    val resultLabel = stringResource(R.string.stats_player_result)
    val avgLabel = stringResource(R.string.stats_player_avg_buy_in)
    val rebuysLabel = stringResource(R.string.stats_player_rebuys)
    val ratio = MoneyFormat.ratioOneDecimal(sheet.rebuyCount.toLong(), sheet.gamesPlayed.coerceAtLeast(1).toLong())
    Column(verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            MetricTile(
                label = gamesLabel,
                value = MoneyFormat.number(sheet.gamesPlayed.toLong()),
                contentDescription = "$gamesLabel, ${sheet.gamesPlayed}",
                modifier = Modifier.weight(weight = 1f),
            )
            MetricTile(
                label = resultLabel,
                value = MoneyFormat.signedNumber(sheet.totalResult),
                contentDescription = "$resultLabel, ${a11ySignedRubles(sheet.totalResult)}",
                variant = when {
                    sheet.totalResult > 0 -> MetricVariant.Pay
                    sheet.totalResult < 0 -> MetricVariant.Owe
                    else -> MetricVariant.Neutral
                },
                modifier = Modifier.weight(weight = 1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            MetricTile(
                label = avgLabel,
                value = MoneyFormat.number(sheet.averageBuyIn),
                contentDescription = "$avgLabel, ${a11yRubles(sheet.averageBuyIn)}",
                modifier = Modifier.weight(weight = 1f),
            )
            MetricTile(
                label = rebuysLabel,
                value = ratio,
                contentDescription = "$rebuysLabel, $ratio",
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

/** Список имён для «Объединить с…»: нажатие — подтверждение. */
@Composable
private fun MergePicker(sheet: PlayerSheetUi, onAction: (StatsAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Text(
        text = stringResource(R.string.stats_merge_pick_title, sheet.name),
        style = type.sectionTitle,
        color = colors.ink,
    )
    if (sheet.mergeCandidates.isEmpty()) {
        Text(text = stringResource(R.string.stats_merge_none), style = type.caption, color = colors.muted)
    }
    Column {
        sheet.mergeCandidates.forEach { name ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = FeltbookDimens.minTouchTarget)
                        .clickable(role = Role.Button) { onAction(StatsAction.PickMergeTarget(name)) }
                        .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerAvatar(name = name, suit = Suit.forName(name), size = AvatarSize.XS)
                Text(
                    text = name,
                    style = type.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f),
                )
            }
        }
    }
    OutlineButton(
        text = stringResource(UiR.string.common_cancel),
        onClick = { onAction(StatsAction.CancelMerge) },
        icon = FeltbookIcons.Close,
        height = FeltbookDimens.button48,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Превью: статистика за всё время по [PreviewHistoryData]. */
internal object PreviewStatsStates {
    fun stats(period: StatsPeriod = StatsPeriod.ALL): StatsUiState = buildStatsUiState(
        games = PreviewHistoryData.games(),
        directory = PreviewHistoryData.directory(),
        inputs = StatsInputs(period = period),
        now = PreviewHistoryData.now,
        zone = ZoneOffset.UTC,
    )

    fun sheet(name: String = "Борис", picking: Boolean = false): PlayerSheetUi = checkNotNull(
        buildStatsUiState(
            games = PreviewHistoryData.games(),
            directory = PreviewHistoryData.directory(),
            inputs = StatsInputs(period = StatsPeriod.ALL, sheetName = name, picking = picking),
            now = PreviewHistoryData.now,
            zone = ZoneOffset.UTC,
        ).sheet,
    )
}

@Preview(widthDp = 390, heightDp = 1000)
@Composable
private fun StatsPreview() {
    FeltbookTheme {
        CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) {
            StatsScreen(state = PreviewStatsStates.stats(), onAction = {})
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun PlayerSheetPreview() {
    FeltbookTheme {
        CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) {
            Column(
                Modifier
                    .background(color = FeltbookTheme.colors.sheet)
                    .padding(all = 20.dp),
            ) { PlayerSheetContent(sheet = PreviewStatsStates.sheet(), onAction = {}) }
        }
    }
}
