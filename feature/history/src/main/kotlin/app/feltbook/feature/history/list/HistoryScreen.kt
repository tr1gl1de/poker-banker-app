package app.feltbook.feature.history.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.DeleteActionChip
import app.feltbook.core.ui.component.DropdownChip
import app.feltbook.core.ui.component.FeltFilterChip
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.LedgerHeader
import app.feltbook.core.ui.component.UNDO_SNACKBAR_MILLIS
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.formatMonthYear
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.DialogAboveBar
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import app.feltbook.feature.history.common.PreviewHistoryData
import java.time.ZoneOffset

/** Запас к окну отмены: удаление не должно обогнать закрытие snackbar. */
private const val UNDO_WINDOW_MARGIN_MILLIS = 500L

/** Маршрут истории: состояние и события [HistoryViewModel], snackbar «Удалено N игр» / «Отменить». */
@Composable
fun HistoryRouteScreen(
    onOpenGame: (gameId: String) -> Unit,
    onOpenStats: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openGame by rememberUpdatedState(onOpenGame)
    val openStats by rememberUpdatedState(onOpenStats)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HistoryEvent.OpenGame -> openGame(event.gameId)
                HistoryEvent.OpenStats -> openStats()
            }
        }
    }
    HistoryScreen(state = state, onAction = viewModel::onAction)
}

/** История (m3-ui-spec §5.11): обычный режим и режим выбора, диалог удаления, snackbar отмены. */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onAction: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.selecting) { onAction(HistoryAction.ClearSelection) }
    DeletionSnackbar(pending = state.pendingDeletion, hostState = snackbarHostState, onAction = onAction)
    Box(modifier = modifier.fillMaxSize().feltBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") {
                if (state.selecting) {
                    SelectionBar(count = state.selection.size, onAction = onAction)
                } else {
                    HistoryHeader(onStats = { onAction(HistoryAction.OpenStats) })
                }
            }
            item(key = "filters") { Filters(filter = state.filter, onAction = onAction) }
            if (state.isEmpty) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = FeltbookTheme.typography.bodyL,
                        color = FeltbookTheme.colors.muted,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                    )
                }
            }
            state.months.forEachIndexed { index, month ->
                item(key = "month-${month.year}-${month.month}") {
                    LedgerHeader(
                        text = formatMonthYear(month.year, month.month),
                        topPadding = if (index == 0) 14.dp else 18.dp,
                    )
                }
                items(items = month.rows, key = { it.gameId }) { row ->
                    HistoryRow(
                        row = row,
                        selecting = state.selecting,
                        selected = row.gameId in state.selection,
                        onClick = { onAction(HistoryAction.RowClick(row.gameId)) },
                        onLongClick = { onAction(HistoryAction.RowLongClick(row.gameId)) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
        FeltbookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
    if (state.deleteDialog) DeleteDialog(count = state.selection.size, onAction = onAction)
}

/** Snackbar отложенного удаления: показывается по токену (в т. ч. после удаления из карточки игры). */
@Composable
private fun DeletionSnackbar(
    pending: PendingDeletionUi?,
    hostState: SnackbarHostState,
    onAction: (HistoryAction) -> Unit,
) {
    val resources = LocalResources.current
    val accessibility = LocalAccessibilityManager.current
    val undoLabel = stringResource(UiR.string.common_undo)
    val action by rememberUpdatedState(onAction)
    LaunchedEffect(pending?.token) {
        val current = pending ?: return@LaunchedEffect
        val window = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = UNDO_SNACKBAR_MILLIS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: UNDO_SNACKBAR_MILLIS
        action(HistoryAction.UndoWindow(current.token, window + UNDO_WINDOW_MARGIN_MILLIS))
        val message = resources.getQuantityString(R.plurals.history_deleted_snackbar, current.count, current.count)
        when (hostState.showUndoSnackbar(message, undoLabel)) {
            SnackbarResult.ActionPerformed -> action(HistoryAction.UndoDelete(current.token))
            SnackbarResult.Dismissed -> action(HistoryAction.SnackbarDismissed(current.token))
        }
    }
}

@Composable
private fun HistoryHeader(onStats: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = FeltbookTheme.typography.displayL,
            color = FeltbookTheme.colors.ink,
            modifier = Modifier.weight(weight = 1f, fill = false).semantics { heading() },
        )
        DropdownChip(text = stringResource(R.string.history_stats), onClick = onStats)
    }
}

/** Шапка режима выбора (макет History): ✕, «Выбрано 2», «Удалить». */
@Composable
private fun SelectionBar(count: Int, onAction: (HistoryAction) -> Unit) {
    val colors = FeltbookTheme.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = colors.card)
                .padding(start = 8.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeltIconButton(
            icon = FeltbookIcons.Close,
            contentDescription = stringResource(R.string.history_clear_selection),
            onClick = { onAction(HistoryAction.ClearSelection) },
            filled = false,
        )
        Text(
            text = stringResource(R.string.history_selected, count),
            style = FeltbookTheme.typography.headlineSans,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )
        DeleteActionChip(
            text = stringResource(R.string.history_delete),
            onClick = { onAction(HistoryAction.DeleteSelected) },
        )
    }
    Box(Modifier.fillMaxWidth().height(height = 1.dp).background(color = colors.brassBorderSoft))
}

@Composable
private fun Filters(filter: HistoryFilter, onAction: (HistoryAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        listOf(
            HistoryFilter.ALL to R.string.history_filter_all,
            HistoryFilter.MONTH to R.string.history_filter_month,
            HistoryFilter.YEAR to R.string.history_filter_year,
        ).forEach { (value, label) ->
            FeltFilterChip(
                text = stringResource(label),
                selected = filter == value,
                onClick = { onAction(HistoryAction.SetFilter(value)) },
                height = FeltbookDimens.historyChipHeight,
                brightSelected = true,
            )
        }
    }
}

