package app.feltbook.core.odds

/** Ранг карты: от двойки до туза. [symbol] — латинская запись («T» — десятка). */
enum class Rank(val symbol: Char) {
    TWO('2'),
    THREE('3'),
    FOUR('4'),
    FIVE('5'),
    SIX('6'),
    SEVEN('7'),
    EIGHT('8'),
    NINE('9'),
    TEN('T'),
    JACK('J'),
    QUEEN('Q'),
    KING('K'),
    ACE('A'),
    ;

    companion object {
        /** Ранг по символу: «2»…«9», «T»/«t», «J», «Q», «K», «A» (регистр не важен). */
        fun fromSymbol(symbol: Char): Rank? = entries.firstOrNull { it.symbol == symbol.uppercaseChar() }
    }
}

/** Масть. [symbol] — латинская запись (c, d, h, s); отображение — забота UI. */
enum class Suit(val symbol: Char) {
    CLUBS('c'),
    DIAMONDS('d'),
    HEARTS('h'),
    SPADES('s'),
    ;

    companion object {
        fun fromSymbol(symbol: Char): Suit? = entries.firstOrNull { it.symbol == symbol.lowercaseChar() }
    }
}

/**
 * Карта колоды. Экземпляров ровно 52 (см. [Card.of]), поэтому сравнение по ссылке корректно.
 * [id] = `rank.ordinal * 4 + suit.ordinal` (0…51) — индекс для быстрых расчётов.
 */
class Card private constructor(val rank: Rank, val suit: Suit) : Comparable<Card> {
    val id: Int = rank.ordinal * Deck.SUITS + suit.ordinal

    /** Бит карты в маске руки: масть — блок из 16 бит, внутри — ранг. */
    internal val bit: Long = 1L shl (suit.ordinal * SUIT_SHIFT + rank.ordinal)

    override fun compareTo(other: Card): Int = id.compareTo(other.id)

    /** Запись вида «As», «Td». */
    override fun toString(): String = "${rank.symbol}${suit.symbol}"

    companion object {
        internal const val SUIT_SHIFT = 16

        private val ALL: List<Card> = Rank.entries.flatMap { rank -> Suit.entries.map { suit -> Card(rank, suit) } }

        fun of(rank: Rank, suit: Suit): Card = ALL[rank.ordinal * Deck.SUITS + suit.ordinal]

        fun fromId(id: Int): Card {
            require(id in 0 until Deck.SIZE) { "Номер карты вне 0…51: $id" }
            return ALL[id]
        }

        /** Разбор «As», «td», «Kh»; `null`, если запись не карта. */
        fun parseOrNull(text: String): Card? {
            val trimmed = text.trim().takeIf { it.length == 2 }
            val rank = trimmed?.let { Rank.fromSymbol(it[0]) }
            val suit = trimmed?.let { Suit.fromSymbol(it[1]) }
            return if (rank != null && suit != null) of(rank, suit) else null
        }

        fun parse(text: String): Card = requireNotNull(parseOrNull(text)) { "Не карта: «$text»" }

        /** Разбор списка: «As Kd», «AsKd», «As,Kd». */
        fun parseList(text: String): List<Card> {
            val compact = text.filterNot { it.isWhitespace() || it == ',' }
            require(compact.length % 2 == 0) { "Не список карт: «$text»" }
            return compact.chunked(2).map(::parse)
        }

        internal val all: List<Card> get() = ALL
    }
}

/** Колода из 52 карт. */
object Deck {
    const val RANKS: Int = 13
    const val SUITS: Int = 4
    const val SIZE: Int = RANKS * SUITS

    /** Все карты по возрастанию [Card.id]. */
    val cards: List<Card> get() = Card.all
}

/** Маска набора карт для оценщика. */
internal fun Iterable<Card>.toMask(): Long = fold(0L) { mask, card -> mask or card.bit }
