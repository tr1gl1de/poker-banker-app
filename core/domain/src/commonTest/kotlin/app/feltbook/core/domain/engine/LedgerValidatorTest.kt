package app.feltbook.core.domain.engine

import app.feltbook.core.domain.Example81
import app.feltbook.core.domain.T0
import app.feltbook.core.domain.entry
import app.feltbook.core.domain.entryId
import app.feltbook.core.domain.gameId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.model.LedgerEntry
import app.feltbook.core.domain.model.Player
import app.feltbook.core.domain.playerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class LedgerValidatorTest {
    private val base = Example81.snapshot(Example81.full)
    private val a = Example81.andrey
    private val b = Example81.boris

    private fun codes(snapshot: GameSnapshot): List<LedgerProblemCode> = LedgerValidator.validate(snapshot).map {
        it.code
    }

    private fun withEntry(e: LedgerEntry): GameSnapshot = base.copy(entries = base.entries + e)

    private fun next(
        type: EntryType,
        player: app.feltbook.core.domain.model.PlayerId?,
        amount: Long,
        counterparty: app.feltbook.core.domain.model.PlayerId? = null,
    ) = entry(20, type, player, amount, op = 20, counterparty = counterparty)

    @Test
    fun `корректные журналы примеров — без проблем`() {
        assertTrue(LedgerValidator.validate(base).isEmpty())
        assertTrue(LedgerValidator.validate(example81().snapshot).isEmpty())
        val reversed = withEntry(entry(20, EntryType.REVERSAL, a, 0, op = 20, reverses = entryId(18), note = "ошибка"))
        assertTrue(LedgerValidator.validate(reversed).isEmpty())
    }

    @Test
    fun `дубль id, разрыв seq, чужая игра`() {
        val dup = base.copy(entries = base.entries + base.entries.last().copy(seq = 20))
        assertEquals(
            listOf(LedgerProblem(entryId(19), LedgerProblemCode.DUPLICATE_ENTRY_ID)),
            LedgerValidator.validate(dup),
        )
        assertEquals(
            listOf(LedgerProblemCode.SEQ_NOT_CONTIGUOUS),
            codes(withEntry(entry(21, EntryType.PAYOUT, b, 1, op = 21))),
        )
        assertEquals(
            listOf(LedgerProblemCode.WRONG_GAME_ID),
            codes(withEntry(entry(20, EntryType.PAYOUT, b, 1, op = 20, game = gameId(2)))),
        )
        val foreignPlayer = base.copy(players = base.players + Player(playerId(9), gameId(2), "Чужой", T0))
        assertEquals(
            listOf(LedgerProblem(null, LedgerProblemCode.WRONG_GAME_ID)),
            LedgerValidator.validate(foreignPlayer),
        )
        val foreignAct = base.copy(
            handovers = listOf(Handover(HandoverId(Uuid.fromLongs(1, 1)), gameId(2), "А", "Б", 0, 0, 19, T0)),
        )
        assertEquals(listOf(LedgerProblemCode.WRONG_GAME_ID), codes(foreignAct))
    }

    @Test
    fun `игрок и получатель по типам записей`() {
        assertEquals(
            listOf(LedgerProblemCode.UNKNOWN_PLAYER),
            codes(withEntry(next(EntryType.PAYMENT, playerId(99), 1))),
        )
        assertEquals(listOf(LedgerProblemCode.PLAYER_REQUIRED), codes(withEntry(next(EntryType.PAYMENT, null, 1))))
        assertEquals(listOf(LedgerProblemCode.PLAYER_NOT_ALLOWED), codes(withEntry(next(EntryType.RAKE_PAID, a, 1))))
        assertEquals(listOf(LedgerProblemCode.COUNTERPARTY_REQUIRED), codes(withEntry(next(EntryType.TIP_CHIPS, a, 1))))
        assertEquals(
            listOf(LedgerProblemCode.COUNTERPARTY_NOT_ALLOWED),
            codes(withEntry(next(EntryType.PAYOUT, a, 1, b))),
        )
        assertEquals(
            listOf(LedgerProblemCode.UNKNOWN_PLAYER),
            codes(withEntry(next(EntryType.TIP, a, 1, playerId(99)))),
        )
        assertTrue(codes(withEntry(next(EntryType.TIP, a, 1, b))).isEmpty())
        assertEquals(
            listOf(LedgerProblemCode.HANDOVER_INCOME_MISMATCH),
            codes(withEntry(next(EntryType.INCOME_TAKEN, null, 1))),
            "INCOME_TAKEN без акта",
        )
    }

    @Test
    fun `суммы вне диапазона`() {
        assertEquals(listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE), codes(withEntry(next(EntryType.PAYMENT, a, 0))))
        assertEquals(listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE), codes(withEntry(next(EntryType.BUY_IN, a, 499))))
        assertEquals(listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE), codes(withEntry(next(EntryType.CASH_OUT, a, -1))))
        assertEquals(
            listOf(LedgerProblemCode.AMOUNT_OUT_OF_RANGE),
            codes(withEntry(next(EntryType.PAYOUT, a, Money.MAX_TOTAL + 1))),
        )
        assertTrue(codes(withEntry(next(EntryType.CASH_OUT, a, 0))).isEmpty())
    }

    @Test
    fun `отмены — цель, порядок, двойная, отмена отмены, несовпадение`() {
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_TARGET_MISSING),
            codes(withEntry(entry(20, EntryType.REVERSAL, a, 1, op = 20, reverses = entryId(99)))),
        )
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_TARGET_MISSING),
            codes(withEntry(entry(20, EntryType.REVERSAL, a, 1, op = 20))),
        )
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_TARGET_NOT_EARLIER),
            codes(withEntry(entry(20, EntryType.REVERSAL, a, 0, op = 20, reverses = entryId(20)))),
        )
        val first = entry(20, EntryType.REVERSAL, a, 0, op = 20, reverses = entryId(18))
        val twice = base.copy(
            entries =
                base.entries + first + entry(21, EntryType.REVERSAL, a, 0, op = 21, reverses = entryId(18)),
        )
        assertEquals(listOf(LedgerProblemCode.DOUBLE_REVERSAL), codes(twice))
        val ofReversal = base.copy(
            entries =
                base.entries + first + entry(21, EntryType.REVERSAL, a, 0, op = 21, reverses = entryId(20)),
        )
        assertEquals(listOf(LedgerProblemCode.REVERSAL_OF_REVERSAL), codes(ofReversal))
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_MISMATCH),
            codes(withEntry(entry(20, EntryType.REVERSAL, a, 5, op = 20, reverses = entryId(18)))),
        )
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_MISMATCH),
            codes(withEntry(entry(20, EntryType.REVERSAL, b, 0, op = 20, reverses = entryId(18)))),
        )
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_MISMATCH),
            codes(withEntry(entry(20, EntryType.REVERSAL, b, 53, op = 20, reverses = entryId(14), counterparty = a))),
        )
        assertEquals(
            listOf(LedgerProblemCode.REVERSAL_MISMATCH),
            codes(withEntry(entry(20, EntryType.PAYOUT, b, 1, op = 20, reverses = entryId(14)))),
        )
    }

    @Test
    fun `ранний выход и заметка — только где положено, банкир и правила`() {
        assertEquals(
            listOf(LedgerProblemCode.EARLY_EXIT_NOT_ON_CASH_OUT),
            codes(withEntry(entry(20, EntryType.PAYOUT, b, 1, op = 20, earlyExit = true))),
        )
        assertEquals(
            listOf(LedgerProblemCode.NOTE_NOT_ALLOWED),
            codes(withEntry(entry(20, EntryType.PAYOUT, b, 1, op = 20, note = "x"))),
        )
        assertEquals(
            listOf(LedgerProblemCode.BANKER_PLAYER_UNKNOWN),
            codes(base.copy(game = base.game.copy(bankerPlayerId = playerId(99)))),
        )
        assertEquals(
            listOf(LedgerProblemCode.INVALID_RULES),
            codes(base.copy(game = base.game.copy(rules = GameRules(rakeValue = -1)))),
        )
    }
}
