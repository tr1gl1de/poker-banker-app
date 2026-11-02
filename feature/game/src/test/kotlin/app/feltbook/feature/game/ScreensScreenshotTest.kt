package app.feltbook.feature.game

import app.feltbook.feature.game.home.HomeScreen
import app.feltbook.feature.game.home.HomeUiState
import app.feltbook.feature.game.home.PreviewHomeState
import app.feltbook.feature.game.newgame.NewGameScreen
import app.feltbook.feature.game.newgame.PreviewNewGameState
import app.feltbook.feature.game.table.PreviewTableData
import app.feltbook.feature.game.table.TableScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Экраны `:feature:game` (src/test/screenshots): тёмная тема, 390 × 844 dp, и шрифт 200 %. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class ScreensScreenshotTest {
    @Test
    fun home() = captureScreen { HomeScreen(state = PreviewHomeState, onAction = {}) }

    @Test
    fun homeFontScale200() = captureScreen(fontScale = 2f) { HomeScreen(state = PreviewHomeState, onAction = {}) }

    @Test
    fun homeEmpty() = captureScreen { HomeScreen(state = HomeUiState(loading = false), onAction = {}) }

    @Test
    fun newGame() = captureScreen { NewGameScreen(state = PreviewNewGameState, onAction = {}) }

    @Test
    fun newGameFontScale200() = captureScreen(fontScale = 2f) {
        NewGameScreen(state = PreviewNewGameState, onAction = {})
    }

    @Test
    fun table() = captureScreen { TableScreen(state = PreviewTableData.table(), onAction = {}, forceSinglePane = true) }

    @Test
    fun tableFontScale200() = captureScreen(fontScale = 2f) {
        TableScreen(state = PreviewTableData.table(), onAction = {}, forceSinglePane = true)
    }

    @Test
    fun tableSettling() =
        captureScreen { TableScreen(state = PreviewTableData.settling(), onAction = {}, forceSinglePane = true) }

    @Test
    fun tableSettlingFontScale200() = captureScreen(fontScale = 2f) {
        TableScreen(state = PreviewTableData.settling(), onAction = {}, forceSinglePane = true)
    }

    @Test
    fun tableEmpty() = captureScreen {
        TableScreen(state = PreviewTableData.empty(), onAction = {}, forceSinglePane = true)
    }

    /** Планшет 840 dp: список + карточка игрока (m3-ui-spec §9). */
    @Test
    @Config(qualifiers = "ru-w840dp-h900dp-xhdpi")
    fun tableTablet() = captureScreen { TableScreen(state = PreviewTableData.tablet(), onAction = {}) }
}
