package app.feltbook.feature.game.newgame

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountKeypad
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.FeltIconButton
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.SegmentedToggle
import app.feltbook.core.ui.component.SegmentedToggleStyle
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.component.SwitchRow
import app.feltbook.core.ui.component.ValueBox
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.formatShortDate
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.LocalTimeZone
import app.feltbook.feature.game.common.toUiText

@Composable
fun NewGameRouteScreen(
    onBack: () -> Unit,
    onStarted: (gameId: String) -> Unit,
    onOpenTable: (gameId: String) -> Unit,
    viewModel: NewGameViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is NewGameEvent.Started -> onStarted(event.gameId)
                is NewGameEvent.OpenActiveGame -> onOpenTable(event.gameId)
            }
        }
    }
    NewGameScreen(
        state = state,
        onAction = { action ->
            if (action == NewGameAction.Back) onBack() else viewModel.onAction(action)
        },
        onOpenActiveGame = viewModel::openActiveGame,
    )
}

/** «2 ноя, 20:05» — название по умолчанию. */
@Composable
fun defaultGameTitle(state: NewGameUiState): String {
    val zone = LocalTimeZone.current
    return stringResource(
        R.string.new_game_default_title,
        formatShortDate(TimeFormat.localDate(state.defaultTitleAt, zone)),
        TimeFormat.clock(state.defaultTitleAt, zone),
    )
}

