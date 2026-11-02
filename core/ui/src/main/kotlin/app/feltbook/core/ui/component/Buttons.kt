// Файл назван по главному компоненту; enum вариантов — его параметр.
@file:Suppress("MatchingDeclarationName")

package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookGradients
import app.feltbook.core.ui.theme.FeltbookShapes
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.FeltbookTypography
import app.feltbook.core.ui.theme.feltBackground

/** Варианты латунной кнопки (m3-ui-spec §4.4). */
enum class BrassButtonStyle(val height: Dp, val gradient: Boolean) {
    /** 56, r16, сплошная: «Расчёт произведён» (макет), «Докуп» в карточке игрока. */
    Flat56(height = 56.dp, gradient = false),

    /** 52, r16, сплошная, иконка справа: «Вернуться за стол». */
    Flat52(height = 52.dp, gradient = false),

    /** 48, r14: «Погасить». */
    Flat48(height = 48.dp, gradient = false),

    /** 64, r20, градиент + тень: «Начать игру», «Выдать 1 000 фишек». */
    Hero64(height = 64.dp, gradient = true),
}

private fun BrassButtonStyle.shape(shapes: FeltbookShapes): Shape = when (this) {
    BrassButtonStyle.Hero64 -> shapes.hero
    BrassButtonStyle.Flat48 -> shapes.field
    BrassButtonStyle.Flat56, BrassButtonStyle.Flat52 -> shapes.card
}

private fun BrassButtonStyle.textStyle(type: FeltbookTypography): TextStyle = when (this) {
    BrassButtonStyle.Hero64 -> type.buttonL
    BrassButtonStyle.Flat48 -> type.labelBold
    BrassButtonStyle.Flat56, BrassButtonStyle.Flat52 -> type.button
}

/**
 * Подпись кнопки: при крупном шрифте сначала уменьшается (до 12 sp) в одну строку, затем переносится
 * по пробелам на вторую строку ([ButtonLabelAutoSize]) — подпись не обрезается при шрифте 200 %.
 * Суммы внутри склеены неразрывными пробелами и не разрываются.
 */
@Composable
internal fun ButtonLabel(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(color = color, textAlign = textAlign),
        maxLines = BUTTON_LABEL_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        autoSize =
            ButtonLabelAutoSize(
                minFontSize = minOf(AmountMinFontSize.value, style.fontSize.value).sp,
                maxFontSize = style.fontSize,
            ),
    )
}

private const val BUTTON_LABEL_MAX_LINES = 2

/**
 * Главная латунная кнопка без удержания, с debounce 600 мс ([rememberDebouncedClick]).
 * После нажатия экран ставит `enabled = false` до завершения операции.
 */
@Composable
fun BrassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BrassButtonStyle = BrassButtonStyle.Hero64,
    enabled: Boolean = true,
    trailingIcon: ImageVector? = null,
    contentDescription: String? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = style.shape(FeltbookTheme.shapes)
    val textStyle = style.textStyle(type)
    val background: Brush =
        when {
            !enabled -> SolidColor(colors.brassDisabled)
            style.gradient -> FeltbookGradients.brassButton(colors)
            else -> SolidColor(colors.brass)
        }
    val content = if (enabled) colors.onBrass else colors.onBrassDisabled
    val click = rememberDebouncedClick(onClick = onClick)
    Row(
        modifier =
            modifier
                .then(if (enabled && style.gradient) Modifier.heroShadow(shape) else Modifier)
                .heightIn(min = style.height)
                .clip(shape)
                .background(brush = background)
                .clickable(enabled = enabled, role = Role.Button, onClick = click)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonLabel(
            text = text,
            style = textStyle,
            color = content,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
        if (trailingIcon != null) {
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size = FeltbookDimens.iconM),
            )
        }
    }
}

/**
 * Кнопка «Выдать N фишек» шторки закупа (hero 68): мини-фишка цвета номинала, заголовок, вторая строка
 * («2 200 ₽ записать в долг»), галочка. Градиент, блик 1 dp сверху, тень; debounce 600 мс.
 */
