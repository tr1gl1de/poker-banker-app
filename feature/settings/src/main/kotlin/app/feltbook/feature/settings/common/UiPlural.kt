package app.feltbook.feature.settings.common

import androidx.annotation.PluralsRes
import app.feltbook.core.ui.format.UiText

fun uiPlural(@PluralsRes id: Int, quantity: Int, vararg args: Any): UiText = UiText.Plural(id, quantity, args.toList())
