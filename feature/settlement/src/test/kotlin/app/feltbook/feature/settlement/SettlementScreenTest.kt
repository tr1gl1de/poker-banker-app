package app.feltbook.feature.settlement

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.theme.FeltbookTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Итог игры на цифрах ТЗ §3.6: сверка, рейк, банкир, касса 853 = 500 + 353, текст «Поделиться». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class SettlementScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun showsSpec36Totals() {
        rule.setContent { FeltbookTheme { SettlementScreen(state = PreviewSettlementData.settled(), onAction = {}) } }
        val n = MoneyFormat::number
        rule.onNodeWithText("Фишки сошлись: выдано ${n(5_000)} = сдано ${n(5_000)}").assertExists()
        rule.onNodeWithText(MoneyFormat.rub(853)).assertExists()
        rule.onNodeWithContentDescription("Заведению · рейк", substring = true).assertExists()
        // AmountText склеивает пробелы в неразрывные.
        rule.onNodeWithText(nb("платит ${MoneyFormat.rub(2_300)}")).assertExists()
        rule.onNodeWithText(nb("получает ${MoneyFormat.rub(3_700)}")).assertExists()
        rule.onNodeWithText(nb("платит ${MoneyFormat.rub(1_053)}")).assertExists()
        rule.onNodeWithContentDescription("Рейк отдан · закрыть игру").assertExists()
    }

    @Test
    fun shareText() {
        val resources = RuntimeEnvironment.getApplication().resources
        val text = settlementShareText(resources, PreviewSettlementData.settled(), ZoneId.of("Europe/Moscow"))
        val expected = listOf(
            "Feltbook · Пятничный стол · 18${MoneyFormat.NBSP}окт",
            "Андрей платит ${MoneyFormat.rub(2_300)}",
            "Борис получает ${MoneyFormat.rub(3_700)}",
            "Влад платит ${MoneyFormat.rub(1_053)}",
            "Рейк заведению ${MoneyFormat.rub(500)} · Банкиру ${MoneyFormat.rub(353)}",
            "Фишки сошлись: ${MoneyFormat.number(5_000)}",
        ).joinToString("\n")
        assertEquals(expected, text)
    }

    @Test
    fun shareTextMarksPlayersStillSeated() {
        val resources = RuntimeEnvironment.getApplication().resources
        val text = settlementShareText(resources, PreviewSettlementData.stillPlaying(), ZoneId.of("Europe/Moscow"))
        assert(text.contains("Андрей — ещё играет")) { text }
        assert(text.contains("Влад — ещё играет")) { text }
    }
}

private fun nb(text: String): String = text.replace(' ', MoneyFormat.NBSP)

/** Скриншоты итога: тёмная тема и шрифт 200 %. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class SettlementScreenshotTest {
    private fun capture(state: SettlementUiState, fontScale: Float = 1f) = captureRoboImage {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            FeltbookTheme(darkTheme = true) { SettlementScreen(state = state, onAction = {}) }
        }
    }

    @Test
    fun settled() = capture(PreviewSettlementData.settled())

    @Test
    fun settledFontScale200() = capture(PreviewSettlementData.settled(), fontScale = 2f)

    @Test
    fun withDebt() = capture(PreviewSettlementData.withDebt())

    @Test
    fun withDebtFontScale200() = capture(PreviewSettlementData.withDebt(), fontScale = 2f)

    @Test
    fun stillPlaying() = capture(PreviewSettlementData.stillPlaying())
}
