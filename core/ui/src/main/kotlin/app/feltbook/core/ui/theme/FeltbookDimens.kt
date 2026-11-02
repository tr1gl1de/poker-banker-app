package app.feltbook.core.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Отступы и размеры (docs/DESIGN.md «Формы и сетка», m3-ui-spec §4). Отступы кратны 4; сенсорные цели ≥ 48 dp.
 * Компоненты берут общие значения отсюда; размеры, которые встречаются в одном компоненте, — рядом с ним.
 */
object FeltbookDimens {
    /** Минимальная сенсорная цель (DoD). */
    val minTouchTarget = 48.dp

    /** Край экрана: обычный и компактный. */
    val screenEdge = 20.dp
    val screenEdgeCompact = 16.dp

    /** Между карточками. */
    val cardGap = 8.dp

    /** Между блоками. */
    val blockGap = 12.dp
    val blockGapLarge = 16.dp

    // Кнопки
    val buttonHero68 = 68.dp
    val buttonHero64 = 64.dp
    val button56 = 56.dp
    val button52 = 52.dp
    val button48 = 48.dp
    val dialogButton = 44.dp
    val iconButtonVisual = 44.dp

    // Иконки
    val iconL = 24.dp
    val icon = 22.dp
    val iconM = 20.dp
    val iconS = 18.dp

    // Клавиатура
    val keypadKeyHeight = 48.dp
    val keypadGap = 6.dp

    // Фишка
    val chip = 64.dp
    val chipEdge = 56.dp
    val chipCenter = 36.dp
    val chipEdgeStroke = 5.dp
    val chipInnerStroke = 1.5.dp
    val chipSelectionGap = 3.dp
    val chipSelectionRing = 2.dp
    val miniChip = 44.dp
    val miniChipEdge = 38.dp
    val miniChipEdgeStroke = 4.dp

    // Табло
    val scoreboardOvalHeight = 132.dp
    val railWidth = 6.dp
    val railInnerRing = 1.5.dp

    // Навигация
    val bottomBarHeight = 80.dp
    val bottomBarIndicatorWidth = 64.dp
    val bottomBarIndicatorHeight = 32.dp
    val fabHeight = 56.dp
    val fabAboveBar = 20.dp

    // Чипы и мелкие кнопки (визуальная высота; цель касания — 48)
    val deleteChipHeight = 40.dp
    val filterChipHeight = 36.dp
    val historyChipHeight = 34.dp
    val smallToggleHeight = 32.dp
    val dropdownChipHeight = 36.dp

    // Поля и панели
    val valueBoxWidth = 96.dp
    val valueBoxHeight = 46.dp
    val resultIconPlate = 44.dp

    // Шторка

    /** Шторка не выше «экран − statusBars − 56 dp». */
    val sheetTopGap = 56.dp
    val dragHandleWidth = 36.dp
    val dragHandleHeight = 4.dp

    // Рамки
    val hairlineWidth = 1.dp
    val focusBorderWidth = 1.5.dp
}