/** Строка истории: «Субботний турнир · 25 окт», мета, справа «оборот 29 000» / «рейк 2 900». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun HistoryRow(
    row: HistoryRowUi,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val zone = LocalHistoryTimeZone.current
    val shape = FeltbookTheme.shapes.card
    val selectedText = stringResource(R.string.history_checked)
    val unselectedText = stringResource(R.string.history_unchecked)
    val date = formatShortDate(TimeFormat.localDate(row.startedAt, zone))
    AmountRowLayout(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = if (selected) colors.brassSelectedRow else colors.card)
                .border(
                    width = 1.5.dp,
                    color = if (selected) colors.selectedBorder else Color.Transparent,
                    shape = shape,
                )
                .combinedClickable(
                    role = if (selecting) Role.Checkbox else Role.Button,
                    onLongClickLabel = stringResource(R.string.history_select_a11y),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).semantics {
                    if (selecting) stateDescription = if (selected) selectedText else unselectedText
                }.padding(horizontal = 14.dp, vertical = 12.dp),
        leading = if (selecting) {
            { SelectionBox(selected = selected) }
        } else {
            null
        },
        trailing = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                AmountText(
                    text = stringResource(R.string.history_turnover, MoneyFormat.number(row.turnover)),
                    style = type.labelBold,
                    color = colors.ink,
                )
                AmountText(
                    text = stringResource(R.string.history_rake, MoneyFormat.number(row.rake)),
                    style = type.tiny,
                    color = colors.ledger,
                )
            }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(
                // Название по умолчанию — уже дата («2 ноя, 07:25»): вторую дату не дописываем.
                text = if (row.title.startsWith(date)) {
                    row.title
                } else {
                    stringResource(R.string.history_row_title, row.title, date)
                },
                style = type.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = historyMeta(row),
                style = type.ledger,
                color = colors.ledger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** «4 ч 05 мин · 8 игроков · 11 докупов» + « · долг» (`owe`), « · изменено после закрытия», « · передана». */
@Composable
private fun historyMeta(row: HistoryRowUi) = buildAnnotatedString {
    val separator = stringResource(UiR.string.separator_dot)
    val parts = buildList {
        row.duration?.let { add(formatDuration(it)) }
        add(pluralStringResource(UiR.plurals.players_count, row.playerCount, row.playerCount))
        if (row.rebuyCount > 0) add(pluralStringResource(UiR.plurals.rebuys_count, row.rebuyCount, row.rebuyCount))
    }
    append(parts.joinToString(separator))
    if (row.hasOpenDebt) {
        withStyle(SpanStyle(color = FeltbookTheme.colors.owe)) { append(stringResource(R.string.history_meta_debt)) }
    }
    if (row.editedAfterClose) append(stringResource(R.string.history_meta_edited))
    if (row.handedOver) append(stringResource(R.string.history_meta_handed_over))
}

/** Чекбокс 24 × 24 r7: выбран — латунь с галочкой, нет — рамка 2 `checkboxOffBorder`. */
@Composable
private fun SelectionBox(selected: Boolean) {
    val colors = FeltbookTheme.colors
    val shape = RoundedCornerShape(size = 7.dp)
    Box(
        modifier =
            Modifier
                .size(size = 24.dp)
                .clip(shape)
                .then(
                    if (selected) {
                        Modifier.background(color = colors.brass)
                    } else {
                        Modifier.border(width = 2.dp, color = colors.checkboxOffBorder, shape = shape)
                    },
                ).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = FeltbookIcons.CheckBold,
                contentDescription = null,
                tint = colors.onBrass,
                modifier = Modifier.size(size = 16.dp),
            )
        }
    }
}

@Composable
private fun DeleteDialog(count: Int, onAction: (HistoryAction) -> Unit) {
    FeltbookDialog(
        title = pluralStringResource(R.plurals.history_delete_title, count, count),
        body = stringResource(if (count == 1) R.string.history_delete_body_one else R.string.history_delete_body_many),
        safeLabel = stringResource(R.string.history_delete_keep),
        onSafe = { onAction(HistoryAction.DismissDialog) },
        confirmLabel = pluralStringResource(R.plurals.history_delete_confirm, count, count),
        onConfirm = { onAction(HistoryAction.ConfirmDelete) },
        bottomInset = DialogAboveBar,
    )
}

/** Превью: состояния истории на настоящих журналах [PreviewHistoryData]. */
internal object PreviewHistoryStates {
    fun list(filter: HistoryFilter = HistoryFilter.ALL): HistoryUiState = HistoryUiState(
        loading = false,
        filter = filter,
        months = buildHistoryMonths(
            PreviewHistoryData.games(),
            hidden = emptySet(),
            filter = filter,
            now = PreviewHistoryData.now,
            zone = ZoneOffset.UTC,
        ),
    )

    fun selecting(): HistoryUiState = list().let { state ->
        state.copy(selection = state.months.first().rows.take(2).map { it.gameId }.toSet())
    }

    fun empty(): HistoryUiState = HistoryUiState(loading = false)
}

@Composable
internal fun HistoryPreviewFrame(content: @Composable () -> Unit) {
    FeltbookTheme {
        CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) { content() }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HistoryPreview() {
    HistoryPreviewFrame { HistoryScreen(state = PreviewHistoryStates.list(), onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HistorySelectingPreview() {
    HistoryPreviewFrame { HistoryScreen(state = PreviewHistoryStates.selecting(), onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun HistoryEmptyPreview() {
    HistoryPreviewFrame { HistoryScreen(state = PreviewHistoryStates.empty(), onAction = {}) }
}
