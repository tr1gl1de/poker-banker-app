package app.feltbook.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale

/**
 * Эллиптический радиальный градиент CSS `radial-gradient(RX% RY% at X% Y%, …)` (m3-ui-spec §1.3).
 *
 * [center] — доли ширины/высоты, [radiusX] — доля ширины, [radiusY] — доля высоты. Эллипс получаем, растягивая
 * круговой градиент радиуса `radiusY` по горизонтали (`scaleX = rx / ry`). Прямоугольник рисуется в уже
 * масштабированном пространстве так, чтобы после масштабирования он ровно покрыл габарит; [shape] — обрезка.
 */
fun Modifier.ellipticalRadialGradient(
    colorStops: List<Pair<Float, Color>>,
    center: Offset,
    radiusX: Float,
    radiusY: Float,
    shape: Shape = RectangleShape,
): Modifier = drawWithCache {
    val rx = (size.width * radiusX).coerceAtLeast(1f)
    val ry = (size.height * radiusY).coerceAtLeast(1f)
    val pivot = Offset(size.width * center.x, size.height * center.y)

    // 3 стопа, копия массива — один раз в drawWithCache.
    @Suppress("SpreadOperator")
    val brush = Brush.radialGradient(*colorStops.toTypedArray(), center = pivot, radius = ry)
    val scaleX = rx / ry
    val left = pivot.x - pivot.x / scaleX
    val right = pivot.x + (size.width - pivot.x) / scaleX
    val rectTopLeft = Offset(left, 0f)
    val rectSize = Size(right - left, size.height)
    val clip =
        if (shape == RectangleShape) {
            null
        } else {
            Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        }
    onDrawBehind {
        val draw = {
            scale(scaleX = scaleX, scaleY = 1f, pivot = pivot) {
                drawRect(brush, topLeft = rectTopLeft, size = rectSize)
            }
        }
        if (clip == null) draw() else clipPath(clip) { draw() }
    }
}

/** Геометрия эллиптического градиента: центр и полуоси в долях габарита. */
@Immutable
data class EllipseGeometry(val center: Offset, val radiusX: Float, val radiusY: Float)

/** Именованные градиенты m3-ui-spec §1.3. */
object FeltbookGradients {
    /** Фон экрана: `120% 70% at 50% 0%`. */
    val Screen = EllipseGeometry(Offset(0.5f, 0f), radiusX = 1.2f, radiusY = 0.7f)

    /** Овал табло: `80% 90% at 50% 40%`. */
    val Table = EllipseGeometry(Offset(0.5f, 0.4f), radiusX = 0.8f, radiusY = 0.9f)

    /** Карточка активной игры на Главной: `90% 120% at 30% 0%`. */
    val ActiveGame = EllipseGeometry(Offset(0.3f, 0f), radiusX = 0.9f, radiusY = 1.2f)

    /** Карты калькулятора: `90% 120% at 50% 0%`. */
    val Odds = EllipseGeometry(Offset(0.5f, 0f), radiusX = 0.9f, radiusY = 1.2f)

    /** Стопы фона экрана: feltGlow → felt 45 % → feltDeep. */
    fun screenStops(colors: FeltbookColors): List<Pair<Float, Color>> =
        listOf(0f to colors.feltGlow, SCREEN_MIDDLE to colors.felt, 1f to colors.feltDeep)

    /** Стопы «стола»: feltLight → feltLightMid 60 % → feltLightEnd. */
    fun tableStops(colors: FeltbookColors): List<Pair<Float, Color>> =
        listOf(0f to colors.feltLight, TABLE_MIDDLE to colors.feltLightMid, 1f to colors.feltLightEnd)

    /** Латунная кнопка: сверху brassGradientEnd, снизу brass (`linear 180deg`). */
    fun brassButton(colors: FeltbookColors): Brush =
        Brush.verticalGradient(listOf(colors.brassGradientEnd, colors.brass))

    /** Панель «выплачивает»: payContainerTop → payContainerBottom. */
    fun payPanel(colors: FeltbookColors): Brush =
        Brush.verticalGradient(listOf(colors.payContainerTop, colors.payContainerBottom))

    private const val SCREEN_MIDDLE = 0.45f
    private const val TABLE_MIDDLE = 0.6f
}

/** Сукно «стола» (табло, карточка игры, калькулятор) в форме [shape]. */
@Composable
fun Modifier.feltTable(shape: Shape, geometry: EllipseGeometry = FeltbookGradients.Table): Modifier {
    val stops = FeltbookGradients.tableStops(FeltbookTheme.colors)
    return ellipticalRadialGradient(stops, geometry.center, geometry.radiusX, geometry.radiusY, shape)
}
