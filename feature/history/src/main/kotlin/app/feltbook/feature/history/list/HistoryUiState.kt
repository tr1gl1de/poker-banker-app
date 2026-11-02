package app.feltbook.feature.history.list

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.Stats
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.feature.history.common.DebtLineUi
import app.feltbook.feature.history.common.DeletionAction
import app.feltbook.feature.history.common.PendingDeletionUi
import app.feltbook.feature.history.common.key
import java.time.ZoneId
import kotlin.time.Duration
import kotlin.time.Instant

/** Фильтр истории (m3-ui-spec §5.11): «Все» — без границ, «Месяц» — текущий календарный месяц, «Год» — текущий год. */
enum class HistoryFilter { ALL, MONTH, YEAR }

/** Строка истории — `GameHistoryItem` закрытой или переданной игры. */
@Immutable
data class HistoryRowUi(
    val gameId: String,
    val title: String,
    val startedAt: Instant,
    /** От начала до закрытия/передачи; `null` — время окончания неизвестно. */
    val duration: Duration?,
    val playerCount: Int,
    val rebuyCount: Int,
    val turnover: Long,
    val rake: Long,
    /** Доход банкира за игру (ТЗ §4 «История» п. 1). */
    val bankerIncome: Long,
    val hasOpenDebt: Boolean,
    val editedAfterClose: Boolean,
    val handedOver: Boolean,
)

/** Месяц `startedAt` (заголовок «Октябрь 2026») и его игры, новые сверху. */
@Immutable
data class HistoryMonthUi(val year: Int, val month: Int, val rows: List<HistoryRowUi>)

/**
 * @property selection выбранные игры (режим выбора, пока не пусто).
 * @property deleteDialog открыт диалог «Удалить N игр?» для [selection].
 * @property pendingDeletion токен отложенного удаления и число игр — snackbar «Удалено N игр» / «Отменить».
 */
@Immutable
data class HistoryUiState(
    val loading: Boolean = true,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val months: List<HistoryMonthUi> = emptyList(),
    val selection: Set<String> = emptySet(),
    val deleteDialog: Boolean = false,
    /** Открытые долги выбранных игр — последствие удаления в диалоге. */
    val deleteDebts: List<DebtLineUi> = emptyList(),
    val pendingDeletion: PendingDeletionUi? = null,
) {
    val selecting: Boolean get() = selection.isNotEmpty()

    val isEmpty: Boolean get() = !loading && months.isEmpty()
}

sealed interface HistoryAction {
    data class SetFilter(val filter: HistoryFilter) : HistoryAction

    /** Обычный режим — открыть карточку игры; режим выбора — переключить строку. */
    data class RowClick(val gameId: String) : HistoryAction

    /** Долгое нажатие — режим выбора с этой строкой. */
    data class RowLongClick(val gameId: String) : HistoryAction

    data object ClearSelection : HistoryAction

    /** «Удалить» в шапке выбора — диалог подтверждения. */
    data object DeleteSelected : HistoryAction

    data object ConfirmDelete : HistoryAction

    data object DismissDialog : HistoryAction

    data object OpenStats : HistoryAction

    /** Snackbar удаления: «Отменить», окно отмены, закрытие. */
    data class Deletion(val action: DeletionAction) : HistoryAction
}

sealed interface HistoryEvent {
    data class OpenGame(val gameId: String) : HistoryEvent

    data object OpenStats : HistoryEvent

    data class Message(val text: UiText) : HistoryEvent
}

/** Строки истории: CLOSED и HANDED_OVER без скрытых (отложенно удалённых), по месяцам `startedAt`, новые сверху. */
fun buildHistoryMonths(
    games: List<GameSnapshot>,
    hidden: Set<GameId>,
    filter: HistoryFilter,
    now: Instant,
    zone: ZoneId,
): List<HistoryMonthUi> {
    val today = TimeFormat.localDate(now, zone)
    return games
        .asSequence()
        .filter { it.game.status == GameStatus.CLOSED || it.game.status == GameStatus.HANDED_OVER }
        .filter { it.game.id !in hidden }
        .filter { snapshot ->
            val date = TimeFormat.localDate(snapshot.game.createdAt, zone)
            when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.MONTH -> date.year == today.year && date.monthValue == today.monthValue
                HistoryFilter.YEAR -> date.year == today.year
            }
        }.map { Stats.historyItem(it) }
        .sortedByDescending { it.startedAt }
        .map { item ->
            HistoryRowUi(
                gameId = item.gameId.key(),
                title = item.title,
                startedAt = item.startedAt,
                duration = item.endedAt?.let { it - item.startedAt },
                playerCount = item.playerCount,
                rebuyCount = item.rebuyCount,
                turnover = item.turnover,
                rake = item.rake,
                bankerIncome = item.bankerIncome,
                hasOpenDebt = item.hasOpenDebt,
                editedAfterClose = item.editedAfterClose,
                handedOver = item.status == GameStatus.HANDED_OVER,
            )
        }.groupBy { row ->
            val date = TimeFormat.localDate(row.startedAt, zone)
            date.year to date.monthValue
        }.map { (key, rows) -> HistoryMonthUi(year = key.first, month = key.second, rows = rows) }
}
