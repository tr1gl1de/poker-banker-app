package app.feltbook.core.odds

import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last

/**
 * Ввод калькулятора: свои [hole] (2 карты), [board] (0, 3, 4 или 5 карт), число игроков за
 * столом [players] (2–10, включая себя) и известные мёртвые карты [dead]. Соперники — случайные руки.
 */
data class EquityRequest(
    val hole: List<Card>,
    val board: List<Card> = emptyList(),
    val players: Int = MIN_PLAYERS,
    val dead: Set<Card> = emptySet(),
) {
    init {
        require(hole.size == HOLE_CARDS) { "Своих карт должно быть 2, передано ${hole.size}" }
        require(board.size in BOARD_SIZES) { "На столе 0, 3, 4 или 5 карт, передано ${board.size}" }
        require(players in MIN_PLAYERS..MAX_PLAYERS) { "Игроков 2–10, передано $players" }
        val known = hole + board + dead
        require(known.toSet().size == known.size) { "Карты повторяются: $known" }
        require(unseen >= missingBoard + HOLE_CARDS * opponents) { "Не хватает карт в колоде на раздачу" }
    }

    /** Соперников. */
    val opponents: Int get() = players - 1

    /** Сколько карт стола ещё откроется. */
    val missingBoard: Int get() = BOARD_CARDS - board.size

    /** Неизвестных карт (колода без своих, стола и мёртвых). */
    val unseen: Int get() = Deck.SIZE - HOLE_CARDS - board.size - dead.size

    companion object {
        const val MIN_PLAYERS = 2
        const val MAX_PLAYERS = 10
        const val HOLE_CARDS = 2
        const val BOARD_CARDS = 5
        val BOARD_SIZES: Set<Int> = setOf(0, 3, 4, 5)
    }
}

/** Настройки расчёта. [seed] = `null` — случайное зерно; в тестах — фиксированное. */
data class EquitySettings(
    val samples: Int = DEFAULT_SAMPLES,
    val batchSize: Int = DEFAULT_BATCH,
    val exactLimit: Long = DEFAULT_EXACT_LIMIT,
    val seed: Long? = null,
) {
    init {
        require(samples > 0) { "samples > 0" }
        require(batchSize > 0) { "batchSize > 0" }
    }

    companion object {
        const val DEFAULT_SAMPLES = 150_000
        const val DEFAULT_BATCH = 10_000
        const val DEFAULT_EXACT_LIMIT = 2_000_000L
    }
}

/**
 * Итог (или промежуточный итог) эквити. Доли — от 0 до 1.
 * - [win] — доля раздач, где рука строго сильнее всех соперников; [tie] — делит первое место;
 *   [lose] — уступает хотя бы одному.
 * - [equity] = [win] + [tieShare]: при дележе на k игроков рука получает 1/k банка.
 * - [winMargin]/[equityMargin] — половина 95 % интервала `1,96 · √(p(1 − p)/n)`; при [exact] — 0.
 */
data class EquityResult(
    val win: Double,
    val tie: Double,
    val lose: Double,
    val tieShare: Double,
    val samples: Long,
    val exact: Boolean,
    val isFinal: Boolean,
) {
    val equity: Double get() = win + tieShare
    val winMargin: Double get() = margin(win)
    val equityMargin: Double get() = margin(equity)

    private fun margin(p: Double): Double = if (exact || samples == 0L) 0.0 else Z_95 * sqrt(p * (1 - p) / samples)

    private companion object {
        const val Z_95 = 1.96
    }
}

/**
 * Эквити против `players − 1` случайных рук.
 *
 * Если число вариантов раздачи ([combinations]) не больше [EquitySettings.exactLimit] (ривер, тёрн
 * и флоп хедз-ап, ривер на 3 игроков) — точный перебор, один итог. Иначе Монте-Карло пачками по
 * [EquitySettings.batchSize] с промежуточным итогом после каждой. Отмена коллектора (смена ввода)
 * останавливает расчёт между пачками и внутри перебора.
 */
object EquityCalculator {
    private const val CANCEL_CHECK_MASK = 0xFFFL

