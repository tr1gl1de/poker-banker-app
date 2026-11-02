package app.feltbook.core.domain

import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.GameState
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.apply

/** Стол в тестах: снимок + часы, команды применяются через `GameSnapshot.apply`. */
class TestTable(
    rules: GameRules = GameRules.DEFAULT,
    banker: String = "Олег",
    bankerPlays: Boolean = false,
    val clock: TestClock = TestClock(),
) {
    var snapshot: GameSnapshot
        private set

    init {
        val change = Commands.startGame(
            NewGameInput("Пятница", "18 окт", banker, bankerPlays, rules),
            null,
            clock.stamp(),
        )
        snapshot = GameSnapshot(change.game!!, change.players, emptyList(), emptyList())
    }

    val state: GameState get() = GameStates.compute(snapshot)

    fun apply(change: GameChange): GameChange {
        snapshot = snapshot.apply(change)
        return change
    }

    fun id(name: String): PlayerId = snapshot.players.single { it.name == name }.id

    fun add(name: String, chips: Long, payment: BuyInPayment = BuyInPayment.DEBT): PlayerId {
        apply(Commands.addPlayer(snapshot, name, chips, payment, clock.stamp()))
        return id(name)
    }

    fun buy(name: String, chips: Long, payment: BuyInPayment = BuyInPayment.DEBT): GameChange =
        apply(Commands.buyIn(snapshot, id(name), chips, payment, clock.stamp()))

    fun pay(name: String, amount: Long): GameChange = apply(Commands.pay(snapshot, id(name), amount, clock.stamp()))

    fun payOut(name: String, amount: Long): GameChange =
        apply(Commands.payOut(snapshot, id(name), amount, clock.stamp()))
}
