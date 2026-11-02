package app.feltbook.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
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

/**
 * Именованные стили m3-ui-spec §2. Экранам размер шрифта задавать нельзя — только эти токены.
 * Вес 800 из макетов везде = [FontWeight.Bold] (у IBM Plex Sans нет ExtraBold). Все стили Plex — с `tnum`.
 */
// Набор токенов темы — плоский data class по образцу Material Typography.
@Suppress("LongParameterList")
@Immutable
data class FeltbookTypography(
    // --- Playfair Display, 700 ---
    /** «Feltbook» на Главной: 30, ls 0.3, lh 1. */
    val brand: TextStyle,
    /** Заголовок «Настройки», «История»: 26. */
    val displayL: TextStyle,
    /** Заголовки экранов, название игры на карточке, имя в карточке игрока: 24. */
    val display: TextStyle,
    /** Название игры в шапке Стола: 24, ls 0.2. */
    val displayTable: TextStyle,
    /** Заголовки шторок: 22. */
    val displayS: TextStyle,
    /** Ранг своей карты калькулятора: 24, lh 1. */
    val cardRankL: TextStyle,
    /** Инициал аватара 44 × 60. */
    val avatarInitialL: TextStyle,
    /** Инициал аватара 34 × 46. */
    val avatarInitialM: TextStyle,
    /** Карта стола калькулятора: 16, lh 1. */
    val cardRankBoard: TextStyle,
    /** Инициал аватара 30 × 40. */
    val avatarInitialS: TextStyle,
    /** Инициал аватара 28 × 38. */
    val avatarInitialXS: TextStyle,
    // --- IBM Plex Sans ---
    val amountHero: TextStyle,
    val amountEquity: TextStyle,
    val amountBoard: TextStyle,
    val amountInput: TextStyle,
    val amountResult: TextStyle,
    val amountTotal: TextStyle,
    val amountCard: TextStyle,
    val amountStat: TextStyle,
    val amountAct: TextStyle,
    val amountTile: TextStyle,
    val headlineSans: TextStyle,
    val keypadDigit: TextStyle,
    val keypadDigitSmall: TextStyle,
    val buttonHero: TextStyle,
    val amountMetric: TextStyle,
    val fieldValueL: TextStyle,
    val dialogTitle: TextStyle,
    val buttonL: TextStyle,
    val fieldValue: TextStyle,
    val fieldValueBold: TextStyle,
    val button: TextStyle,
    val emphasisM: TextStyle,
    val titleM: TextStyle,
    val sectionTitle: TextStyle,
    val amountRow: TextStyle,
    val title: TextStyle,
    val bodyL: TextStyle,
    val labelBold: TextStyle,
    val labelSemi: TextStyle,
    val body: TextStyle,
    /** Текст диалога: body с lh 1.45. */
    val bodyDialog: TextStyle,
    val chipBold: TextStyle,
    val chip: TextStyle,
    val caption: TextStyle,
    val chipSecondary: TextStyle,
    /** «ИДЁТ ИГРА» — текст переводится в верхний регистр в UI. */
    val overline: TextStyle,
    /** «ФИШЕК В ИГРЕ» (uppercase). */
    val overlineBoard: TextStyle,
    /** «ФИШКИ НА РУКАХ» (uppercase). */
    val fieldLabelCaps: TextStyle,
    val small: TextStyle,
    val smallSemi: TextStyle,
    val navSelected: TextStyle,
    val tiny: TextStyle,
    val tinyBold: TextStyle,
    /** «МОИ КАРТЫ» (uppercase). */
    val tinyCaps: TextStyle,
    val chipDenomination: TextStyle,
    val suitXL: TextStyle,
    val suitL: TextStyle,
    val suitBoard: TextStyle,
    val suitM: TextStyle,
    val suitS: TextStyle,
    val suitXS: TextStyle,
    val suitTab: TextStyle,
    val glyphButton: TextStyle,
    // --- IBM Plex Mono ---
    val codeDigit: TextStyle,
    val ledgerL: TextStyle,
    val ledger: TextStyle,
    val ledgerTight: TextStyle,
    /** Заголовки секций «ОКТЯБРЬ 2026» (uppercase). */
    val ledgerCaps: TextStyle,
    val ledgerS: TextStyle,
    /** «БАНК ПОКЕРНОГО СТОЛА» (uppercase). */
    val taglineCaps: TextStyle,
    val ledgerSCaps: TextStyle,
    val positionLabel: TextStyle,
    val positionLabelSelected: TextStyle,
)

private const val DISPLAY_LINE_HEIGHT = 1.2f
private const val TEXT_LINE_HEIGHT = 1.35f

private val CenteredLineHeight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun playfair(size: Float, letterSpacing: Float = 0f, lineHeight: Float = DISPLAY_LINE_HEIGHT): TextStyle =
    TextStyle(
        fontFamily = PlayfairDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        letterSpacing = letterSpacing.sp,
        lineHeightStyle = CenteredLineHeight,
    )

private fun sans(
    size: Float,
    weight: FontWeight,
    letterSpacing: Float = 0f,
    lineHeight: Float = TEXT_LINE_HEIGHT,
): TextStyle = TextStyle(
    fontFamily = IbmPlexSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = TABULAR_NUMBERS,
    lineHeightStyle = CenteredLineHeight,
)

private fun mono(
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Float = 0f,
    lineHeight: Float = TEXT_LINE_HEIGHT,
): TextStyle = TextStyle(
    fontFamily = IbmPlexMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = TABULAR_NUMBERS,
    lineHeightStyle = CenteredLineHeight,
)

