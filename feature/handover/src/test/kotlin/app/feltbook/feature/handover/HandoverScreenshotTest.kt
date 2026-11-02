package app.feltbook.feature.handover

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

/** Эталоны экрана «Передача комнаты» (src/test/screenshots): тёмная тема 390 × 844; ключевые — и при шрифте 200 %. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class HandoverScreenshotTest {
    @Test
    fun giveCode() = capture { SendPreview(PreviewHandoverData.code) }

    @Test
    fun giveCodeFontScale200() = capture(fontScale = 2f) { SendPreview(PreviewHandoverData.code) }

    @Test
    fun giveCodeKeepIncome() = capture { SendPreview(PreviewHandoverData.codeKeepIncome) }

    @Test
    fun giveSearching() = capture { SendPreview(PreviewHandoverData.searching) }

    @Test
    fun giveChooseDevice() = capture { SendPreview(PreviewHandoverData.choose) }

    @Test
    fun giveSending() = capture { SendPreview(PreviewHandoverData.sending) }

    @Test
    fun giveAwaitingAccept() = capture { SendPreview(PreviewHandoverData.awaiting) }

    @Test
    fun givePermission() = capture { SendPreview(PreviewHandoverData.permission) }

    @Test
    fun giveFailedAckLost() = capture { SendPreview(PreviewHandoverData.failed) }

    @Test
    fun giveFailedFontScale200() = capture(fontScale = 2f) { SendPreview(PreviewHandoverData.failed) }

    @Test
    fun giveNoPlayServices() = capture { SendPreview(PreviewHandoverData.noGms) }

    @Test
    fun giveDone() = capture { SendPreview(PreviewHandoverData.done) }

    @Test
    fun giveTemplate() = capture { SendPreview(PreviewHandoverData.template) }

    @Test
    fun receiveName() = capture { ReceivePreview(PreviewHandoverData.receiveName) }

    @Test
    fun receiveWaiting() = capture { ReceivePreview(PreviewHandoverData.receiveWaiting) }

    @Test
    fun receiveCode() = capture { ReceivePreview(PreviewHandoverData.receiveCode) }

    @Test
    fun receiveCodeFontScale200() = capture(fontScale = 2f) { ReceivePreview(PreviewHandoverData.receiveCode) }

    @Test
    fun receiveReview() = capture { ReceivePreview(PreviewHandoverData.review) }

    @Test
    fun receiveReviewFontScale200() = capture(fontScale = 2f) { ReceivePreview(PreviewHandoverData.review) }

    @Test
    fun receiveReviewError() = capture { ReceivePreview(PreviewHandoverData.reviewError) }

    @Test
    fun receiveFile() = capture { ReceivePreview(PreviewHandoverData.reviewFile) }

    @Test
    fun receiveTemplate() = capture { ReceivePreview(PreviewHandoverData.reviewTemplate) }

    @Test
    fun receiveCorruptedFile() = capture { ReceivePreview(PreviewHandoverData.receiveCorrupted) }

    @Test
    fun giveCodeLight() = capture(darkTheme = false) { SendPreview(PreviewHandoverData.code) }

    private fun capture(fontScale: Float = 1f, darkTheme: Boolean = true, content: @Composable () -> Unit) {
        captureRoboImage {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FeltbookTheme(darkTheme = darkTheme, content = content)
            }
        }
    }
}
