package app.feltbook.feature.settings

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
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.ui.component.FeltbookDialogCard
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.settings.main.PreviewSettingsStates
import app.feltbook.feature.settings.main.RuleField
import app.feltbook.feature.settings.main.RulesEditorContent
import app.feltbook.feature.settings.main.RulesEditorUi
import app.feltbook.feature.settings.main.SettingsScreen
import app.feltbook.feature.settings.main.importReportText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Эталоны `:feature:settings`: тёмная тема 390 × 844 и шрифт 200 %; шторки и диалоги — содержимым. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class SettingsScreenshotTest {
    @Test
    fun settings() = capture {
        SettingsScreen(state = PreviewSettingsStates.default(), onAction = {}, versionName = "1.0")
    }

    @Test
    fun settingsFontScale200() = capture(fontScale = 2f) {
        SettingsScreen(state = PreviewSettingsStates.default(), onAction = {}, versionName = "1.0")
    }

    @Test
    fun settingsWithSecretTable() = capture {
        SettingsScreen(state = PreviewSettingsStates.secret(), onAction = {}, versionName = "1.0")
    }

    @Test
    fun settingsWithoutDynamicColor() = capture {
        SettingsScreen(
            state = PreviewSettingsStates.default(),
            onAction = {},
            versionName = "1.0",
            dynamicColorAvailable = false,
        )
    }

    @Test
    fun feeEditor() = capture { EditorFrame(RuleField.BANKER_FEE) }

    @Test
    fun feeEditorFontScale200() = capture(fontScale = 2f) { EditorFrame(RuleField.BANKER_FEE) }

    @Test
    fun rakeEditor() = capture { EditorFrame(RuleField.RAKE) }

    @Test
    fun paymentEditor() = capture { EditorFrame(RuleField.PAYMENT) }

    @Test
    fun importReport() = capture {
        DialogFrame {
            FeltbookDialogCard(
                title = "Восстановлено",
                body = importReportText(PreviewSettingsStates.importDone()),
                safeLabel = "Понятно",
                onSafe = {},
                confirmLabel = "К истории",
                onConfirm = {},
                destructive = false,
            )
        }
    }

    @Composable
    private fun EditorFrame(field: RuleField) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(color = FeltbookTheme.colors.sheet)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            RulesEditorContent(editor = RulesEditorUi.open(field, GameRules.DEFAULT), onAction = {})
        }
    }

    @Composable
    private fun DialogFrame(content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().feltBackground().padding(all = 16.dp)) { content() }
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
