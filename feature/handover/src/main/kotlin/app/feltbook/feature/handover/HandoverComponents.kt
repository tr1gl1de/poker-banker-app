package app.feltbook.feature.handover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.feltbook.core.domain.engine.HandoverAct
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountRowLayout
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BreakdownList
import app.feltbook.core.ui.component.BreakdownRow
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltbookSwitch
import app.feltbook.core.ui.component.SegmentedToggle
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/**
 * Каркас экрана (m3-ui-spec §5.10): шапка «Передача комнаты», переключатель «Отдать игру / Принять игру»
 * и содержимое вкладки. Нижняя панель приложения скрыта; ширина ≤ 600 dp по центру (планшет).
 */
@Composable
fun HandoverFrame(
    mode: HandoverMode,
    onModeChange: (HandoverMode) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    modeLocked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Box(modifier = modifier.fillMaxSize().feltBackground(), contentAlignment = Alignment.TopCenter) {
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp)) {
            Row(
                modifier =
                    Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FeltIconButton(
                    icon = FeltbookIcons.Back,
                    contentDescription = stringResource(R.string.handover_back),
                    onClick = onBack,
                    filled = false,
                )
                Text(
                    text = stringResource(R.string.handover_title),
                    style = type.display,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
            }
            SegmentedToggle(
                options = listOf(stringResource(R.string.handover_give), stringResource(R.string.handover_receive)),
                selectedIndex = mode.ordinal,
                onSelect = { onModeChange(HandoverMode.entries[it]) },
                enabled = !modeLocked,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
            )
            content()
        }
    }
}

/** Карточка устройства/состояния: `bg c.card, r20, p16, gap 12`. */
@Composable
fun DeviceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = FeltbookTheme.colors
    val shape = RoundedCornerShape(size = 20.dp)
    Column(
        modifier =
            modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.card)
                .padding(all = 16.dp),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

/** «Телефон: Влад» / «найден рядом» с иконкой телефона в круге `payIconContainer`. */
@Composable
fun DeviceRow(
    peerName: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (onClick != null) {
                        Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                ),
        horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(size = 40.dp).clip(CircleShape).background(color = colors.payIconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = FeltbookIcons.Phone,
                contentDescription = null,
                tint = colors.pay,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(
                text = stringResource(R.string.handover_device_name, peerName),
                style = type.title,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: stringResource(R.string.handover_device_nearby),
                style = type.ledger,
                color = colors.ledger,
            )
        }
    }
}

/** «Сверьте код на обоих телефонах» + 4 цифры `52×64 r12 bg feltDeep border brassBorder`, TalkBack — «Код 4 7 1 9». */
@Composable
fun CodeDigits(digits: String, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val spoken = digits.toList().joinToString(separator = " ")
    val description = stringResource(R.string.handover_code_a11y, spoken)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.handover_check_code), style = type.caption, color = colors.inkSoft)
        Row(
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
        ) {
            digits.forEach { digit ->
                val shape = RoundedCornerShape(size = 12.dp)
                Box(
                    modifier =
                        Modifier
                            .widthIn(min = 52.dp)
                            .heightIn(min = 64.dp)
                            .clip(shape)
                            .background(color = colors.feltDeep)
                            .border(width = 1.dp, color = colors.brassBorder, shape = shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = digit.toString(), style = type.codeDigit, color = colors.brassBright)
                }
            }
        }
    }
}

/** Строка ожидания: латунный индикатор 20 dp + текст; TalkBack объявляет смену состояния. */
@Composable
fun WaitingRow(text: String, modifier: Modifier = Modifier, hint: String? = null) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.brass, strokeWidth = 2.dp)
            Text(text = text, style = type.title, color = colors.ink)
        }
        if (hint != null) {
            Text(text = hint, style = type.caption, color = colors.inkSoft, textAlign = TextAlign.Center)
        }
    }
}

/** «Отправляем…» с полосой прогресса (null — неизвестный размер). */
@Composable
fun ProgressRow(text: String, fraction: Float?, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val percent = ((fraction ?: 0f) * PERCENT).toInt()
    val description = stringResource(R.string.handover_progress_a11y, percent)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = type.title, color = colors.ink)
        val barModifier = Modifier.fillMaxWidth().semantics { contentDescription = description }
        if (fraction == null) {
            LinearProgressIndicator(modifier = barModifier, color = colors.brass, trackColor = colors.feltDeep)
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = barModifier,
                color = colors.brass,
                trackColor = colors.feltDeep,
            )
        }
    }
}

/** Заголовок и текст внутри карточки (разрешения, имя, итог). */
@Composable
fun CardMessage(title: String, body: String?, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(space = 6.dp),
    ) {
        Text(text = title, style = type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
        if (body != null) Text(text = body, style = type.caption, color = colors.inkSoft)
    }
}

