package app.feltbook.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цветовые токены «Сукна» (docs/DESIGN.md). Других цветов в коде нет: экраны берут их только отсюда
 * через [FeltbookTheme.colors].
 */
@Immutable
data class FeltbookColors(
    /** Фон под шторками, нижняя навигация, «утопленные» поля. */
    val feltDeep: Color,
    /** Основной фон. */
    val felt: Color,
    /** Верх радиального градиента фона. */
    val feltGlow: Color,
    /** Bottom sheet, вторичные панели. */
    val sheet: Color,
    /** Карточки, поля ввода. */
    val card: Color,
    /** Табло-«стол», карточка активной игры. */
    val feltLight: Color,
    /** Конец градиента [feltLight]. */
    val feltLightEnd: Color,
    /** Борт овального стола. */
    val rail: Color,
    /** Главные кнопки, FAB, активные состояния, обводки. */
    val brass: Color,
    /** Светлый конец градиента латунной кнопки. */
    val brassGradientEnd: Color,
    /** Акцентный текст. */
    val brassLight: Color,
    /** Крупные «банкирские» суммы. */
    val brassBright: Color,
    /** Текст на латуни. */
    val onBrass: Color,
    /** Вторичный текст на латуни. */
    val onBrassSecondary: Color,
    /** Акцент темы: латунь в тёмной, зелёный в светлой «Бумаге и латуни». */
    val accent: Color,
    /** Текст на [accent]. */
    val onAccent: Color,
    /** Основной текст. */
    val ink: Color,
    /** Подписи. */
    val inkSoft: Color,
    /** Служебные строки (IBM Plex Mono). */
    val ledger: Color,
    /** Неактивные пункты. */
    val muted: Color,
    /** «Игроку выплатить», «оплачено». */
    val pay: Color,
    /** «С игрока получить», долг. */
    val owe: Color,
    /** Фон строки долга. */
    val oweBackground: Color,
    /** Карты-аватары, вставки фишек. */
    val ivory: Color,
    /** Червы и бубны на картах. */
    val suitRed: Color,
    /** Контур второстепенных кнопок. */
    val outline: Color,
    /** Номиналы фишек. */
    val chips: FeltbookChipColors,
)

/** Цвета фишек-номиналов: DESIGN.md + ADR 0003 (100 — `#C2423C` из Icon.dc.html, 10 000 — латунь). */
@Immutable
data class FeltbookChipColors(
    val chip100: Color,
    val chip500: Color,
    val chip1000: Color,
    val chip2000: Color,
    val chip5000: Color,
    val chip10000: Color,
    /** Пунктирный кант фишки. */
    val edge: Color,
)

private val ChipColors =
    FeltbookChipColors(
        chip100 = Color(0xFFC2423C),
        chip500 = Color(0xFF2E7D4F),
        chip1000 = Color(0xFF1C1C1E),
        chip2000 = Color(0xFFA8322D),
        chip5000 = Color(0xFF5B3C8C),
        chip10000 = Color(0xFFC9A35B),
        edge = Color(0xFFF3EDDC),
    )

/** Тёмная тема «Сукно» — по умолчанию. */
val FeltbookDarkColors: FeltbookColors =
    FeltbookColors(
        feltDeep = Color(0xFF071A14),
        felt = Color(0xFF0B2A20),
        feltGlow = Color(0xFF10382B),
        sheet = Color(0xFF0E2E23),
        card = Color(0xFF12352A),
        feltLight = Color(0xFF1B5A43),
        feltLightEnd = Color(0xFF0D3426),
        rail = Color(0xFF3A2A18),
        brass = Color(0xFFC9A35B),
        brassGradientEnd = Color(0xFFD9B66C),
        brassLight = Color(0xFFE2C27E),
        brassBright = Color(0xFFF0D79E),
        onBrass = Color(0xFF1A1408),
        onBrassSecondary = Color(0xFF4A3A1C),
        accent = Color(0xFFC9A35B),
        onAccent = Color(0xFF1A1408),
        ink = Color(0xFFEDE6D3),
        inkSoft = Color(0xFFCFE0D6),
        ledger = Color(0xFFB5AE96),
        muted = Color(0xFF9FB5AC),
        pay = Color(0xFF6EDBA4),
        owe = Color(0xFFFF8A80),
        oweBackground = Color(0x1FE5484D),
        ivory = Color(0xFFF3EDDC),
        suitRed = Color(0xFFB3261E),
        outline = Color(0x38EDE6D3),
        chips = ChipColors,
    )

/**
 * Светлая тема «Бумага и латунь». Из DESIGN.md заданы фон `#F6F2E8`, акцент `#0F4A37`, латунь `#8C6A2A`;
 * остальные значения — производные заглушки M0, уточняются при появлении макета светлой темы.
 */
val FeltbookLightColors: FeltbookColors =
    FeltbookColors(
        feltDeep = Color(0xFFECE5D3),
        felt = Color(0xFFF6F2E8),
        feltGlow = Color(0xFFFBF8F1),
        sheet = Color(0xFFFBF8F1),
        card = Color(0xFFFFFDF8),
        feltLight = Color(0xFF0F4A37),
        feltLightEnd = Color(0xFF0B3A2B),
        rail = Color(0xFF8C6A2A),
        brass = Color(0xFF8C6A2A),
        brassGradientEnd = Color(0xFFA27D36),
        brassLight = Color(0xFF8C6A2A),
        brassBright = Color(0xFF6E5220),
        onBrass = Color(0xFFFFFDF8),
        onBrassSecondary = Color(0xFFF3EDDC),
        accent = Color(0xFF0F4A37),
        onAccent = Color(0xFFF6F2E8),
        ink = Color(0xFF1B241F),
        inkSoft = Color(0xFF3D4A43),
        ledger = Color(0xFF6B6450),
        muted = Color(0xFF66766F),
        pay = Color(0xFF1E7A4E),
        owe = Color(0xFFB3261E),
        oweBackground = Color(0x1FE5484D),
        ivory = Color(0xFFF3EDDC),
        suitRed = Color(0xFFB3261E),
        outline = Color(0x381B241F),
        chips = ChipColors,
    )
