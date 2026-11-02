package app.feltbook.feature.history.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.component.FeltbookDialogCard
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import app.feltbook.feature.history.debts.DebtPayContent
import app.feltbook.feature.history.debts.DebtPaySheetUi
import app.feltbook.feature.history.debts.DebtsScreen
import app.feltbook.feature.history.debts.PreviewDebtsStates
import app.feltbook.feature.history.detail.GameDetailScreen
import app.feltbook.feature.history.detail.PreviewDetailStates
import app.feltbook.feature.history.list.HistoryScreen
import app.feltbook.feature.history.list.PreviewHistoryStates
import app.feltbook.feature.history.stats.PlayerSheetContent
import app.feltbook.feature.history.stats.PreviewStatsStates
import app.feltbook.feature.history.stats.StatsScreen
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneOffset
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Эталоны экранов `:feature:history` (src/test/screenshots): тёмная тема 390 × 844 и шрифт 200 %.
 * Диалоги и шторки — их содержимым (окна Dialog/ModalBottomSheet в снимок экрана не попадают).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class HistoryScreenshotTest {
    @Test
    fun history() = capture { HistoryScreen(state = PreviewHistoryStates.list(), onAction = {}) }

    @Test
    fun historyFontScale200() = capture(fontScale = 2f) {
        HistoryScreen(state = PreviewHistoryStates.list(), onAction = {})
    }

    @Test
    fun historySelecting() = capture { HistoryScreen(state = PreviewHistoryStates.selecting(), onAction = {}) }

    @Test
    fun historyEmpty() = capture { HistoryScreen(state = PreviewHistoryStates.empty(), onAction = {}) }

    @Test
    fun historyDeleteDialog() = capture {
        Column(Modifier.fillMaxWidth().feltBackground().padding(all = 16.dp)) {
            FeltbookDialogCard(
                title = "Удалить 2 игры?",
                body = "Журналы, расчёты и статистика этих игр исчезнут с телефона. Отменить можно 5 секунд.",
                safeLabel = "Оставить",
                onSafe = {},
                confirmLabel = "Удалить 2 игры",
                onConfirm = {},
            )
        }
    }

    @Test
    fun gameDetail() = capture { GameDetailScreen(state = PreviewDetailStates.detail(), onAction = {}) }

    @Test
    fun gameDetailFontScale200() = capture(fontScale = 2f) {
        GameDetailScreen(state = PreviewDetailStates.tournament(), onAction = {})
    }

    @Test
    fun stats() = capture { StatsScreen(state = PreviewStatsStates.stats(), onAction = {}) }

    @Test
    fun statsFontScale200() = capture(fontScale = 2f) {
        StatsScreen(state = PreviewStatsStates.stats(), onAction = {})
    }

    @Test
    fun playerSheet() = capture { SheetFrame { PlayerSheetContent(sheet = PreviewStatsStates.sheet(), onAction = {}) } }

    @Test
    fun playerSheetFontScale200() = capture(fontScale = 2f) {
        SheetFrame { PlayerSheetContent(sheet = PreviewStatsStates.sheet(), onAction = {}) }
    }

    @Test
    fun playerSheetMergePicker() = capture {
        SheetFrame {
            PlayerSheetContent(sheet = PreviewStatsStates.sheet(name = "Боря", picking = true), onAction = {})
        }
    }

    @Test
    fun debts() = capture { DebtsScreen(state = PreviewDebtsStates.debts(), onAction = {}) }

    @Test
    fun debtsFontScale200() = capture(fontScale = 2f) { DebtsScreen(state = PreviewDebtsStates.debts(), onAction = {}) }

    @Test
    fun debtsEmpty() = capture { DebtsScreen(state = PreviewDebtsStates.empty(), onAction = {}) }

    @Test
    fun debtPaySheet() = capture { DebtPayFrame() }

    @Test
    fun debtPaySheetFontScale200() = capture(fontScale = 2f) { DebtPayFrame() }

    @Composable
    private fun DebtPayFrame() {
        val card = PreviewDebtsStates.debts().cards.first()
        SheetFrame {
            Column(verticalArrangement = Arrangement.spacedBy(space = 12.dp)) {
                DebtPayContent(
                    sheet = DebtPaySheetUi(card, AmountEntry.preset(card.amount)),
                    busy = false,
                    onAction = {},
                )
            }
        }
    }

    @Composable
    private fun SheetFrame(content: @Composable () -> Unit) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(color = FeltbookTheme.colors.sheet)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        ) { content() }
    }

    private fun capture(fontScale: Float = 1f, content: @Composable () -> Unit) {
        captureRoboImage {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FeltbookTheme {
                    CompositionLocalProvider(LocalHistoryTimeZone provides ZoneOffset.UTC) { content() }
                }
            }
        }
    }
}