    fun equity(
        request: EquityRequest,
        settings: EquitySettings = EquitySettings(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): Flow<EquityResult> = flow {
        val context = currentCoroutineContext()
        val deal = Deal(request)
        if (combinations(request) <= settings.exactLimit) {
            val tally = Tally()
            ExactEnumerator(deal, tally) { context.ensureActive() }.run()
            emit(tally.toResult(exact = true, isFinal = true))
        } else {
            val tally = Tally()
            val sampler = MonteCarlo(deal, settings.seed?.let { Random(it) } ?: Random(Random.nextLong()))
            var dealt = 0
            while (dealt < settings.samples) {
                val batch = min(settings.batchSize, settings.samples - dealt)
                sampler.run(batch, tally)
                dealt += batch
                context.ensureActive()
                emit(tally.toResult(exact = false, isFinal = dealt == settings.samples))
            }
        }
    }.flowOn(dispatcher)

    /** Только итог — для тестов и бенчмарков. */
    suspend fun calculate(
        request: EquityRequest,
        settings: EquitySettings = EquitySettings(),
        dispatcher: CoroutineDispatcher = Dispatchers.Default,
    ): EquityResult = equity(request, settings, dispatcher).last()

    /** Число вариантов точного перебора: доска × руки соперников (соперники различимы); с насыщением. */
    fun combinations(request: EquityRequest): Long {
        var total = Combinatorics.choose(request.unseen, request.missingBoard)
        var left = request.unseen - request.missingBoard
        repeat(request.opponents) {
            total = Combinatorics.saturatingTimes(total, Combinatorics.choose(left, EquityRequest.HOLE_CARDS))
            left -= EquityRequest.HOLE_CARDS
        }
        return total
    }

    /** Подготовленная раздача: маски своих карт и стола, биты неизвестных карт. */
    internal class Deal(request: EquityRequest) {
        val heroMask: Long = request.hole.toMask()
        val boardMask: Long = request.board.toMask()
        val missing: Int = request.missingBoard
        val opponents: Int = request.opponents
        val unseen: LongArray = run {
            val known = heroMask or boardMask or request.dead.toMask()
            Deck.cards.filter { (it.bit and known) == 0L }.map { it.bit }.toLongArray()
        }
    }

    internal class Tally {
        var wins = 0L
        var ties = 0L
        var losses = 0L
        var share = 0.0
        val total: Long get() = wins + ties + losses

        /** [beaten] — кто-то из соперников сильнее; [tied] — соперников с той же силой. */
        fun add(beaten: Boolean, tied: Int) {
            when {
                beaten -> losses++

                tied > 0 -> {
                    ties++
                    share += 1.0 / (tied + 1)
                }

                else -> wins++
            }
        }

        fun toResult(exact: Boolean, isFinal: Boolean): EquityResult {
            val n = total.toDouble()
            return EquityResult(
                win = wins / n,
                tie = ties / n,
                lose = losses / n,
                tieShare = share / n,
                samples = total,
                exact = exact,
                isFinal = isFinal,
            )
        }
    }

    internal class MonteCarlo(private val deal: Deal, private val random: Random) {
        private val deck = deal.unseen.copyOf()
        private val need = deal.missing + EquityRequest.HOLE_CARDS * deal.opponents

        fun run(count: Int, tally: Tally) {
            repeat(count) { sample(tally) }
        }

        private fun sample(tally: Tally) {
            // Частичная тасовка Фишера — Йетса: первые need карт — случайная выборка без повторов.
            for (i in 0 until need) {
                val j = i + random.nextInt(deck.size - i)
                val card = deck[j]
                deck[j] = deck[i]
                deck[i] = card
            }
            var board = deal.boardMask
            for (i in 0 until deal.missing) board = board or deck[i]
            val hero = HandEvaluator.evaluate(deal.heroMask or board)
            var beaten = false
            var tied = 0
            var next = deal.missing
            // Как только кто-то сильнее, остальных можно не оценивать.
            while (next < need && !beaten) {
                val rank = HandEvaluator.evaluate(board or deck[next] or deck[next + 1])
                next += 2
                if (rank > hero) {
                    beaten = true
                } else if (rank == hero) {
                    tied++
                }
            }
            tally.add(beaten, tied)
        }
    }

    /** Полный перебор досок и рук соперников. [check] бросает CancellationException при отмене. */
    internal class ExactEnumerator(private val deal: Deal, private val tally: Tally, private val check: () -> Unit) {
        private val cards = deal.unseen
        private var leaves = 0L
        private var hero = 0

        fun run() {
            dealBoard(0, deal.missing, deal.boardMask, 0L)
        }

        /** [used] — биты занятых индексов в [cards]. */
        private fun dealBoard(start: Int, left: Int, board: Long, used: Long) {
            if (left == 0) {
                hero = HandEvaluator.evaluate(deal.heroMask or board)
                opponent(0, board, used, 0)
            } else {
                for (i in start..cards.size - left) {
                    dealBoard(i + 1, left - 1, board or cards[i], used or (1L shl i))
                }
            }
        }

        /** [outcome] — [BEATEN] или число соперников с той же силой, что у нас. */
        private fun opponent(index: Int, board: Long, used: Long, outcome: Int) {
            if (index == deal.opponents) {
                tally.add(outcome == BEATEN, outcome)
                if ((++leaves and CANCEL_CHECK_MASK) == 0L) check()
                return
            }
            for (i in cards.indices) {
                if ((used and (1L shl i)) == 0L) {
                    secondCard(index, i, board, used or (1L shl i), outcome)
                }
            }
        }

        private fun secondCard(index: Int, first: Int, board: Long, used: Long, outcome: Int) {
            for (j in first + 1 until cards.size) {
                if ((used and (1L shl j)) == 0L) {
                    val rank = HandEvaluator.evaluate(board or cards[first] or cards[j])
                    val next = when {
                        outcome == BEATEN || rank > hero -> BEATEN
                        rank == hero -> outcome + 1
                        else -> outcome
                    }
                    opponent(index + 1, board, used or (1L shl j), next)
                }
            }
        }

        private companion object {
            const val BEATEN = -1
        }
    }
}

internal object Combinatorics {
    /** C(n, k) с насыщением до Long.MAX_VALUE. */
    fun choose(n: Int, k: Int): Long {
        if (k < 0 || k > n) return 0L
        val r = min(k, n - k)
        var result = 1L
        for (i in 1..r) {
            // result · (n − r + i) / i — делится нацело на каждом шаге.
            val factor = (n - r + i).toLong()
            result = if (result > Long.MAX_VALUE / factor) Long.MAX_VALUE else result * factor / i
        }
        return result
    }

    fun saturatingTimes(a: Long, b: Long): Long = if (b != 0L && a > Long.MAX_VALUE / b) Long.MAX_VALUE else a * b
}
