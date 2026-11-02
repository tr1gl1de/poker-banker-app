// Данные превью: суммы и время — пример сквозной игры, именованные константы ничего бы не прояснили.
@file:Suppress("MagicNumber", "TooManyFunctions")

package app.feltbook.feature.history.common

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.engine.Tip
import app.feltbook.core.domain.engine.TipTarget
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.model.apply
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Данные превью, скриншотов и тестов истории — настоящие журналы, собранные командами движка (банкир Саша
 * не играет, правила по умолчанию: рейк 10 %, сбор 100 ₽ разово). Цифры считает движок, не экран.
 */
internal object PreviewHistoryData {
    val now: Instant = Instant.parse("2026-11-02T17:05:00Z")

    private var counter = 0L

    private fun stamp(at: Instant): Stamp = Stamp(at) { Uuid.fromLongs(0L, ++counter) }

    private fun newSnapshot(change: GameChange): GameSnapshot = GameSnapshot(
        game = checkNotNull(change.game),
        players = change.players,
        entries = change.entries,
        handovers = change.handovers,
    )

    fun started(title: String, at: Instant, rules: GameRules = GameRules.DEFAULT): GameSnapshot = newSnapshot(
        Commands.startGame(
            NewGameInput(title = title, defaultTitle = title, bankerName = "Саша", bankerPlays = false, rules = rules),
            activeGame = null,
            stamp = stamp(at),
        ),
    )

    fun GameSnapshot.id(name: String): PlayerId = players.first { it.name == name }.id

    fun GameSnapshot.add(name: String, chips: Long, payment: BuyInPayment, at: Instant): GameSnapshot =
        apply(Commands.addPlayer(this, name, chips, payment, stamp(at)))

    fun GameSnapshot.exit(
        name: String,
        chips: Long,
        at: Instant,
        settlement: SettlementChoice = SettlementChoice.Full,
        tip: Tip? = null,
    ): GameSnapshot = apply(Commands.cashOut(this, id(name), chips, tip, settlement, false, stamp(at)))

    fun GameSnapshot.closeAt(at: Instant): GameSnapshot {
        val settling = apply(Commands.finish(this))
        val required = Settlements.compute(settling).requiredConfirmations
        return settling.apply(Commands.close(settling, payRake = true, acknowledged = required, stamp = stamp(at)))
    }

    /** 18 окт: Влад ушёл с долгом 1 053 ₽, Егор выиграл 753. */
    fun fridayWithDebt(): GameSnapshot {
        val at = now - 15.days
        return started("Пятничный стол", at)
            .add("Влад", 2_000, BuyInPayment.DEBT, at)
            .add("Егор", 1_000, BuyInPayment.PAID, at)
            .exit("Влад", 1_247, at + 120.minutes, SettlementChoice.None)
            .exit("Егор", 1_753, at + 121.minutes)
            .closeAt(at + 130.minutes)
    }

    /** 25 окт: три игрока, докуп Андрея, чаевые банкиру; все рассчитались. */
    fun saturdayTournament(): GameSnapshot {
        val at = now - 8.days
        var s = started("Субботний турнир", at)
            .add("Андрей", 1_000, BuyInPayment.PAID, at)
            .add("Борис", 1_000, BuyInPayment.PAID, at + 1.minutes)
            .add("Дима", 2_000, BuyInPayment.PAID, at + 2.minutes)
        s = s.apply(Commands.buyIn(s, s.id("Андрей"), 1_000, BuyInPayment.PAID, stamp(at + 60.minutes)))
        return s
            .exit("Андрей", 0, at + 150.minutes)
            .exit("Борис", 2_500, at + 200.minutes, tip = Tip(100, TipTarget.Banker))
            .exit("Дима", 2_500, at + 240.minutes)
            .closeAt(at + 245.minutes)
    }

    /** 30 окт: Борис отдал часть при выходе и позже погасил ещё 600 — «отдал 800 из 1 400». */
    fun thursdayPartial(): GameSnapshot {
        val at = now - 3.days
        var s = started("Четверг", at)
            .add("Борис", 2_000, BuyInPayment.DEBT, at)
            .add("Егор", 1_000, BuyInPayment.PAID, at)
            .exit("Борис", 900, at + 90.minutes, SettlementChoice.Partial(200))
            .exit("Егор", 2_100, at + 95.minutes)
            .closeAt(at + 100.minutes)
        s = s.apply(Commands.pay(s, s.id("Борис"), 600, stamp(at + 1.days)))
        return s
    }

    /** 23 сен: прошлый месяц. */
    fun homeSeptember(): GameSnapshot {
        val at = now - 40.days
        return started("Домашний стол", at)
            .add("Андрей", 1_000, BuyInPayment.PAID, at)
            .add("Егор", 1_000, BuyInPayment.PAID, at)
            .exit("Андрей", 1_500, at + 180.minutes)
            .exit("Егор", 500, at + 181.minutes)
            .closeAt(at + 185.minutes)
    }

    /** Все игры, новые сверху (как `observeAllGames`). */
    fun games(): List<GameSnapshot> = listOf(thursdayPartial(), saturdayTournament(), fridayWithDebt(), homeSeptember())

    /** Справочник: «Боря» ещё не объединён с «Борис». */
    fun directory(): List<PlayerName> = listOf("Андрей", "Борис", "Боря", "Влад", "Дима", "Егор").map {
        PlayerName(it, now - 60.days)
    }
}
