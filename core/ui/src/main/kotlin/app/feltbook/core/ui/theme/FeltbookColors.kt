package app.feltbook.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Цветовые токены «Сукна» (docs/DESIGN.md, m3-ui-spec §1). Других цветов в коде нет: экраны берут их только
 * отсюда через [FeltbookTheme.colors]. Полупрозрачные токены задаются здесь (`base.copy(alpha = …)`), экран
 * никогда не вызывает `copy(alpha)` сам.
 */
// Набор токенов темы — плоский data class по образцу Material ColorScheme.
@Suppress("LongParameterList")
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
    /** 60 % градиента «стола». */
    val feltLightMid: Color,
    /** Конец градиента [feltLight]. */
    val feltLightEnd: Color,
    /** Борт овального стола. */
    val rail: Color,
    /** Главные кнопки, FAB, активные состояния, обводки. */
    val brass: Color,
    /** Светлый (верхний) конец градиента латунной кнопки. */
    val brassGradientEnd: Color,
    /** Акцентный текст. */
    val brassLight: Color,
    /** Крупные «банкирские» суммы. */
    val brassBright: Color,
    /** Вторичный текст на латунной плитке. */
    val brassMuted: Color,
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
    /** Светлый «гроссбух»: мета-строка активной игры, «↳»-строки журнала. */
    val ledgerLight: Color,
    /** Неактивные пункты. */
    val muted: Color,
    /** «Игроку выплатить», «оплачено». */
    val pay: Color,
    /** Плашка иконки «выплачивает». */
    val payIcon: Color,
    /** «С игрока получить», долг. */
    val owe: Color,
    /** Подпись на плашке долга. */
    val oweSoft: Color,
    /** Текст внутри красных панелей. */
    val oweTint: Color,
    /** Плашка иконки «доплачивает». */
    val oweIcon: Color,
    /** Карты-аватары, вставки фишек. */
    val ivory: Color,
    /** Червы и бубны на картах. */
    val suitRed: Color,
    /** Пики и трефы, инициал на картах. */
    val suitBlack: Color,
    /** Фон карты-аватара вышедшего игрока. */
    val cardInactive: Color,
    /** Незаполненная часть hold-кнопки. */
    val holdTrack: Color,
    /** Поверхность диалога и snackbar. */
    val dialog: Color,
    /** Тени (альфа — в спецификации тени). */
    val shadow: Color,
    /** Внутренний блик 1 dp сверху у кнопки «Выдать». */
    val brassHighlight: Color,
    /** Затемнение под шторкой и диалогом. */
    val scrim: Color,
    // --- от ink ---
    /** Заливка контурных кнопок (ink 0.05). */
    val outlinedFill: Color,
    /** Круглые иконки-кнопки, клавиши клавиатуры (ink 0.06). */
    val quietFill: Color,
    /** Разделители строк, рамка неактивного поля (ink 0.08). */
    val hairline: Color,
    /** Черта над итоговой строкой (ink 0.12). */
    val divider: Color,
    /** Рамка диалога (ink 0.14). */
    val dialogBorder: Color,
    /** Контур второстепенных кнопок (ink 0.22). */
    val outline: Color,
    /** Рамка кнопки «Оставить» в диалоге (ink 0.25). */
    val outlineDialog: Color,
    /** Ручка шторки (ink 0.30). */
    val dragHandle: Color,
    /** Рамка выключенного переключателя (ink 0.35). */
    val switchOffBorder: Color,
    /** Рамка пустого чекбокса (ink 0.40). */
    val checkboxOffBorder: Color,
    /** Недоступное содержимое контурной кнопки (ink 0.38). */
    val inkDisabled: Color,
    // --- от brass ---
    /** Фон выбранной строки (brass 0.12). */
    val brassSelectedRow: Color,
    /** Фон латунной плитки, граница нижней панели (brass 0.14). */
    val brassContainer: Color,
    /** Рамка плиток табло и итога (brass 0.16). */
    val brassHairline: Color,
    /** Рамка блока «Правила» (brass 0.20). */
    val brassBorderSoft: Color,
    /** Индикатор вкладки, фон выбранного чипа (brass 0.22). */
    val brassIndicator: Color,
    /** Рамка «Акта передачи», «Памятки» (brass 0.25). */
    val brassBorderMedium: Color,
    /** Верхняя кромка шторки (brass 0.28). */
    val sheetEdge: Color,
    /** Рамка блока карт калькулятора (brass 0.35). */
    val brassBorderOdds: Color,
    /** Кольцо логотипа (brass 0.40). */
    val logoRing: Color,
    /** Рамка карточки активной игры, ячеек кода (brass 0.45). */
    val brassBorder: Color,
    /** «докуп ×N», плитка «♥ Банкиру», «Поделиться» (brass 0.50). */
    val brassBorderStrong: Color,
    /** Рамка поля в фокусе, внутреннее кольцо овала (brass 0.55). */
    val focusBorder: Color,
    /** Inset-рамка выбранного сегмента (brass 0.60). */
    val selectedBorder: Color,
    /** Фон недоступной латунной кнопки (brass 0.38). */
    val brassDisabled: Color,
    /** Текст недоступной латунной кнопки (onBrass 0.6). */
    val onBrassDisabled: Color,
    // --- от muted, owe, pay, ivory, inkSoft ---
    /** Рамка невыбранного чипа (muted 0.35). */
    val chipBorder: Color,
    /** Фон красных панелей (#E5484D 0.12). */
    val oweBackground: Color,
    /** Заливка «Не заплатил» (#E5484D 0.08). */
    val oweBackgroundSoft: Color,
    /** Кнопка «Удалить» в шапке мультивыбора (#E5484D 0.14). */
    val oweBackgroundStrong: Color,
    /** Плашка «Долг» (owe 0.40). */
    val oweBorder: Color,
    /** Рамка «Не заплатил» (owe 0.45). */
    val oweBorderMedium: Color,
    /** Панели «доплачивает», «Не оплачено» (owe 0.50). */
    val oweBorderStrong: Color,
    /** «Проигрыш» на полосе эквити (owe 0.55). */
    val oweBar: Color,
    /** Баннер «Фишки сошлись» (#4CC38A 0.12). */
    val payContainer: Color,
    /** Верх градиента панели «выплачивает» (#4CC38A 0.18). */
    val payContainerTop: Color,
    /** Низ градиента панели «выплачивает» (#4CC38A 0.08). */
    val payContainerBottom: Color,
    /** Плашка иконки телефона (pay 0.15). */
    val payIconContainer: Color,
    /** Баннер «Фишки сошлись» (pay 0.40). */
    val payBorder: Color,
    /** Плитка «Шансы банка» (pay 0.45). */
    val payBorderMedium: Color,
    /** Панель «выплачивает» (pay 0.55). */
    val payBorderStrong: Color,
    /** Кольцо центра фишки (ivory 0.70). */
    val chipInnerRing: Color,
    /** Пунктир «+ тёрн» (ivory 0.45). */
    val placeholderBorder: Color,
    /** Пунктир «+ ривер» (ivory 0.25). */
    val placeholderBorderDisabled: Color,
    /** «+» недоступного слота (inkSoft 0.50). */
    val inkSoftDisabled: Color,
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

