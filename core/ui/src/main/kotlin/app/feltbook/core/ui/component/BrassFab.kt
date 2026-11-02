package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/**
 * Латунный FAB (m3-ui-spec §4.8): h56, r18, «+» и текст («Новая игра», «Игрок»). Позиция — `end 16`, на 20 dp
 * выше нижней панели ([FeltbookDimens.fabAboveBar]). [contentDescription] — «Добавить игрока». Debounce 600 мс.
 */
@Composable
fun BrassFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    icon: ImageVector = FeltbookIcons.Plus,
) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.button
    val click = rememberDebouncedClick(onClick = onClick)
    Row(
        modifier =
            modifier
                .feltShadow(shape, offsetY = 8.dp, blur = 24.dp, alpha = 0.35f)
                .heightIn(min = FeltbookDimens.fabHeight)
                .clip(shape)
                .background(color = colors.brass)
                .clickable(role = Role.Button, onClick = click)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ).padding(start = 18.dp, end = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onBrass,
            modifier = Modifier.size(size = FeltbookDimens.icon),
        )
        Text(
            text = text,
            style = FeltbookTheme.typography.button,
            color = colors.onBrass,
            maxLines = 1,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}

@Preview
@Composable
private fun BrassFabPreview() {
    FeltbookTheme {
        Box(Modifier.feltBackground().padding(all = 24.dp)) {
            BrassFab(text = "Игрок", onClick = {}, contentDescription = "Добавить игрока")
        }
    }
}
