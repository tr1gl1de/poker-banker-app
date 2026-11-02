package app.feltbook.feature.history.debts

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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.AvatarSize
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.a11yDebt
import app.feltbook.core.ui.format.a11yOwesBanker
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.DeletionUndoSnackbar
import app.feltbook.feature.history.common.DialogAboveBar
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import app.feltbook.feature.history.common.PreviewHistoryData
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.time.Instant
import kotlinx.coroutines.launch

/** Маршрут «Открытые долги»: события [DebtsViewModel] → навигация и snackbar «Отменить». */
@Composable
fun DebtsRouteScreen(
    onBack: () -> Unit,
    onOpenGame: (gameId: String) -> Unit,
    onOpenTable: (gameId: String) -> Unit,
    viewModel: DebtsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoLabel = stringResource(UiR.string.common_undo)
    val back by rememberUpdatedState(onBack)
    val openGame by rememberUpdatedState(onOpenGame)
    val openTable by rememberUpdatedState(onOpenTable)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                DebtsEvent.Back -> back()

                is DebtsEvent.OpenGame -> openGame(event.gameId)

                is DebtsEvent.OpenTable -> openTable(event.gameId)

                is DebtsEvent.Undoable -> scope.launch {
                    val result = snackbar.showUndoSnackbar(event.message.resolve(resources), undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onAction(DebtsAction.Undo(event.token))
                }

                is DebtsEvent.Message -> scope.launch {
                    snackbar.showUndoSnackbar(event.text.resolve(resources), actionLabel = null)
                }
            }
        }
    }
    DebtsScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbar)
}

/** Открытые долги (m3-ui-spec §5.9): сводка «Вам должны», карточки, пояснение; нижняя панель видна. */
@Composable
fun DebtsScreen(
    state: DebtsUiState,
    onAction: (DebtsAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    DeletionUndoSnackbar(
        pending = state.pendingDeletion,
        hostState = snackbarHostState,
        onAction = { onAction(DebtsAction.Deletion(it)) },
    )
    Box(modifier = modifier.fillMaxSize().feltBackground()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "header") { DebtsHeader(onBack = { onAction(DebtsAction.Back) }) }
            if (state.isEmpty) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.debts_empty),
                        style = type.bodyL,
                        color = colors.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 32.dp),
                    )
                }
            } else if (!state.loading) {
                item(key = "summary") { Summary(state = state) }
                items(items = state.cards, key = { it.key }) { card ->
                    DebtCard(
                        card = card,
                        now = state.now,
                        onAction = onAction,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    )
                }
                item(key = "footer") {
                    Text(
                        text = stringResource(R.string.debts_footer),
                        style = type.caption,
                        color = colors.ledger,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    )
                }
            }
        }
        FeltbookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
    state.paySheet?.let { sheet -> DebtPaySheet(sheet = sheet, busy = state.busy, onAction = onAction) }
    state.forgiveDialog?.let { card ->
        val amount = MoneyFormat.rub(card.amount)
        FeltbookDialog(
            title = stringResource(R.string.debts_forgive_title, amount),
            body = stringResource(R.string.debts_forgive_body, card.gameTitle, amount),
            safeLabel = stringResource(R.string.debts_forgive_keep),
            onSafe = { onAction(DebtsAction.DismissDialog) },
            confirmLabel = stringResource(R.string.debts_forgive_confirm, amount),
            onConfirm = { onAction(DebtsAction.ConfirmForgive) },
            bottomInset = DialogAboveBar,
        )
    }
}

