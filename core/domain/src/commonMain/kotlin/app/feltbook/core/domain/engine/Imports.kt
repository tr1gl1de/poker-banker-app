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

    /**
     * Локальная копия новее или равна по журналу, а строки игры/игроков отличаются (старая резервная копия):
     * пропустить, ничего не откатывать.
     */
    LOCAL_NEWER,

    /** Входящий журнал не прошёл [LedgerValidator]. */
    INVALID_LEDGER,
}

object Imports {
    /**
     * Чистое решение «что делать с входящей игрой». Никогда не удаляет и не меняет локальные записи журнала.
     * Без новых записей и актов строки игры и игроков меняются только при возврате своей же переданной игры
     * (локальная HANDED_OVER → входящая активная, QUESTIONS №13); иначе — Identical или `LOCAL_NEWER`.
     */
    fun plan(incoming: GameSnapshot, local: GameSnapshot?, activeGameId: GameId?): ImportPlan {
        val status = incoming.game.status
        val game = if (status == GameStatus.CREATED) incoming.game.copy(status = GameStatus.RUNNING) else incoming.game
        val id = game.id
        require(local == null || local.game.id == id) { "local game ${local?.game?.id} is not $id" }
        return when {
            LedgerValidator.validate(incoming).isNotEmpty() -> ImportPlan.Conflict(ImportConflictReason.INVALID_LEDGER)

            game.status.isActive && activeGameId != null && activeGameId != id ->
                ImportPlan.Conflict(ImportConflictReason.SECOND_ACTIVE_GAME)

            local == null -> ImportPlan.Insert(
                GameChange(id, game, incoming.players, incoming.entries, incoming.handovers),
            )

            local.entries.isPrefixOf(incoming.entries) && local.handovers.isPrefixOf(incoming.handovers) ->
                extend(local, incoming.copy(game = game))

            incoming.entries.isPrefixOf(local.entries) && incoming.handovers.isPrefixOf(local.handovers) ->
                ImportPlan.Conflict(ImportConflictReason.LOCAL_NEWER)

            else -> ImportPlan.Conflict(ImportConflictReason.DIVERGED_LEDGER)
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
        val newRecords = change.entries.isNotEmpty() || change.handovers.isNotEmpty()
        val returnedToSender = local.game.status == GameStatus.HANDED_OVER && incoming.game.status.isActive
        return when {
            newRecords || (returnedToSender && change.game != null) -> ImportPlan.Extend(change)
            change.game == null && players.isEmpty() -> ImportPlan.Identical
            else -> ImportPlan.Conflict(ImportConflictReason.LOCAL_NEWER)
        }
    }

    private fun <T> List<T>.isPrefixOf(other: List<T>): Boolean = size <= other.size && this == other.subList(0, size)
}
