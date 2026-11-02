package app.feltbook.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/** Радиусы из docs/DESIGN.md и m3-ui-spec §4 (`r16` = `RoundedCornerShape(16.dp)`). */
@Immutable
data class FeltbookShapes(
    /** Карточка, плитка табло, кнопки 52–56, AmountField. */
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    val cardLarge: RoundedCornerShape = RoundedCornerShape(20.dp),
    val board: RoundedCornerShape = RoundedCornerShape(24.dp),
    /** Шторка — скругление только сверху. */
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val button: RoundedCornerShape = RoundedCornerShape(18.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(18.dp),
    /** Поля, кнопка 48, контейнер сегментов. */
    val field: RoundedCornerShape = RoundedCornerShape(14.dp),
    /** Значки «докуп ×N», «БАНКИР». */
    val badge: RoundedCornerShape = RoundedCornerShape(6.dp),
    /** Клавиши клавиатуры, ValueBox, контейнер компактных сегментов. */
    val key: RoundedCornerShape = RoundedCornerShape(12.dp),
    /** Сегмент обычного переключателя. */
    val segment: RoundedCornerShape = RoundedCornerShape(10.dp),
    /** Сегмент компактного переключателя. */
    val segmentCompact: RoundedCornerShape = RoundedCornerShape(9.dp),
    /** SmallToggle. */
    val smallToggle: RoundedCornerShape = RoundedCornerShape(10.dp),
    /** Hero-кнопки 64/68, ResultPanel. */
    val hero: RoundedCornerShape = RoundedCornerShape(20.dp),
    /** Кнопки диалога (h44), DeleteActionChip. */
    val pill: RoundedCornerShape = RoundedCornerShape(percent = 50),
    /** Диалог. */
    val dialog: RoundedCornerShape = RoundedCornerShape(24.dp),
    /** Ручка шторки. */
    val dragHandle: RoundedCornerShape = RoundedCornerShape(2.dp),
    /** Овал табло (стадион). */
    val stadium: RoundedCornerShape = RoundedCornerShape(percent = 50),
    /** Логотип 40 × 40. */
    val logo: RoundedCornerShape = RoundedCornerShape(12.dp),
    val avatarL: RoundedCornerShape = RoundedCornerShape(8.dp),
    val avatarM: RoundedCornerShape = RoundedCornerShape(6.dp),
    val avatarS: RoundedCornerShape = RoundedCornerShape(5.dp),
    val circle: Shape = CircleShape,
)

internal fun FeltbookShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = field,
    small = field,
    medium = card,
    large = cardLarge,
    extraLarge = sheet,
)
