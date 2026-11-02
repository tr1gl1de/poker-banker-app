package app.feltbook.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Фон-сукно: `radial-gradient(120% 70% at 50% 0%, feltGlow 0%, felt 45%, feltDeep 100%)` из макетов
 * (частный случай [ellipticalRadialGradient]).
 */
@Composable
fun Modifier.feltBackground(): Modifier {
    val geometry = FeltbookGradients.Screen
    return ellipticalRadialGradient(
        colorStops = FeltbookGradients.screenStops(FeltbookTheme.colors),
        center = geometry.center,
        radiusX = geometry.radiusX,
        radiusY = geometry.radiusY,
    )
}
