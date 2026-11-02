package app.feltbook.core.ui.component

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.feltbook.core.ui.theme.FeltbookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ТЗ §5.1: закрытие шторки с введённой суммой — «Отменить ввод?»; пустая шторка закрывается сразу. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class SheetDiscardTest {
    @get:Rule
    val rule = createComposeRule()

    private var dismissed = 0

    private fun setSheet(hasInput: Boolean) {
        rule.setContent {
            FeltbookTheme {
                FeltbookSheet(onDismiss = { dismissed++ }, hasInput = hasInput) {
                    Text(text = "Докуп · Дима")
                    val close = LocalSheetDismissRequest.current
                    OutlineButton(text = "Отмена", onClick = close)
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun `пустая шторка закрывается сразу, без вопроса`() {
        setSheet(hasInput = false)
        rule.onNodeWithText("Отмена").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
        rule.onNodeWithText("Отменить ввод?").assertDoesNotExist()
    }

    @Test
    fun `введена сумма — «Отменить ввод?», «Продолжить ввод» оставляет шторку`() {
        setSheet(hasInput = true)
        rule.onNodeWithText("Отмена").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Отменить ввод?").assertExists()
        assertEquals(0, dismissed)

        rule.onNodeWithText("Продолжить ввод").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Отменить ввод?").assertDoesNotExist()
        rule.onNodeWithText("Докуп · Дима").assertExists()
        assertEquals(0, dismissed)
    }

    @Test
    fun `введена сумма — «Отменить ввод» закрывает шторку`() {
        setSheet(hasInput = true)
        rule.onNodeWithText("Отмена").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Отменить ввод").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissed)
    }
}
