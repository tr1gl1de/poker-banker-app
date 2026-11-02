package app.feltbook.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.feltbook.R
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/** Экран, который появится на своём этапе: заголовок и подпись, без выдуманных данных. */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(modifier = modifier.fillMaxSize().feltBackground()) {
        Row(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = if (onBack != null) 8.dp else 20.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                FeltIconButton(
                    icon = FeltbookIcons.Back,
                    contentDescription = stringResource(UiR.string.common_back),
                    onClick = onBack,
                    filled = false,
                )
            }
            Text(text = title, style = type.displayL, color = colors.ink)
        }
        Text(
            text = stringResource(R.string.placeholder_soon),
            style = type.caption,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        content()
    }
}
