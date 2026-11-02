package app.feltbook.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import kotlinx.coroutines.delay

/** Snackbar «Отменить» живёт 5 с (ТЗ §5.1); TalkBack-настройка «Время на действие» может увеличить. */
const val UNDO_SNACKBAR_MILLIS: Long = 5_000L

private class FeltbookSnackbarVisuals(override val message: String, override val actionLabel: String?) :
    SnackbarVisuals {
    override val withDismissAction: Boolean = false
    override val duration: SnackbarDuration = SnackbarDuration.Indefinite
}

/**
 * Показывает snackbar «Отменить». Новая операция заменяет старую: текущий snackbar закрывается (старая
 * операция остаётся отменяемой через журнал). Время показа (5 с с поправкой Accessibility) отсчитывает
 * [FeltbookSnackbarHost]. [actionLabel] = `null` — snackbar без действия (подсказки пасхалки).
 * Возвращает [SnackbarResult.ActionPerformed], если нажали действие.
 */
suspend fun SnackbarHostState.showUndoSnackbar(message: String, actionLabel: String?): SnackbarResult {
    currentSnackbarData?.dismiss()
    return showSnackbar(FeltbookSnackbarVisuals(message, actionLabel))
}

/**
 * Хост snackbar в стиле «Сукна»; размещать на 16 dp над FAB/нижней панелью. Snackbar живёт [durationMillis]
 * (5 с), увеличенные по настройке «Время на действие» (`calculateRecommendedTimeoutMillis`).
 */
@Composable
fun FeltbookSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    durationMillis: Long = UNDO_SNACKBAR_MILLIS,
) {
    val accessibility = LocalAccessibilityManager.current
    SnackbarHost(hostState = hostState, modifier = modifier) { data: SnackbarData ->
        val timeout =
            accessibility?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = durationMillis,
                containsIcons = false,
                containsText = true,
                containsControls = data.visuals.actionLabel != null,
            ) ?: durationMillis
        LaunchedEffect(data) {
            delay(timeout)
            data.dismiss()
        }
        UndoSnackbar(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = { data.performAction() },
        )
    }
}

/** Snackbar (m3-ui-spec §4.13): поверхность `dialog`, рамка `dialogBorder`, действие `brassLight`. */
@Composable
fun UndoSnackbar(message: String, actionLabel: String?, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    Row(
        modifier =
            modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .dialogShadow(shape)
                .clip(shape)
                .background(color = colors.dialog)
                .border(width = 1.dp, color = colors.dialogBorder, shape = shape)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                .heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = type.body,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f).padding(vertical = 8.dp),
        )
        if (actionLabel != null) {
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .clip(FeltbookTheme.shapes.pill)
                        .clickable(role = Role.Button, onClick = onAction)
                        .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = actionLabel, style = type.labelBold, color = colors.brassLight, maxLines = 1)
            }
        }
    }
}

@Preview
@Composable
private fun UndoSnackbarPreview() {
    FeltbookTheme {
        Box(Modifier.feltBackground().width(width = 390.dp).padding(vertical = 24.dp)) {
            UndoSnackbar(message = "Докуп 1 000 — Дима", actionLabel = "Отменить", onAction = {})
        }
    }
}