private val Bold = FontWeight.Bold
private val Semi = FontWeight.SemiBold
private val Regular = FontWeight.Normal

val DefaultFeltbookTypography: FeltbookTypography =
    FeltbookTypography(
        brand = playfair(30f, letterSpacing = 0.3f, lineHeight = 1f),
        displayL = playfair(26f),
        display = playfair(24f),
        displayTable = playfair(24f, letterSpacing = 0.2f),
        displayS = playfair(22f),
        cardRankL = playfair(24f, lineHeight = 1f),
        avatarInitialL = playfair(22f, lineHeight = 1f),
        avatarInitialM = playfair(17f, lineHeight = 1f),
        cardRankBoard = playfair(16f, lineHeight = 1f),
        avatarInitialS = playfair(15f, lineHeight = 1f),
        avatarInitialXS = playfair(14f, lineHeight = 1f),
        amountHero = sans(52f, Bold, letterSpacing = -1f, lineHeight = 1.05f),
        amountEquity = sans(44f, Bold, letterSpacing = -1f, lineHeight = 1f),
        amountBoard = sans(40f, Bold, letterSpacing = -0.5f, lineHeight = 1.1f),
        amountInput = sans(36f, Bold, lineHeight = 1.15f),
        amountResult = sans(34f, Bold, letterSpacing = -0.5f, lineHeight = 1.15f),
        amountTotal = sans(28f, Bold, lineHeight = 1.2f),
        amountCard = sans(26f, Bold, lineHeight = 1.2f),
        amountStat = sans(24f, Bold, lineHeight = 1.2f),
        amountAct = sans(22f, Bold, lineHeight = 1.2f),
        amountTile = sans(20f, Bold, lineHeight = 1.1f),
        headlineSans = sans(20f, Semi),
        keypadDigit = sans(20f, Semi),
        keypadDigitSmall = sans(18f, Semi),
        buttonHero = sans(18f, Bold, lineHeight = 1.15f),
        amountMetric = sans(18f, Bold),
        fieldValueL = sans(18f, Semi),
        dialogTitle = sans(18f, Bold),
        buttonL = sans(17f, Bold, lineHeight = 1.2f),
        fieldValue = sans(17f, Semi),
        fieldValueBold = sans(17f, Bold),
        button = sans(16f, Bold),
        emphasisM = sans(16f, Bold),
        titleM = sans(16f, Semi),
        sectionTitle = sans(15f, Bold),
        amountRow = sans(15f, Bold),
        title = sans(15f, Semi),
        bodyL = sans(15f, Regular),
        labelBold = sans(14f, Bold),
        labelSemi = sans(14f, Semi),
        body = sans(14f, Regular),
        bodyDialog = sans(14f, Regular, lineHeight = 1.45f),
        chipBold = sans(13f, Bold),
        chip = sans(13f, Semi),
        caption = sans(13f, Regular),
        chipSecondary = sans(13f, Semi),
        overline = sans(12f, Bold, letterSpacing = 1.2f),
        overlineBoard = sans(12f, Regular, letterSpacing = 1.4f),
        fieldLabelCaps = sans(12f, Regular, letterSpacing = 1f),
        small = sans(12f, Regular),
        smallSemi = sans(12f, Semi),
        navSelected = sans(12f, Bold),
        tiny = sans(11f, Regular),
        tinyBold = sans(11f, Bold, lineHeight = 1.2f),
        tinyCaps = sans(11f, Regular, letterSpacing = 1f),
        chipDenomination = sans(12f, Bold, lineHeight = 1f),
        suitXL = sans(20f, Regular, lineHeight = 1f),
        suitL = sans(15f, Regular, lineHeight = 1f),
        suitBoard = sans(13f, Regular, lineHeight = 1f),
        suitM = sans(12f, Regular, lineHeight = 1f),
        suitS = sans(11f, Regular, lineHeight = 1f),
        suitXS = sans(10f, Regular, lineHeight = 1f),
        suitTab = sans(18f, Regular, lineHeight = 1f),
        glyphButton = sans(20f, Regular, lineHeight = 1f),
        codeDigit = mono(32f, FontWeight.Medium, lineHeight = 1.1f),
        ledgerL = mono(13f),
        ledger = mono(12f),
        ledgerTight = mono(12f, letterSpacing = -0.2f),
        ledgerCaps = mono(12f, FontWeight.Medium, letterSpacing = 1.4f),
        ledgerS = mono(11f),
        taglineCaps = mono(11f, letterSpacing = 1.6f),
        ledgerSCaps = mono(11f, letterSpacing = 1f),
        positionLabel = mono(12f),
        positionLabelSelected = mono(12f, FontWeight.Medium),
    )

/**
 * Material 3 целиком на токенах [DefaultFeltbookTypography]: display/headline — Playfair Display,
 * title/body/label — IBM Plex Sans.
 */
internal fun materialTypography(type: FeltbookTypography = DefaultFeltbookTypography): Typography = Typography(
    displayLarge = playfair(30f),
    displayMedium = playfair(28f),
    displaySmall = type.displayL,
    headlineLarge = type.displayL,
    headlineMedium = type.display,
    headlineSmall = type.displayS,
    titleLarge = type.buttonL,
    titleMedium = type.titleM,
    titleSmall = type.title,
    bodyLarge = type.bodyL,
    bodyMedium = type.body,
    bodySmall = type.caption,
    labelLarge = type.labelBold,
    labelMedium = type.smallSemi,
    labelSmall = type.small,
)
