package app.feltbook.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Образец темы-заглушки: токены, шрифты, фон. Эталоны — src/test/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class FeltbookThemeScreenshotTest {
    @Test
    fun darkTheme() {
        captureRoboImage { FeltbookTheme(darkTheme = true) { ThemeSpecimen() } }
    }

    @Test
    fun lightTheme() {
        captureRoboImage { FeltbookTheme(darkTheme = false) { ThemeSpecimen() } }
    }

    /** Весь экран портретного телефона: градиент сукна без боковых полос и швов. */
    @Test
    @Config(qualifiers = "w360dp-h800dp-xxhdpi")
    fun feltBackgroundFullScreenPhone() {
        captureRoboImage { FeltbookTheme(darkTheme = true) { Box(Modifier.fillMaxSize().feltBackground()) } }
    }
}

@Composable
private fun ThemeSpecimen() {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier = Modifier.fillMaxWidth().feltBackground().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Пятничный стол", style = type.display, color = colors.ink)
        Text("8 300 ₽", style = type.amount, color = colors.brassBright)
        Text("Игроку выплатить +1 250 ₽", style = type.title, color = colors.pay)
        Text("С игрока получить −500 ₽", style = type.body, color = colors.owe)
        Text("ФИШЕК В ИГРЕ", style = type.label, color = colors.inkSoft)
        Text("21:40 · закуп 2 000 · докуп ×2", style = type.ledger, color = colors.ledger)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(colors.feltDeep, colors.sheet, colors.card, colors.feltLight, colors.rail, colors.brass)
                .forEach { Swatch(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            with(colors.chips) { listOf(chip100, chip500, chip1000, chip2000, chip5000, chip10000) }
                .forEach { Box(Modifier.size(36.dp).background(it, CircleShape)) }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(Modifier.size(36.dp).background(color, FeltbookTheme.shapes.field))
}