@Composable
fun IssueChipsButton(
    title: String,
    subtitle: String,
    chipColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.hero
    val click = rememberDebouncedClick(onClick = onClick)
    val highlight = colors.brassHighlight
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.heroShadow(shape) else Modifier)
                .heightIn(min = FeltbookDimens.buttonHero68)
                .clip(shape)
                .background(
                    brush = if (enabled) FeltbookGradients.brassButton(colors) else SolidColor(colors.brassDisabled),
                ).drawWithContent {
                    drawContent()
                    if (enabled) drawRect(highlight, size = Size(size.width, 1.dp.toPx()))
                }.clickable(enabled = enabled, role = Role.Button, onClick = click)
                .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniChip(color = chipColor)
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 1.dp)) {
            val content = if (enabled) colors.onBrass else colors.onBrassDisabled
            ButtonLabel(text = title, style = type.buttonHero, color = content, textAlign = TextAlign.Start)
            ButtonLabel(
                textAlign = TextAlign.Start,
                text = subtitle,
                style = type.chipSecondary,
                color = if (enabled) colors.onBrassSecondary else colors.onBrassDisabled,
            )
        }
        Icon(
            imageVector = FeltbookIcons.Check,
            contentDescription = null,
            tint = if (enabled) colors.onBrass else colors.onBrassDisabled,
            modifier = Modifier.size(size = FeltbookDimens.iconL),
        )
    }
}

/**
 * Контурная кнопка (отмена, второстепенное): рамка `outline`, заливка `outlinedFill`. [height] 52 / 48 / 56;
 * у 48 — r14. [emphasized] — текст `ink` вместо `inkSoft` («Отдал часть…», «Посадить с долгом»).
 */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = FeltbookDimens.button52,
    icon: ImageVector? = null,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val shape = if (height <= FeltbookDimens.button48) FeltbookTheme.shapes.field else FeltbookTheme.shapes.card
    val content =
        when {
            !enabled -> colors.inkDisabled
            emphasized -> colors.ink
            else -> colors.inkSoft
        }
    Row(
        modifier =
            modifier
                .heightIn(min = height)
                .clip(shape)
                .background(color = colors.outlinedFill)
                .border(width = 1.dp, color = colors.outline, shape = shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size = FeltbookDimens.iconS),
            )
        }
        ButtonLabel(
            text = text,
            style = FeltbookTheme.typography.title,
            color = content,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
    }
}

/** «Не заплатил»: красная контурная кнопка с [FeltbookIcons.Alert]. */
@Composable
fun DangerOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = FeltbookIcons.Alert,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.card
    val content = if (enabled) colors.owe else colors.inkDisabled
    Row(
        modifier =
            modifier
                .heightIn(min = FeltbookDimens.button52)
                .clip(shape)
                .background(color = colors.oweBackgroundSoft)
                .border(width = 1.dp, color = colors.oweBorderMedium, shape = shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size = FeltbookDimens.iconS),
        )
        ButtonLabel(
            text = text,
            style = FeltbookTheme.typography.sectionTitle,
            color = content,
            modifier = Modifier.weight(weight = 1f, fill = false),
        )
    }
}

/** Текстовая кнопка диалога h44 r22: опасная (`owe`) или нейтральное подтверждение (`brassLight`). */
@Composable
fun DialogTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val color =
        when {
            !enabled -> colors.inkDisabled
            destructive -> colors.owe
            else -> colors.brassLight
        }
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = FeltbookDimens.dialogButton)
                .clip(FeltbookTheme.shapes.pill)
                .clickable(enabled = enabled, role = Role.Button, onClick = rememberDebouncedClick(onClick = onClick))
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text = text, style = FeltbookTheme.typography.labelBold, color = color)
    }
}

