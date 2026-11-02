package app.feltbook.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookTheme

/** Тень CSS `0 Y B rgba(0,0,0,a)` → `shadow(y, blur, alpha)` из m3-ui-spec; цвет — токен `shadow`. */
@Composable
fun Modifier.feltShadow(shape: Shape, offsetY: Dp, blur: Dp, alpha: Float): Modifier = dropShadow(
    shape = shape,
    shadow =
        Shadow(
            radius = blur,
            color = FeltbookTheme.colors.shadow,
            offset = DpOffset(x = 0.dp, y = offsetY),
            alpha = alpha,
        ),
)

/** Тень hero-кнопок `shadow(10, 24, 0.4)`. */
@Composable
internal fun Modifier.heroShadow(shape: Shape): Modifier =
    feltShadow(shape, offsetY = 10.dp, blur = 24.dp, alpha = 0.4f)

/** Тень диалога и snackbar `shadow(16, 40, 0.5)`. */
@Composable
internal fun Modifier.dialogShadow(shape: Shape): Modifier =
    feltShadow(shape, offsetY = 16.dp, blur = 40.dp, alpha = 0.5f)
