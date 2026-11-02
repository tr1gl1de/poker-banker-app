package app.feltbook.feature.history.detail

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.GameSettlement
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.engine.Stats
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.format.UiText
import app.feltbook.feature.history.common.key
import kotlin.time.Duration
import kotlin.time.Instant

/** Строка «Расчёт с игроками» только для чтения (`SettlementRow`). */
@Immutable
data class DetailPlayerUi(
    val playerId: String,
    val name: String,
    val suit: Suit,
    val isBanker: Boolean,
    val charged: Long,
    val paid: Long,
    val withdrawal: Long,
    val tipsGiven: Long,
    /** Расчёт при выходе: < 0 — платил, > 0 — получал; `null` — не выходил. */
    val balanceAtExit: Long?,
    /** Остаток сейчас: < 0 — долг, > 0 — банкир должен, 0 — рассчитан. */
    val outstanding: Long,
)

/** Диалоги карточки игры. */
sealed interface DetailDialog {
    data object Reopen : DetailDialog

    data object Delete : DetailDialog

    /** «Отменить «Погашение 600»?» с полем причины. */
    data class Reverse(val operationId: String, val title: UiText, val reason: String = "") : DetailDialog
}

/**
 * Карточка игры только для чтения (m3-ui-spec §5.11 «GameDetail»): шапка, блоки итога, расчёт с игроками и
 * полный журнал. [missing] — игры больше нет (удалена) — экран уходит назад.
 */
@Immutable
data class GameDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val gameId: String = "",
    val title: String = "",
    val status: GameStatus = GameStatus.CLOSED,
    val startedAt: Instant = Instant.fromEpochMilliseconds(0),
    val duration: Duration? = null,
    val playerCount: Int = 0,
    val rebuyCount: Int = 0,
    val editedAfterClose: Boolean = false,
    val hasOpenDebt: Boolean = false,
    val settlement: GameSettlement? = null,
    val players: List<DetailPlayerUi> = emptyList(),
    val journal: List<JournalRowUi> = emptyList(),
    val now: Instant = Instant.fromEpochMilliseconds(0),
    val dialog: DetailDialog? = null,
    val busy: Boolean = false,
) {
    /** «Переоткрыть для исправления» — только закрытая игра. */
    val canReopen: Boolean get() = status == GameStatus.CLOSED

    /** Активную игру удаляют со стола, не из истории. */
    val canDelete: Boolean get() = !status.isActive

    val handedOver: Boolean get() = status == GameStatus.HANDED_OVER
}

sealed interface GameDetailAction {
    data object Back : GameDetailAction

    data object Share : GameDetailAction

    data object Reopen : GameDetailAction

    data object ConfirmReopen : GameDetailAction

    data object Delete : GameDetailAction

    data object ConfirmDelete : GameDetailAction

    data object DismissDialog : GameDetailAction

    /** Удержание строки журнала 0,6 с. */
    data class JournalHold(val operationId: String) : GameDetailAction

    data class ReverseReason(val value: String) : GameDetailAction

    data object ConfirmReverse : GameDetailAction
}

sealed interface GameDetailEvent {
    data object Back : GameDetailEvent

    /** Игра переоткрыта — на стол. */
    data class OpenTable(val gameId: String) : GameDetailEvent

    /** Удаление отложено — назад к истории (там snackbar «Отменить»). */
    data object Deleted : GameDetailEvent

    data object Share : GameDetailEvent

    data class Message(val text: UiText) : GameDetailEvent
}

/** Состояние карточки по снимку: всё — из `Stats.historyItem`, `Settlements.compute`, `Journal.items`. */
fun buildGameDetailUiState(
    snapshot: GameSnapshot,
    now: Instant,
    dialog: DetailDialog?,
    busy: Boolean,
): GameDetailUiState {
    val item = Stats.historyItem(snapshot)
    val settlement = Settlements.compute(snapshot)
    val seats = snapshot.players.mapIndexed { index, player -> player.id to Suit.forSeat(index) }.toMap()
    return GameDetailUiState(
        loading = false,
        gameId = snapshot.game.id.key(),
        title = snapshot.game.title,
        status = snapshot.game.status,
        startedAt = item.startedAt,
        duration = item.endedAt?.let { it - item.startedAt },
        playerCount = item.playerCount,
        rebuyCount = item.rebuyCount,
        editedAfterClose = item.editedAfterClose,
        hasOpenDebt = item.hasOpenDebt,
        settlement = settlement,
        players = settlement.rows.map { row ->
            DetailPlayerUi(
                playerId = row.playerId.key(),
                name = row.name,
                suit = seats[row.playerId] ?: Suit.forName(row.name),
                isBanker = row.isBanker,
                charged = row.charged,
                paid = row.paidBeforeExit,
                withdrawal = row.withdrawal,
                tipsGiven = row.tipsGiven,
                balanceAtExit = row.balanceAtExit,
                outstanding = row.outstanding,
            )
        },
        journal = JournalRows.of(snapshot),
        now = now,
        dialog = dialog,
        busy = busy,
    )
}
