package app.feltbook.core.odds

/** Группа позиции для памятки: ранние, средние, поздние (CO, BTN), блайнды. */
enum class PositionGroup { EARLY, MIDDLE, LATE, BLINDS }

/**
 * Место за столом. [label] — как в макете («UTG», «UTG+1», «MP», «CO», «BTN», «SB», «BB»;
 * хедз-ап — «SB/BTN»). [playersBehind] — сколько игроков ходит после этого места на префлопе.
 */
data class Position(val label: String, val group: PositionGroup, val playersBehind: Int)

/**
 * Ориентир (не совет): какую долю рук обычно разыгрывают первым входом с этой позиции —
 * «топ [openTopPercent] % рук». Для BB — доля рук, которыми обычно защищают блайнд.
 */
data class PositionGuide(val position: Position, val openTopPercent: Double) {
    /** Попадает ли рука в ориентир позиции. */
    fun includes(strength: PreflopStrength): Boolean = strength.topPercent <= openTopPercent
}

/**
 * Позиции по числу игроков (ТЗ §4, docs/plan/m3-ui-spec.md §5.14) в порядке хода на префлопе:
 * 2 — SB/BTN, BB; 3 — BTN, SB, BB; 4 — CO, BTN, SB, BB; 5 — UTG, CO, BTN, SB, BB;
 * 6 — UTG, MP, CO, BTN, SB, BB; 7–10 — UTG, UTG+1…, MP…, CO, BTN, SB, BB (ранних не меньше средних).
 */
object Positions {
    private const val FIXED_SEATS = 4 // CO, BTN, SB, BB
    private const val HEADS_UP = 2

    /** Ориентир по числу игроков после нас: индекс — playersBehind (0 — BB, 1 — SB, 2 — BTN, 3 — CO…). */
    private val OPEN_BY_PLAYERS_BEHIND = doubleArrayOf(40.0, 36.0, 45.0, 27.0, 20.0, 16.0, 13.0, 11.0, 10.0, 8.0)
    private const val HEADS_UP_BUTTON = 80.0
    private const val HEADS_UP_BIG_BLIND = 60.0

    fun forPlayers(players: Int): List<Position> {
        require(players in EquityRequest.MIN_PLAYERS..EquityRequest.MAX_PLAYERS) { "Игроков 2–10, передано $players" }
        val labels: List<Pair<String, PositionGroup>> = if (players == HEADS_UP) {
            listOf("SB/BTN" to PositionGroup.LATE, "BB" to PositionGroup.BLINDS)
        } else {
            val open = (players - FIXED_SEATS).coerceAtLeast(0)
            val early = (open + 1) / 2
            val middle = open - early
            val tail = listOf(
                "CO" to PositionGroup.LATE,
                "BTN" to PositionGroup.LATE,
                "SB" to PositionGroup.BLINDS,
                "BB" to PositionGroup.BLINDS,
            )
            List(early) { seatLabel("UTG", it) to PositionGroup.EARLY } +
                List(middle) { seatLabel("MP", it) to PositionGroup.MIDDLE } +
                tail.takeLast(players.coerceAtMost(FIXED_SEATS))
        }
        return labels.mapIndexed { index, (label, group) -> Position(label, group, players - 1 - index) }
    }

    /** Ориентир для позиции [label] при [players] игроках. */
    fun guide(players: Int, label: String): PositionGuide {
        val position = requireNotNull(forPlayers(players).firstOrNull { it.label == label }) {
            "Нет позиции «$label» при $players игроках"
        }
        val percent = when {
            players == HEADS_UP && position.playersBehind > 0 -> HEADS_UP_BUTTON
            players == HEADS_UP -> HEADS_UP_BIG_BLIND
            else -> OPEN_BY_PLAYERS_BEHIND[position.playersBehind]
        }
        return PositionGuide(position, percent)
    }

    private fun seatLabel(base: String, index: Int): String = if (index == 0) base else "$base+$index"
}
