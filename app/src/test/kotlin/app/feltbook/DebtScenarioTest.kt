package app.feltbook

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.component.ClickClock
import app.feltbook.core.ui.component.DEBOUNCE_WINDOW_MILLIS
import app.feltbook.core.ui.component.KeypadKey
import app.feltbook.core.ui.component.LocalClickClock
import app.feltbook.core.ui.component.keypadTestTag
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.table.TableScreen
import app.feltbook.feature.game.table.TableViewModel
import app.feltbook.feature.history.debts.DebtsScreen
import app.feltbook.feature.history.debts.DebtsViewModel
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Критерий M4 (roadmap M4.7): «ушёл с долгом → вернулся → погасил» через интерфейс.
 * Влад выходит со стола кнопкой «Не заплатил», игра закрывается; за новым столом при посадке Влада
 * всплывает «Влад должен 1 053 ₽»; на экране «Открытые долги» «Погасить» пишет PAYMENT в старую игру.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class DebtScenarioTest {
    @get:Rule
    val rule = createComposeRule()

    private val clock = TestClock()
    private val repository = FakeGameRepository()
    private val names = FakePlayerNameRepository()
    private val settings = FakeSettingsRepository(AppSettings(lastBankerName = "Саша"))

    private fun stamp() = Stamp(clock.current)

    private fun commit(change: GameChange) = runBlocking { repository.commit(listOf(change)) }

    private fun startGame(title: String, banker: String): GameId {
        val change = Commands.startGame(
            NewGameInput(title, title, banker, bankerPlays = false, rules = GameRules.DEFAULT),
            activeGame = null,
            stamp = stamp(),
        )
        commit(change)
        return change.gameId
    }

    private fun GameId.snapshot(): GameSnapshot = repository.snapshot(this)

    private fun GameSnapshot.id(name: String) = players.first { it.name == name }.id

    private fun clickable(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        rule.onNode(hasText(text, substring = substring) and hasClickAction())

    private fun pause() = rule.mainClock.advanceTimeBy(DEBOUNCE_WINDOW_MILLIS + 100)

    private fun type(amount: String) {
        amount.forEach { rule.onNodeWithTag(keypadTestTag(KeypadKey.Digit(it.digitToInt()))).performClick() }
        rule.waitForIdle()
    }

    @Suppress("LongMethod") // Один сквозной сценарий — шаги подряд.
    @Test
    fun leftWithDebtReturnedAndRepaid() {
        // Пятничный стол: Влад 2 000 в долг (начислено 2 300), Егор 1 000 оплачено и уже вышел.
        val friday = startGame("Пятничный стол", banker = "Саша")
        commit(Commands.addPlayer(friday.snapshot(), "Влад", 2_000, BuyInPayment.DEBT, stamp()))
        commit(Commands.addPlayer(friday.snapshot(), "Егор", 1_000, BuyInPayment.PAID, stamp()))
        clock.current += 3.hours
        commit(
            Commands.cashOut(
                friday.snapshot(),
                friday.snapshot().id("Егор"),
                1_753,
                null,
                SettlementChoice.Full,
                false,
                stamp(),
            ),
        )

        var screen by mutableStateOf("friday")
        var newGame: GameId? = null
        rule.setContent {
            CompositionLocalProvider(LocalClickClock provides ClickClock { rule.mainClock.currentTime }) {
                FeltbookTheme {
                    when (screen) {
                        "friday", "next" -> {
                            val id = if (screen == "friday") friday else checkNotNull(newGame)
                            val table = remember(id) {
                                TableViewModel(
                                    SavedStateHandle(mapOf(TableViewModel.ARG_GAME_ID to id.value.toString())),
                                    repository,
                                    names,
                                    clock,
                                )
                            }
                            val state by table.uiState.collectAsState()
                            TableScreen(state = state, onAction = table::onAction, forceSinglePane = true)
                        }

                        else -> {
                            val debts = remember {
                                DebtsViewModel(repository, names, settings, GameDeletionScheduler(repository), clock)
                            }
                            val state by debts.uiState.collectAsState()
                            DebtsScreen(state = state, onAction = debts::onAction)
                        }
                    }
                }
            }
        }
        rule.waitForIdle()

        // 1. Ушёл с долгом: выход Влада с 1 247 фишками — «Не заплатил».
        pause()
        clickable("Влад").performClick()
        rule.waitForIdle()
        clickable("Выход").performClick()
        rule.waitForIdle()
        type("1247")
        clickable("Готово").performClick()
        rule.waitForIdle()
        pause()
        clickable("Не заплатил").performClick()
        rule.waitForIdle()
        val vlad = friday.snapshot().id("Влад")
        assertEquals(PlayerStatus.LEFT_WITH_DEBT, GameStates.compute(friday.snapshot()).player(vlad)?.status)

        // Игру закрывают с долгом.
        commit(Commands.finish(friday.snapshot()))
        val settling = friday.snapshot()
        val required = Settlements.compute(settling).requiredConfirmations
        commit(Commands.close(settling, payRake = true, acknowledged = required, stamp = stamp()))
        assertEquals(1_053L, Debts.open(repository.games.value.values.toList(), emptyList()).single().amount)

        // 2. Вернулся: новый стол через две недели, банкир Миша; при посадке Влада — предупреждение о долге.
        clock.current += 14.days
        newGame = startGame("Новый стол", banker = "Миша")
        screen = "next"
        rule.waitForIdle()
        pause()
        rule.onNodeWithContentDescription("Добавить игрока").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput("Влад")
        rule.waitForIdle()
        rule.onNode(hasText("Влад должен", substring = true)).assertExists()

        // 3. Погасил: «Открытые долги» → «Погасить» → «Принять 1 053 ₽».
        screen = "debts"
        rule.waitForIdle()
        pause()
        clickable("Погасить").performClick()
        rule.waitForIdle()
        pause()
        clickable("Принять", substring = true).performClick()
        rule.waitForIdle()

        val old = friday.snapshot()
        val payment = old.entries.last()
        assertEquals(EntryType.PAYMENT, payment.type)
        assertEquals(1_053L, payment.amount)
        assertEquals(vlad, payment.playerId)
        assertEquals("Миша", payment.bankerName) // принял банкир активной игры
        assertEquals(PlayerStatus.CASHED_OUT, GameStates.compute(old).player(vlad)?.status)
        assertTrue(Debts.open(repository.games.value.values.toList(), emptyList()).isEmpty())
        rule.onNode(hasText("Долгов нет", substring = true)).assertExists()
    }
}
