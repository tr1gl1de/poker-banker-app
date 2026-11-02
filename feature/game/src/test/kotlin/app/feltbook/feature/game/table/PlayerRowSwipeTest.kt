package app.feltbook.feature.game.table

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.theme.FeltbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ТЗ §5.1: свайп по карточке игрока срабатывает только после 40 % ширины; на половине пути карточка возвращается. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class PlayerRowSwipeTest {
    @get:Rule
    val rule = createComposeRule()

    private var buyIns = 0
    private var cashOuts = 0

    private val playing = PlayerRowUi(
        id = "p1",
        name = "Дима",
        suit = Suit.Spades,
        status = PlayerStatus.PLAYING,
        isBanker = false,
        rebuyCount = 0,
        chipsBought = 1_000,
        bought = true,
        playedFor = null,
        earlyMinutes = null,
        minPlayMinutes = 60,
        outstanding = -1_200,
        balanceAtExit = null,
        canBuyIn = true,
        canCashOut = true,
    )

    private fun setRow(row: PlayerRowUi = playing) {
        rule.setContent {
            FeltbookTheme {
                PlayerRow(
                    row = row,
                    onClick = {},
                    onSwipeBuyIn = { buyIns++ },
                    onSwipeCashOut = { cashOuts++ },
                    modifier = Modifier.testTag(ROW),
                )
            }
        }
        rule.waitForIdle()
    }

    /** Свайп от середины строки на [fraction] ширины (знак — направление). */
    private fun swipeBy(fraction: Float) {
        rule.onNodeWithTag(ROW).performTouchInput {
            swipe(start = center, end = center.copy(x = center.x + width * fraction), durationMillis = 400)
        }
        rule.waitForIdle()
    }

    @Test
    fun `свайп вправо на 30 процентов — докупа нет, на 60 — докуп`() {
        setRow()
        swipeBy(0.3f)
        assertEquals(0, buyIns)
        swipeBy(0.6f)
        assertEquals(1, buyIns)
        assertEquals(0, cashOuts)
    }

    @Test
    fun `свайп влево на 30 процентов — выхода нет, на 60 — выход`() {
        setRow()
        swipeBy(-0.3f)
        assertEquals(0, cashOuts)
        swipeBy(-0.6f)
        assertEquals(1, cashOuts)
        assertEquals(0, buyIns)
    }

    @Test
    fun `вышедший игрок — свайп влево не открывает выход, вправо — возврат докупом`() {
        setRow(playing.copy(status = PlayerStatus.CASHED_OUT, canCashOut = false, outstanding = 0))
        swipeBy(-0.6f)
        assertEquals(0, cashOuts)
        swipeBy(0.6f)
        assertEquals(1, buyIns)
    }

    private companion object {
        const val ROW = "row"
    }
}
