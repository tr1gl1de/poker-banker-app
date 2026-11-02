package app.feltbook.feature.game.newgame

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.data.handover.ReceivedTemplateStore
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.Money
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.engine.Pricing
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.CommitOutcome
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.common.messageOrNull
import app.feltbook.feature.game.common.toUiText
import app.feltbook.feature.game.common.tryCommit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * «Новая игра» (m3-ui-spec §5.2): правила из `rulesForNewGame`, банкир — `lastBankerName`; «Начать игру» →
 * `Commands.startGame` → commit; изменённые правила и имя банкира сохраняются в настройки (QUESTIONS №4).
 */
@HiltViewModel
class NewGameViewModel @Inject constructor(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    private val templateStore: ReceivedTemplateStore,
) : ViewModel() {
    private val state = MutableStateFlow(NewGameUiState(defaultTitleAt = clock.now()))
    val uiState: StateFlow<NewGameUiState> = state.asStateFlow()

    /** Правила принятого шаблона, если «Новая игра» открыта с ним. */
    private var templateRules: GameRules? = null

    private val eventChannel = Channel<NewGameEvent>(Channel.BUFFERED)
    val events: Flow<NewGameEvent> = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val template = templateStore.template.value
            // Принятый шаблон (сценарий 2) задаёт правила этой игры; настройки по умолчанию он не меняет.
            val rules = template?.rules ?: settings.rulesForNewGame
            templateRules = template?.rules
            state.update {
                it.copy(
                    loading = false,
                    templateFrom = template?.fromBanker,
                    templateNames = template?.playerNames.orEmpty(),
                    bankerName = settings.lastBankerName,
                    bankerPlays = settings.bankerPlays,
                    rules = rules,
                    feeFixedValue = if (rules.bankerFeeMode.isPercent) DEFAULT_FEE_FIXED else rules.bankerFeeValue,
                    feePercentValue = if (rules.bankerFeeMode.isPercent) rules.bankerFeeValue else DEFAULT_FEE_PERCENT,
                ).withRules(rules)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod") // Плоский разбор действий экрана.
    fun onAction(action: NewGameAction) {
        when (action) {
            is NewGameAction.TitleChange ->
                state.update { it.copy(title = action.value.take(Game.MAX_TITLE_LENGTH)) }

            is NewGameAction.BankerChange ->
                state.update { it.copy(bankerName = action.value.take(PlayerNames.MAX_LENGTH), bankerError = null) }

            is NewGameAction.BankerPlaysChange -> state.update { it.copy(bankerPlays = action.value) }

            is NewGameAction.FeeSegmentClick -> onFeeSegment(action.segment)

            is NewGameAction.FeeEachPercent -> state.update { s ->
                val mode = if (action.percent) BankerFeeMode.EACH_PERCENT else BankerFeeMode.EACH_FIXED
                s.withRules(s.rules.copy(bankerFeeMode = mode, bankerFeeValue = s.feeValueFor(mode)))
            }

            is NewGameAction.RakePercent -> state.update { s ->
                val mode = if (action.percent) RakeMode.PERCENT else RakeMode.FIXED
                if (mode == s.rules.rakeMode) {
                    s
                } else {
                    val value = if (mode == RakeMode.PERCENT) DEFAULT_RAKE_PERCENT else DEFAULT_RAKE_FIXED
                    s.withRules(s.rules.copy(rakeMode = mode, rakeValue = value))
                }
            }

            is NewGameAction.EditRule -> state.update { s ->
                s.copy(editor = RuleEditorUi(action.field, AmountEntry.preset(s.valueOf(action.field))))
            }

            is NewGameAction.EditorEntry -> state.update { s ->
                s.copy(editor = s.editor?.copy(entry = action.entry))
            }

            NewGameAction.EditorDone -> state.update { s ->
                val editor = s.editor ?: return@update s
                s.applyEditor(editor).copy(editor = null)
            }

            NewGameAction.EditorDismiss -> state.update { it.copy(editor = null) }

            NewGameAction.ToggleSplitPayment -> state.update { s ->
                s.withRules(s.rules.copy(splitPayment = !s.rules.splitPayment))
            }

            is NewGameAction.Start -> start(action.defaultTitle)

            NewGameAction.DismissDialog -> state.update { it.copy(anotherGameTitle = null, anotherGameId = null) }

            NewGameAction.Back -> Unit
        }
    }

    private fun onFeeSegment(segment: FeeSegment) {
        state.update { s ->
            if (segment == s.feeSegment && segment != FeeSegment.EACH) {
                // Повторное нажатие на выбранный сегмент — шторка ввода значения (m3-ui-spec §5.2).
                return@update s.copy(
                    editor = RuleEditorUi(RuleField.BANKER_FEE, AmountEntry.preset(s.rules.bankerFeeValue)),
                )
            }
            if (segment == s.feeSegment) return@update s
            val mode = when (segment) {
                FeeSegment.ONCE_FIXED -> BankerFeeMode.ONCE_FIXED
                FeeSegment.ONCE_PERCENT -> BankerFeeMode.ONCE_PERCENT
                FeeSegment.EACH -> BankerFeeMode.EACH_FIXED
            }
            s.withRules(s.rules.copy(bankerFeeMode = mode, bankerFeeValue = s.feeValueFor(mode)))
        }
    }

    private fun start(defaultTitle: String) {
        val current = state.value
        if (!current.canStart) return
        val banker = PlayerNames.normalize(current.bankerName)
        if (banker.isEmpty()) {
            state.update { it.copy(bankerError = uiText(R.string.new_game_banker_required)) }
            return
        }
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val active = gameRepository.getActiveGame()
            if (active != null) {
                showAnotherGame(active.game)
                return@launch
            }
            var change: GameChange? = null
            val outcome = gameRepository.tryCommit {
                val input = NewGameInput(
                    title = current.title,
                    defaultTitle = defaultTitle,
                    bankerName = banker,
                    bankerPlays = current.bankerPlays,
                    rules = current.rules,
                )
                listOf(Commands.startGame(input, activeGame = null, stamp = Stamp.now(clock)).also { change = it })
            }
            when {
                outcome == CommitOutcome.Ok -> {
                    rememberChoices(current, banker)
                    templateStore.clear()
                    state.update { it.copy(busy = false) }
                    change?.let { eventChannel.send(NewGameEvent.Started(it.gameId.key())) }
                }

                outcome == CommitOutcome.SecondActiveGame ||
                    (outcome as? CommitOutcome.Rejected)?.error is DomainError.AnotherGameActive -> {
                    val game = gameRepository.getActiveGame()?.game
                    if (game != null) showAnotherGame(game) else state.update { it.copy(busy = false) }
                }

                outcome is CommitOutcome.Rejected && outcome.error.isNameError() ->
                    state.update { it.copy(busy = false, bankerError = outcome.error.toUiText()) }

                else -> state.update { it.copy(busy = false, error = outcome.messageOrNull()) }
            }
        }
    }

    /** Изменённые правила и имя банкира — в настройки (QUESTIONS №4); правила чужого шаблона без правок — нет. */
    private suspend fun rememberChoices(current: NewGameUiState, banker: String) {
        val fromTemplate = templateRules != null && current.rules == templateRules
        settingsRepository.update {
            it.copy(
                rulesForNewGame = if (fromTemplate) it.rulesForNewGame else current.rules,
                lastBankerName = banker,
                bankerPlays = current.bankerPlays,
            )
        }
    }

    private fun showAnotherGame(game: Game) {
        state.update { it.copy(busy = false, anotherGameTitle = game.title, anotherGameId = game.id.key()) }
    }

    fun openActiveGame() {
        val id = state.value.anotherGameId ?: return
        state.update { it.copy(anotherGameTitle = null, anotherGameId = null) }
        viewModelScope.launch { eventChannel.send(NewGameEvent.OpenActiveGame(id)) }
    }

    private companion object {
        const val DEFAULT_FEE_FIXED = 100L
        const val DEFAULT_FEE_PERCENT = 10L
        const val DEFAULT_RAKE_PERCENT = 10L
        const val DEFAULT_RAKE_FIXED = 100L
    }
}

