package app.feltbook.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale

/**
 * Фон-сукно: `radial-gradient(120% 70% at 50% 0%, feltGlow 0%, felt 45%, feltDeep 100%)` из макетов.
 * Эллипс получаем, растягивая круговой градиент по горизонтали (`scaleX = radiusX / radiusY`).
 * Прямоугольник рисуется в уже масштабированном пространстве, поэтому его ширина — `width / scaleX`
 * со сдвигом влево: после масштабирования он ровно покрывает экран (на портретных телефонах
 * `scaleX < 1`, иначе по бокам оставались полосы сплошного `feltDeep`).
 */
@Composable
fun Modifier.feltBackground(): Modifier {
    val colors = FeltbookTheme.colors
    return drawWithCache {
        val radiusX = size.width * GRADIENT_WIDTH
        val radiusY = size.height * GRADIENT_HEIGHT
        val center = Offset(size.width / 2f, 0f)
        val brush =
            Brush.radialGradient(
                GRADIENT_START to colors.feltGlow,
                GRADIENT_MIDDLE to colors.felt,
                GRADIENT_END to colors.feltDeep,
                center = center,
                radius = radiusY.coerceAtLeast(1f),
            )
        val scaleX = radiusX.coerceAtLeast(1f) / radiusY.coerceAtLeast(1f)
        val scaledWidth = size.width / scaleX
        val topLeft = Offset(-(scaledWidth - size.width) / 2f, 0f)
        val scaledSize = Size(scaledWidth, size.height)
        onDrawBehind {
            scale(scaleX = scaleX, scaleY = 1f, pivot = center) {
                drawRect(brush, topLeft = topLeft, size = scaledSize)
            }
        }
    }
}

private const val GRADIENT_WIDTH = 1.2f
private const val GRADIENT_HEIGHT = 0.7f
private const val GRADIENT_START = 0f
private const val GRADIENT_MIDDLE = 0.45f
private const val GRADIENT_END = 1f
