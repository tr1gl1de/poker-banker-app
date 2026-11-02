package app.feltbook.feature.settings.odds

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.Position
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.SegmentedToggle
import app.feltbook.core.ui.component.SegmentedToggleStyle
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookGradients
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.core.ui.theme.feltTable
import app.feltbook.feature.settings.R

/** Наибольшее число позиций, которое помещается в сегменты равной ширины; больше — горизонтальная прокрутка. */
private const val FIXED_POSITIONS = 6
private const val SCROLLING_POSITIONS_FONT_SCALE = 1.5f
private const val FLOP_CARDS = 3
private const val TURN_INDEX = 3
private const val SECOND_CARD_TILT = 4f

/** Маршрут [app.feltbook.feature.settings.OddsRoute]: экран без нижней панели, «Назад» — к настройкам. */
@Composable
fun OddsRouteScreen(onBack: () -> Unit, viewModel: OddsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OddsScreen(state = state, onAction = viewModel::onAction, onBack = onBack)
}

/**
 * Калькулятор шансов (m3-ui-spec §5.14, макет `Odds.dc.html`): карты, игроки, банк и ставка, позиция, эквити,
 * шансы банка, ауты или сила руки на префлопе, памятка по позициям. Только отрисовка [state].
 */
@Composable
fun OddsScreen(state: OddsUiState, onAction: (OddsAction) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().feltBackground(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
        ) {
            OddsHeader(onBack = onBack, onReset = { onAction(OddsAction.Reset) })
            CardsSection(
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
            )
            PlayersAndAmounts(
                state = state,
                onAction = onAction,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
            PositionSelector(
                positions = state.positions,
                selected = state.position,
                onSelect = { onAction(OddsAction.SelectPosition(it)) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )
            EquitySection(state = state, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp))
            ResultTiles(state = state, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
            StatusLine(state = state, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
            MemoSection(
                positions = state.positions,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }
    }
    state.picker?.let { CardPickerSheet(picker = it, onAction = onAction) }
    state.amountEditor?.let { OddsAmountSheet(editor = it, onAction = onAction) }
}

@Composable
private fun OddsHeader(onBack: () -> Unit, onReset: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeltIconButton(
            icon = FeltbookIcons.Back,
            contentDescription = stringResource(R.string.odds_back),
            onClick = onBack,
            filled = false,
        )
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 1.dp)) {
            Text(
                text = stringResource(R.string.odds_title),
                style = type.displayS,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(R.string.odds_subtitle), style = type.ledgerSCaps, color = colors.brass)
        }
        FeltIconButton(
            icon = FeltbookIcons.Reset,
            contentDescription = stringResource(R.string.odds_reset),
            onClick = onReset,
        )
    }
}

@Composable
private fun CardsSection(state: OddsUiState, onAction: (OddsAction) -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .feltTable(shape = shape, geometry = FeltbookGradients.Odds)
                .border(width = 1.dp, color = colors.brassBorderOdds, shape = shape)
                .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
    ) {
        val hole: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                Text(
                    text = stringResource(R.string.odds_my_cards).uppercase(),
                    style = type.tinyCaps,
                    color = colors.inkSoft,
                    maxLines = 1,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                    state.hole.forEachIndexed { index, card ->
                        HoleSlot(index = index, card = card, onAction = onAction)
                    }
                }
            }
        }
        val board: @Composable (Modifier) -> Unit = { boardModifier ->
            Column(modifier = boardModifier, verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
                Text(
                    text = stringResource(boardLabel(state.board.size)).uppercase(),
                    style = type.tinyCaps,
                    color = colors.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                BoardRow(board = state.board, onAction = onAction)
            }
        }
        // Крупный шрифт или узкий экран (360 dp): стол уходит под свои карты, чтобы пять слотов не обрезались.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (isLargeFont() || maxWidth < CardsRowMinWidth) {
                Column(verticalArrangement = Arrangement.spacedBy(space = 12.dp)) {
                    hole()
                    board(Modifier.fillMaxWidth())
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(space = 16.dp)) {
                    hole()
                    board(Modifier.weight(weight = 1f))
                }
            }
        }
        DeadCardsRow(state = state, onClick = { onAction(OddsAction.OpenDeadCards) })
    }
}

private fun boardLabel(size: Int): Int = when (size) {
    0 -> R.string.odds_board_preflop
    in 1..FLOP_CARDS -> R.string.odds_board_flop
    TURN_INDEX + 1 -> R.string.odds_board_turn
    else -> R.string.odds_board_river
}

@Composable
private fun HoleSlot(index: Int, card: Card?, onAction: (OddsAction) -> Unit) {
    val open = { onAction(OddsAction.OpenSlot(CardSlot.Hole(index))) }
    val size = PlayingCardSize.L
    if (card != null) {
        PlayingCard(
            card = card,
            size = size,
            onClick = open,
            modifier = if (index == 1) Modifier.rotate(degrees = SECOND_CARD_TILT) else Modifier,
        )
    } else {
        CardSlotBox(
            width = size.width,
            height = size.height,
            radius = size.radius,
            label = stringResource(R.string.odds_slot_plus),
            contentDescription = stringResource(R.string.odds_pick_hole),
            enabled = true,
            onClick = open,
        )
    }
}

