package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/**
 * Текстовое поле (имя, название, банкир; m3-ui-spec §4.12): подпись `small ledger`, значение `fieldValue`
 * ([large] — `fieldValueL` для имени игрока), рамка в фокусе 1.5 `focusBorder`, ошибка — текст и рамка `owe`.
 */
@Composable
fun FeltTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    error: String? = null,
    placeholder: String? = null,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.field
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val (borderWidth, borderColor) =
        when {
            error != null -> FeltbookDimens.focusBorderWidth to colors.oweBorderStrong
            focused -> FeltbookDimens.focusBorderWidth to colors.focusBorder
            else -> FeltbookDimens.hairlineWidth to colors.hairline
        }
    val valueStyle = (if (large) type.fieldValueL else type.fieldValue).merge(color = colors.ink)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = valueStyle,
            cursorBrush = SolidColor(colors.brass),
            interactionSource = interaction,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(color = colors.card)
                            .border(width = borderWidth, color = borderColor, shape = shape)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp),
                ) {
                    Text(text = label, style = type.small, color = colors.ledger)
                    Box {
                        if (value.isEmpty() && placeholder != null) {
                            Text(text = placeholder, style = valueStyle.merge(color = colors.muted), maxLines = 1)
                        }
                        inner()
                    }
                }
            },
        )
        if (error != null) Text(text = error, style = type.small, color = colors.owe)
    }
}

/**
 * Поле суммы (фишки на руках): системной клавиатуры нет — нажатие фокусирует поле ([onClick]),
 * экран показывает [AmountKeypad]. [focused] — состояние экрана. `0` показывается как «0».
 */
@Composable
fun AmountField(
    label: String,
    amount: Long,
    focused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    val text = MoneyFormat.number(amount)
    val description = contentDescription ?: "$label, $text"
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.card)
                .border(
                    width = if (focused) FeltbookDimens.focusBorderWidth else FeltbookDimens.hairlineWidth,
                    color = if (focused) colors.focusBorder else colors.hairline,
                    shape = shape,
                ).clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = description }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(
            text = label.uppercase(),
            style = type.fieldLabelCaps,
            color = if (focused) colors.muted else colors.ledger,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {},
        )
        AmountText(
            text = text,
            style = if (focused) type.amountBoard else type.amountInput,
            color = colors.ink,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** Значение правила (процент рейка): 96 × 46, r12, «10 %». Нажатие — шторка ввода с клавиатурой. */
@Composable
fun ValueBox(
    value: String,
    unit: String,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    selectedLabel: String? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            modifier
                .width(width = 96.dp)
                .clip(FeltbookTheme.shapes.key)
                .background(color = colors.card)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics {
                    this.contentDescription = contentDescription
                    if (selectedLabel != null) stateDescription = selectedLabel
                }.padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = type.fieldValueBold,
            color = colors.ink,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {
            },
        )
        Text(
            text = unit,
            style = type.fieldValueBold,
            color = colors.ledger,
            maxLines = 1,
            modifier = Modifier.clearAndSetSemantics {
            },
        )
    }
}

@Preview
@Composable
private fun FieldsPreview() {
    FeltbookTheme {
        Column(
            modifier = Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            FeltTextField(value = "Пятничный стол", onValueChange = {}, label = "Название")
            FeltTextField(value = "Влад", onValueChange = {
            }, label = "Имя", large = true, error = "Такой игрок уже за столом")
            AmountField(label = "Фишки на руках", amount = 2_700L, focused = true, onClick = {})
            ValueBox(value = "10", unit = "%", onClick = {}, contentDescription = "Рейк 10 %")
        }
    }
}
