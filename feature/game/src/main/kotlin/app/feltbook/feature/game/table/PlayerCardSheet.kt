package app.feltbook.feature.game.table

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.ui.component.AvatarSize
import app.feltbook.core.ui.component.BankerBadge
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.HOLD_RELEASE_MILLIS
import app.feltbook.core.ui.component.HOLD_TO_CONFIRM_MILLIS
import app.feltbook.core.ui.component.MetricTile
import app.feltbook.core.ui.component.MetricVariant
import app.feltbook.core.ui.component.NameWithBadges
import app.feltbook.core.ui.component.PlayerAvatar
import app.feltbook.core.ui.component.RebuyBadge
import app.feltbook.core.ui.component.rememberDebouncedClick
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.formatDuration
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.LocalConfirmHaptics
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.LocalTimeZone
import kotlin.time.Instant
import kotlinx.coroutines.launch

@Composable
fun PlayerCardSheet(
    card: PlayerCardUi,
    now: Instant,
    onAction: (TableAction) -> Unit,
    snackbar: SnackbarHostState? = null,
) {
    FeltbookSheet(
        onDismiss = { onAction(TableAction.DismissSheet) },
        contentSpacing = 16.dp,
        snackbarHostState = snackbar,
    ) {
        PlayerCardContent(card = card, now = now, onAction = onAction)
    }
}

/** Содержимое карточки (макет Player): в шторке на телефоне и в панели деталей на планшете. */
@Composable
fun ColumnScope.PlayerCardContent(card: PlayerCardUi, now: Instant, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val zone = LocalTimeZone.current
    Row(horizontalArrangement = Arrangement.spacedBy(space = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerAvatar(
            name = card.name,
            suit = card.suit,
            size = AvatarSize.L,
            active = card.status == PlayerStatus.PLAYING,
        )
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
            NameWithBadges(name = card.name, style = type.display) {
                if (card.isBanker) BankerBadge()
                if (card.rebuyCount > 0) RebuyBadge(count = card.rebuyCount)
            }
            Text(
                text = cardMeta(card, zone),
                style = type.ledger,
                color = colors.ledger,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CardMenu(card = card, onAction = onAction)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        MetricTile(
            label = stringResource(R.string.player_chips_bought),
            value = MoneyFormat.number(card.chipsBought),
            contentDescription =
                stringResource(
                    R.string.player_metric_a11y,
                    stringResource(R.string.player_chips_bought),
                    MoneyFormat.number(card.chipsBought),
                ),
            modifier = Modifier.weight(weight = 1f),
        )
        MetricTile(
            label = stringResource(R.string.player_charged),
            value = MoneyFormat.rub(card.charged),
            contentDescription =
                stringResource(
                    R.string.player_metric_a11y,
                    stringResource(R.string.player_charged),
                    a11yRubles(card.charged),
                ),
            modifier = Modifier.weight(weight = 1f),
        )
        val (label, amount, variant) =
            when {
                card.debt > 0 -> Triple(stringResource(R.string.player_debt), card.debt, MetricVariant.Owe)

                card.owedByBanker > 0 -> Triple(
                    stringResource(R.string.player_banker_owes),
                    card.owedByBanker,
                    MetricVariant.Pay,
                )

                else -> Triple(stringResource(R.string.player_paid), card.paid, MetricVariant.Neutral)
            }
        MetricTile(
            label = label,
            value = MoneyFormat.rub(amount),
            contentDescription = stringResource(R.string.player_metric_a11y, label, a11yRubles(amount)),
            variant = variant,
            modifier = Modifier.weight(weight = 1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        CardAction(
            text = stringResource(R.string.player_action_buy_in),
            icon = FeltbookIcons.Plus,
            primary = true,
            enabled = card.canBuyIn,
            onClick = { onAction(TableAction.CardBuyIn(card.playerId)) },
            modifier = Modifier.weight(weight = 1f),
        )
        CardAction(
            text = stringResource(
                if (card.canPayout &&
                    !card.canPay
                ) {
                    R.string.player_action_payout
                } else {
                    R.string.player_action_pay
                },
            ),
            icon = FeltbookIcons.Payment,
            primary = false,
            enabled = card.canPay || card.canPayout,
            onClick = {
                onAction(if (card.canPay) TableAction.CardPay(card.playerId) else TableAction.CardPayout(card.playerId))
            },
            modifier = Modifier.weight(weight = 1f),
        )
        if (card.status == PlayerStatus.PLAYING && card.canCashOut) {
            CardAction(
                text = stringResource(R.string.player_action_cash_out),
                icon = FeltbookIcons.Exit,
                primary = false,
                enabled = true,
                onClick = { onAction(TableAction.CardCashOut(card.playerId)) },
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.journal_title),
            style = type.sectionTitle,
            color = colors.ink,
            modifier = Modifier.weight(weight = 1f),
        )
        Text(text = stringResource(R.string.journal_hold_hint), style = type.small, color = colors.ledger)
    }
    JournalList(
        rows = card.journal,
        now = now,
        onHold = { onAction(TableAction.JournalHold(card.playerId, it)) },
    )
}

@Composable
private fun cardMeta(card: PlayerCardUi, zone: java.time.ZoneId): String {
    val played = card.playedFor?.let { formatDuration(it) } ?: stringResource(app.feltbook.core.ui.R.string.common_dash)
    val base = when {
        card.status == PlayerStatus.PLAYING && card.firstBuyInAt != null ->
            stringResource(R.string.player_meta_playing, played, TimeFormat.clock(card.firstBuyInAt, zone))

        card.status == PlayerStatus.PLAYING -> stringResource(R.string.table_row_not_bought)

        else ->
            stringResource(
                R.string.player_meta_left,
                card.lastCashOutAt?.let { TimeFormat.clock(it, zone) }.orEmpty(),
                played,
            )
    }
    return if (card.earlyExit) base + stringResource(R.string.player_meta_early) else base
}

@Composable
private fun CardMenu(card: PlayerCardUi, onAction: (TableAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    var open by remember { mutableStateOf(false) }
    if (!card.canRename && !card.canEarlyExit && !card.canForgive) return
    Box {
        FeltIconButton(
            icon = FeltbookIcons.MoreVert,
            contentDescription = stringResource(R.string.player_more_a11y),
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.dialog) {
            if (card.canRename) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.player_menu_rename), style = type.bodyL, color = colors.ink)
                    },
                    onClick = {
                        open = false
                        onAction(TableAction.CardRename(card.playerId))
                    },
                )
            }
            if (card.canEarlyExit) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.player_menu_early_exit), style = type.bodyL, color = colors.ink)
                    },
                    onClick = {
                        open = false
                        onAction(TableAction.CardEarlyExit(card.playerId))
                    },
                )
            }
            if (card.canForgive) {
                DropdownMenuItem(
                    text = {
                        Text(stringResource(R.string.player_menu_forgive), style = type.bodyL, color = colors.owe)
                    },
                    onClick = {
                        open = false
                        onAction(TableAction.CardForgive(card.playerId))
                    },
                )
            }
        }
    }
}

