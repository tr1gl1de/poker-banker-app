package app.feltbook.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Иконки m3-ui-spec §3: `viewBox 0 0 24 24`, контур `currentColor`, `strokeLineCap = Round`.
 * Цвет — только через `Icon(tint = …)` из темы; размер — через `Modifier.size` (по месту: 18–24 dp).
 * Разные толщины одной иконки — отдельные `ImageVector` (`Check`, `CheckThin`, `CheckBold`).
 */
object FeltbookIcons {
    val Back: ImageVector by lazy { stroke("Back", width = 2f, round = true, "M15 5l-7 7 7 7") }
    val ArrowForward: ImageVector by lazy {
        stroke("ArrowForward", width = 2.2f, round = true, "M5 12h14M13 6l6 6-6 6")
    }
    val Plus: ImageVector by lazy { stroke("Plus", width = 2.2f, round = false, "M12 5v14M5 12h14") }
    val History: ImageVector by lazy {
        stroke(
            "History",
            width = 1.8f,
            round = false,
            "M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0",
            "M12 7.5V12l3 2",
        )
    }
    val Settings: ImageVector by lazy {
        stroke(
            "Settings",
            width = 1.8f,
            round = false,
            "M4 7h10M18 7h2M4 17h4M12 17h8",
            "M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        )
    }
    val Handover: ImageVector by lazy { stroke("Handover", width = 1.8f, round = true, "M7 7l10 10-5 5V2l5 5L7 17") }
    val MoreVert: ImageVector by lazy {
        fill(
            "MoreVert",
            "M10.2 5a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0",
            "M10.2 12a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0",
            "M10.2 19a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0",
        )
    }
    val Backspace: ImageVector by lazy {
        stroke("Backspace", width = 1.8f, round = true, "M9 5h11v14H9l-6-7z", "M13 9l5 6M18 9l-5 6")
    }

    /** «Выдать»: 24 / 2.4. */
    val Check: ImageVector by lazy { stroke("Check", width = 2.4f, round = true, CHECK) }

    /** Баннер «Фишки сошлись»: 20 / 2.2. */
    val CheckThin: ImageVector by lazy { stroke("CheckThin", width = 2.2f, round = true, CHECK) }

    /** Чекбокс: 16 / 3. */
    val CheckBold: ImageVector by lazy { stroke("CheckBold", width = 3f, round = true, CHECK) }
    val Payment: ImageVector by lazy {
        stroke(
            "Payment",
            width = 1.9f,
            round = true,
            "M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2z",
            "M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
        )
    }
    val Exit: ImageVector by lazy { stroke("Exit", width = 1.9f, round = true, "M14 4h5v16h-5M10 8l-4 4 4 4M6 12h10") }
    val Close: ImageVector by lazy { stroke("Close", width = 2f, round = false, "M6 6l12 12M18 6L6 18") }
    val ArrowDown: ImageVector by lazy { stroke("ArrowDown", width = 2.2f, round = true, "M12 5v14M6 13l6 6 6-6") }
    val ArrowUp: ImageVector by lazy { stroke("ArrowUp", width = 2.2f, round = true, "M12 19V5M6 11l6-6 6 6") }
    val Alert: ImageVector by lazy {
        stroke("Alert", width = 2f, round = false, "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0", "M12 7v6M12 16.5v.5")
    }
    val Share: ImageVector by lazy {
        stroke(
            "Share",
            width = 1.9f,
            round = true,
            "M15.5 5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
            "M3.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
            "M15.5 19a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0",
            "M8.2 10.8l7.6-4.3M8.2 13.2l7.6 4.3",
        )
    }
    val Phone: ImageVector by lazy {
        stroke(
            "Phone",
            width = 1.9f,
            round = true,
            "M9 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H9a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z",
            "M11 18h2",
        )
    }
    val Delete: ImageVector by lazy {
        stroke("Delete", width = 2f, round = true, "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")
    }
    val Reset: ImageVector by lazy { stroke("Reset", width = 1.9f, round = true, "M4 12a8 8 0 1 0 2.3-5.6M4 4v4h4") }

    /** Пика логотипа: `viewBox 0 0 100 100`, заливка. */
    val Spade: ImageVector by lazy {
        ImageVector.Builder(
            name = "Spade",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = SPADE_VIEWPORT,
            viewportHeight = SPADE_VIEWPORT,
        ).addPath(pathData = addPathNodes(SPADE_PATH), fill = SolidColor(Color.Black)).build()
    }

    /** Контур пики в координатах `viewBox 0 0 100 100` (для логотипа и иконки приложения). */
    const val SPADE_PATH: String =
        "M50 8C50 8 14 36 14 58C14 72 26 80 38 76C42 75 45 72 46 70C45 80 41 88 34 92L66 92" +
            "C59 88 55 80 54 70C55 72 58 75 62 76C74 80 86 72 86 58C86 36 50 8 50 8Z"

    private const val CHECK = "M5 12l5 5 9-10"
    private const val VIEWPORT = 24f
    private const val SPADE_VIEWPORT = 100f

    private fun stroke(name: String, width: Float, round: Boolean, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        )
        paths.forEach { data ->
            // Цвет-заглушка: реальный цвет задаёт Icon(tint) из темы.
            builder.addPath(
                pathData = addPathNodes(data),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = if (round) StrokeJoin.Round else StrokeJoin.Miter,
            )
        }
        return builder.build()
    }

    private fun fill(name: String, vararg paths: String): ImageVector {
        val builder = ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        )
        paths.forEach { data -> builder.addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black)) }
        return builder.build()
    }
}
