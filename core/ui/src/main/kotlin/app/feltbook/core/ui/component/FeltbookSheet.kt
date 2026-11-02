package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import kotlinx.coroutines.launch

/** Наибольшая ширина шторки на планшете (m3-ui-spec §9). */
private val SheetMaxWidth = 640.dp

/**
 * Шторка «Сукна» (m3-ui-spec §4.15): `ModalBottomSheet` без промежуточного положения, фон `sheet`, scrim, верх r28
 * с кромкой `sheetEdge`, своя ручка. Содержимое прокручивается (шрифт 200 %).
 *
 * [hasInput] = `true` (введена сумма или данные) — закрытие свайпом, «назад» или тапом мимо спрашивает
 * «Отменить ввод?» ([discardBody] — «Введённая сумма 2 000 не сохранится.»); пустая шторка закрывается сразу.
 * [onDismiss] вызывается после того, как шторка скрылась.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeltbookSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    hasInput: Boolean = false,
    discardBody: String = stringResource(R.string.sheet_discard_body_generic),
    contentSpacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val hasInputState = rememberUpdatedState(hasInput)
    val showDiscard = remember { mutableStateOf(false) }
    val allowHide = remember { mutableStateOf(false) }
    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                if (target == SheetValue.Hidden && hasInputState.value && !allowHide.value) {
                    showDiscard.value = true
                    false
                } else {
                    true
                }
            },
        )
    val scope = rememberCoroutineScope()
    val colors = FeltbookTheme.colors
    val shape = FeltbookTheme.shapes.sheet
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    // Не выше «экран − statusBars − 56 dp»: при шрифте 200 % содержимое прокручивается, а не уходит под статус-бар.
    val density = LocalDensity.current
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val statusBars = WindowInsets.statusBars.getTop(density)
    val maxSheetHeight = with(density) { (windowHeight - statusBars).toDp() } - FeltbookDimens.sheetTopGap

    ModalBottomSheet(
        onDismissRequest = { currentOnDismiss() },
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = SheetMaxWidth,
        shape = shape,
        containerColor = colors.sheet,
        contentColor = colors.ink,
        scrimColor = colors.scrim,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxSheetHeight.coerceAtLeast(FeltbookDimens.minTouchTarget))
                    .drawWithCache {
                        val outline = Path().apply {
                            addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache))
                        }
                        val line = 1.dp.toPx()
                        val top = shape.topStart.toPx(size, this)
                        onDrawBehind {
                            clipRect(bottom = top + line) {
                                drawPath(
                                    outline,
                                    colors.sheetEdge,
                                    style = Stroke(
                                        width =
                                            line * 2,
                                    ),
                                )
                            }
                        }
                    }.verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = contentSpacing),
        ) {
            SheetDragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }

    if (showDiscard.value) {
        FeltbookDialog(
            title = stringResource(R.string.sheet_discard_title),
            body = discardBody,
            safeLabel = stringResource(R.string.sheet_discard_keep),
            onSafe = { showDiscard.value = false },
            confirmLabel = stringResource(R.string.sheet_discard_confirm),
            onConfirm = {
                showDiscard.value = false
                allowHide.value = true
                scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnDismiss() }
            },
        )
    }
}

/** Текст «Введённая сумма 2 000 не сохранится.» для [FeltbookSheet]. */
@Composable
fun discardAmountBody(formattedAmount: String): String = stringResource(R.string.sheet_discard_body, formattedAmount)

/** Ручка шторки 36 × 4, r2, `dragHandle`. */
@Composable
fun SheetDragHandle(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(width = FeltbookDimens.dragHandleWidth, height = FeltbookDimens.dragHandleHeight)
                .background(color = FeltbookTheme.colors.dragHandle, shape = FeltbookTheme.shapes.dragHandle),
    )
}

/** Заголовок шторки: Playfair 22 + подзаголовок. */
@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        Text(text = title, style = type.displayS, color = colors.ink)
        if (subtitle != null) Text(text = subtitle, style = type.caption, color = colors.muted)
    }
}

/** Содержимое шторки без окна — для превью. */
@Preview
@Composable
private fun SheetContentPreview() {
    FeltbookTheme {
        Column(
            modifier =
                Modifier
                    .width(width = 390.dp)
                    .background(color = FeltbookTheme.colors.sheet, shape = FeltbookTheme.shapes.sheet)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = 14.dp),
        ) {
            SheetDragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            SheetTitle(title = "Докуп · Дима", subtitle = "сумма от 500")
            AmountKeypad(onKey = {})
        }
    }
}
