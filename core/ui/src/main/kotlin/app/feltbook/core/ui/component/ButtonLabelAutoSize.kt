package app.feltbook.core.ui.component

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Авторазмер подписи кнопки: сначала наибольший кегль, при котором подпись помещается **в одну строку**;
 * если такого нет до [minFontSize] — наибольший, при котором она помещается в разрешённые строки
 * с переносом только по пробелам (слово не рвётся посередине). Иначе — [minFontSize].
 */
internal data class ButtonLabelAutoSize(
    val minFontSize: TextUnit,
    val maxFontSize: TextUnit,
    val step: TextUnit = 0.5.sp,
) : TextAutoSize {
    override fun TextAutoSizeLayoutScope.getFontSize(constraints: Constraints, text: AnnotatedString): TextUnit {
        val sizes = generateSequence(maxFontSize.value) { it - step.value }
            .takeWhile { it >= minFontSize.value }
            .map { it.sp }
            .toList()
            .ifEmpty { listOf(minFontSize) }
        sizes.firstOrNull { size ->
            val layout = performLayout(constraints, text, size)
            layout.lineCount == 1 && !layout.hasVisualOverflow
        }?.let { return it }
        return sizes.firstOrNull { size ->
            val layout = performLayout(constraints, text, size)
            !layout.hasVisualOverflow && layout.breaksOnlyAtSpaces(text.text)
        } ?: minFontSize
    }
}

private fun TextLayoutResult.breaksOnlyAtSpaces(text: String): Boolean = (0 until lineCount - 1).all { line ->
    val end = getLineEnd(line)
    end <= 0 || end >= text.length || text[end - 1] == ' ' || text[end] == ' '
}
