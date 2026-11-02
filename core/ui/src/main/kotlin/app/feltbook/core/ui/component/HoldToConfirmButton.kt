package app.feltbook.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookGradients
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.LocalConfirmHaptics
import app.feltbook.core.ui.theme.feltBackground
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Время удержания до подтверждения (ТЗ §5.1). */
const val HOLD_TO_CONFIRM_MILLIS: Int = 600

/** Возврат полосы, если отпустили раньше. */
const val HOLD_RELEASE_MILLIS: Int = 200

/** Высоты hold-кнопки: 68 / 64 — r20, 56 — r16. */
enum class HoldButtonSize(val height: Dp) {
    H68(height = 68.dp),
    H64(height = 64.dp),
    H56(height = 56.dp),
}

/**
 * «Деньги на руки» (m3-ui-spec §4.4): удержание 0,6 с с полосой слева направо, вибрация `LongPress` в конце,
 * затем [onConfirm]. Отпустили раньше — полоса возвращается за 0,2 с, ничего не происходит. [title] повторяет
 * сумму («Выплатить 300 ₽»), [hint] — «удерживайте, чтобы подтвердить».
 *
 * TalkBack: обычное двойное касание не подтверждает (удержание недоступно); подтверждение — кастомное действие
 * «Подтвердить» или двойное касание с удержанием (long click). После подтверждения экран ставит
 * `enabled = false` до завершения операции.
 */
@Composable
fun HoldToConfirmButton(
    title: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String = stringResource(R.string.common_hold_to_confirm),
    size: HoldButtonSize = HoldButtonSize.H68,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = if (size == HoldButtonSize.H56) FeltbookTheme.shapes.card else FeltbookTheme.shapes.hero
    val haptic = LocalHapticFeedback.current
    val haptics = LocalConfirmHaptics.current
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val confirmLabel = stringResource(R.string.common_confirm_action)
    val gradient = FeltbookGradients.brassButton(colors)
    val confirm = {
        if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        currentOnConfirm()
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = size.height)
                .clip(shape)
                .background(brush = if (enabled) gradient else SolidColor(colors.brassDisabled))
                .drawBehind {
                    val value = progress.value
                    if (enabled && value > 0f) {
                        drawRect(colors.holdTrack)
                        drawRect(gradient, size = Size(this.size.width * value, this.size.height))
                    }
                }.semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = title
                    stateDescription = hint
                    if (!enabled) {
                        disabled()
                    } else {
                        customActions = listOf(
                            CustomAccessibilityAction(confirmLabel) {
                                confirm()
                                true
                            },
                        )
                        onLongClick(label = confirmLabel) {
                            confirm()
                            true
                        }
                    }
                }.pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val fill =
                            scope.launch {
                                val remaining = ((1f - progress.value) * HOLD_TO_CONFIRM_MILLIS).toInt()
                                progress.animateTo(1f, tween(durationMillis = remaining, easing = LinearEasing))
                                confirm()
                            }
                        waitForUpOrCancellation()
                        val completed = fill.isCompleted && !fill.isCancelled
                        fill.cancel(CancellationException("released"))
                        scope.launch {
                            if (completed) {
                                progress.snapTo(0f)
                            } else {
                                progress.animateTo(0f, tween(durationMillis = HOLD_RELEASE_MILLIS))
                            }
                        }
                    }
                },
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val main = if (enabled) colors.onBrass else colors.onBrassDisabled
            ButtonLabel(text = title, style = type.buttonL, color = main)
            ButtonLabel(
                text = hint,
                style = type.smallSemi,
                color = if (enabled) colors.onBrassSecondary else colors.onBrassDisabled,
            )
        }
    }
}

@Preview
@Composable
private fun HoldToConfirmButtonPreview() {
    FeltbookTheme {
        Column(
            modifier = Modifier.feltBackground().width(width = 390.dp).padding(all = 20.dp),
            verticalArrangement = Arrangement.spacedBy(space = FeltbookDimens.blockGap),
        ) {
            HoldToConfirmButton(title = "Выплатить 353 ₽", onConfirm = {})
            HoldToConfirmButton(title = "Закрыть игру", onConfirm = {}, size = HoldButtonSize.H56, enabled = false)
        }
    }
}