/** Новая игра (m3-ui-spec §5.2). */
@Composable
fun NewGameScreen(
    state: NewGameUiState,
    onAction: (NewGameAction) -> Unit,
    modifier: Modifier = Modifier,
    onOpenActiveGame: () -> Unit = {},
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val defaultTitle = defaultGameTitle(state)
    Box(modifier = modifier.fillMaxSize().feltBackground(), contentAlignment = Alignment.TopCenter) {
        // Edge-to-edge: окно не сжимается под клавиатуру — «Начать игру» поднимается сам.
        Column(modifier = Modifier.fillMaxSize().widthIn(max = 600.dp).imePadding()) {
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
                    contentDescription = stringResource(UiR.string.common_back),
                    onClick = { onAction(NewGameAction.Back) },
                    filled = false,
                )
                Text(text = stringResource(R.string.new_game_title), style = type.display, color = colors.ink)
            }
            Column(
                modifier = Modifier.weight(weight = 1f).verticalScroll(rememberScrollState()),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(space = 10.dp),
                ) {
                    FeltTextField(
                        value = state.title,
                        onValueChange = { onAction(NewGameAction.TitleChange(it)) },
                        label = stringResource(R.string.new_game_field_title),
                        placeholder = defaultTitle,
                    )
                    FeltTextField(
                        value = state.bankerName,
                        onValueChange = { onAction(NewGameAction.BankerChange(it)) },
                        label = stringResource(R.string.new_game_field_banker),
                        error = state.bankerError?.resolve(),
                    )
                    SwitchRow(
                        title = stringResource(R.string.new_game_banker_plays),
                        subtitle = stringResource(R.string.new_game_banker_plays_hint),
                        checked = state.bankerPlays,
                        onCheckedChange = { onAction(NewGameAction.BankerPlaysChange(it)) },
                        modifier =
                            Modifier
                                .background(color = colors.card, shape = FeltbookTheme.shapes.field)
                                .padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                }
                RulesSection(state = state, onAction = onAction)
                state.error?.let { error ->
                    Text(
                        text = error.resolve(),
                        style = type.small,
                        color = colors.owe,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    )
                }
            }
            Box(
                modifier = Modifier.navigationBarsPadding().padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
            ) {
                BrassButton(
                    text = stringResource(R.string.new_game_start),
                    leadingGlyph = stringResource(R.string.new_game_start_glyph),
                    onClick = { onAction(NewGameAction.Start(defaultTitle)) },
                    style = BrassButtonStyle.Hero64,
                    enabled = state.canStart,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    state.editor?.let { editor -> RuleEditorSheet(editor = editor, rules = state, onAction = onAction) }
    if (state.anotherGameTitle != null) {
        FeltbookDialog(
            title = stringResource(R.string.home_game_already_running_title),
            body = stringResource(R.string.home_game_already_running_body, state.anotherGameTitle),
            safeLabel = stringResource(UiR.string.common_ok),
            onSafe = { onAction(NewGameAction.DismissDialog) },
            confirmLabel = stringResource(R.string.home_game_already_running_go),
            onConfirm = onOpenActiveGame,
            destructive = false,
        )
    }
}

@Composable
private fun RulesSection(state: NewGameUiState, onAction: (NewGameAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.cardLarge
    val rules = state.rules
    Column(
        modifier =
            Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .fillMaxWidth()
                .background(color = colors.sheet, shape = shape)
                .border(width = 1.dp, color = colors.brassBorderSoft, shape = shape)
                .padding(all = 14.dp),
        verticalArrangement = Arrangement.spacedBy(space = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.new_game_rules),
                style = type.sectionTitle,
                color = colors.ink,
                modifier = Modifier.weight(weight = 1f),
            )
            Text(text = stringResource(R.string.new_game_rules_hint), style = type.small, color = colors.ledger)
        }
        // ♥ Сбор банкира
        Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            Text(text = stringResource(R.string.rules_banker_fee), style = type.caption, color = colors.inkSoft)
            SegmentedToggle(
                options =
                    listOf(
                        stringResource(R.string.rules_fee_once_fixed, MoneyFormat.rub(state.feeFixedValue)),
                        stringResource(
                            R.string.rules_fee_once_percent,
                            MoneyFormat.percent(state.feePercentValue.toInt()),
                        ),
                        stringResource(R.string.rules_fee_each),
                    ),
                selectedIndex = state.feeSegment.ordinal,
                onSelect = { onAction(NewGameAction.FeeSegmentClick(FeeSegment.entries[it])) },
                style = SegmentedToggleStyle.Compact,
            )
            if (state.feeSegment == FeeSegment.EACH) {
                ModeAndValueRow(
                    percent = rules.bankerFeeMode == BankerFeeMode.EACH_PERCENT,
                    value = rules.bankerFeeValue,
                    valueDescription = stringResource(R.string.rules_fee_value_a11y),
                    onPercent = { onAction(NewGameAction.FeeEachPercent(it)) },
                    onValueClick = { onAction(NewGameAction.EditRule(RuleField.BANKER_FEE)) },
                )
            }
            ProblemText(state.problems, feeProblems)
        }
        // ♣ Рейк
        Column(verticalArrangement = Arrangement.spacedBy(space = 6.dp)) {
            Text(text = stringResource(R.string.rules_rake_each_purchase), style = type.caption, color = colors.inkSoft)
            ModeAndValueRow(
                percent = rules.rakeMode == RakeMode.PERCENT,
                value = rules.rakeValue,
                valueDescription =
                    stringResource(
                        if (rules.rakeMode == RakeMode.PERCENT) {
                            R.string.rules_rake_value_a11y
                        } else {
                            R.string.rules_rake_value_fixed_a11y
                        },
                    ),
                onPercent = { onAction(NewGameAction.RakePercent(it)) },
                onValueClick = { onAction(NewGameAction.EditRule(RuleField.RAKE)) },
            )
            ProblemText(state.problems, rakeProblems)
        }
        Column {
            RuleLine(
                label = stringResource(R.string.rules_min_buy_in),
                value =
                    stringResource(
                        R.string.rules_min_buy_in_value,
                        pluralStringResource(
                            UiR.plurals.chips_count,
                            pluralQuantity(rules.minBuyIn),
                            MoneyFormat.number(rules.minBuyIn),
                        ),
                    ),
                onClick = { onAction(NewGameAction.EditRule(RuleField.MIN_BUY_IN)) },
            )
            ProblemText(state.problems, minBuyInProblems)
            RuleLine(
                label = stringResource(R.string.rules_min_play_time),
                value = stringResource(R.string.rules_min_play_time_value, rules.minPlayMinutes),
                onClick = { onAction(NewGameAction.EditRule(RuleField.MIN_PLAY_MINUTES)) },
            )
            ProblemText(state.problems, listOf(RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE))
            RuleLine(
                label = stringResource(R.string.rules_payment),
                value =
                    stringResource(
                        if (rules.splitPayment) R.string.rules_payment_split else R.string.rules_payment_single,
                    ),
                onClick = { onAction(NewGameAction.ToggleSplitPayment) },
            )
        }
        val example = state.example
        Text(
            text =
                stringResource(
                    R.string.new_game_example_line1,
                    MoneyFormat.number(example.chips),
                    MoneyFormat.rub(example.total),
                ) + "\n" +
                    stringResource(
                        R.string.new_game_example_line2,
                        MoneyFormat.number(example.chips),
                        MoneyFormat.number(example.rake),
                        MoneyFormat.number(example.fee),
                    ),
            style = type.ledger,
            color = colors.ledgerLight,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(color = colors.feltDeep, shape = FeltbookTheme.shapes.key)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

private val feeProblems = listOf(
    RulesProblem.BANKER_FEE_NEGATIVE,
    RulesProblem.BANKER_FEE_PERCENT_ABOVE_MAX,
    RulesProblem.BANKER_FEE_ABOVE_MAX_AMOUNT,
)
private val rakeProblems = listOf(
    RulesProblem.RAKE_NEGATIVE,
    RulesProblem.RAKE_PERCENT_ABOVE_MAX,
    RulesProblem.RAKE_ABOVE_MAX_AMOUNT,
)
private val minBuyInProblems = listOf(RulesProblem.MIN_BUY_IN_NOT_POSITIVE, RulesProblem.MIN_BUY_IN_ABOVE_MAX_AMOUNT)

@Composable
private fun ProblemText(problems: List<RulesProblem>, relevant: List<RulesProblem>) {
    val problem = problems.firstOrNull { it in relevant } ?: return
    Text(text = problem.toUiText().resolve(), style = FeltbookTheme.typography.small, color = FeltbookTheme.colors.owe)
}

@Composable
private fun ModeAndValueRow(
    percent: Boolean,
    value: Long,
    valueDescription: String,
    onPercent: (Boolean) -> Unit,
    onValueClick: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SegmentedToggle(
            options = listOf(stringResource(R.string.rules_percent), stringResource(R.string.rules_fixed)),
            selectedIndex = if (percent) 0 else 1,
            onSelect = { onPercent(it == 0) },
            style = SegmentedToggleStyle.Compact,
            modifier = Modifier.weight(weight = 1f),
        )
        ValueBox(
            value = MoneyFormat.number(value),
            unit = stringResource(if (percent) R.string.rules_unit_percent else R.string.rules_unit_rub),
            onClick = onValueClick,
            contentDescription = "$valueDescription, ${MoneyFormat.number(value)}",
        )
    }
}

@Composable
private fun RuleLine(label: String, value: String, onClick: () -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(color = colors.hairline))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = type.body, color = colors.inkSoft, modifier = Modifier.weight(weight = 1f))
            Text(text = value, style = type.labelBold, color = colors.ink, maxLines = 1)
        }
    }
}

