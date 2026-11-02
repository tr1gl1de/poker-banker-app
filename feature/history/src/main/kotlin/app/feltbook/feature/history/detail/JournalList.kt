package app.feltbook.feature.history.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.component.HOLD_RELEASE_MILLIS
import app.feltbook.core.ui.component.HOLD_TO_CONFIRM_MILLIS
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.LocalConfirmHaptics
import app.feltbook.feature.history.R
import app.feltbook.feature.history.common.LocalHistoryTimeZone
import kotlin.time.Instant
import kotlinx.coroutines.launch

/** Отменённая операция в журнале — вся строка приглушена (m3-ui-spec §5.5). */
private const val REVERSED_ALPHA = 0.55f

/**
 * Полный журнал игры: новые сверху; удержание строки 0,6 с — запрос отмены ([onHold], диалог решает экран).
 * Акт передачи не удерживается. Колонки 52 dp | текст | сумма (`nowrap`).
 */
@Composable
fun JournalList(rows: List<JournalRowUi>, now: Instant, onHold: (id: String) -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    if (rows.isEmpty()) {
        Text(
            text = stringResource(R.string.detail_journal_empty),
            style = type.caption,
            color = colors.muted,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEach { row -> JournalRow(row = row, now = now, onHold = { onHold(row.id) }) }
    }
}

@Composable
private fun JournalRow(row: JournalRowUi, now: Instant, onHold: () -> Unit) {
    val colors = FeltbookTheme.colors
    val zone = LocalHistoryTimeZone.current
    val haptic = LocalHapticFeedback.current
    val haptics = LocalConfirmHaptics.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val hold by rememberUpdatedState(onHold)
    val time = if (TimeFormat.localDate(row.at, zone) == TimeFormat.localDate(now, zone)) {
        TimeFormat.clock(row.at, zone)
    } else {
        formatShortDate(TimeFormat.localDate(row.at, zone))
    }
    val actionLabel = stringResource(R.string.detail_journal_reverse_action_a11y)
    val title = row.title.resolve()
    val holdable = !row.handover
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(colors.hairline, size = Size(size.width, 1.dp.toPx()))
                    if (progress.value > 0f) {
                        drawRect(colors.brassSelectedRow, size = Size(size.width * progress.value, size.height))
                    }
                }.then(
                    if (holdable) {
                        Modifier
                            .pointerInput(row.id) {
                                detectTapGestures(
                                    onPress = {
                                        val job = scope.launch {
                                            progress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = tween(HOLD_TO_CONFIRM_MILLIS, easing = LinearEasing),
                                            )
                                            if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            hold()
                                        }
                                        tryAwaitRelease()
                                        if (progress.value < 1f) job.cancel()
                                        scope.launch { progress.animateTo(0f, tween(HOLD_RELEASE_MILLIS)) }
                                    },
                                )
                            }.semantics(mergeDescendants = true) {
                                customActions = listOf(
                                    CustomAccessibilityAction(actionLabel) {
                                        hold()
                                        true
                                    },
                                )
                            }
                    } else {
                        Modifier.semantics(mergeDescendants = true) {}
                    },
                ),
    ) {
        JournalLine(
            time = time,
            text = title,
            amount = row.amount,
            trailing = if (row.reversed) stringResource(R.string.detail_journal_reversed) else null,
            textColor = if (row.handover) colors.brassLight else colors.ink,
            struck = row.reversed,
            modifier = if (row.reversed) Modifier.alpha(alpha = REVERSED_ALPHA) else Modifier,
        )
        if (!row.reversed) {
            row.subs.forEach { sub ->
                JournalLine(
                    time = "",
                    text = sub.text.resolve(),
                    amount = sub.amount,
                    trailing = null,
                    textColor = colors.ledgerLight,
                    struck = false,
                )
            }
        }
    }
}

@Composable
private fun JournalLine(
    time: String,
    text: String,
    amount: Long?,
    trailing: String?,
    textColor: Color,
    struck: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Text(
            text = time,
            style = type.ledgerL,
            color = colors.ledger,
            maxLines = 1,
            modifier = Modifier.width(width = 52.dp),
        )
        Text(
            text = text,
            style = type.ledgerL.copy(textDecoration = if (struck) TextDecoration.LineThrough else null),
            color = textColor,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )
        when {
            trailing != null -> Text(text = trailing, style = type.ledgerL, color = colors.muted, maxLines = 1)

            amount != null -> Text(
                text = MoneyFormat.number(amount),
                style = type.ledgerL,
                color = textColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