/** Кнопка карточки: h56 r16, иконка над подписью; «Докуп» — латунь, остальные — контур. Debounce 600 мс. */
@Composable
private fun CardAction(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    val click = rememberDebouncedClick(onClick = onClick)
    val content = when {
        primary && enabled -> colors.onBrass
        primary -> colors.onBrassDisabled
        enabled -> colors.ink
        else -> colors.inkDisabled
    }
    Column(
        modifier =
            modifier
                .heightIn(min = 56.dp)
                .clip(shape)
                .then(
                    if (primary) {
                        Modifier.background(color = if (enabled) colors.brass else colors.brassDisabled)
                    } else {
                        Modifier.background(
                            color = colors.outlinedFill,
                        ).border(width = 1.dp, color = colors.outline, shape = shape)
                    },
                ).clickable(enabled = enabled, role = Role.Button, onClick = click)
                .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 2.dp, alignment = Alignment.CenterVertically),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(size = 20.dp))
        Text(
            text = text,
            style = if (primary) type.labelBold else type.labelSemi,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Журнал игрока: новые сверху; удержание 0,6 с — отмена записи через диалог. */
@Composable
fun JournalList(rows: List<JournalRowUi>, now: Instant, onHold: (operationId: String) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    if (rows.isEmpty()) {
        Text(text = stringResource(R.string.journal_empty), style = type.caption, color = colors.muted)
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row -> JournalRow(row = row, now = now, onHold = { onHold(row.operationId) }) }
    }
}

@Composable
private fun JournalRow(row: JournalRowUi, now: Instant, onHold: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val zone = LocalTimeZone.current
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
    val actionLabel = stringResource(R.string.journal_reverse_action_a11y)
    val title = row.title.resolve()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(colors.hairline, size = Size(size.width, 1.dp.toPx()))
                    if (progress.value > 0f) {
                        drawRect(colors.brassSelectedRow, size = Size(size.width * progress.value, size.height))
                    }
                }.pointerInput(row.operationId) {
                    detectTapGestures(
                        onPress = {
                            if (!row.reversible) {
                                // Не отменяется: без заливки — вибрация отказа и причина.
                                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.Reject)
                                hold()
                                return@detectTapGestures
                            }
                            val job = scope.launch {
                                progress.animateTo(
                                    1f,
                                    tween(durationMillis = HOLD_TO_CONFIRM_MILLIS, easing = LinearEasing),
                                )
                                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                hold()
                            }
                            tryAwaitRelease()
                            if (progress.value < 1f) job.cancel()
                            scope.launch { progress.animateTo(0f, tween(durationMillis = HOLD_RELEASE_MILLIS)) }
                        },
                    )
                }.semantics(mergeDescendants = true) {
                    customActions = listOf(
                        CustomAccessibilityAction(actionLabel) {
                            hold()
                            true
                        },
                    )
                },
    ) {
        JournalLine(
            time = time,
            text = title,
            amount = row.amount,
            trailing = if (row.reversed) stringResource(R.string.journal_reversed) else null,
            soft = false,
            struck = row.reversed,
            modifier = if (row.reversed) Modifier.alpha(alpha = 0.55f) else Modifier,
        )
        if (!row.reversed) {
            row.subs.forEach { sub ->
                JournalLine(
                    time = time,
                    text = sub.text.resolve(),
                    amount = sub.amount,
                    trailing = null,
                    soft = true,
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
    soft: Boolean,
    struck: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val textColor = if (soft) colors.ledgerLight else colors.ink
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
            maxLines = 2,
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
            )
        }
    }
}
