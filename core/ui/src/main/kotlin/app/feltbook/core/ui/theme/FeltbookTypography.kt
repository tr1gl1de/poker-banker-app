package app.feltbook.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.feltbook.core.ui.R

/** Только OFL-шрифты из res/font (железное правило №8). */
val PlayfairDisplay: FontFamily =
    FontFamily(
        Font(R.font.playfair_display_regular, FontWeight.Normal),
        Font(R.font.playfair_display_bold, FontWeight.Bold),
        Font(R.font.playfair_display_black, FontWeight.Black),
    )

val IbmPlexSans: FontFamily =
    FontFamily(
        Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
        Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
    )

val IbmPlexMono: FontFamily =
    FontFamily(
        Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    )

/** Табличные цифры: столбцы сумм не прыгают. */
const val TABULAR_NUMBERS: String = "tnum"

/** Роли типографики из docs/DESIGN.md. */
@Immutable
data class FeltbookTypography(
    /** Бренд, заголовки экранов: Playfair Display 22–30 sp, 700. */
    val display: TextStyle,
    /** Крупные суммы: IBM Plex Sans 34–52 sp, 700, tnum. */
    val amount: TextStyle,
    /** Заголовок карточки: IBM Plex Sans 15–17 sp, 600. */
    val title: TextStyle,
    /** Основной текст: IBM Plex Sans 14–15 sp, 400. */
    val body: TextStyle,
    /** Подписи и кнопки: IBM Plex Sans 12–13 sp, 600. */
    val label: TextStyle,
    /** Служебные строки (время, закуп, журнал): IBM Plex Mono 11–13 sp. */
    val ledger: TextStyle,
)

val DefaultFeltbookTypography: FeltbookTypography =
    FeltbookTypography(
        display = TextStyle(fontFamily = PlayfairDisplay, fontWeight = FontWeight.Bold, fontSize = 24.sp),
        amount =
            TextStyle(
                fontFamily = IbmPlexSans,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                fontFeatureSettings = TABULAR_NUMBERS,
            ),
        title = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
        body = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.Normal, fontSize = 15.sp),
        label = TextStyle(fontFamily = IbmPlexSans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        ledger =
            TextStyle(
                fontFamily = IbmPlexMono,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                fontFeatureSettings = TABULAR_NUMBERS,
            ),
    )

/**
 * Material 3 целиком на токенах DESIGN.md, размеры — самые частые в макетах (`docs/mockups`):
 * display/headline — Playfair Display 700, 22–30 sp; title — IBM Plex Sans 600, 15–17 sp;
 * body — IBM Plex Sans 400, 14–15 sp; label — IBM Plex Sans 600, 12–13 sp.
 */
internal fun materialTypography(): Typography = Typography(
    displayLarge = playfair(30),
    displayMedium = playfair(28),
    displaySmall = playfair(26),
    headlineLarge = playfair(26),
    headlineMedium = playfair(24),
    headlineSmall = playfair(22),
    titleLarge = plexSans(17, FontWeight.SemiBold),
    titleMedium = plexSans(16, FontWeight.SemiBold),
    titleSmall = plexSans(15, FontWeight.SemiBold),
    bodyLarge = plexSans(15, FontWeight.Normal),
    bodyMedium = plexSans(14, FontWeight.Normal),
    bodySmall = plexSans(14, FontWeight.Normal),
    labelLarge = plexSans(13, FontWeight.SemiBold),
    labelMedium = plexSans(12, FontWeight.SemiBold),
    labelSmall = plexSans(12, FontWeight.SemiBold),
)

private fun playfair(size: Int): TextStyle = TextStyle(
    fontFamily = PlayfairDisplay,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size * DISPLAY_LINE_HEIGHT).sp,
    letterSpacing = 0.sp,
)

private fun plexSans(size: Int, weight: FontWeight): TextStyle = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * TEXT_LINE_HEIGHT).sp,
    letterSpacing = 0.sp,
)

private const val DISPLAY_LINE_HEIGHT = 1.2f
private const val TEXT_LINE_HEIGHT = 1.35f
