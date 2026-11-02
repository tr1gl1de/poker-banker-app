package app.feltbook.feature.settings.odds

import androidx.lifecycle.SavedStateHandle
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.Deck
import app.feltbook.core.odds.EquityRequest
import app.feltbook.core.odds.Positions
import kotlin.math.abs

/** Место карты на экране: своя карта (0, 1) или карта стола (0…4). */
sealed interface CardSlot {
    data class Hole(val index: Int) : CardSlot

    data class Board(val index: Int) : CardSlot
}

/** Можно ли считать эквити с текущим вводом. */
enum class EquityStatus {
    /** Нет одной или обеих своих карт. */
    NEED_HOLE,

    /** На столе 1–2 карты: флоп не добран (допустимо 0, 3, 4 или 5). */
    INCOMPLETE_BOARD,

    /** Мёртвых карт столько, что раздать всем игрокам и стол нельзя. */
    TOO_MANY_DEAD,
    READY,
}

/**
 * Ввод калькулятора — единственное, что переживает пересоздание экрана (только [SavedStateHandle],
 * на диск ничего не пишется, ТЗ §4 п. 5). Карты стола идут подряд; инварианты держит [OddsViewModel].
 */
internal data class OddsInput(
    val hole: List<Card?> = listOf(null, null),
    val board: List<Card> = emptyList(),
    val dead: Set<Card> = emptySet(),
    val players: Int = DEFAULT_PLAYERS,
    val position: String = DEFAULT_POSITION,
    val pot: Long = 0L,
    val call: Long = 0L,
) {
    /** Все карты, которые уже где-то лежат. */
    val used: Set<Card> get() = hole.filterNotNull().toSet() + board + dead

    /** Обе свои карты или `null`. */
    val holeCards: List<Card>? get() = hole.filterNotNull().takeIf { it.size == EquityRequest.HOLE_CARDS }

    val status: EquityStatus
        get() {
            val opponents = players - 1
            val unseen = Deck.SIZE - EquityRequest.HOLE_CARDS - board.size - dead.size
            val needed = EquityRequest.BOARD_CARDS - board.size + EquityRequest.HOLE_CARDS * opponents
            return when {
                holeCards == null -> EquityStatus.NEED_HOLE
                board.size !in EquityRequest.BOARD_SIZES -> EquityStatus.INCOMPLETE_BOARD
                unseen < needed -> EquityStatus.TOO_MANY_DEAD
                else -> EquityStatus.READY
            }
        }

    /** Запрос эквити — только при [EquityStatus.READY]. Позиция, банк и ставка в него не входят. */
    fun equityRequest(): EquityRequest? {
        val cards = holeCards
        return if (status == EquityStatus.READY && cards != null) {
            EquityRequest(hole = cards, board = board, players = players, dead = dead)
        } else {
            null
        }
    }

    fun cardAt(slot: CardSlot): Card? = when (slot) {
        is CardSlot.Hole -> hole.getOrNull(slot.index)
        is CardSlot.Board -> board.getOrNull(slot.index)
    }

    /** Положить [card] в [slot]; `null` — слот недопустим (дырка в столе, шестая карта). */
    fun place(slot: CardSlot, card: Card): OddsInput? = when (slot) {
        is CardSlot.Hole ->
            if (slot.index in hole.indices) copy(hole = hole.toMutableList().also { it[slot.index] = card }) else null

        is CardSlot.Board -> when {
            slot.index < board.size -> copy(board = board.toMutableList().also { it[slot.index] = card })
            slot.index == board.size && board.size < EquityRequest.BOARD_CARDS -> copy(board = board + card)
            else -> null
        }
    }

    fun remove(slot: CardSlot): OddsInput = when (slot) {
        is CardSlot.Hole ->
            if (slot.index in hole.indices) copy(hole = hole.toMutableList().also { it[slot.index] = null }) else this

        is CardSlot.Board ->
            if (slot.index < board.size) copy(board = board.toMutableList().also { it.removeAt(slot.index) }) else this
    }

    /** Куда шторке выбора перейти после карты в [slot]: вторая своя карта, следующая карта флопа или закрыться. */
    fun nextSlotAfter(slot: CardSlot): CardSlot? = when (slot) {
        is CardSlot.Hole -> hole.indexOfFirst { it == null }.takeIf { it >= 0 }?.let { CardSlot.Hole(it) }
        is CardSlot.Board -> if (board.size in 1 until FLOP) CardSlot.Board(board.size) else null
    }

    fun withPlayers(count: Int): OddsInput {
        val next = count.coerceIn(EquityRequest.MIN_PLAYERS, EquityRequest.MAX_PLAYERS)
        return if (next == players) this else copy(players = next, position = positionFor(players, position, next))
    }

    fun save(handle: SavedStateHandle) {
        handle[KEY_HOLE] = IntArray(hole.size) { hole[it]?.id ?: NO_CARD }
        handle[KEY_BOARD] = board.map { it.id }.toIntArray()
        handle[KEY_DEAD] = dead.map { it.id }.sorted().toIntArray()
        handle[KEY_PLAYERS] = players
        handle[KEY_POSITION] = position
        handle[KEY_POT] = pot
        handle[KEY_CALL] = call
    }

    companion object {
        const val DEFAULT_PLAYERS = 6
        const val DEFAULT_POSITION = "BTN"
        const val FLOP = 3

        private const val KEY_HOLE = "odds_hole"
        private const val KEY_BOARD = "odds_board"
        private const val KEY_DEAD = "odds_dead"
        private const val KEY_PLAYERS = "odds_players"
        private const val KEY_POSITION = "odds_position"
        private const val KEY_POT = "odds_pot"
        private const val KEY_CALL = "odds_call"
        private const val NO_CARD = -1
        private const val HEADS_UP_BUTTON = "SB/BTN"

        /** Восстановление после пересоздания процесса; всё сомнительное (повторы, чужие номера) отбрасывается. */
        fun restore(handle: SavedStateHandle): OddsInput {
            val seen = mutableSetOf<Card>()
            fun take(id: Int): Card? = id.takeIf { it in 0 until Deck.SIZE }?.let(Card::fromId)?.takeIf(seen::add)

            val hole = (handle.get<IntArray>(KEY_HOLE) ?: IntArray(0)).toList()
                .let { ids -> List(EquityRequest.HOLE_CARDS) { ids.getOrNull(it)?.let(::take) } }
            val board = (handle.get<IntArray>(KEY_BOARD) ?: IntArray(0)).toList()
                .take(EquityRequest.BOARD_CARDS).mapNotNull(::take)
            val dead = (handle.get<IntArray>(KEY_DEAD) ?: IntArray(0)).toList().mapNotNull(::take).toSet()
            val players = (handle.get<Int>(KEY_PLAYERS) ?: DEFAULT_PLAYERS)
                .coerceIn(EquityRequest.MIN_PLAYERS, EquityRequest.MAX_PLAYERS)
            val position = handle.get<String>(KEY_POSITION)
                ?.takeIf { label -> Positions.forPlayers(players).any { it.label == label } }
                ?: Positions.forPlayers(players).firstOrNull { it.label == DEFAULT_POSITION }?.label
                ?: HEADS_UP_BUTTON
            return OddsInput(
                hole = hole,
                board = board,
                dead = dead,
                players = players,
                position = position,
                pot = (handle.get<Long>(KEY_POT) ?: 0L).coerceAtLeast(0L),
                call = (handle.get<Long>(KEY_CALL) ?: 0L).coerceAtLeast(0L),
            )
        }

        /**
         * Позиция при смене числа игроков: та же метка, если есть; «SB/BTN» хедз-апа ↔ «BTN»; иначе место
         * с ближайшим числом игроков после нас (UTG при 5 → CO при 4).
         */
        fun positionFor(oldPlayers: Int, label: String, newPlayers: Int): String {
            val positions = Positions.forPlayers(newPlayers)
            val old = Positions.forPlayers(oldPlayers).firstOrNull { it.label == label }
            return when {
                positions.any { it.label == label } -> label
                label == HEADS_UP_BUTTON && positions.any { it.label == DEFAULT_POSITION } -> DEFAULT_POSITION
                old == null -> positions.first { it.playersBehind == 0 }.label
                else -> positions.minBy { abs(it.playersBehind - old.playersBehind) }.label
            }
        }
    }
}
