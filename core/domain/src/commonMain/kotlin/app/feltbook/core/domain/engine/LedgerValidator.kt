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

    /** Два игрока с одним id. */
    DUPLICATE_PLAYER_ID,

    /** `TIP`/`TIP_CHIPS` самому себе. */
    TIP_TO_SELF,

    /** `Handover.incomeTaken` не равен записи `INCOME_TAKEN` акта (или сумме всех таких записей). */
    HANDOVER_INCOME_MISMATCH,

    /** `Handover.ledgerSeq` вне `0..последний seq`. */
    HANDOVER_SEQ_OUT_OF_RANGE,

    /** `INCOME_TAKEN` отменён — он часть акта передачи и не отменяется. */
    INCOME_TAKEN_REVERSED,
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
        val duplicatePlayers = snapshot.players.size - playerIds.size
        repeat(duplicatePlayers) { add(LedgerProblem(null, LedgerProblemCode.DUPLICATE_PLAYER_ID)) }
        addAll(handoverProblems(snapshot))
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
        val selfTip = entry.counterpartyPlayerId != null && entry.counterpartyPlayerId == entry.playerId
        if (entry.type != EntryType.REVERSAL && selfTip) add(LedgerProblemCode.TIP_TO_SELF)
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

        target.type == EntryType.INCOME_TAKEN -> listOf(LedgerProblemCode.INCOME_TAKEN_REVERSED)

        !reversed.add(target.id) -> listOf(LedgerProblemCode.DOUBLE_REVERSAL)

        target.playerId != entry.playerId ||
            target.counterpartyPlayerId != entry.counterpartyPlayerId ||
            target.amount != entry.amount -> listOf(LedgerProblemCode.REVERSAL_MISMATCH)

        else -> emptyList()
    }

    private fun handoverProblems(snapshot: GameSnapshot): List<LedgerProblem> = buildList {
        val lastSeq = snapshot.entries.lastOrNull()?.seq ?: 0L
        val bySeq = snapshot.entries.associateBy { it.seq }
        snapshot.handovers.forEach { handover ->
            if (handover.ledgerSeq !in 0..lastSeq) add(LedgerProblem(null, LedgerProblemCode.HANDOVER_SEQ_OUT_OF_RANGE))
            val taken = bySeq[handover.ledgerSeq]
            val matches = taken?.type == EntryType.INCOME_TAKEN && taken.amount == handover.incomeTaken
            if (handover.incomeTaken != 0L &&
                !matches
            ) {
                add(LedgerProblem(null, LedgerProblemCode.HANDOVER_INCOME_MISMATCH))
            }
        }
        val takenTotal = snapshot.entries.filter { it.type == EntryType.INCOME_TAKEN }.sumOf { it.amount }
        if (takenTotal != snapshot.handovers.sumOf { it.incomeTaken }) {
            add(LedgerProblem(null, LedgerProblemCode.HANDOVER_INCOME_MISMATCH))
        }
    }

    private fun amountValid(entry: LedgerEntry, minBuyIn: Long): Boolean = when (entry.type) {
        EntryType.CASH_OUT -> entry.amount in 0..Money.MAX_TOTAL
        EntryType.BUY_IN -> entry.amount in minBuyIn..Money.MAX_TOTAL
        else -> entry.amount in 1..Money.MAX_TOTAL
    }

    private val NO_PLAYER = setOf(EntryType.RAKE_PAID, EntryType.INCOME_TAKEN)
}
