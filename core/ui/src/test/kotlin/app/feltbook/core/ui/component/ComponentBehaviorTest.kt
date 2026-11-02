package app.feltbook.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.theme.FeltbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Поведение ключевых компонентов: клавиатура, debounce, hold-to-confirm (Robolectric + Compose UI Test). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class ComponentBehaviorTest {
    @get:Rule
    val rule = createComposeRule()

    private var entry by mutableStateOf(AmountEntry())

    private fun setKeypad(max: Long = DEFAULT_MAX_AMOUNT) {
        rule.setContent {
            FeltbookTheme {
                Column {
                    Text(text = MoneyFormat.number(entry.value), modifier = Modifier.testTag("display"))
                    AmountKeypad(entry = entry, onEntryChange = { entry = it }, maxValue = max)
                }
            }
        }
    }

    private fun press(key: KeypadKey) {
        rule.onNodeWithTag(keypadTestTag(key)).performClick()
    }

    @Test
    fun keypadTypesAmount() {
        setKeypad()
        press(KeypadKey.Digit(0))
        press(KeypadKey.Digit(1))
        press(KeypadKey.Digit(2))
        press(KeypadKey.TripleZero)
        rule.onNodeWithTag("display").assertTextEquals(MoneyFormat.number(12_000))
        press(KeypadKey.Backspace)
        rule.onNodeWithTag("display").assertTextEquals(MoneyFormat.number(1_200))
        assertEquals(1_200L, entry.value)
    }

    @Test
    fun keypadLongBackspaceClears() {
        setKeypad()
        press(KeypadKey.Digit(9))
        press(KeypadKey.Digit(9))
        rule.onNodeWithTag(keypadTestTag(KeypadKey.Backspace)).performTouchInput { longClick() }
        assertEquals(AmountEntry(), entry)
    }

    @Test
    fun keypadRejectsAboveMax() {
        setKeypad(max = 500)
        press(KeypadKey.Digit(5))
        press(KeypadKey.Digit(0))
        press(KeypadKey.Digit(0))
        press(KeypadKey.Digit(1))
        assertEquals(500L, entry.value)
    }

    @Test
    fun keypadDeleteHasContentDescription() {
        setKeypad()
        rule.onNodeWithText("000").assertExists()
        rule.onNode(hasContentDescription("Стереть")).assertExists()
    }

    @Test
    fun brassButtonIgnoresSecondClickWithin600ms() {
        var now = 10_000L
        var clicks by mutableIntStateOf(0)
        rule.setContent {
            CompositionLocalProvider(LocalClickClock provides ClickClock { now }) {
                FeltbookTheme { BrassButton(text = "Выдать 1 000 фишек", onClick = { clicks++ }) }
            }
        }
        val button = rule.onNodeWithText("Выдать 1 000 фишек")
        button.performClick()
        button.performClick()
        assertEquals(1, clicks)
        now += 599
        button.performClick()
        assertEquals(1, clicks)
        now += 1
        button.performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun holdConfirmsOnlyAfter600ms() {
        var confirmed = 0
        rule.setContent {
            FeltbookTheme { HoldToConfirmButton(title = "Выплатить 353 ₽", onConfirm = { confirmed++ }) }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val button = rule.onNode(hasContentDescription("Выплатить 353 ₽"))
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(400)
        assertEquals(0, confirmed)
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1, confirmed)
        button.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(1, confirmed)
    }

    @Test
    fun holdReleasedEarlyCancels() {
        var confirmed = 0
        rule.setContent { FeltbookTheme { HoldToConfirmButton(title = "Рейк отдан", onConfirm = { confirmed++ }) } }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val button = rule.onNode(hasContentDescription("Рейк отдан"))
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300)
        button.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(0, confirmed)
        // Повторное удержание снова требует полных 0,6 с от текущего прогресса.
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        button.performTouchInput { up() }
        assertEquals(1, confirmed)
    }

    @Test
    fun holdTapDoesNotConfirmButAccessibilityActionDoes() {
        var confirmed = 0
        rule.setContent { FeltbookTheme { HoldToConfirmButton(title = "Закрыть игру", onConfirm = { confirmed++ }) } }
        val button = rule.onNode(hasContentDescription("Закрыть игру"))
        button.performClick()
        rule.waitForIdle()
        assertEquals(0, confirmed)
        button.performSemanticsAction(SemanticsActions.OnLongClick)
        assertEquals(1, confirmed)
        val custom = button.fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        custom.action()
        assertEquals(2, confirmed)
    }

    @Test
    fun disabledHoldIgnoresEverything() {
        var confirmed = 0
        rule.setContent {
            FeltbookTheme { HoldToConfirmButton(title = "Закрыть", onConfirm = { confirmed++ }, enabled = false) }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val button = rule.onNode(hasContentDescription("Закрыть"))
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(1_000)
        button.performTouchInput { up() }
        assertEquals(0, confirmed)
    }

    @Test
    fun holdAfterConfirmAndReEnableNeedsFullHoldAgain() {
        var confirmed = 0
        var enabled by mutableStateOf(true)
        rule.setContent {
            FeltbookTheme {
                HoldToConfirmButton(
                    title = "Выплатить",
                    onConfirm = {
                        confirmed++
                        enabled = false
                    },
                    enabled = enabled,
                )
            }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val button = rule.onNode(hasContentDescription("Выплатить"))
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(700)
        assertEquals(1, confirmed)
        button.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(100)
        // Операция завершилась — экран снова включает кнопку.
        enabled = true
        rule.mainClock.advanceTimeBy(100)
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(50)
        button.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1_000)
        assertEquals(1, confirmed)
    }

    @Test
    fun holdDisabledMidwayDoesNotConfirm() {
        var confirmed = 0
        var enabled by mutableStateOf(true)
        rule.setContent {
            FeltbookTheme { HoldToConfirmButton(title = "Рейк", onConfirm = { confirmed++ }, enabled = enabled) }
        }
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        val button = rule.onNode(hasContentDescription("Рейк"))
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(300)
        enabled = false
        rule.mainClock.advanceTimeBy(1_000)
        button.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(300)
        assertEquals(0, confirmed)
    }
}
