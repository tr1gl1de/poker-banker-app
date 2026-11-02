package app.feltbook.core.domain

import app.feltbook.core.domain.model.EntryType.BANKER_FEE
import app.feltbook.core.domain.model.EntryType.BUY_IN
import app.feltbook.core.domain.model.EntryType.CASH_OUT
import app.feltbook.core.domain.model.EntryType.PAYMENT
import app.feltbook.core.domain.model.EntryType.PAYOUT
import app.feltbook.core.domain.model.EntryType.RAKE
import app.feltbook.core.domain.model.EntryType.TIP
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.Player

/** Пример 8.1 контракта (ТЗ §3.6), записанный вручную — эталон для проверки движка без команд. */
object Example81 {
    val andrey = playerId(1)
    val boris = playerId(2)
    val vlad = playerId(3)

    val players = listOf(
        Player(andrey, gameId(1), "Андрей", T0),
        Player(boris, gameId(1), "Борис", T0),
        Player(vlad, gameId(1), "Влад", T0),
    )

    /** seq 1–12: op1–op4, все за столом. */
    val afterOp4: List<LedgerEntry> = listOf(
        entry(1, BUY_IN, andrey, 1_000, op = 1),
        entry(2, RAKE, andrey, 100, op = 1),
        entry(3, BANKER_FEE, andrey, 100, op = 1),
        entry(4, BUY_IN, boris, 1_000, op = 2),
        entry(5, RAKE, boris, 100, op = 2),
        entry(6, BANKER_FEE, boris, 100, op = 2),
        entry(7, PAYMENT, boris, 1_200, op = 2),
        entry(8, BUY_IN, vlad, 2_000, op = 3),
        entry(9, RAKE, vlad, 200, op = 3),
        entry(10, BANKER_FEE, vlad, 100, op = 3),
        entry(11, BUY_IN, andrey, 1_000, op = 4),
        entry(12, RAKE, andrey, 100, op = 4),
    )

    val full: List<LedgerEntry> = afterOp4 + listOf(
        entry(13, CASH_OUT, boris, 3_753, op = 5),
        entry(14, TIP, boris, 53, op = 5),
        entry(15, PAYOUT, boris, 3_700, op = 6),
        entry(16, CASH_OUT, vlad, 1_247, op = 7),
        entry(17, PAYMENT, vlad, 1_053, op = 8),
        entry(18, CASH_OUT, andrey, 0, op = 9),
        entry(19, PAYMENT, andrey, 2_300, op = 10),
    )

    fun snapshot(entries: List<LedgerEntry>): GameSnapshot = GameSnapshot(emptyGame(), players, entries, emptyList())
}