private fun DomainError.isNameError(): Boolean = this == DomainError.NameBlank || this == DomainError.NameTooLong

/** Новые правила + пересчёт проблем и живого примера (закуп 1 000, первый, не банкир). */
internal fun NewGameUiState.withRules(rules: GameRules): NewGameUiState {
    val problems = rules.problems()
    val example = if (problems.isEmpty()) exampleFor(rules) else example
    val remembered = when (rules.bankerFeeMode) {
        BankerFeeMode.ONCE_FIXED, BankerFeeMode.EACH_FIXED -> copy(feeFixedValue = rules.bankerFeeValue)
        BankerFeeMode.ONCE_PERCENT, BankerFeeMode.EACH_PERCENT -> copy(feePercentValue = rules.bankerFeeValue)
    }
    return remembered.copy(rules = rules, problems = problems, example = example)
}

internal fun exampleFor(rules: GameRules): RulesExample {
    val chips = EXAMPLE_CHIPS
    return RulesExample(
        chips = chips,
        rake = Pricing.rake(chips, rules),
        fee = Pricing.bankerFee(chips, rules, isFirstBuyIn = true, isBanker = false),
    )
}

private const val EXAMPLE_CHIPS = 1_000L

private fun NewGameUiState.feeValueFor(mode: BankerFeeMode): Long =
    if (mode.isPercent) feePercentValue else feeFixedValue

private fun NewGameUiState.valueOf(field: RuleField): Long = when (field) {
    RuleField.BANKER_FEE -> rules.bankerFeeValue
    RuleField.RAKE -> rules.rakeValue
    RuleField.MIN_BUY_IN -> rules.minBuyIn
    RuleField.MIN_PLAY_MINUTES -> rules.minPlayMinutes.toLong()
}

private fun NewGameUiState.applyEditor(editor: RuleEditorUi): NewGameUiState {
    val value = editor.entry.value.coerceAtMost(Money.MAX_AMOUNT)
    val rules = when (editor.field) {
        RuleField.BANKER_FEE -> rules.copy(bankerFeeValue = value)
        RuleField.RAKE -> rules.copy(rakeValue = value)
        RuleField.MIN_BUY_IN -> rules.copy(minBuyIn = value)
        RuleField.MIN_PLAY_MINUTES -> rules.copy(minPlayMinutes = value.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
    return withRules(rules)
}
