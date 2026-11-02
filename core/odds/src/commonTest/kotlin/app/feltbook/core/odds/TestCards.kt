package app.feltbook.core.odds

/** «As Kd» → карты. */
fun cards(text: String): List<Card> = Card.parseList(text)

fun rankOf(text: String): Int = HandEvaluator.rank(cards(text))

fun categoryOf(text: String): HandCategory = HandEvaluator.category(cards(text))