/** Сплошные цвета темы; полупрозрачные токены выводятся из них в [toColors]. */
private data class Palette(
    val feltDeep: Color,
    val felt: Color,
    val feltGlow: Color,
    val sheet: Color,
    val card: Color,
    val feltLight: Color,
    val feltLightMid: Color,
    val feltLightEnd: Color,
    val rail: Color,
    val brass: Color,
    val brassGradientEnd: Color,
    val brassLight: Color,
    val brassBright: Color,
    val brassMuted: Color,
    val onBrass: Color,
    val onBrassSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val ink: Color,
    val inkSoft: Color,
    val ledger: Color,
    val ledgerLight: Color,
    val muted: Color,
    val pay: Color,
    val payIcon: Color,
    val owe: Color,
    val oweSoft: Color,
    val oweTint: Color,
    val oweIcon: Color,
    val ivory: Color,
    val suitRed: Color,
    val suitBlack: Color,
    val cardInactive: Color,
    val holdTrack: Color,
    val dialog: Color,
    val scrim: Color,
    /** База зелёных панелей (`#4CC38A` в тёмной). */
    val payPanelBase: Color,
    /** База красных панелей (`#E5484D`). */
    val owePanelBase: Color,
)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

// Таблица токенов: одно присваивание на токен, дробить бессмысленно.
@Suppress("LongMethod")
private fun Palette.toColors(): FeltbookColors = FeltbookColors(
    feltDeep = feltDeep,
    felt = felt,
    feltGlow = feltGlow,
    sheet = sheet,
    card = card,
    feltLight = feltLight,
    feltLightMid = feltLightMid,
    feltLightEnd = feltLightEnd,
    rail = rail,
    brass = brass,
    brassGradientEnd = brassGradientEnd,
    brassLight = brassLight,
    brassBright = brassBright,
    brassMuted = brassMuted,
    onBrass = onBrass,
    onBrassSecondary = onBrassSecondary,
    accent = accent,
    onAccent = onAccent,
    ink = ink,
    inkSoft = inkSoft,
    ledger = ledger,
    ledgerLight = ledgerLight,
    muted = muted,
    pay = pay,
    payIcon = payIcon,
    owe = owe,
    oweSoft = oweSoft,
    oweTint = oweTint,
    oweIcon = oweIcon,
    ivory = ivory,
    suitRed = suitRed,
    suitBlack = suitBlack,
    cardInactive = cardInactive,
    holdTrack = holdTrack,
    dialog = dialog,
    shadow = Black,
    brassHighlight = White.copy(alpha = 0.35f),
    scrim = scrim,
    outlinedFill = ink.copy(alpha = 0.05f),
    quietFill = ink.copy(alpha = 0.06f),
    hairline = ink.copy(alpha = 0.08f),
    divider = ink.copy(alpha = 0.12f),
    dialogBorder = ink.copy(alpha = 0.14f),
    outline = ink.copy(alpha = 0.22f),
    outlineDialog = ink.copy(alpha = 0.25f),
    dragHandle = ink.copy(alpha = 0.30f),
    switchOffBorder = ink.copy(alpha = 0.35f),
    checkboxOffBorder = ink.copy(alpha = 0.40f),
    inkDisabled = ink.copy(alpha = 0.38f),
    brassSelectedRow = brass.copy(alpha = 0.12f),
    brassContainer = brass.copy(alpha = 0.14f),
    brassHairline = brass.copy(alpha = 0.16f),
    brassBorderSoft = brass.copy(alpha = 0.20f),
    brassIndicator = brass.copy(alpha = 0.22f),
    brassBorderMedium = brass.copy(alpha = 0.25f),
    sheetEdge = brass.copy(alpha = 0.28f),
    brassBorderOdds = brass.copy(alpha = 0.35f),
    logoRing = brass.copy(alpha = 0.40f),
    brassBorder = brass.copy(alpha = 0.45f),
    brassBorderStrong = brass.copy(alpha = 0.50f),
    focusBorder = brass.copy(alpha = 0.55f),
    selectedBorder = brass.copy(alpha = 0.60f),
    brassDisabled = brass.copy(alpha = 0.38f),
    onBrassDisabled = onBrass.copy(alpha = 0.6f),
    chipBorder = muted.copy(alpha = 0.35f),
    oweBackground = owePanelBase.copy(alpha = 0.12f),
    oweBackgroundSoft = owePanelBase.copy(alpha = 0.08f),
    oweBackgroundStrong = owePanelBase.copy(alpha = 0.14f),
    oweBorder = owe.copy(alpha = 0.40f),
    oweBorderMedium = owe.copy(alpha = 0.45f),
    oweBorderStrong = owe.copy(alpha = 0.50f),
    oweBar = owe.copy(alpha = 0.55f),
    payContainer = payPanelBase.copy(alpha = 0.12f),
    payContainerTop = payPanelBase.copy(alpha = 0.18f),
    payContainerBottom = payPanelBase.copy(alpha = 0.08f),
    payIconContainer = pay.copy(alpha = 0.15f),
    payBorder = pay.copy(alpha = 0.40f),
    payBorderMedium = pay.copy(alpha = 0.45f),
    payBorderStrong = pay.copy(alpha = 0.55f),
    chipInnerRing = ivory.copy(alpha = 0.70f),
    placeholderBorder = ivory.copy(alpha = 0.45f),
    placeholderBorderDisabled = ivory.copy(alpha = 0.25f),
    inkSoftDisabled = inkSoft.copy(alpha = 0.50f),
    chips = ChipColors,
)