@Composable
private fun BoardRow(board: List<Card>, onAction: (OddsAction) -> Unit) {
    val size = PlayingCardSize.S
    val plus = stringResource(R.string.odds_slot_plus)
    Row(horizontalArrangement = Arrangement.spacedBy(space = 4.dp)) {
        board.forEachIndexed { index, card ->
            PlayingCard(card = card, size = size, onClick = { onAction(OddsAction.OpenSlot(CardSlot.Board(index))) })
        }
        val next = board.size
        val openNext = { onAction(OddsAction.OpenSlot(CardSlot.Board(next))) }
        when (next) {
            0 ->
                CardSlotBox(
                    width = size.width * FLOP_CARDS + 4.dp * (FLOP_CARDS - 1),
                    height = size.height,
                    radius = size.radius,
                    label = stringResource(R.string.odds_add_flop_label),
                    contentDescription = stringResource(R.string.odds_add_flop),
                    enabled = true,
                    onClick = openNext,
                    labelStyle = FeltbookTheme.typography.labelSemi,
                )

            in 1 until FLOP_CARDS -> repeat(FLOP_CARDS - next) { offset ->
                BoardSlot(plus, R.string.odds_add_flop, enabled = offset == 0, onClick = openNext)
            }

            TURN_INDEX -> {
                BoardSlot(plus, R.string.odds_add_turn, enabled = true, onClick = openNext)
                BoardSlot(plus, R.string.odds_add_river, enabled = false, onClick = {})
            }

            TURN_INDEX + 1 -> BoardSlot(plus, R.string.odds_add_river, enabled = true, onClick = openNext)

            else -> Unit
        }
    }
}

@Composable
private fun BoardSlot(label: String, description: Int, enabled: Boolean, onClick: () -> Unit) {
    val size = PlayingCardSize.S
    CardSlotBox(
        width = size.width,
        height = size.height,
        radius = size.radius,
        label = label,
        contentDescription = stringResource(description),
        enabled = enabled,
        onClick = onClick,
    )
}

/** Необязательные мёртвые карты [без макета]: строка внизу блока карт, открывает шторку на вкладке «Мёртвые карты». */
@Composable
private fun DeadCardsRow(state: OddsUiState, onClick: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val value =
        if (state.dead.isEmpty()) {
            stringResource(R.string.odds_dead_none)
        } else {
            state.dead.map { cardShort(it) }.joinToString(separator = " ")
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .clip(FeltbookTheme.shapes.key)
                .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.odds_dead_cards).uppercase(),
            style = type.tinyCaps,
            color = colors.inkSoft,
        )
        Text(
            text = value,
            style = type.ledger,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f),
        )
        Icon(
            imageVector = FeltbookIcons.ArrowForward,
            contentDescription = null,
            tint = colors.inkSoft,
            modifier = Modifier.size(size = FeltbookDimens.iconS),
        )
    }
}

@Composable
private fun PlayersAndAmounts(state: OddsUiState, onAction: (OddsAction) -> Unit, modifier: Modifier = Modifier) {
    val amounts: @Composable (Modifier) -> Unit = { amountsModifier ->
        Row(modifier = amountsModifier, horizontalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            AmountBox(
                label = stringResource(R.string.odds_pot),
                value = state.pot,
                onClick = { onAction(OddsAction.OpenAmount(OddsAmountField.POT)) },
                modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
            )
            AmountBox(
                label = stringResource(R.string.odds_bet),
                value = state.call,
                onClick = { onAction(OddsAction.OpenAmount(OddsAmountField.CALL)) },
                modifier = Modifier.weight(weight = 1f).fillMaxHeight(),
            )
        }
    }
    // Крупный шрифт: банк и ставка — отдельной строкой во всю ширину, суммы не обрезаются.
    if (isLargeFont()) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            PlayersStepper(state = state, onAction = onAction)
            amounts(Modifier.fillMaxWidth().height(IntrinsicSize.Min))
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            PlayersStepper(state = state, onAction = onAction)
            amounts(Modifier.weight(weight = 1f).fillMaxHeight())
        }
    }
}

@Composable
private fun PlayersStepper(state: OddsUiState, onAction: (OddsAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val count = pluralStringResource(UiR.plurals.players_count, state.players, state.players)
    Row(
        modifier =
            Modifier
                .fillMaxHeight()
                .background(color = colors.card, shape = FeltbookTheme.shapes.field)
                .padding(all = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            glyph = stringResource(R.string.odds_minus_glyph),
            description = stringResource(R.string.odds_fewer_players),
            enabled = state.canRemovePlayer,
            onClick = { onAction(OddsAction.FewerPlayers) },
        )
        Column(
            modifier = Modifier.widthIn(min = 64.dp).clearAndSetSemantics { contentDescription = count },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = state.players.toString(), style = type.amountMetric, color = colors.ink, maxLines = 1)
            Text(
                text = pluralStringResource(UiR.plurals.players_word, state.players),
                style = type.caption,
                color = colors.inkSoft,
                maxLines = 1,
            )
        }
        StepButton(
            glyph = stringResource(R.string.odds_plus_glyph),
            description = stringResource(R.string.odds_more_players),
            enabled = state.canAddPlayer,
            onClick = { onAction(OddsAction.MorePlayers) },
        )
    }
}

