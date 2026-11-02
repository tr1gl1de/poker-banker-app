package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus

/** Результат сопоставления входящей игры с локальной (импорт резервной копии и приём передачи). */
sealed interface ImportPlan {
    /** Такой игры нет — вставить целиком. */
    data class Insert(val change: GameChange) : ImportPlan

    /**
     * Локальный журнал — префикс входящего (те же id в том же порядке, допускается равенство), а хвост, строка игры или
     * игроки отличаются: дописать хвост, обновить игру и игроков. Так игра возвращается первому банкиру, а отправитель
     * может открыть свой же файл, если получатель его не принял (QUESTIONS №13).
     */
    data class Extend(val change: GameChange) : ImportPlan

    /** Журнал, акты, строка игры и игроки совпадают — пропустить. */
    data object Identical : ImportPlan

    data class Conflict(val reason: ImportConflictReason) : ImportPlan
}

enum class ImportConflictReason {
    /** Журналы разошлись (не префикс) — локальное не трогаем. */
    DIVERGED_LEDGER,

    /** Стало бы две активные игры. */
    SECOND_ACTIVE_GAME,

    /** Входящий журнал не прошёл [LedgerValidator]. */
    INVALID_LEDGER,
}

object Imports {
    /** Чистое решение «что делать с входящей игрой». Никогда не удаляет и не меняет локальные записи журнала. */
    fun plan(incoming: GameSnapshot, local: GameSnapshot?, activeGameId: GameId?): ImportPlan {
        val game = incoming.game.let {
            if (it.status ==
                GameStatus.CREATED
            ) {
                it.copy(status = GameStatus.RUNNING)
            } else {
                it
            }
        }
        val id = game.id
        require(local == null || local.game.id == id) { "local game ${local?.game?.id} is not $id" }
        return when {
            LedgerValidator.validate(incoming).isNotEmpty() -> ImportPlan.Conflict(ImportConflictReason.INVALID_LEDGER)

            game.status.isActive && activeGameId != null && activeGameId != id ->
                ImportPlan.Conflict(ImportConflictReason.SECOND_ACTIVE_GAME)

            local == null -> ImportPlan.Insert(
                GameChange(id, game, incoming.players, incoming.entries, incoming.handovers),
            )

            !local.entries.isPrefixOf(incoming.entries) || !local.handovers.isPrefixOf(incoming.handovers) ->
                ImportPlan.Conflict(ImportConflictReason.DIVERGED_LEDGER)

            else -> extend(local, incoming.copy(game = game))
        }
    }

    private fun extend(local: GameSnapshot, incoming: GameSnapshot): ImportPlan {
        val players = incoming.players.filter { it != local.player(it.id) }
        val change = GameChange(
            gameId = incoming.game.id,
            game = incoming.game.takeIf { it != local.game },
            players = players,
            entries = incoming.entries.drop(local.entries.size),
            handovers = incoming.handovers.drop(local.handovers.size),
        )
        val unchanged =
            change.game == null && players.isEmpty() && change.entries.isEmpty() && change.handovers.isEmpty()
        return if (unchanged) ImportPlan.Identical else ImportPlan.Extend(change)
    }

    private fun <T> List<T>.isPrefixOf(other: List<T>): Boolean = size <= other.size && this == other.subList(0, size)
}
