package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId

/** Пишет новые записи, продолжая журнал снимка; локальный построитель внутри одной команды. */
internal class EntryWriter(private val snapshot: GameSnapshot, private val stamp: Stamp) {
    private var nextSeq = snapshot.nextSeq
    private val written = mutableListOf<LedgerEntry>()

    val entries: List<LedgerEntry> get() = written.toList()

    fun operation(): OperationId = OperationId(stamp.nextUuid())

    @Suppress("LongParameterList")
    fun add(
        operation: OperationId,
        type: EntryType,
        playerId: PlayerId?,
        amount: Long,
        counterparty: PlayerId? = null,
        bankerName: String = snapshot.game.currentBankerName,
        reverses: EntryId? = null,
        note: String? = null,
        earlyExit: Boolean = false,
    ) {
        written += LedgerEntry(
            id = EntryId(stamp.nextUuid()),
            gameId = snapshot.game.id,
            seq = nextSeq++,
            operationId = operation,
            type = type,
            playerId = playerId,
            amount = amount,
            counterpartyPlayerId = counterparty,
            bankerName = bankerName,
            createdAt = stamp.at,
            reversesEntryId = reverses,
            note = note,
            earlyExit = earlyExit,
        )
    }
}