/** Тёмная тема «Сукно» — по умолчанию. */
val FeltbookDarkColors: FeltbookColors =
    Palette(
        feltDeep = Color(0xFF071A14),
        felt = Color(0xFF0B2A20),
        feltGlow = Color(0xFF10382B),
        sheet = Color(0xFF0E2E23),
        card = Color(0xFF12352A),
        feltLight = Color(0xFF1B5A43),
        feltLightMid = Color(0xFF12432F),
        feltLightEnd = Color(0xFF0D3426),
        rail = Color(0xFF3A2A18),
        brass = Color(0xFFC9A35B),
        brassGradientEnd = Color(0xFFD9B66C),
        brassLight = Color(0xFFE2C27E),
        brassBright = Color(0xFFF0D79E),
        brassMuted = Color(0xFFCFC2A0),
        onBrass = Color(0xFF1A1408),
        onBrassSecondary = Color(0xFF4A3A1C),
        accent = Color(0xFFC9A35B),
        onAccent = Color(0xFF1A1408),
        ink = Color(0xFFEDE6D3),
        inkSoft = Color(0xFFCFE0D6),
        ledger = Color(0xFFB5AE96),
        ledgerLight = Color(0xFFD8CFB4),
        muted = Color(0xFF9FB5AC),
        pay = Color(0xFF6EDBA4),
        payIcon = Color(0xFF2E7D4F),
        owe = Color(0xFFFF8A80),
        oweSoft = Color(0xFFFFB4AC),
        oweTint = Color(0xFFFFD0CA),
        oweIcon = Color(0xFF8E2B2B),
        ivory = Color(0xFFF3EDDC),
        suitRed = Color(0xFFB3261E),
        suitBlack = Color(0xFF1A1A1A),
        cardInactive = Color(0xFF2A4A3E),
        holdTrack = Color(0xFF8C7444),
        dialog = Color(0xFF1A3D31),
        scrim = Color(0xFF071A14).copy(alpha = 0.45f),
        payPanelBase = Color(0xFF4CC38A),
        owePanelBase = Color(0xFFE5484D),
    ).toColors()

