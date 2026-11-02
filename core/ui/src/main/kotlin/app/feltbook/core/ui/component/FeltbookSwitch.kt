package app.feltbook.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/**
 * Переключатель 52 × 32 (m3-ui-spec §4.11): вкл. — латунь с бегунком `onBrass`, выкл. — рамка `switchOffBorder`.
 * [onCheckedChange] = `null` — когда переключает вся строка ([SwitchRow]).
 */
@Composable
fun FeltbookSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors =
            SwitchDefaults.colors(
                checkedTrackColor = colors.brass,
                checkedThumbColor = colors.onBrass,
                checkedBorderColor = Color.Transparent,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = colors.switchOffBorder,
                uncheckedThumbColor = colors.muted,
            ),
    )
}

/** Строка настройки с переключателем: цель касания — вся строка (`toggleable`). */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = FeltbookDimens.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(text = title, style = type.bodyL, color = colors.ink)
            if (subtitle != null) Text(text = subtitle, style = type.small, color = colors.ledger)
        }
        FeltbookSwitch(checked = checked, onCheckedChange = null)
    }
}

@Preview
@Composable
private fun SwitchRowPreview() {
    FeltbookTheme {
        Column(Modifier.feltBackground().width(width = 390.dp).padding(horizontal = 20.dp)) {
            SwitchRow(title = "Не гасить экран", checked = true, onCheckedChange = {})
            SwitchRow(
                title = "Вибрация при подтверждении",
                subtitle = "удержание 0,6 с",
                checked = false,
                onCheckedChange = {},
            )
        }
    }
}
