package app.feltbook.core.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

val LocalFeltbookColors = staticCompositionLocalOf { FeltbookDarkColors }
val LocalFeltbookTypography = staticCompositionLocalOf { DefaultFeltbookTypography }
val LocalFeltbookShapes = staticCompositionLocalOf { FeltbookShapes() }

/** Настройка «Вибрация при подтверждении»: hold-кнопки и клавиатура вибрируют, только если `true`. */
val LocalConfirmHaptics = staticCompositionLocalOf { true }

/**
 * Тема «Сукно». Тёмная — по умолчанию; светлая «Бумага и латунь» и Dynamic Color — только по настройке.
 * Dynamic Color меняет лишь Material colorScheme, фирменные токены [FeltbookTheme.colors] остаются.
 */
@Composable
fun FeltbookTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    confirmHaptics: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) FeltbookDarkColors else FeltbookLightColors
    val shapes = FeltbookShapes()
    val colorScheme =
        if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            colors.toColorScheme(darkTheme)
        }
    CompositionLocalProvider(
        LocalFeltbookColors provides colors,
        LocalFeltbookTypography provides DefaultFeltbookTypography,
        LocalFeltbookShapes provides shapes,
        LocalConfirmHaptics provides confirmHaptics,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialTypography(),
            shapes = shapes.toMaterialShapes(),
            content = content,
        )
    }
}

/** Доступ к токенам: `FeltbookTheme.colors.brass`. */
object FeltbookTheme {
    val colors: FeltbookColors
        @Composable @ReadOnlyComposable
        get() = LocalFeltbookColors.current

    val typography: FeltbookTypography
        @Composable @ReadOnlyComposable
        get() = LocalFeltbookTypography.current

    val shapes: FeltbookShapes
        @Composable @ReadOnlyComposable
        get() = LocalFeltbookShapes.current
}

internal fun FeltbookColors.toColorScheme(dark: Boolean): ColorScheme = if (dark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = feltLight,
        onPrimaryContainer = ink,
        secondary = brassLight,
        onSecondary = onBrass,
        tertiary = pay,
        background = felt,
        onBackground = ink,
        surface = sheet,
        onSurface = ink,
        surfaceVariant = card,
        onSurfaceVariant = inkSoft,
        surfaceContainerLowest = feltDeep,
        surfaceContainerLow = felt,
        surfaceContainer = sheet,
        surfaceContainerHigh = card,
        surfaceContainerHighest = card,
        outline = outline,
        outlineVariant = outline,
        error = owe,
        onError = onBrass,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = feltLight,
        onPrimaryContainer = onAccent,
        secondary = brass,
        onSecondary = onBrass,
        tertiary = pay,
        background = felt,
        onBackground = ink,
        surface = sheet,
        onSurface = ink,
        surfaceVariant = card,
        onSurfaceVariant = inkSoft,
        surfaceContainerLowest = card,
        surfaceContainerLow = sheet,
        surfaceContainer = felt,
        surfaceContainerHigh = feltDeep,
        surfaceContainerHighest = feltDeep,
        outline = outline,
        outlineVariant = outline,
        error = owe,
        onError = onAccent,
    )
}
