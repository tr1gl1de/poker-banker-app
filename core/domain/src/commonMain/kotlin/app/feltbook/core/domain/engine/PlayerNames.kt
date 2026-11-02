package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.PlayerName

object PlayerNames {
    const val MAX_LENGTH: Int = 40

    private val WHITESPACE = Regex("\\s+")

    /**
     * trim + схлопывание пробелов. Сравнение имён везде — после нормализации, с учётом регистра
     * (ТЗ §4: точное совпадение).
     */
    fun normalize(raw: String): String = raw.trim().replace(WHITESPACE, " ")

    /** Каноническое имя: идёт по `mergedInto` до конца; при цикле — исходное имя. */
    fun canonical(name: String, directory: List<PlayerName>): String {
        val start = normalize(name)
        val links = directory.associate { it.name to it.mergedInto }
        val seen = mutableSetOf(start)
        var current = start
        while (true) {
            val next = links[current] ?: return current
            if (!seen.add(next)) return start
            current = next
        }
    }

    /** Можно ли объединить `from` → `into`: разные, оба есть, не образует цикл. */
    fun canMerge(from: String, into: String, directory: List<PlayerName>): Boolean {
        val names = directory.map { it.name }.toSet()
        val source = normalize(from)
        val target = normalize(into)
        return source != target && source in names && target in names && !reaches(target, source, directory)
    }

    /** Подсказки для поля «Имя»: по префиксу без учёта регистра, сначала недавние; без `exclude` (уже за столом). */
    fun suggestions(prefix: String, directory: List<PlayerName>, exclude: Set<String>, limit: Int = 8): List<String> {
        val start = normalize(prefix)
        val excluded = exclude.map { normalize(it) }.toSet()
        return directory
            .filter { it.mergedInto == null && it.name.startsWith(start, ignoreCase = true) && it.name !in excluded }
            .sortedByDescending { it.firstSeenAt }
            .map { it.name }
            .take(limit)
    }

    /** Цепочка `mergedInto` от `start` доходит до `goal` (или уже зациклена). */
    private fun reaches(start: String, goal: String, directory: List<PlayerName>): Boolean {
        val links = directory.associate { it.name to it.mergedInto }
        val seen = mutableSetOf<String>()
        var current: String? = start
        while (current != null && seen.add(current)) {
            if (current == goal) return true
            current = links[current]
        }
        return current != null
    }
}
