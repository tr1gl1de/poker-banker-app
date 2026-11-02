package app.feltbook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            FeltbookTheme {
                BrandPlaceholder()
            }
        }
    }
}

/** Заглушка M0: навигация и экраны появятся на этапе M3. */
@Composable
private fun BrandPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().feltBackground().safeDrawingPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.brand_title),
            style = FeltbookTheme.typography.display,
            color = FeltbookTheme.colors.brassLight,
        )
        Text(
            text = stringResource(R.string.brand_tagline),
            style = FeltbookTheme.typography.ledger,
            color = FeltbookTheme.colors.ledger,
        )
    }
}

@Preview
@Composable
private fun BrandPlaceholderPreview() {
    FeltbookTheme { BrandPlaceholder() }
}
