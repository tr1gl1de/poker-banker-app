package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/** «докуп ×N» (m3-ui-spec §4.5): показывать при `rebuyCount ≥ 1`. */
@Composable
fun RebuyBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.badge
    Text(
        text = stringResource(R.string.badge_rebuy, count),
        style = FeltbookTheme.typography.tinyBold,
        color = colors.brassLight,
        maxLines = 1,
        modifier =
            modifier
                .border(width = 1.dp, color = colors.brassBorderStrong, shape = shape)
                .padding(horizontal = 5.dp),
    )
}

/** «БАНКИР»: латунная плашка. Если у банкира есть докупы — сначала этот значок, потом [RebuyBadge]. */
@Composable
fun BankerBadge(modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    Text(
        text = stringResource(R.string.badge_banker),
        style = FeltbookTheme.typography.tinyBold,
        color = colors.onBrass,
        maxLines = 1,
        modifier =
            modifier
                .background(color = colors.brass, shape = FeltbookTheme.shapes.badge)
                .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

@Preview
@Composable
private fun BadgesPreview() {
    FeltbookTheme {
        Row(
            modifier = Modifier.feltBackground().padding(all = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Дима", style = FeltbookTheme.typography.title, color = FeltbookTheme.colors.ink)
            BankerBadge()
            RebuyBadge(count = 2)
        }
    }
}