@Composable
private fun StepButton(glyph: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = FeltbookTheme.colors
    Box(
        modifier =
            Modifier
                .minimumInteractiveComponentSize()
                .size(size = 40.dp)
                .clip(FeltbookTheme.shapes.segment)
                .background(color = colors.quietFill)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        FixedFontScale {
            Text(
                text = glyph,
                style = FeltbookTheme.typography.glyphButton,
                color = if (enabled) colors.ink else colors.inkDisabled,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** «Банк» / «Ставка»: нажатие — шторка со своей клавиатурой (системная не открывается). */
@Composable
private fun AmountBox(label: String, value: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val editLabel = stringResource(R.string.odds_amount_edit)
    Column(
        modifier =
            modifier
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .clip(FeltbookTheme.shapes.key)
                .background(color = colors.card)
                .clickable(role = Role.Button, onClickLabel = editLabel, onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = label, style = type.tiny, color = colors.ledger, maxLines = 1, overflow = TextOverflow.Ellipsis)
        AmountText(
            text = MoneyFormat.number(value),
            style = type.emphasisM,
            color = colors.ink,
            contentDescription = a11yRubles(value),
        )
    }
}

@Composable
private fun PositionSelector(
    positions: List<Position>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.odds_position)
    val labeled = modifier.semantics { contentDescription = description }
    // Крупный шрифт (≥ 150 %) — прокрутка с растущим текстом: в равных сегментах аббревиатуры не помещаются.
    val largeFont = LocalDensity.current.fontScale >= SCROLLING_POSITIONS_FONT_SCALE
    if (positions.size <= FIXED_POSITIONS && !largeFont) {
        SegmentedToggle(
            options = positions.map { it.label },
            selectedIndex = positions.indexOfFirst { it.label == selected },
            onSelect = { onSelect(positions[it].label) },
            modifier = labeled,
            style = SegmentedToggleStyle.Position,
        )
    } else {
        ScrollingPositions(positions = positions, selected = selected, onSelect = onSelect, modifier = labeled)
    }
}

/** 7–10 игроков: сегменты шириной 56 с горизонтальной прокруткой (m3-ui-spec §5.14), вид — как `SegmentedToggle`. */
@Composable
private fun ScrollingPositions(
    positions: List<Position>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val segmentShape = FeltbookTheme.shapes.segmentCompact
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(FeltbookTheme.shapes.key)
                .background(color = colors.feltDeep)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 3.dp)
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        positions.forEach { position ->
            val isSelected = position.label == selected
            Box(
                modifier =
                    Modifier
                        .widthIn(min = 56.dp)
                        .heightIn(min = FeltbookDimens.minTouchTarget)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(position.label) },
                        )
                        .padding(vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .widthIn(min = 56.dp)
                            .heightIn(min = 36.dp)
                            .then(
                                if (isSelected) {
                                    Modifier
                                        .background(color = colors.card, shape = segmentShape)
                                        .border(width = 1.5.dp, color = colors.selectedBorder, shape = segmentShape)
                                } else {
                                    Modifier
                                },
                            ).padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = position.label,
                        style = if (isSelected) type.positionLabelSelected else type.positionLabel,
                        color = if (isSelected) colors.brassBright else colors.muted,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun OddsFlopPreview() {
    FeltbookTheme { OddsScreen(state = PreviewOddsStates.flop(), onAction = {}, onBack = {}) }
}

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun OddsEmptyPreview() {
    FeltbookTheme { OddsScreen(state = PreviewOddsStates.empty(), onAction = {}, onBack = {}) }
}

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun OddsPreflopPreview() {
    FeltbookTheme { OddsScreen(state = PreviewOddsStates.preflop(), onAction = {}, onBack = {}) }
}

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun OddsCalculatingPreview() {
    FeltbookTheme { OddsScreen(state = PreviewOddsStates.calculating(), onAction = {}, onBack = {}) }
}

@Preview(widthDp = 390, heightDp = 1200)
@Composable
private fun OddsRiverTenPlayersPreview() {
    FeltbookTheme { OddsScreen(state = PreviewOddsStates.riverTenPlayers(), onAction = {}, onBack = {}) }
}

/** Шрифт крупнее 130 %: раскладки строк превращаются в столбцы. */
@Composable
private fun isLargeFont(): Boolean = LocalDensity.current.fontScale > LARGE_FONT_SCALE

private const val LARGE_FONT_SCALE = 1.3f

/** Свои карты (2 × 52 + 6) + зазор 16 + стол (5 × 36 + 4 × 4): уже — стол переносится под свои карты. */
private val CardsRowMinWidth = 322.dp
