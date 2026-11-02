package app.feltbook.core.odds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PositionsTest {
    private fun labels(players: Int) = Positions.forPlayers(players).map { it.label }

    @Test
    fun `позиции по числу игроков`() {
        assertEquals(listOf("SB/BTN", "BB"), labels(2))
        assertEquals(listOf("BTN", "SB", "BB"), labels(3))
        assertEquals(listOf("CO", "BTN", "SB", "BB"), labels(4))
        assertEquals(listOf("UTG", "CO", "BTN", "SB", "BB"), labels(5))
        assertEquals(listOf("UTG", "MP", "CO", "BTN", "SB", "BB"), labels(6))
        assertEquals(listOf("UTG", "UTG+1", "MP", "CO", "BTN", "SB", "BB"), labels(7))
        assertEquals(listOf("UTG", "UTG+1", "MP", "MP+1", "CO", "BTN", "SB", "BB"), labels(8))
        assertEquals(listOf("UTG", "UTG+1", "UTG+2", "MP", "MP+1", "CO", "BTN", "SB", "BB"), labels(9))
        assertEquals(
            listOf("UTG", "UTG+1", "UTG+2", "MP", "MP+1", "MP+2", "CO", "BTN", "SB", "BB"),
            labels(10),
        )
        assertFailsWith<IllegalArgumentException> { Positions.forPlayers(1) }
        assertFailsWith<IllegalArgumentException> { Positions.forPlayers(11) }
    }

    @Test
    fun `группы и порядок хода`() {
        val six = Positions.forPlayers(6)
        assertEquals(
            listOf(PositionGroup.EARLY, PositionGroup.MIDDLE, PositionGroup.LATE, PositionGroup.LATE) +
                listOf(PositionGroup.BLINDS, PositionGroup.BLINDS),
            six.map { it.group },
        )
        assertEquals(listOf(5, 4, 3, 2, 1, 0), six.map { it.playersBehind })
    }

    @Test
    fun `ориентир шире к баттону`() {
        for (players in 3..10) {
            val opens = Positions.forPlayers(players)
                .filter { it.group != PositionGroup.BLINDS }
                .map { Positions.guide(players, it.label).openTopPercent }
            assertEquals(opens.sorted(), opens, "$players игроков: $opens")
        }
        assertEquals(8.0, Positions.guide(10, "UTG").openTopPercent)
        assertEquals(45.0, Positions.guide(6, "BTN").openTopPercent)
        assertEquals(80.0, Positions.guide(2, "SB/BTN").openTopPercent)
        assertEquals(60.0, Positions.guide(2, "BB").openTopPercent)
        assertFailsWith<IllegalArgumentException> { Positions.guide(6, "UTG+1") }
    }

    @Test
    fun `рука в ориентире позиции`() {
        val suitedConnector = PreflopTable.strength(cards("Th 9h")) // топ ≈ 34,8 %
        assertTrue(Positions.guide(6, "BTN").includes(suitedConnector))
        assertFalse(Positions.guide(9, "UTG").includes(suitedConnector))
        assertTrue(Positions.guide(9, "UTG").includes(PreflopTable.strength(cards("As Kd"))))
    }
}
