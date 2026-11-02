package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.PlayerStatus
import kotlin.time.Instant

/**
 * Открытый долг (экраны Debts, DebtWarning, «Открытые долги» на главной).
 * @property owedAtExit баланс при выходе (> 0); «отдал 600 из 1 200» = `owedAtExit − amount` из `owedAtExit`.
 * @property since время выхода (последний CASH_OUT) — «с 18 окт», «15 дней».
 */
data class OpenDebt(
    val canonicalName: String,
    val playerName: String,
    val playerId: PlayerId,
    val gameId: GameId,
    val gameTitle: String,
    val gameCreatedAt: Instant,
    val amount: Long,
    val owedAtExit: Long,
    val since: Instant,
)

object Debts {
    /**
     * Все открытые долги: игроки LEFT_WITH_DEBT во всех играх, кроме HANDED_OVER (их долги ушли новому банкиру).
     * Сортировка — старые сверху.
     */
    fun open(games: List<GameSnapshot>, directory: List<PlayerName>): List<OpenDebt> =
        games.filter { it.game.status != GameStatus.HANDED_OVER }
            .flatMap { snapshot ->
                GameStates.compute(snapshot).players
                    .filter { !it.isVoid && it.status == PlayerStatus.LEFT_WITH_DEBT }
                    .map { state ->
                        OpenDebt(
                            canonicalName = PlayerNames.canonical(state.player.name, directory),
                            playerName = state.player.name,
                            playerId = state.player.id,
                            gameId = snapshot.game.id,
                            gameTitle = snapshot.game.title,
                            gameCreatedAt = snapshot.game.createdAt,
                            amount = state.debt,
                            owedAtExit = maxOf(state.debt, -(state.balanceAtExit ?: 0L)),
                            since = state.lastCashOutAt ?: snapshot.game.createdAt,
                        )
                    }
            }
            .sortedWith(compareBy<OpenDebt> { it.since }.thenBy { it.gameCreatedAt })

    /**
     * Долги человека по каноническому имени, кроме игры `excludeGameId` (предупреждение «Влад должен 1 053 ₽ с 18
     * окт»).
     */
    fun forName(
        name: String,
        games: List<GameSnapshot>,
        directory: List<PlayerName>,
        excludeGameId: GameId?,
    ): List<OpenDebt> {
        val canonical = PlayerNames.canonical(name, directory)
        return open(games, directory).filter { it.canonicalName == canonical && it.gameId != excludeGameId }
    }
}
