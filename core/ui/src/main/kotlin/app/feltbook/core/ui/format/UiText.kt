package app.feltbook.core.ui.format

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalResources

/**
 * Текст, который ViewModel отдаёт экрану без доступа к ресурсам (snackbar, ошибки, строки журнала).
 * Аргументы — уже отформатированные строки/числа или вложенные [UiText].
 */
@Immutable
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Plural(@PluralsRes val id: Int, val quantity: Int, val args: List<Any> = emptyList()) : UiText

    /** Склейка частей без разделителя: «Закуп 1 000» + « · в долг». */
    data class Concat(val parts: List<UiText>) : UiText

    data class Raw(val text: String) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

operator fun UiText.plus(other: UiText): UiText = UiText.Concat(
    (if (this is UiText.Concat) parts else listOf(this)) + other,
)

@Suppress("SpreadOperator") // getString принимает только vararg; аргументов единицы.
fun UiText.resolve(resources: Resources): String = when (this) {
    is UiText.Res -> resources.getString(id, *args.map { it.resolveArg(resources) }.toTypedArray())

    is UiText.Plural ->
        resources.getQuantityString(id, quantity, *args.map { it.resolveArg(resources) }.toTypedArray())

    is UiText.Concat -> parts.joinToString(separator = "") { it.resolve(resources) }

    is UiText.Raw -> text
}

private fun Any.resolveArg(resources: Resources): Any = if (this is UiText) resolve(resources) else this

@Composable
fun UiText.resolve(): String = resolve(LocalResources.current)