/**
 * Светлая тема «Бумага и латунь». Из DESIGN.md заданы фон `#F6F2E8`, акцент `#0F4A37`, латунь `#8C6A2A`;
 * остальное — предложения m3-ui-spec §1.2 (светлого макета нет), альфа-токены — та же альфа от светлых баз.
 */
val FeltbookLightColors: FeltbookColors =
    Palette(
        feltDeep = Color(0xFFECE5D3),
        felt = Color(0xFFF6F2E8),
        feltGlow = Color(0xFFFBF8F1),
        sheet = Color(0xFFFBF8F1),
        card = Color(0xFFFFFDF8),
        feltLight = Color(0xFF0F4A37),
        feltLightMid = Color(0xFF0D4231),
        feltLightEnd = Color(0xFF0B3A2B),
        rail = Color(0xFF8C6A2A),
        brass = Color(0xFF8C6A2A),
        brassGradientEnd = Color(0xFFA27D36),
        brassLight = Color(0xFF8C6A2A),
        brassBright = Color(0xFF6E5220),
        brassMuted = Color(0xFF6E5A33),
        onBrass = Color(0xFFFFFDF8),
        onBrassSecondary = Color(0xFFF3EDDC),
        accent = Color(0xFF0F4A37),
        onAccent = Color(0xFFF6F2E8),
        ink = Color(0xFF1B241F),
        inkSoft = Color(0xFF3D4A43),
        ledger = Color(0xFF6B6450),
        ledgerLight = Color(0xFF5A5444),
        muted = Color(0xFF66766F),
        pay = Color(0xFF1E7A4E),
        payIcon = Color(0xFF2E7D4F),
        owe = Color(0xFFB3261E),
        oweSoft = Color(0xFF8C1D18),
        oweTint = Color(0xFF7A1A15),
        oweIcon = Color(0xFF8E2B2B),
        ivory = Color(0xFFF3EDDC),
        suitRed = Color(0xFFB3261E),
        suitBlack = Color(0xFF1A1A1A),
        cardInactive = Color(0xFFD9D2C0),
        holdTrack = Color(0xFFC9B48A),
        dialog = Color(0xFFFFFDF8),
        scrim = Color(0xFF1B241F).copy(alpha = 0.32f),
        payPanelBase = Color(0xFF1E7A4E),
        owePanelBase = Color(0xFFE5484D),
    ).toColors()
