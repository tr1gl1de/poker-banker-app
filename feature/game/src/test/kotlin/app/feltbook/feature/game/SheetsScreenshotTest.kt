package app.feltbook.feature.game

import androidx.compose.runtime.CompositionLocalProvider
import app.feltbook.core.ui.component.LocalCompactSheet
import app.feltbook.feature.game.table.PreviewTableData
import app.feltbook.feature.game.table.SheetPreviewContent
import app.feltbook.feature.game.table.TableUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Шторки стола — содержимое без окна (`ModalBottomSheet` рисуется в отдельном окне и в снимок не попадает).
 * Высокое окно, чтобы при шрифте 200 % содержимое было видно целиком.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h2400dp-xxhdpi")
class SheetsScreenshotTest {
    private fun sheet(state: TableUiState, fontScale: Float = 1f) =
        captureScreen(fontScale) { SheetPreviewContent(state) }

    @Test
    fun playerCard() = sheet(PreviewTableData.card())

    @Test
    fun playerCardFontScale200() = sheet(PreviewTableData.card(), fontScale = 2f)

    @Test
    fun playerCardLeft() = sheet(PreviewTableData.card(name = "Борис"))

    @Test
    fun buyIn() = sheet(PreviewTableData.buyIn())

    @Test
    fun buyInFontScale200() = sheet(PreviewTableData.buyIn(), fontScale = 2f)

    /** Низкий экран (Redmi 6A): меньше сумма, фишки 48 dp, без подсказки. */
    @Test
    fun buyInCompact() = captureScreen {
        CompositionLocalProvider(LocalCompactSheet provides true) { SheetPreviewContent(PreviewTableData.buyIn()) }
    }

    @Test
    fun buyInEmpty() = sheet(PreviewTableData.buyIn(chips = 0))

    @Test
    fun buyInSuspicious() = sheet(PreviewTableData.suspiciousBuyIn())

    @Test
    fun buyInBelowMinimum() = sheet(PreviewTableData.buyIn(chips = 300))

    @Test
    fun newPlayer() = sheet(PreviewTableData.newPlayer())

    @Test
    fun newPlayerAmount() = sheet(PreviewTableData.newPlayer(name = "Егор", chips = 1_000, amountFocused = true))

    @Test
    fun newPlayerDebtWarning() = sheet(PreviewTableData.newPlayerWithDebt())

    @Test
    fun newPlayerDebtWarningFontScale200() = sheet(PreviewTableData.newPlayerWithDebt(), fontScale = 2f)

    @Test
    fun pay() = sheet(PreviewTableData.pay())

    @Test
    fun payFontScale200() = sheet(PreviewTableData.pay(), fontScale = 2f)

    @Test
    fun cashOutPay() = sheet(PreviewTableData.cashOutPay())

    @Test
    fun cashOutPayFontScale200() = sheet(PreviewTableData.cashOutPay(), fontScale = 2f)

    @Test
    fun cashOutOwe() = sheet(PreviewTableData.cashOutOwe())

    @Test
    fun cashOutOweFontScale200() = sheet(PreviewTableData.cashOutOwe(), fontScale = 2f)

    @Test
    fun cashOutKeypad() = sheet(PreviewTableData.cashOutKeypad())

    @Test
    fun breakdownCash() = sheet(PreviewTableData.breakdown())

    @Test
    fun breakdownDebtsFontScale200() =
        sheet(PreviewTableData.breakdown(app.feltbook.feature.game.table.BreakdownKind.DEBTS), fontScale = 2f)
}
