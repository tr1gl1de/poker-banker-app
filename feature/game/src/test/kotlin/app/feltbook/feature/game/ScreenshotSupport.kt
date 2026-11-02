package app.feltbook.feature.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.common.LocalTimeZone
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneId

/** Снимок экрана в тёмной теме; [fontScale] = 2 — проверка «шрифт 200 % без обрезки» (DoD). */
fun captureScreen(fontScale: Float = 1f, content: @Composable () -> Unit) {
    captureRoboImage {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale),
            LocalTimeZone provides ZoneId.of("Europe/Moscow"),
        ) {
            FeltbookTheme(darkTheme = true) { content() }
        }
    }
}
