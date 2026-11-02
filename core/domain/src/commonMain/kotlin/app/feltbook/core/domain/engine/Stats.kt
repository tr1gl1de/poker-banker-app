package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.PlayerStatus
import kotlin.time.Instant

/** Строка истории (ТЗ §4 «История» п. 1, макеты History, Home). `turnover` = Σ BUY_IN. */
data class GameHistoryItem(
    val gameId: GameId,
    val title: String,
    val status: GameStatus,
    val startedAt: Instant,
    /** closedAt, время передачи (HANDED_OVER) или null у активной. */
    val endedAt: Instant?,
    val playerCount: Int,
    val rebuyCount: Int,
    val turnover: Long,
    val rake: Long,
    val bankerIncome: Long,
    val hasOpenDebt: Boolean,
    val editedAfterClose: Boolean,
)

/**
 * Статистика по имени за период (ТЗ §4 п. 4). Считаются только игры CLOSED и HANDED_OVER и только вышедшие игроки
 * (QUESTIONS №7). Средние — в UI через `Money.divRoundHalfUp` / форматирование «1,4» из целых счётчиков (без Double).
 * @property totalResult Σ (W − Σ закупов без рейка и сбора).
 * @property totalChipsBought Σ BUY_IN — «средний закуп» = totalChipsBought / gamesPlayed (QUESTIONS №6).
 */
data class PlayerStats(
    val canonicalName: String,
    val gamesPlayed: Int,
    val totalResult: Long,
    val totalChipsBought: Long,
    val buyInCount: Int,
    val rebuyCount: Int,
    val openDebt: Long,
    val lastPlayedAt: Instant?,
) {
    fun averageBuyInPerGame(): Long =
        if (gamesPlayed == 0) 0L else Money.divRoundHalfUp(totalChipsBought, gamesPlayed.toLong())
}

data class GameIncomePoint(val gameId: GameId, val title: String, val startedAt: Instant, val bankerIncome: Long)

/** Итоги периода (макет Stats): «Игр 12 · Оборот фишек 214 500 · Рейк 21 450 · Доход банкира 7 100». */
data class PeriodStats(
    val gameCount: Int,
    val turnover: Long,
    val rake: Long,
    val bankerIncome: Long,
    /** По возрастанию времени — для графика «Доход банкира, последние N игр». */
    val incomeByGame: List<GameIncomePoint>,
    /** По убыванию totalResult. */
    val players: List<PlayerStats>,
)

object Stats {
    fun historyItem(snapshot: GameSnapshot): GameHistoryItem {
        val state = GameStates.compute(snapshot)
        val game = snapshot.game
        val endedAt = game.closedAt
            ?: if (game.status == GameStatus.HANDED_OVER) snapshot.handovers.lastOrNull()?.at else null
        return GameHistoryItem(
            gameId = game.id,
            title = game.title,
            status = game.status,
            startedAt = game.createdAt,
            endedAt = endedAt,
            playerCount = state.players.count { !it.isVoid },
            rebuyCount = state.table.rebuyCount,
            turnover = state.table.chipsIssued,
            rake = state.table.rakeAccrued,
            bankerIncome = state.table.bankerIncome,
            hasOpenDebt = state.hasOpenDebt,
            editedAfterClose = game.editedAfterClose,
        )
    }

    /** Игры CLOSED/HANDED_OVER с `createdAt ∈ [from, to)`; null — без границы. */
    fun period(games: List<GameSnapshot>, directory: List<PlayerName>, from: Instant?, to: Instant?): PeriodStats {
        val included = finished(games).filter { snapshot ->
            val at = snapshot.game.createdAt
            (from == null || at >= from) && (to == null || at < to)
        }.sortedBy { it.game.createdAt }
        val items = included.map { historyItem(it) }
        val names = included.flatMap { exited(it) }.map { PlayerNames.canonical(it.player.name, directory) }.distinct()
        return PeriodStats(
            gameCount = included.size,
            turnover = items.sumOf { it.turnover },
            rake = items.sumOf { it.rake },
            bankerIncome = items.sumOf { it.bankerIncome },
            incomeByGame = items.map { GameIncomePoint(it.gameId, it.title, it.startedAt, it.bankerIncome) },
            players = names.mapNotNull { playerStats(it, included, games, directory) }
                .sortedWith(compareByDescending<PlayerStats> { it.totalResult }.thenBy { it.canonicalName }),
        )
    }

    fun player(canonicalName: String, games: List<GameSnapshot>, directory: List<PlayerName>): PlayerStats? =
        playerStats(PlayerNames.canonical(canonicalName, directory), finished(games), games, directory)

    private fun playerStats(
        canonicalName: String,
        included: List<GameSnapshot>,
        allGames: List<GameSnapshot>,
        directory: List<PlayerName>,
    ): PlayerStats? {
        val played = included.mapNotNull { snapshot ->
            exited(snapshot)
                .filter { PlayerNames.canonical(it.player.name, directory) == canonicalName }
                .takeIf { it.isNotEmpty() }
                ?.let { snapshot to it }
        }
        if (played.isEmpty()) return null
        val states = played.flatMap { it.second }
        return PlayerStats(
            canonicalName = canonicalName,
            gamesPlayed = played.size,
            totalResult = states.sumOf { it.result ?: 0L },
            totalChipsBought = states.sumOf { it.chipsBought },
            buyInCount = states.sumOf { it.buyInCount },
            rebuyCount = states.sumOf { it.rebuyCount },
            openDebt = Debts.forName(canonicalName, allGames, directory, excludeGameId = null).sumOf { it.amount },
            lastPlayedAt = played.maxOf { it.first.game.createdAt },
        )
    }

    private fun finished(games: List<GameSnapshot>): List<GameSnapshot> =
        games.filter { it.game.status == GameStatus.CLOSED || it.game.status == GameStatus.HANDED_OVER }

    private fun exited(snapshot: GameSnapshot): List<PlayerState> =
        GameStates.compute(snapshot).players.filter { !it.isVoid && it.status != PlayerStatus.PLAYING }
}
