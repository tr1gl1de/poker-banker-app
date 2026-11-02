package app.feltbook.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.LocalConfirmHaptics
import app.feltbook.core.ui.theme.feltBackground

/** Тег клавиши для UI-тестов: `keypad_1`, `keypad_000`, `keypad_backspace`. */
fun keypadTestTag(key: KeypadKey): String = when (key) {
    is KeypadKey.Digit -> "keypad_${key.value}"
    KeypadKey.TripleZero -> "keypad_000"
    KeypadKey.Backspace, KeypadKey.Clear -> "keypad_backspace"
}

private val Rows: List<List<KeypadKey>> =
    listOf(
        listOf(KeypadKey.Digit(value = 1), KeypadKey.Digit(value = 2), KeypadKey.Digit(value = 3)),
        listOf(KeypadKey.Digit(value = 4), KeypadKey.Digit(value = 5), KeypadKey.Digit(value = 6)),
        listOf(KeypadKey.Digit(value = 7), KeypadKey.Digit(value = 8), KeypadKey.Digit(value = 9)),
        listOf(KeypadKey.TripleZero, KeypadKey.Digit(value = 0), KeypadKey.Backspace),
    )

/**
 * Цифровая клавиатура 3 × 4 (m3-ui-spec §4.3): `1 2 3 / 4 5 6 / 7 8 9 / 000 0 ⌫`, клавиши 48 dp (QUESTIONS №19),
 * долгое «Стереть» — [KeypadKey.Clear]. Системная клавиатура не открывается никогда.
 */
@Composable
fun AmountKeypad(onKey: (KeypadKey) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = FeltbookDimens.keypadGap),
    ) {
        Rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(space = FeltbookDimens.keypadGap)) {
                row.forEach { key -> KeypadButton(key = key, onKey = onKey, enabled = enabled) }
            }
        }
    }
}

/**
 * Клавиатура поверх [AmountEntry]: применяет [AmountEntry.press]; отклонённое нажатие (больше [maxValue]) —
 * короткая вибрация отказа, состояние не меняется.
 */
@Composable
fun AmountKeypad(
    entry: AmountEntry,
    onEntryChange: (AmountEntry) -> Unit,
    modifier: Modifier = Modifier,
    maxValue: Long = DEFAULT_MAX_AMOUNT,
    enabled: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val haptics = LocalConfirmHaptics.current
    AmountKeypad(
        onKey = { key ->
            val next = entry.press(key, maxValue)
            when {
                next == null -> if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Reject)
                next != entry -> onEntryChange(next)
            }
        },
        modifier = modifier,
        enabled = enabled,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.KeypadButton(key: KeypadKey, onKey: (KeypadKey) -> Unit, enabled: Boolean) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.key
    val base =
        Modifier
            .weight(weight = 1f)
            .heightIn(min = FeltbookDimens.keypadKeyHeight)
            .clip(shape)
            .background(color = colors.quietFill)
            .testTag(keypadTestTag(key))
    val modifier =
        if (key == KeypadKey.Backspace) {
            base.combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = stringResource(R.string.keypad_delete),
                onLongClickLabel = stringResource(R.string.keypad_clear),
                onLongClick = { onKey(KeypadKey.Clear) },
                onClick = { onKey(KeypadKey.Backspace) },
            )
        } else {
            base.combinedClickable(enabled = enabled, role = Role.Button, onClick = { onKey(key) })
        }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (key) {
            is KeypadKey.Digit -> Text(text = key.value.toString(), style = type.keypadDigit, color = colors.ink)

            KeypadKey.TripleZero ->
                Text(
                    text = stringResource(R.string.keypad_triple_zero),
                    style = type.keypadDigitSmall,
                    color = colors.ink,
                )

            KeypadKey.Backspace, KeypadKey.Clear ->
                Icon(
                    imageVector = FeltbookIcons.Backspace,
                    contentDescription = stringResource(R.string.keypad_delete),
                    tint = colors.ink,
                    modifier = Modifier.size(size = FeltbookDimens.icon),
                )
        }
    }
}

@Preview
@Composable
private fun AmountKeypadPreview() {
    FeltbookTheme {
        Box(Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp)) {
            AmountKeypad(onKey = {})
        }
    }
}