@Composable
private fun RuleEditorSheet(editor: RuleEditorUi, rules: NewGameUiState, onAction: (NewGameAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val title = when (editor.field) {
        RuleField.BANKER_FEE -> stringResource(R.string.rules_banker_fee)
        RuleField.RAKE -> stringResource(R.string.rules_rake_each_purchase)
        RuleField.MIN_BUY_IN -> stringResource(R.string.rules_min_buy_in)
        RuleField.MIN_PLAY_MINUTES -> stringResource(R.string.rules_min_play_time)
    }
    val unit = when (editor.field) {
        RuleField.BANKER_FEE ->
            stringResource(
                if (rules.rules.bankerFeeMode.isPercent) R.string.rules_unit_percent else R.string.rules_unit_rub,
            )

        RuleField.RAKE ->
            stringResource(
                if (rules.rules.rakeMode ==
                    RakeMode.PERCENT
                ) {
                    R.string.rules_unit_percent
                } else {
                    R.string.rules_unit_rub
                },
            )

        RuleField.MIN_BUY_IN -> stringResource(R.string.rules_edit_chips)

        RuleField.MIN_PLAY_MINUTES -> stringResource(R.string.rules_edit_minutes)
    }
    FeltbookSheet(onDismiss = { onAction(NewGameAction.EditorDismiss) }, contentSpacing = 12.dp) {
        SheetTitle(title = title)
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AmountText(
                text = MoneyFormat.number(editor.entry.value),
                style = type.amountHero,
                color = if (editor.entry.isEmpty) colors.muted else colors.ink,
                textAlign = TextAlign.Center,
            )
            Text(text = unit, style = type.caption, color = colors.muted)
        }
        AmountKeypad(entry = editor.entry, onEntryChange = { onAction(NewGameAction.EditorEntry(it)) })
        BrassButton(
            text = stringResource(R.string.rules_edit_done),
            onClick = { onAction(NewGameAction.EditorDone) },
            style = BrassButtonStyle.Hero64,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

internal val PreviewNewGameState =
    NewGameUiState(loading = false, bankerName = "Андрей")
        .withRules(GameRules.DEFAULT)

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NewGameScreenPreview() {
    FeltbookTheme { NewGameScreen(state = PreviewNewGameState, onAction = {}) }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun NewGameScreenEachFeePreview() {
    FeltbookTheme {
        NewGameScreen(
            state =
                PreviewNewGameState.withRules(
                    PreviewNewGameState.rules.copy(bankerFeeMode = BankerFeeMode.EACH_PERCENT, bankerFeeValue = 150),
                ),
            onAction = {},
        )
    }
}
