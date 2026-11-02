package app.feltbook.feature.settings.odds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.feltbook.core.ui.theme.FeltbookTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Эталоны калькулятора шансов: тёмная тема 390 × 844 и шрифт 200 %; шторки — содержимым без окна. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class OddsScreenshotTest {
    @Test
    fun oddsFlop() = capture { Screen(PreviewOddsStates.flop()) }

    @Test
    fun oddsFlopFontScale200() = capture(fontScale = 2f) { Screen(PreviewOddsStates.flop()) }

    @Test
    fun oddsEmpty() = capture { Screen(PreviewOddsStates.empty()) }

    @Test
    fun oddsPreflop() = capture { Screen(PreviewOddsStates.preflop()) }

    @Test
    fun oddsCalculatingTurn() = capture { Screen(PreviewOddsStates.calculating()) }

    @Test
    fun oddsIncompleteFlop() = capture { Screen(PreviewOddsStates.incompleteFlop()) }

    @Test
    fun oddsRiverTenPlayers() = capture { Screen(PreviewOddsStates.riverTenPlayers()) }

    @Test
    fun oddsRiverTenPlayersFontScale200() = capture(fontScale = 2f) { Screen(PreviewOddsStates.riverTenPlayers()) }

    @Test
    fun cardPicker() = capture { Picker(PreviewOddsStates.pickerSlot()) }

    @Test
    fun cardPickerFontScale200() = capture(fontScale = 2f) { Picker(PreviewOddsStates.pickerSlot()) }

    @Test
    fun deadCardsPicker() = capture { Picker(PreviewOddsStates.pickerDead()) }

    @Test
    fun amountSheet() = capture {
        SheetFrame {
            OddsAmountContent(editor = checkNotNull(PreviewOddsStates.amountEditor().amountEditor), onAction = {})
        }
    }

    @Test
    fun amountSheetFontScale200() = capture(fontScale = 2f) {
        SheetFrame {
            OddsAmountContent(editor = checkNotNull(PreviewOddsStates.amountEditor().amountEditor), onAction = {})
        }
    }

    /** Экран без шторок: [OddsScreen] сам открывает `ModalBottomSheet`, если в состоянии есть picker/amount. */
    @Composable
    private fun Screen(state: OddsUiState) {
        OddsScreen(state = state.copy(picker = null, amountEditor = null), onAction = {}, onBack = {})
    }

    @Composable
    private fun Picker(state: OddsUiState) {
        SheetFrame { CardPickerContent(picker = checkNotNull(state.picker), onAction = {}) }
    }

    private fun capture(fontScale: Float = 1f, content: @Composable () -> Unit) {
        captureRoboImage {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FeltbookTheme { content() }
            }
        }
    }
}
