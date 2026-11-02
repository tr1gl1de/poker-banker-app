package app.feltbook.feature.game.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BankerBadge
import app.feltbook.core.ui.component.NameWithBadges
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.RebuyBadge
import app.feltbook.core.ui.component.dashedBorder
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.LocalConfirmHaptics
import app.feltbook.feature.game.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Порог свайпа строки — 40 % ширины (m3-ui-spec §5.3). */
const val SWIPE_THRESHOLD_FRACTION: Float = 0.4f

/**
 * Строка игрока со свайпами: вправо — докуп (и возврат вышедшего), влево — выход (только за столом).
 * Срабатывание после 40 % ширины, в точке порога — один тик вибрации; отпустили раньше — пружинный возврат.
 */
@Composable
fun PlayerRow(
    row: PlayerRowUi,
    onClick: () -> Unit,
    onSwipeBuyIn: () -> Unit,
    onSwipeCashOut: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val buyInLabel = stringResource(R.string.table_swipe_buy_in)
    val cashOutLabel = stringResource(R.string.table_swipe_cash_out)
    val description = rowDescription(row)
    SwipeRow(
        canSwipeRight = row.canBuyIn,
        canSwipeLeft = row.canCashOut,
        onSwipeRight = onSwipeBuyIn,
        onSwipeLeft = onSwipeCashOut,
        modifier =
            modifier.semantics {
                customActions = buildList {
                    if (row.canBuyIn) {
                        add(
                            CustomAccessibilityAction(buyInLabel) {
                                onSwipeBuyIn()
                                true
                            },
                        )
                    }
                    if (row.canCashOut) {
                        add(
                            CustomAccessibilityAction(cashOutLabel) {
                                onSwipeCashOut()
                                true
                            },
                        )
                    }
                }
            },
    ) {
        PlayerRowContent(row = row, onClick = onClick, description = description, selected = selected)
    }
}

@Composable
private fun rowDescription(row: PlayerRowUi): String = when {
    row.status != PlayerStatus.PLAYING ->
        stringResource(
            R.string.table_row_a11y_left,
            row.name,
            when {
                row.status == PlayerStatus.LEFT_WITH_DEBT ->
                    stringResource(R.string.table_row_left_with_debt) + ", " + a11yRubles(-row.outstanding)

                row.outstanding > 0 ->
                    stringResource(R.string.table_row_left_banker_owes) + ", " + a11yRubles(row.outstanding)

                else -> stringResource(R.string.table_row_left_settled)
            },
        )

    row.outstanding < 0 -> stringResource(
        R.string.table_row_a11y_debt,
        row.name,
        row.rebuyCount,
        a11yRubles(-row.outstanding),
    )

    row.outstanding > 0 -> stringResource(
        R.string.table_row_a11y_owed,
        row.name,
        row.rebuyCount,
        a11yRubles(row.outstanding),
    )

    else -> stringResource(R.string.table_row_a11y_paid, row.name, row.rebuyCount)
}

@Composable
private fun SwipeRow(
    canSwipeRight: Boolean,
    canSwipeLeft: Boolean,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    val haptic = LocalHapticFeedback.current
    val haptics = LocalConfirmHaptics.current
    val offset = remember { Animatable(0f) }
    var width by remember { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val right by rememberUpdatedState(onSwipeRight)
    val left by rememberUpdatedState(onSwipeLeft)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { width = it.width }
                .pointerInput(canSwipeRight, canSwipeLeft) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = width * SWIPE_THRESHOLD_FRACTION
                            val value = offset.value
                            when {
                                value >= threshold -> right()
                                value <= -threshold -> left()
                            }
                            armed = false
                            scope.launch {
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onDragCancel = {
                            armed = false
                            scope.launch {
                                offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            }
                        },
                        onHorizontalDrag = { change, delta ->
                            val min = if (canSwipeLeft) -width.toFloat() else 0f
                            val max = if (canSwipeRight) width.toFloat() else 0f
                            val next = (offset.value + delta).coerceIn(min, max)
                            if (next != offset.value) change.consume()
                            scope.launch { offset.snapTo(next) }
                            val crossed = abs(next) >= width * SWIPE_THRESHOLD_FRACTION
                            if (crossed != armed) {
                                armed = crossed
                                if (crossed &&
                                    haptics
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                }
                            }
                        },
                    )
                },
    ) {
        val value = offset.value
        if (value != 0f) {
            val toRight = value > 0
            Row(
                modifier =
                    Modifier
                        .matchParentSize()
                        .clip(shape)
                        .background(color = if (toRight) colors.brassIndicator else colors.quietFill)
                        .padding(horizontal = 20.dp),
                horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (toRight) {
                    Icon(
                        imageVector = FeltbookIcons.Plus,
                        contentDescription = null,
                        tint = colors.brassLight,
                        modifier = Modifier.size(size = 22.dp),
                    )
                    Text(
                        text = stringResource(R.string.table_swipe_buy_in),
                        style = type.labelBold,
                        color = colors.brassLight,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.table_swipe_cash_out),
                        style = type.labelBold,
                        color = colors.ink,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Icon(
                        imageVector = FeltbookIcons.Exit,
                        contentDescription = null,
                        tint = colors.ink,
                        modifier = Modifier.size(size = 20.dp),
                    )
                }
            }
        }
        Box(modifier = Modifier.offset { IntOffset(value.roundToInt(), 0) }) { content() }
    }
}

