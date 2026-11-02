@file:Suppress("MagicNumber", "TooManyFunctions") // Данные превью — суммы примера ТЗ §3.6.

package app.feltbook.feature.settlement

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.engine.Tip
import app.feltbook.core.domain.engine.TipTarget
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.apply
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Сквозной пример ТЗ §3.6, собранный командами движка: банкир Саша не играет, рейк 10 %, сбор 100 ₽.
 * Андрей 1 000 + 1 000 в долг, Борис 1 000 оплачено (выход 3 753, 53 банкиру), Влад 2 000 в долг (выход 1 247).
 */
internal object PreviewSettlementData {
    val start: Instant = Instant.parse("2026-10-18T17:00:00Z")
    private var counter = 0L

    private fun stamp(minutes: Int): Stamp = Stamp(start + minutes.minutes) { Uuid.fromLongs(1L, ++counter) }

    private fun GameSnapshot.id(name: String): PlayerId = players.first { it.name == name }.id

    /** До выходов Андрея и Влада. [vladPays] — Влад рассчитался полностью или ушёл с долгом. */
    fun snapshot(allOut: Boolean = true, vladPays: Boolean = true): GameSnapshot {
        val begin = Commands.startGame(
            NewGameInput("Пятничный стол", "Пятничный стол", "Саша", bankerPlays = false, rules = GameRules.DEFAULT),
            activeGame = null,
            stamp = stamp(0),
        )
        var s = GameSnapshot(checkNotNull(begin.game), begin.players, begin.entries, begin.handovers)
        s = s.apply(Commands.addPlayer(s, "Андрей", 1_000, BuyInPayment.DEBT, stamp(1)))
        s = s.apply(Commands.addPlayer(s, "Борис", 1_000, BuyInPayment.PAID, stamp(2)))
        s = s.apply(Commands.addPlayer(s, "Влад", 2_000, BuyInPayment.DEBT, stamp(3)))
        s = s.apply(Commands.buyIn(s, s.id("Андрей"), 1_000, BuyInPayment.DEBT, stamp(60)))
        s = s.apply(
            Commands.cashOut(
                s,
                s.id("Борис"),
                3_753,
                Tip(53, TipTarget.Banker),
                SettlementChoice.Full,
                false,
                stamp(120),
            ),
        )
        if (allOut) {
            s = s.apply(Commands.cashOut(s, s.id("Андрей"), 0, null, SettlementChoice.Full, false, stamp(200)))
            val choice = if (vladPays) SettlementChoice.Full else SettlementChoice.None
            s = s.apply(Commands.cashOut(s, s.id("Влад"), 1_247, null, choice, false, stamp(210)))
        }
        return s.apply(Commands.finish(s))
    }

    private val now: Instant = start + 220.minutes

    fun settled(): SettlementUiState = buildSettlementUiState(snapshot(), now)

    fun withDebt(): SettlementUiState = buildSettlementUiState(snapshot(vladPays = false), now)

    fun stillPlaying(): SettlementUiState = buildSettlementUiState(snapshot(allOut = false), now)
}