@Composable
private fun DebtsHeader(onBack: () -> Unit) {
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
            onClick = onBack,
            filled = false,
        )
        Text(
            text = stringResource(R.string.debts_title),
            style = FeltbookTheme.typography.display,
            color = FeltbookTheme.colors.ink,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** «Вам должны · 2 игрока · 2 игры» | «1 653 ₽». */
@Composable
private fun Summary(state: DebtsUiState) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(size = 18.dp)
    val label = stringResource(R.string.debts_owed_to_you)
    AmountRowLayout(
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.oweBackground)
                .border(width = 1.dp, color = colors.oweBorder, shape = shape)
                .semantics(mergeDescendants = true) {}
                .padding(horizontal = 16.dp, vertical = 14.dp),
        trailing = {
            AmountText(
                amount = state.total,
                style = type.amountTotal,
                color = colors.owe,
                contentDescription = "$label, ${a11yRubles(state.total)}",
            )
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(text = label, style = type.caption, color = colors.oweTint)
            Text(
                text = stringResource(
                    R.string.debts_summary_meta,
                    pluralStringResource(UiR.plurals.players_count, state.people, state.people),
                    pluralStringResource(UiR.plurals.games_count, state.games, state.games),
                ),
                style = type.ledger,
                color = colors.oweTint,
            )
        }
    }
}

/** «Пятничный стол · 18 окт · 15 дней» или « · отдал 600 из 1 200». */
@Composable
internal fun debtMeta(card: DebtCardUi, now: Instant): String {
    val zone = LocalHistoryTimeZone.current
    val date = formatShortDate(TimeFormat.localDate(card.gameCreatedAt, zone))
    val last = if (card.paidPart > 0) {
        stringResource(R.string.debts_partial, MoneyFormat.number(card.paidPart), MoneyFormat.number(card.owedAtExit))
    } else {
        val days = ChronoUnit.DAYS.between(TimeFormat.localDate(card.since, zone), TimeFormat.localDate(now, zone))
            .coerceAtLeast(0L)
            .toInt()
        if (days == 0) {
            stringResource(R.string.debts_today)
        } else {
            pluralStringResource(UiR.plurals.days_count, days, days)
        }
    }
    // Название по умолчанию — уже дата («2 ноя, 07:25»): вторую дату не дописываем.
    return if (card.gameTitle.startsWith(date)) {
        stringResource(R.string.debts_summary_meta, card.gameTitle, last)
    } else {
        stringResource(R.string.debts_meta, card.gameTitle, date, last)
    }
}

@Composable
private fun DebtCard(card: DebtCardUi, now: Instant, onAction: (DebtsAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(size = 18.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.card)
                .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        AmountRowLayout(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.debts_open_game_a11y),
                    ) { onAction(DebtsAction.OpenGame(card.gameId)) },
            leading = { PlayerAvatar(name = card.playerName, suit = card.suit, size = AvatarSize.M) },
            trailing = {
                AmountText(
                    amount = card.amount,
                    style = type.amountMetric,
                    color = colors.owe,
                    contentDescription = a11yOwesBanker(card.playerName, card.amount),
                )
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
                Text(
                    text = card.playerName,
                    style = type.titleM,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = debtMeta(card, now),
                    style = type.ledger,
                    color = colors.ledger,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            BrassButton(
                text = stringResource(R.string.debts_pay),
                onClick = { onAction(DebtsAction.Pay(card.key)) },
                style = BrassButtonStyle.Flat48,
                contentDescription = stringResource(R.string.debts_pay_a11y, card.playerName, a11yDebt(card.amount)),
                modifier = Modifier.weight(weight = 2f),
            )
            OutlineButton(
                text = stringResource(R.string.debts_forgive),
                onClick = { onAction(DebtsAction.Forgive(card.key)) },
                height = FeltbookDimens.button48,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}

/** Превью: долги [PreviewHistoryData] (Влад — 1 053 ₽, Борис — 600 из 1 400). */
internal object PreviewDebtsStates {
    fun debts(): DebtsUiState = buildDebtsUiState(
        debts = Debts.open(PreviewHistoryData.games(), PreviewHistoryData.directory()),
        now = PreviewHistoryData.now,
        paySheet = null,
        forgiveKey = null,
        busy = false,
    )

    fun empty(): DebtsUiState = DebtsUiState(loading = false)
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DebtsPreview() {
    FeltbookTheme {
        CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) {
            DebtsScreen(state = PreviewDebtsStates.debts(), onAction = {})
        }
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun DebtsEmptyPreview() {
    FeltbookTheme { DebtsScreen(state = PreviewDebtsStates.empty(), onAction = {}) }
}