/** «♦ Акт передачи кассы» (макет Handover): касса, рейк, долги, доход, «Оставить доход себе», «Отдать наличными». */
@Composable
fun ActSection(
    act: HandoverAct,
    keepIncome: Boolean,
    keepIncomeEnabled: Boolean,
    cashToHandOver: Long,
    onKeepIncome: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    switchEnabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    SheetSection(modifier = modifier) {
        Text(
            text = stringResource(R.string.handover_act),
            style = type.sectionTitle,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        BreakdownList(
            rows = buildList {
                add(row(stringResource(R.string.handover_cash), act.cashOnHand))
                add(row(stringResource(R.string.handover_rake), act.rakeDue))
                add(row(stringResource(R.string.handover_debts), act.playersOwe))
                if (act.bankerOwes > 0) add(row(stringResource(R.string.handover_banker_owes), act.bankerOwes))
            },
            card = false,
            ledgerLabels = true,
        )
        AmountRowLayout(
            trailing = {
                AmountText(amount = act.fromBankerIncome, style = type.body, color = colors.brassLight)
            },
        ) {
            Text(text = stringResource(R.string.handover_my_income), style = type.body, color = colors.ledger)
        }
        val canToggle = keepIncomeEnabled && switchEnabled
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .alpha(alpha = if (keepIncomeEnabled) 1f else DISABLED_ALPHA)
                    .toggleable(
                        value = keepIncome,
                        enabled = canToggle,
                        role = Role.Switch,
                        onValueChange = onKeepIncome,
                    ),
            horizontalArrangement = Arrangement.spacedBy(space = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label =
                if (keepIncomeEnabled) {
                    stringResource(R.string.handover_keep_income, MoneyFormat.rub(act.incomeTakeable))
                } else {
                    stringResource(R.string.handover_keep_income_later)
                }
            Text(text = label, style = type.caption, color = colors.ink, modifier = Modifier.weight(weight = 1f))
            // Переключает вся строка (toggleable): у самого переключателя своей цели касания нет.
            FeltbookSwitch(checked = keepIncome, onCheckedChange = null)
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = colors.divider,
                            start = Offset.Zero,
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }.padding(top = 10.dp)
                    .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.handover_cash_to_give),
                style = type.labelBold,
                color = colors.ink,
                modifier = Modifier.weight(weight = 1f),
            )
            AmountText(
                amount = cashToHandOver,
                style = type.amountAct,
                color = colors.brassBright,
                modifier = Modifier.widthIn(max = 200.dp),
            )
        }
    }
}

/** «Передаётся: 6 игроков · 8 300 фишек · 23 записи журнала». */
@Composable
fun PayloadLine(act: HandoverAct, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Text(
        text = stringResource(
            R.string.handover_payload,
            pluralStringResource(UiR.plurals.players_count, act.playersAtTable, act.playersAtTable),
            pluralStringResource(
                UiR.plurals.chips_count,
                pluralQuantity(act.chipsInPlay),
                MoneyFormat.number(act.chipsInPlay),
            ),
            pluralStringResource(UiR.plurals.entries_count, act.entryCount, act.entryCount),
        ),
        style = type.ledger,
        color = colors.ledger,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
    )
}

/** Шаблон после расчёта: правила + число имён (сценарий 2). */
@Composable
fun TemplateSection(rules: GameRules, names: Int, modifier: Modifier = Modifier, body: String? = null) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    SheetSection(modifier = modifier) {
        Text(
            text = stringResource(R.string.handover_template_title),
            style = type.sectionTitle,
            color = colors.ink,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) Text(text = body, style = type.caption, color = colors.inkSoft)
        BreakdownList(
            rows = listOf(
                BreakdownRow(stringResource(R.string.handover_template_rules), rulesSummary(rules)),
                BreakdownRow(stringResource(R.string.handover_template_names), MoneyFormat.number(names.toLong())),
            ),
            card = false,
            ledgerLabels = true,
        )
    }
}

/** «рейк 10 % · сбор 100 ₽». */
@Composable
fun rulesSummary(rules: GameRules): String {
    val rake = if (rules.rakeMode == RakeMode.PERCENT) {
        MoneyFormat.percent(rules.rakeValue.toInt())
    } else {
        MoneyFormat.rub(rules.rakeValue)
    }
    val fee = if (rules.bankerFeeMode.isPercent) {
        MoneyFormat.percent(rules.bankerFeeValue.toInt())
    } else {
        MoneyFormat.rub(rules.bankerFeeValue)
    }
    return stringResource(R.string.handover_rules_summary, rake, fee)
}

/** Блок `bg c.sheet, border 1 c.brassBorderMedium, r20, p(14 16), gap 10` — акт, сводка, шаблон. */
@Composable
fun SheetSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = FeltbookTheme.colors
    val shape = RoundedCornerShape(size = 20.dp)
    Column(
        modifier =
            modifier
                .padding(start = 16.dp, end = 16.dp, top = 12.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.sheet)
                .border(width = 1.dp, color = colors.brassBorderMedium, shape = shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        content()
    }
}

@Composable
private fun row(label: String, amount: Long): BreakdownRow =
    BreakdownRow(label = label, value = MoneyFormat.rub(amount), contentDescription = a11yRubles(amount))

private const val PERCENT = 100
private const val DISABLED_ALPHA = 0.5f
