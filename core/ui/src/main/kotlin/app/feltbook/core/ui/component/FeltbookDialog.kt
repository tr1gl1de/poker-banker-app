package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/** Наибольшая ширина диалога на планшете. */
private val DialogMaxWidth = 560.dp

/**
 * Диалог «с глаголом» (m3-ui-spec §4.14), прижат к низу (зона большого пальца) над нижней панелью:
 * [bottomInset] = 16 dp без панели, 96 dp над панелью; `navigationBars` добавляются сами.
 * Безопасная кнопка ([safeLabel]) — по умолчанию и первая для TalkBack; «назад» и тап мимо = [onSafe].
 * [destructive] — глагол красным (`owe`), иначе латунью. [content] — опц. поле ввода («УДАЛИТЬ»).
 */
@Composable
fun FeltbookDialog(
    title: String,
    body: String,
    safeLabel: String,
    onSafe: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    confirmEnabled: Boolean = true,
    bottomInset: Dp = 16.dp,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onSafe,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // Своё затемнение c.scrim вместо системного dim окна.
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(0f) }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(color = FeltbookTheme.colors.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSafe,
                    ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            FeltbookDialogCard(
                title = title,
                body = body,
                safeLabel = safeLabel,
                onSafe = onSafe,
                confirmLabel = confirmLabel,
                onConfirm = onConfirm,
                destructive = destructive,
                confirmEnabled = confirmEnabled,
                content = content,
                modifier =
                    modifier
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = bottomInset)
                        .widthIn(max = DialogMaxWidth)
                        // Тап по карточке не закрывает диалог (без семантики клика).
                        .pointerInput(Unit) { detectTapGestures {} },
            )
        }
    }
}

/** Карточка диалога без окна — для превью и скриншотов. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeltbookDialogCard(
    title: String,
    body: String,
    safeLabel: String,
    onSafe: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.dialog
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .dialogShadow(shape)
                .clip(shape)
                .background(color = colors.dialog)
                .border(width = 1.dp, color = colors.dialogBorder, shape = shape)
                .semantics { paneTitle = title }
                .padding(all = 18.dp),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Text(text = title, style = type.dialogTitle, color = colors.ink)
        Text(text = body, style = type.bodyDialog, color = colors.inkSoft)
        content?.invoke(this)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.End),
            verticalArrangement = Arrangement.spacedBy(space = 4.dp),
        ) {
            SafeDialogButton(text = safeLabel, onClick = onSafe)
            DialogTextButton(
                text = confirmLabel,
                onClick = onConfirm,
                destructive = destructive,
                enabled = confirmEnabled,
            )
        }
    }
}

@Preview
@Composable
private fun FeltbookDialogCardPreview() {
    FeltbookTheme {
        Box(Modifier.feltBackground().width(width = 390.dp).padding(all = 16.dp)) {
            FeltbookDialogCard(
                title = "Удалить 3 игры?",
                body = "Игры, их журналы и долги по ним исчезнут с этого телефона. Отменить можно 5 секунд.",
                safeLabel = "Оставить",
                onSafe = {},
                confirmLabel = "Удалить 3 игры",
                onConfirm = {},
            )
        }
    }
}
