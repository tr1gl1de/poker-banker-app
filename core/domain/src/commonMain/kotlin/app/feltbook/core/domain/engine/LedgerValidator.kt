package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.PlayerId

data class LedgerProblem(val entryId: EntryId?, val code: LedgerProblemCode)

enum class LedgerProblemCode {
    DUPLICATE_ENTRY_ID,
    SEQ_NOT_CONTIGUOUS,
    WRONG_GAME_ID,
    UNKNOWN_PLAYER,
    PLAYER_REQUIRED,
    PLAYER_NOT_ALLOWED,
    COUNTERPARTY_REQUIRED,
    COUNTERPARTY_NOT_ALLOWED,
    AMOUNT_OUT_OF_RANGE,
    REVERSAL_TARGET_MISSING,
    REVERSAL_TARGET_NOT_EARLIER,
    REVERSAL_OF_REVERSAL,
    DOUBLE_REVERSAL,
    REVERSAL_MISMATCH,
    EARLY_EXIT_NOT_ON_CASH_OUT,
    NOTE_NOT_ALLOWED,
    BANKER_PLAYER_UNKNOWN,
    INVALID_RULES,
}

object LedgerValidator {
    /**
     * Структурная проверка снимка по таблице §3.1 (импорт, приём передачи, property-тесты). Пусто — снимок корректен.
     */
    fun validate(snapshot: GameSnapshot): List<LedgerProblem> = buildList {
        val game = snapshot.game
        val playerIds = snapshot.players.map { it.id }.toSet()
        if (game.rules.problems().isNotEmpty()) add(LedgerProblem(null, LedgerProblemCode.INVALID_RULES))
        game.bankerPlayerId?.let {
            if (it !in
                playerIds
            ) {
                add(LedgerProblem(null, LedgerProblemCode.BANKER_PLAYER_UNKNOWN))
            }
        }
        val foreignRows =
            snapshot.players.count { it.gameId != game.id } + snapshot.handovers.count { it.gameId != game.id }
        repeat(foreignRows) { add(LedgerProblem(null, LedgerProblemCode.WRONG_GAME_ID)) }
        val byId = snapshot.entries.groupBy { it.id }
        val reversedTargets = mutableSetOf<EntryId>()
        snapshot.entries.forEachIndexed { index, entry ->
            val duplicate = byId.getValue(entry.id).first() !== entry
            entryProblems(snapshot, entry, index, duplicate, playerIds).forEach { add(LedgerProblem(entry.id, it)) }
            if (entry.type == EntryType.REVERSAL) {
                reversalProblems(entry, byId[entry.reversesEntryId]?.first(), reversedTargets)
                    .forEach { add(LedgerProblem(entry.id, it)) }
            }
        }
    }

    private fun entryProblems(
        snapshot: GameSnapshot,
        entry: LedgerEntry,
        index: Int,
        duplicate: Boolean,
        players: Set<PlayerId>,
    ): List<LedgerProblemCode> = buildList {
        if (duplicate) add(LedgerProblemCode.DUPLICATE_ENTRY_ID)
        if (entry.seq != index + 1L) add(LedgerProblemCode.SEQ_NOT_CONTIGUOUS)
        if (entry.gameId != snapshot.game.id) add(LedgerProblemCode.WRONG_GAME_ID)
        addAll(participantProblems(entry, players))
        if (entry.earlyExit && entry.type != EntryType.CASH_OUT) add(LedgerProblemCode.EARLY_EXIT_NOT_ON_CASH_OUT)
        if (entry.type != EntryType.REVERSAL) {
            if (!amountValid(entry, snapshot.game.rules.minBuyIn)) add(LedgerProblemCode.AMOUNT_OUT_OF_RANGE)
            if (entry.note != null) add(LedgerProblemCode.NOTE_NOT_ALLOWED)
            if (entry.reversesEntryId != null) add(LedgerProblemCode.REVERSAL_MISMATCH)
        }
    }

    private fun participantProblems(entry: LedgerEntry, players: Set<PlayerId>): List<LedgerProblemCode> = buildList {
        val type = entry.type
        val player = entry.playerId
        val counterparty = entry.counterpartyPlayerId
        when {
            type == EntryType.REVERSAL -> Unit
            type in NO_PLAYER && player != null -> add(LedgerProblemCode.PLAYER_NOT_ALLOWED)
            type !in NO_PLAYER && player == null -> add(LedgerProblemCode.PLAYER_REQUIRED)
        }
        when {
            type == EntryType.REVERSAL || type == EntryType.TIP -> Unit
            type == EntryType.TIP_CHIPS && counterparty == null -> add(LedgerProblemCode.COUNTERPARTY_REQUIRED)
            type != EntryType.TIP_CHIPS && counterparty != null -> add(LedgerProblemCode.COUNTERPARTY_NOT_ALLOWED)
        }
        if (listOfNotNull(player, counterparty).any { it !in players }) add(LedgerProblemCode.UNKNOWN_PLAYER)
    }

    private fun reversalProblems(
        entry: LedgerEntry,
        target: LedgerEntry?,
        reversed: MutableSet<EntryId>,
    ): List<LedgerProblemCode> = when {
        target == null -> listOf(LedgerProblemCode.REVERSAL_TARGET_MISSING)

        target.seq >= entry.seq -> listOf(LedgerProblemCode.REVERSAL_TARGET_NOT_EARLIER)

        target.type == EntryType.REVERSAL -> listOf(LedgerProblemCode.REVERSAL_OF_REVERSAL)

        !reversed.add(target.id) -> listOf(LedgerProblemCode.DOUBLE_REVERSAL)

        target.playerId != entry.playerId ||
            target.counterpartyPlayerId != entry.counterpartyPlayerId ||
            target.amount != entry.amount -> listOf(LedgerProblemCode.REVERSAL_MISMATCH)

        else -> emptyList()
    }

    private fun amountValid(entry: LedgerEntry, minBuyIn: Long): Boolean = when (entry.type) {
        EntryType.CASH_OUT -> entry.amount in 0..Money.MAX_AMOUNT
        EntryType.BUY_IN -> entry.amount in minBuyIn..Money.MAX_AMOUNT
        else -> entry.amount in 1..Money.MAX_AMOUNT
    }

    private val NO_PLAYER = setOf(EntryType.RAKE_PAID, EntryType.INCOME_TAKEN)
}
