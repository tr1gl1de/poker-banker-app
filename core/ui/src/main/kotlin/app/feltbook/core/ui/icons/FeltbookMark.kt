package app.feltbook.core.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.PlayfairDisplay

/**
 * Логотип Главной (m3-ui-spec §3): `viewBox 0 0 108 108` — сукно, латунное кольцо, «F» Playfair Display Bold
 * цвета `ivory` (базовая линия y = 70) и латунная пика; снаружи — кольцо 1 dp `logoRing`. Декоративный:
 * название «Feltbook» стоит рядом текстом.
 */
@Composable
fun FeltbookMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.logo
    val measurer = rememberTextMeasurer()
    val spade = remember { PathParser().parsePathString(FeltbookIcons.SPADE_PATH).toPath() }
    Canvas(
        modifier =
            modifier
                .size(size = size)
                .drawBehind {
                    val ring = 1.dp.toPx()
                    val corner = shape.topStart.toPx(this.size, this) + ring / 2
                    drawRoundRect(
                        color = colors.logoRing,
                        topLeft = Offset(-ring / 2, -ring / 2),
                        size = Size(this.size.width + ring, this.size.height + ring),
                        cornerRadius = CornerRadius(corner, corner),
                        style = Stroke(width = ring),
                    )
                }.clip(shape),
    ) {
        val unit = this.size.width / VIEWBOX
        drawRect(colors.felt)
        drawCircle(
            color = colors.brass,
            radius = RING_RADIUS * unit,
            center = Offset(CENTER * unit, CENTER * unit),
            style = Stroke(width = RING_STROKE * unit),
        )
        val letter =
            measurer.measure(
                text = "F",
                style =
                    TextStyle(
                        fontFamily = PlayfairDisplay,
                        fontWeight = FontWeight.Bold,
                        fontSize = (LETTER_SIZE * unit).toSp(),
                        color = colors.ivory,
                    ),
            )
        drawText(
            textLayoutResult = letter,
            topLeft =
                Offset(
                    x = CENTER * unit - letter.size.width / 2f,
                    y = BASELINE * unit - letter.firstBaseline,
                ),
        )
        scale(scale = unit, pivot = Offset.Zero) {
            translate(left = SPADE_X, top = SPADE_Y) {
                scale(scale = SPADE_SCALE, pivot = Offset.Zero) { drawPath(spade, colors.brass) }
            }
        }
    }
}

private const val VIEWBOX = 108f
private const val CENTER = 54f
private const val RING_RADIUS = 34f
private const val RING_STROKE = 4f
private const val LETTER_SIZE = 46f
private const val BASELINE = 70f
private const val SPADE_X = 49f
private const val SPADE_Y = 74f
private const val SPADE_SCALE = 0.1f

@Preview
@Composable
private fun FeltbookMarkPreview() {
    FeltbookTheme { FeltbookMark() }
}
