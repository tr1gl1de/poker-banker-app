@file:Suppress("MagicNumber", "TooManyFunctions") // Данные превью — суммы примера ТЗ §3.6.

package app.feltbook.feature.game.table

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
import app.feltbook.core.domain.model.apply
import app.feltbook.core.ui.component.AmountEntry
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Данные превью и скриншотов стола — настоящий журнал, собранный командами движка (сквозной пример ТЗ §3.6:
 * банкир Саша не играет, рейк 10 %, сбор 100 ₽). Никаких выдуманных цифр: всё считает движок.
 */
internal object PreviewTableData {
    val start: Instant = Instant.parse("2026-11-02T17:05:00Z")
    val now: Instant = start + 112.minutes

    private var counter = 0L

    private fun stamp(at: Instant): Stamp = Stamp(at) { Uuid.fromLongs(0L, ++counter) }

    fun newSnapshot(change: GameChange): GameSnapshot = GameSnapshot(
        game = checkNotNull(change.game),
        players = change.players,
        entries = change.entries,
        handovers = change.handovers,
    )

    fun started(
        title: String = "Пятничный стол",
        at: Instant = start,
        rules: GameRules = GameRules.DEFAULT,
    ): GameSnapshot = newSnapshot(
        Commands.startGame(
            NewGameInput(
                title = title,
                defaultTitle = title,
                bankerName = "Саша",
                bankerPlays = false,
                rules = rules,
            ),
            activeGame = null,
            stamp = stamp(at),
        ),
    )

    fun GameSnapshot.id(name: String): PlayerId = players.first { it.name == name }.id

    private fun GameSnapshot.add(name: String, chips: Long, payment: BuyInPayment, at: Instant): GameSnapshot =
        apply(Commands.addPlayer(this, name, chips, payment, stamp(at)))

    /** Андрей 1 000 в долг + докуп 1 000, Борис 1 000 оплачено, Влад 2 000 в долг; Борис вышел с 3 753, 53 банкиру. */
    fun running(): GameSnapshot {
        var s = started()
        s = s.add("Андрей", 1_000, BuyInPayment.DEBT, start)
        s = s.add("Борис", 1_000, BuyInPayment.PAID, start + 1.minutes)
        s = s.add("Влад", 2_000, BuyInPayment.DEBT, start + 2.minutes)
        s = s.apply(Commands.buyIn(s, s.id("Андрей"), 1_000, BuyInPayment.DEBT, stamp(start + 50.minutes)))
        s = s.apply(
            Commands.cashOut(
                s,
                s.id("Борис"),
                chips = 3_753,
                tip = Tip(53, TipTarget.Banker),
                settlement = SettlementChoice.Full,
                earlyExitConfirmed = false,
                stamp = stamp(start + 100.minutes),
            ),
        )
        return s
    }

    /** Все вышли (Андрей с 0 и Влад с 1 247 — «Не заплатил»), «Завершить игру». */
    fun settlingSnapshot(): GameSnapshot {
        var s = running()
        s = s.apply(
            Commands.cashOut(s, s.id("Андрей"), 0, null, SettlementChoice.Full, false, stamp(start + 200.minutes)),
        )
        s = s.apply(
            Commands.cashOut(s, s.id("Влад"), 1_247, null, SettlementChoice.None, false, stamp(start + 210.minutes)),
        )
        return s.apply(Commands.finish(s))
    }

    /** Прошлая закрытая игра: Влад ушёл с долгом 1 053 ₽, Егор выиграл 753 (предупреждение при посадке). */
    fun oldGameWithDebt(): GameSnapshot {
        val at = start - 15.days
        var s = started(title = "Пятничный стол", at = at)
        s = s.add("Влад", 2_000, BuyInPayment.DEBT, at)
        s = s.add("Егор", 1_000, BuyInPayment.PAID, at)
        s =
            s.apply(
                Commands.cashOut(s, s.id("Влад"), 1_247, null, SettlementChoice.None, false, stamp(at + 120.minutes)),
            )
        s =
            s.apply(
                Commands.cashOut(s, s.id("Егор"), 1_753, null, SettlementChoice.Full, false, stamp(at + 121.minutes)),
            )
        s = s.apply(Commands.finish(s))
        val required = Settlements.compute(s).requiredConfirmations
        return s.apply(Commands.close(s, payRake = true, acknowledged = required, stamp = stamp(at + 130.minutes)))
    }

    fun table(): TableUiState = buildTableUiState(running(), now, TableInputs())

    /** Планшет: выбран Андрей — его карточка в панели деталей. */
    fun tablet(): TableUiState = running().let { buildTableUiState(it, now, TableInputs(selected = it.id("Андрей"))) }

    fun empty(): TableUiState = buildTableUiState(started(), start + 3.minutes, TableInputs())

    fun settling(): TableUiState = buildTableUiState(settlingSnapshot(), start + 215.minutes, TableInputs())

    fun withSheet(sheet: (GameSnapshot) -> SheetInput, snapshot: GameSnapshot = running()): TableUiState =
        buildTableUiState(
            snapshot,
            now,
            TableInputs(sheet = sheet(snapshot)),
            games = listOf(snapshot, oldGameWithDebt()),
        )

    fun card(name: String = "Андрей"): TableUiState = withSheet({ SheetInput.Card(it.id(name)) })

    fun buyIn(name: String = "Андрей", chips: Long = 2_000): TableUiState =
        withSheet({ SheetInput.BuyIn(it.id(name), AmountEntry.preset(chips)) })

    fun suspiciousBuyIn(): TableUiState = buyIn(chips = 20_000)

    fun newPlayer(name: String = "", chips: Long = 0, amountFocused: Boolean = false): TableUiState =
        withSheet({ SheetInput.NewPlayer(name = name, entry = AmountEntry(chips), amountFocused = amountFocused) })

    /** «Влад» садится за новый стол со старым долгом 1 053 ₽ (макет DebtWarning). */
    fun newPlayerWithDebt(): TableUiState {
        val snapshot = started().add("Андрей", 1_000, BuyInPayment.DEBT, start)
        return buildTableUiState(
            snapshot,
            now,
            TableInputs(sheet = SheetInput.NewPlayer(name = "Влад", entry = AmountEntry(1_000))),
            games = listOf(snapshot, oldGameWithDebt()),
        )
    }

    fun pay(name: String = "Влад"): TableUiState =
        withSheet({ s -> SheetInput.Pay(s.id(name), payout = false, entry = AmountEntry.preset(2_300)) })

    /** Выход Влада с 1 247 фишками — «Влад доплачивает банкиру 1 053 ₽». */
    fun cashOutOwe(focus: CashOutFocus = CashOutFocus.NONE): TableUiState =
        withSheet({ s -> SheetInput.CashOut(s.id("Влад"), chips = AmountEntry(1_247), focus = focus) })

    /** Выход Андрея с 3 753 фишками и чаевыми «до 100» — «получает от банкира». */
    fun cashOutPay(): TableUiState = withSheet({ s ->
        SheetInput.CashOut(
            s.id("Андрей"),
            chips = AmountEntry(5_753),
            focus = CashOutFocus.NONE,
            tip = TipChoice.ROUND_100,
        )
    })

    fun cashOutKeypad(): TableUiState = cashOutOwe(focus = CashOutFocus.CHIPS)

    fun breakdown(kind: BreakdownKind = BreakdownKind.CASH): TableUiState = withSheet({ SheetInput.Breakdown(kind) })
}
