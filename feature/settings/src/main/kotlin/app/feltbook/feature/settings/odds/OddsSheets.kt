package app.feltbook.feature.settings.odds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.odds.Card
import app.feltbook.core.ui.component.AmountKeypad
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.component.SegmentedToggle
import app.feltbook.core.ui.component.SegmentedToggleStyle
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.settings.R

private const val UNAVAILABLE_ALPHA = 0.3f
private const val FLOP_CARDS = 3
private const val TURN_INDEX = 3

/** Шторка выбора карты [без макета] (m3-ui-spec §5.14): сетка 4 масти × 13 рангов, вкладка «Мёртвые карты». */
@Composable
internal fun CardPickerSheet(picker: CardPickerUi, onAction: (OddsAction) -> Unit) {
    FeltbookSheet(onDismiss = { onAction(OddsAction.ClosePicker) }, contentSpacing = 12.dp) {
        CardPickerContent(picker = picker, onAction = onAction)
    }
}

/** Содержимое шторки выбора (отдельно — для скриншотов без окна). */
@Composable
internal fun CardPickerContent(picker: CardPickerUi, onAction: (OddsAction) -> Unit) {
    val slot = picker.slot
    SheetTitle(
        title = stringResource(if (picker.deadMode) R.string.odds_dead_cards else R.string.odds_card_picker),
        subtitle = stringResource(if (picker.deadMode || slot == null) R.string.odds_dead_hint else slotLabel(slot)),
    )
    if (slot != null) {
        SegmentedToggle(
            options = listOf(stringResource(R.string.odds_picker_tab_card), stringResource(R.string.odds_dead_cards)),
            selectedIndex = if (picker.deadMode) 1 else 0,
            onSelect = { onAction(OddsAction.PickerTab(dead = it == 1)) },
            style = SegmentedToggleStyle.Compact,
        )
    }
    CardGrid(picker = picker, onPick = { onAction(OddsAction.PickCard(it)) })
    when {
        picker.deadMode ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
                if (picker.deadCount > 0) {
                    OutlineButton(
                        text = stringResource(R.string.odds_clear_dead),
                        onClick = { onAction(OddsAction.ClearDead) },
                        modifier = Modifier.weight(weight = 1f),
                    )
                }
                BrassButton(
                    text = stringResource(R.string.odds_done),
                    onClick = { onAction(OddsAction.ClosePicker) },
                    modifier = Modifier.weight(weight = 1f),
                    style = BrassButtonStyle.Flat52,
                )
            }

        picker.canRemove ->
            OutlineButton(
                text = stringResource(R.string.odds_remove_card),
                onClick = { onAction(OddsAction.RemoveCard) },
                modifier = Modifier.fillMaxWidth(),
            )
    }
}

private fun slotLabel(slot: CardSlot): Int = when (slot) {
    is CardSlot.Hole -> R.string.odds_my_cards

    is CardSlot.Board -> when {
        slot.index < FLOP_CARDS -> R.string.odds_board_flop
        slot.index == TURN_INDEX -> R.string.odds_board_turn
        else -> R.string.odds_board_river
    }
}

/** 13 строк (туз сверху) × 4 колонки (♠ ♥ ♦ ♣): 4 × 13 колонок не помещаются в цели 48 dp. */
@Composable
private fun CardGrid(picker: CardPickerUi, onPick: (Card) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        RankOrder.forEach { rank ->
            Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                SuitOrder.forEach { suit ->
                    val card = Card.of(rank, suit)
                    GridCard(
                        card = card,
                        unavailable = card in picker.unavailable,
                        selected = card in picker.selected,
                        checkbox = picker.deadMode,
                        onClick = { onPick(card) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.GridCard(
    card: Card,
    unavailable: Boolean,
    selected: Boolean,
    checkbox: Boolean,
    onClick: () -> Unit,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(6.dp)
    val name = cardName(card)
    val taken = stringResource(R.string.odds_card_unavailable)
    Box(
        modifier =
            Modifier
                .weight(weight = 1f)
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .alpha(alpha = if (unavailable) UNAVAILABLE_ALPHA else 1f)
                .clip(shape)
                .background(color = colors.ivory)
                .then(if (selected) Modifier.border(width = 2.5.dp, color = colors.brass, shape = shape) else Modifier)
                .selectable(
                    selected = selected,
                    enabled = !unavailable,
                    role = if (checkbox) Role.Checkbox else Role.RadioButton,
                    onClick = onClick,
                ).semantics {
                    contentDescription = name
                    if (unavailable) stateDescription = taken
                },
        contentAlignment = Alignment.Center,
    ) {
        CardFace(card = card, rankStyle = type.cardRankBoard, suitStyle = type.suitBoard)
    }
}

/** Шторка суммы «Банк» / «Ставка»: своя клавиатура, системная не открывается. */
@Composable
internal fun OddsAmountSheet(editor: AmountEditorUi, onAction: (OddsAction) -> Unit) {
    FeltbookSheet(onDismiss = { onAction(OddsAction.CloseAmount) }) {
        OddsAmountContent(editor = editor, onAction = onAction)
    }
}

@Composable
internal fun OddsAmountContent(editor: AmountEditorUi, onAction: (OddsAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val (title, hint) = when (editor.field) {
        OddsAmountField.POT -> R.string.odds_pot to R.string.odds_pot_hint
        OddsAmountField.CALL -> R.string.odds_bet to R.string.odds_bet_hint
    }
    SheetTitle(title = stringResource(title), subtitle = stringResource(hint))
    AmountText(
        text = MoneyFormat.number(editor.entry.value),
        style = type.amountInput,
        color = if (editor.entry.isEmpty) colors.muted else colors.ink,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = a11yRubles(editor.entry.value),
    )
    AmountKeypad(entry = editor.entry, onEntryChange = { onAction(OddsAction.AmountInput(it)) })
    BrassButton(
        text = stringResource(R.string.odds_done),
        onClick = { onAction(OddsAction.ConfirmAmount) },
        modifier = Modifier.fillMaxWidth(),
        style = BrassButtonStyle.Flat56,
    )
}

/** Для превью и скриншотов: содержимое шторки на фоне `sheet`. */
@Composable
internal fun SheetFrame(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(color = FeltbookTheme.colors.sheet, shape = FeltbookTheme.shapes.sheet)
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        content()
    }
}

@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun CardPickerPreview() {
    FeltbookTheme {
        SheetFrame { CardPickerContent(picker = requireNotNull(PreviewOddsStates.pickerSlot().picker), onAction = {}) }
    }
}

@Preview(widthDp = 390, heightDp = 900)
@Composable
private fun DeadCardsPickerPreview() {
    FeltbookTheme {
        SheetFrame { CardPickerContent(picker = requireNotNull(PreviewOddsStates.pickerDead().picker), onAction = {}) }
    }
}

@Preview(widthDp = 390)
@Composable
private fun AmountSheetPreview() {
    FeltbookTheme {
        SheetFrame {
            OddsAmountContent(editor = requireNotNull(PreviewOddsStates.amountEditor().amountEditor), onAction = {})
        }
    }
}
