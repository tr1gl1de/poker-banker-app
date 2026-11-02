package app.feltbook.core.domain

import app.feltbook.core.domain.model.EntryType.BANKER_FEE
import app.feltbook.core.domain.model.EntryType.BUY_IN
import app.feltbook.core.domain.model.EntryType.CASH_OUT
import app.feltbook.core.domain.model.EntryType.PAYMENT
import app.feltbook.core.domain.model.EntryType.PAYOUT
import app.feltbook.core.domain.model.EntryType.RAKE
import app.feltbook.core.domain.model.EntryType.TIP
import app.feltbook.core.domain.model.EntryType.TIP_CHIPS
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.Player

/** Пример 8.3 контракта: банкир Андрей играет, Дима, Егор. */
object Example83 {
    val andrey = playerId(1)
    val dima = playerId(2)
    val egor = playerId(3)

    val players = listOf(
        Player(andrey, gameId(1), "Андрей", T0),
        Player(dima, gameId(1), "Дима", T0),
        Player(egor, gameId(1), "Егор", T0),
    )

    val entries = listOf(
        entry(1, BUY_IN, andrey, 1_000, op = 1, banker = "Андрей"),
        entry(2, RAKE, andrey, 100, op = 1, banker = "Андрей"),
        entry(3, PAYMENT, andrey, 1_100, op = 1, banker = "Андрей"),
        entry(4, BUY_IN, dima, 1_000, op = 2, banker = "Андрей"),
        entry(5, RAKE, dima, 100, op = 2, banker = "Андрей"),
        entry(6, BANKER_FEE, dima, 100, op = 2, banker = "Андрей"),
        entry(7, BUY_IN, egor, 1_000, op = 3, banker = "Андрей"),
        entry(8, RAKE, egor, 100, op = 3, banker = "Андрей"),
        entry(9, BANKER_FEE, egor, 100, op = 3, banker = "Андрей"),
        entry(10, PAYMENT, egor, 1_200, op = 3, banker = "Андрей"),
        entry(11, CASH_OUT, egor, 1_500, op = 4, banker = "Андрей"),
        entry(12, TIP_CHIPS, egor, 100, op = 4, counterparty = dima, banker = "Андрей"),
        entry(13, PAYOUT, egor, 1_400, op = 5, banker = "Андрей"),
        entry(14, BUY_IN, egor, 500, op = 6, banker = "Андрей"),
        entry(15, RAKE, egor, 50, op = 6, banker = "Андрей"),
        entry(16, CASH_OUT, dima, 1_300, op = 7, banker = "Андрей"),
        entry(17, TIP, dima, 50, op = 7, banker = "Андрей"),
        entry(18, PAYOUT, dima, 50, op = 8, banker = "Андрей"),
        entry(19, CASH_OUT, egor, 200, op = 9, banker = "Андрей"),
        entry(20, PAYMENT, egor, 100, op = 10, banker = "Андрей"),
        entry(21, CASH_OUT, andrey, 600, op = 11, banker = "Андрей"),
        entry(22, PAYOUT, andrey, 600, op = 12, banker = "Андрей"),
    )

    fun snapshot(entries: List<LedgerEntry>): GameSnapshot =
        GameSnapshot(emptyGame(banker = "Андрей", bankerPlayerId = andrey), players, entries, emptyList())
}
