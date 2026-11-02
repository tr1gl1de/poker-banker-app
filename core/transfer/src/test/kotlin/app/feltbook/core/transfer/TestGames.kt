package app.feltbook.core.transfer

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.PreparedHandover
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Случайные, но корректные игры (через команды движка) для проверок «туда-обратно». */
class TestGames(private val random: Random) {
    private var now = Instant.parse("2026-11-02T17:00:00Z")

    fun stamp(): Stamp {
        now += 1.minutes
        return Stamp(now) { Uuid.fromLongs(random.nextLong(), random.nextLong()) }
    }

    fun rules(): GameRules = GameRules(
        bankerFeeMode = BankerFeeMode.entries.random(random),
        bankerFeeValue = random.nextLong(0, 21),
        rakeMode = RakeMode.entries.random(random),
        rakeValue = random.nextLong(0, 21),
        minBuyIn = 500,
        splitPayment = random.nextBoolean(),
        minPlayMinutes = random.nextInt(0, 120),
    )

    /** Идущая игра: банкир, 1–9 игроков, докупы. */
    fun running(banker: String = "Андрей"): GameSnapshot {
        val start = Commands.startGame(
            NewGameInput(
                title = TITLES.random(random),
                defaultTitle = "Игра",
                bankerName = banker,
                bankerPlays = random.nextBoolean(),
                rules = rules(),
            ),
            activeGame = null,
            stamp = stamp(),
        )
        var snapshot = GameSnapshot(start.game!!, start.players, start.entries, start.handovers)
        val names = NAMES.shuffled(random).take(random.nextInt(1, NAMES.size + 1))
        names.forEach { name ->
            snapshot = snapshot.apply(Commands.addPlayer(snapshot, name, chips(), payment(), stamp()))
        }
        repeat(random.nextInt(0, 25)) {
            val player = snapshot.players.filter { it.name in names }.random(random)
            snapshot = snapshot.apply(Commands.buyIn(snapshot, player.id, chips(), payment(), stamp()))
        }
        return snapshot
    }

    fun prepared(snapshot: GameSnapshot = running(), to: String = "Влад"): PreparedHandover =
        Handovers.prepare(snapshot, to, keepIncome = random.nextBoolean(), stamp = stamp())

    fun handoverPackageText(prepared: PreparedHandover = prepared()): String = FeltbookCodec.encode(
        FeltbookPackage.HandoverPackage(prepared.outgoing, prepared.handover),
        createdAt = now,
    )

    private fun chips(): Long = random.nextLong(5, 51) * 100

    private fun payment(): BuyInPayment = if (random.nextBoolean()) BuyInPayment.PAID else BuyInPayment.DEBT

    private companion object {
        val NAMES = listOf(
            "Дима",
            "Борис",
            "Егор",
            "Саша",
            "Ёжик «Тот самый»",
            "O'Brien",
            "Влад \"Туз\"",
            "Лёха 🃏",
            "Жанна",
        )
        val TITLES = listOf("Пятничный стол", "Дача \\ баня", "Игра №7 — «финал»", "")
    }
}