/** Опасное действие текстом: красный текст на нейтральном фоне (в диалоге). */
@Composable
fun DangerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    DialogTextButton(text = text, onClick = onClick, modifier = modifier, destructive = true, enabled = enabled)
}

/** Безопасная кнопка диалога («Оставить», «Продолжить ввод»): по умолчанию, рамка `outlineDialog`. */
@Composable
fun SafeDialogButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.pill
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = FeltbookDimens.dialogButton)
                .clip(shape)
                .background(color = colors.quietFill)
                .border(width = 1.dp, color = colors.outlineDialog, shape = shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(text = text, style = FeltbookTheme.typography.labelBold, color = colors.ink)
    }
}

/**
 * Круглая иконка-кнопка: визуально 44 dp, цель касания 48. [filled] = false — без фона («назад» в шапке).
 */
@Composable
fun FeltIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    tint: Color = FeltbookTheme.colors.ink,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .size(size = FeltbookDimens.iconButtonVisual)
                .clip(CircleShape)
                .then(if (filled) Modifier.background(color = FeltbookTheme.colors.quietFill) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else FeltbookTheme.colors.inkDisabled,
            modifier = Modifier.size(size = FeltbookDimens.icon),
        )
    }
}

/** «Поделиться итогом»: 56 × 56, r16, рамка `brassBorderStrong`. */
@Composable
fun ShareButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.card
    Box(
        modifier =
            modifier
                .size(size = FeltbookDimens.button56)
                .clip(shape)
                .border(width = 1.dp, color = colors.brassBorderStrong, shape = shape)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = FeltbookIcons.Share,
            contentDescription = null,
            tint = colors.brassLight,
            modifier = Modifier.size(size = FeltbookDimens.icon),
        )
    }
}

/** «Удалить» в шапке мультивыбора: h40 r20, фон `oweBackgroundStrong`. */
@Composable
fun DeleteActionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    Row(
        modifier =
            modifier
                .minimumInteractiveComponentSize()
                .heightIn(min = FeltbookDimens.deleteChipHeight)
                .clip(FeltbookTheme.shapes.pill)
                .background(color = colors.oweBackgroundStrong)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = FeltbookIcons.Delete,
            contentDescription = null,
            tint = colors.owe,
            modifier = Modifier.size(size = FeltbookDimens.iconS).clearAndSetSemantics {},
        )
        ButtonLabel(text = text, style = FeltbookTheme.typography.labelBold, color = colors.owe)
    }
}

@Preview
@Composable
private fun ButtonsPreview() {
    FeltbookTheme {
        Column(
            modifier = Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        ) {
            BrassButton(text = "Начать игру", onClick = {}, modifier = Modifier.fillMaxWidth())
            BrassButton(
                text = "Вернуться за стол",
                onClick = {},
                style = BrassButtonStyle.Flat52,
                trailingIcon = FeltbookIcons.ArrowForward,
                modifier = Modifier.fillMaxWidth(),
            )
            BrassButton(text = "Погасить", onClick = {}, style = BrassButtonStyle.Flat48, enabled = false)
            IssueChipsButton(
                title = "Выдать 2 000 фишек",
                subtitle = "2 200 ₽ записать в долг",
                chipColor = FeltbookTheme.colors.chips.chip2000,
                onClick = {},
            )
            Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                OutlineButton(text = "Отмена", onClick = {
                }, icon = FeltbookIcons.Close, modifier = Modifier.weight(weight = 1f))
                DangerOutlineButton(text = "Не заплатил", onClick = {}, modifier = Modifier.weight(weight = 1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SafeDialogButton(text = "Оставить", onClick = {})
                DangerTextButton(text = "Удалить 3 игры", onClick = {})
                FeltIconButton(icon = FeltbookIcons.MoreVert, contentDescription = "Меню игры", onClick = {})
                ShareButton(contentDescription = "Поделиться", onClick = {})
            }
            DeleteActionChip(text = "Удалить", onClick = {})
        }
    }
}
