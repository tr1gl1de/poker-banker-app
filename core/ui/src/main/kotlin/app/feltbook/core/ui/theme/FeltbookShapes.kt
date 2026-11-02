package app.feltbook.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Радиусы из docs/DESIGN.md. */
@Immutable
data class FeltbookShapes(
    val card: RoundedCornerShape = RoundedCornerShape(16.dp),
    val cardLarge: RoundedCornerShape = RoundedCornerShape(20.dp),
    val board: RoundedCornerShape = RoundedCornerShape(24.dp),
    /** Шторка — скругление только сверху. */
    val sheet: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    val button: RoundedCornerShape = RoundedCornerShape(18.dp),
    val chip: RoundedCornerShape = RoundedCornerShape(18.dp),
    val field: RoundedCornerShape = RoundedCornerShape(14.dp),
)

internal fun FeltbookShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = field,
    small = field,
    medium = card,
    large = cardLarge,
    extraLarge = sheet,
)