@Composable
private fun PlayerRowContent(row: PlayerRowUi, onClick: () -> Unit, description: String, selected: Boolean) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    val playing = row.status == PlayerStatus.PLAYING
    val container =
        when {
            selected ->
                Modifier
                    .background(color = colors.brassSelectedRow, shape = shape)
                    .border(width = 1.dp, color = colors.selectedBorder, shape = shape)

            playing -> Modifier.background(color = colors.card, shape = shape)

            row.status == PlayerStatus.LEFT_WITH_DEBT ->
                Modifier.alpha(alpha = 0.8f).dashedBorder(colors.oweBorderStrong, radius = 16.dp)

            else -> Modifier.alpha(alpha = 0.8f).dashedBorder(colors.chipBorder, radius = 16.dp)
        }
    AmountRowLayout(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(container)
                .clip(shape)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = 12.dp, vertical = 10.dp),
        leading = {
            PlayerAvatar(name = row.name, suit = row.suit, active = playing)
        },
        trailing = { RowAmount(row) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            NameWithBadges(name = row.name) {
                if (row.isBanker) BankerBadge()
                if (row.rebuyCount > 0) RebuyBadge(count = row.rebuyCount)
            }
            RowMeta(row)
        }
    }
}

@Composable
private fun RowMeta(row: PlayerRowUi) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val bought = MoneyFormat.number(row.chipsBought)
    when {
        row.status == PlayerStatus.LEFT_WITH_DEBT ->
            MetaText(stringResource(R.string.table_row_left_with_debt), type.ledgerTight, colors.ledger)

        row.status == PlayerStatus.CASHED_OUT ->
            MetaText(
                stringResource(
                    if (row.outstanding > 0) R.string.table_row_left_banker_owes else R.string.table_row_left_settled,
                ),
                type.ledgerTight,
                colors.ledger,
            )

        !row.bought -> MetaText(stringResource(R.string.table_row_not_bought), type.ledgerTight, colors.ledger)

        row.earlyMinutes != null ->
            MetaText(
                stringResource(R.string.table_row_buy_in_early, bought, row.earlyMinutes.toInt(), row.minPlayMinutes),
                type.small,
                colors.brassLight,
            )

        else ->
            MetaText(
                stringResource(
                    R.string.table_row_buy_in,
                    bought,
                    row.playedFor?.let { formatDuration(it) } ?: stringResource(UiR.string.common_dash),
                ),
                type.ledgerTight,
                colors.ledger,
            )
    }
}

@Composable
private fun MetaText(text: String, style: TextStyle, color: Color) {
    Text(text = text, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun RowAmount(row: PlayerRowUi) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val (amount, amountColor, caption) =
        when (row.status) {
            PlayerStatus.PLAYING ->
                when {
                    row.outstanding < 0 ->
                        Triple(MoneyFormat.rub(row.outstanding), colors.owe, stringResource(R.string.table_row_debt))

                    row.outstanding > 0 ->
                        Triple(
                            MoneyFormat.signedRub(row.outstanding),
                            colors.pay,
                            stringResource(R.string.table_row_banker_owes),
                        )

                    else ->
                        Triple(
                            stringResource(R.string.table_row_paid),
                            colors.pay,
                            if (row.isBanker) stringResource(R.string.table_row_no_fee) else null,
                        )
                }

            PlayerStatus.CASHED_OUT ->
                if (row.outstanding > 0) {
                    Triple(
                        MoneyFormat.signedRub(row.outstanding),
                        colors.pay,
                        stringResource(R.string.table_row_not_paid_out),
                    )
                } else {
                    val balance = row.balanceAtExit ?: 0L
                    Triple(
                        MoneyFormat.signedNumber(balance),
                        colors.inkSoft,
                        stringResource(if (balance < 0) R.string.table_row_received else R.string.table_row_paid_out),
                    )
                }

            PlayerStatus.LEFT_WITH_DEBT ->
                Triple(MoneyFormat.rub(row.outstanding), colors.owe, stringResource(R.string.table_row_debt_label))
        }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
        AmountText(text = amount, style = type.amountRow, color = amountColor)
        if (caption != null) Text(text = caption, style = type.tiny, color = colors.muted, maxLines = 1)
    }
}
