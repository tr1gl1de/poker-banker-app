package app.feltbook.feature.game

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import app.feltbook.core.domain.engine.Settlements
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.ui.component.ClickClock
import app.feltbook.core.ui.component.DEBOUNCE_WINDOW_MILLIS
import app.feltbook.core.ui.component.KeypadKey
import app.feltbook.core.ui.component.LocalClickClock
import app.feltbook.core.ui.component.keypadTestTag
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.common.LocalTimeZone
import app.feltbook.feature.game.common.toGameId
import app.feltbook.feature.game.newgame.NewGameEvent
import app.feltbook.feature.game.newgame.NewGameScreen
import app.feltbook.feature.game.newgame.NewGameViewModel
import app.feltbook.feature.game.table.TableScreen
import app.feltbook.feature.game.table.TableViewModel
import app.feltbook.feature.game.testing.FakeGameRepository
import app.feltbook.feature.game.testing.FakePlayerNameRepository
import app.feltbook.feature.game.testing.FakeSettingsRepository
import app.feltbook.feature.game.testing.TestClock
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Ключевой сценарий ТЗ §9 на цифрах сквозного примера §3.6 — через интерфейс:
 * создать игру → 3 игрока → докуп → выход с чаевыми → все вышли → «Завершить игру» → итог 853 = 500 + 353.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class TableScenarioTest {
    @get:Rule
    val rule = createComposeRule()

    private val clock = TestClock()
    private val repository = FakeGameRepository()
    private val names = FakePlayerNameRepository()
    private val settings = FakeSettingsRepository(AppSettings(lastBankerName = "Саша"))

    private fun key(key: KeypadKey) = rule.onNodeWithTag(keypadTestTag(key)).performClick()

    /** Набрать сумму на своей клавиатуре: «3753» → 3, 7, 5, 3; «1000» → 1, 000. */
    private fun type(amount: String) {
        var rest = amount
        while (rest.isNotEmpty()) {
            if (rest.startsWith("000") && rest != amount) {
                key(KeypadKey.TripleZero)
                rest = rest.drop(3)
            } else {
                key(KeypadKey.Digit(rest.first().digitToInt()))
                rest = rest.drop(1)
            }
        }
        rule.waitForIdle()
    }

    private fun clickable(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        rule.onNode(hasText(text, substring = substring) and hasClickAction())

    private fun hold(titlePrefix: String) {
        rule.onNode(
            hasContentDescription(titlePrefix, substring = true),
        ).performSemanticsAction(SemanticsActions.OnLongClick)
        rule.waitForIdle()
    }

    private fun addPlayer(name: String, chips: String, paid: Boolean) {
        pause()
        rule.onNodeWithContentDescription("Добавить игрока").performClick()
        rule.waitForIdle()
        rule.onNode(hasSetTextAction()).performTextInput(name)
        clickable("Первый закуп").performClick()
        rule.waitForIdle()
        type(chips)
        if (paid) rule.onNodeWithText("Оплачено сейчас").performClick()
        pause()
        clickable("Выдать", substring = true).performClick()
        rule.waitForIdle()
    }

    /** Защита от двойного нажатия (600 мс) считает по часам кадров теста — между действиями «проходит время». */
    private fun pause() = rule.mainClock.advanceTimeBy(DEBOUNCE_WINDOW_MILLIS + 100)

    private fun openCard(name: String) {
        pause()
        clickable(name).performClick()
        rule.waitForIdle()
    }

    @Suppress("LongMethod") // Один сквозной сценарий ТЗ §9 — шаги подряд.
    @Test
    fun spec36EndToEnd() {
        val newGame = NewGameViewModel(repository, settings, clock)
        var gameId by mutableStateOf<String?>(null)
        rule.setContent {
            CompositionLocalProvider(
                LocalTimeZone provides ZoneId.of("Europe/Moscow"),
                LocalClickClock provides ClickClock { rule.mainClock.currentTime },
            ) {
                FeltbookTheme {
                    val id = gameId
                    if (id == null) {
                        val state by newGame.uiState.collectAsState()
                        LaunchedEffect(Unit) {
                            newGame.events.collect { if (it is NewGameEvent.Started) gameId = it.gameId }
                        }
                        NewGameScreen(state = state, onAction = newGame::onAction)
                    } else {
                        val table = remember(id) {
                            TableViewModel(
                                SavedStateHandle(mapOf(TableViewModel.ARG_GAME_ID to id)),
                                repository,
                                names,
                                clock,
                            )
                        }
                        val state by table.uiState.collectAsState()
                        TableScreen(state = state, onAction = table::onAction, forceSinglePane = true)
                    }
                }
            }
        }
        rule.waitForIdle()

        // 1. Новая игра: банкир Саша не играет, правила по умолчанию (рейк 10 %, сбор разово 100 ₽).
        rule.onNode(hasClickAction() and hasText("Я тоже играю", substring = true)).performClick()
        rule.onNode(hasText("Начать игру") and hasClickAction()).performClick()
        rule.waitForIdle()
        val id = checkNotNull(gameId).toGameId()
        assertEquals(GameStatus.RUNNING, repository.snapshot(id).game.status)

        // 2. Три игрока: Андрей 1 000 в долг, Борис 1 000 оплачено, Влад 2 000 в долг.
        addPlayer("Андрей", "1000", paid = false)
        addPlayer("Борис", "1000", paid = true)
        addPlayer("Влад", "2000", paid = false)
        assertEquals(3, repository.snapshot(id).players.size)

        // 3. Докуп Андрею 1 000 в долг — из карточки игрока.
        openCard("Андрей")
        clickable("Докуп").performClick()
        rule.waitForIdle()
        type("1000")
        pause()
        clickable("Выдать", substring = true).performClick()
        rule.waitForIdle()

        // Прошло 3 часа — выходы не ранние.
        clock.current = clock.current + 3.hours

        // 4. Борис выходит с 3 753, чаевые «до 100» (53) банкиру → банкир выплачивает 3 700.
        openCard("Борис")
        clickable("Выход").performClick()
        rule.waitForIdle()
        type("3753")
        clickable("Готово").performClick()
        rule.waitForIdle()
        clickable("до 100", substring = true).performClick()
        rule.waitForIdle()
        hold("Расчёт произведён")

        // 5. Андрей выходит с 0 и платит 2 300; Влад с 1 247 и доплачивает 1 053.
        openCard("Андрей")
        clickable("Выход").performClick()
        rule.waitForIdle()
        clickable("Готово").performClick()
        rule.waitForIdle()
        hold("Получил")

        openCard("Влад")
        clickable("Выход").performClick()
        rule.waitForIdle()
        type("1247")
        clickable("Готово").performClick()
        rule.waitForIdle()
        hold("Получил")

        // 6. «⋮ → Завершить игру».
        rule.onNodeWithContentDescription("Меню игры").performClick()
        rule.waitForIdle()
        clickable("Завершить игру").performClick()
        rule.waitForIdle()

        val snapshot = repository.snapshot(id)
        assertEquals(GameStatus.SETTLING, snapshot.game.status)
        val settlement = Settlements.compute(snapshot)
        assertEquals(5_000L, settlement.chipsIssued)
        assertEquals(5_000L, settlement.chipsReturned)
        assertTrue(settlement.chipsReconciled)
        assertEquals(500L, settlement.rakeDue)
        assertEquals(300L, settlement.bankerFees)
        assertEquals(53L, settlement.tipsToBanker)
        assertEquals(353L, settlement.bankerIncome)
        assertEquals(853L, settlement.expectedCash)
        assertEquals(853L, settlement.cashOnHand)
        assertTrue(settlement.unpaidDebts.isEmpty())
        val balances = settlement.rows.associate { it.name to it.balanceAtExit }
        assertEquals(mapOf("Андрей" to -2_300L, "Борис" to 3_700L, "Влад" to -1_053L), balances)
    }
}
