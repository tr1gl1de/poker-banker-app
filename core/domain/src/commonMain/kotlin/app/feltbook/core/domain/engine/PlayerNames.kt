package app.feltbook.core.domain.engine

object PlayerNames {
    const val MAX_LENGTH: Int = 40

    private val WHITESPACE = Regex("\\s+")

    /**
     * trim + схлопывание пробелов. Сравнение имён везде — после нормализации, с учётом регистра (ТЗ §4: точное
     * совпадение).
     */
    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")
}
