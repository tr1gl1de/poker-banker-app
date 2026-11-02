package app.feltbook.feature.settings.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.repository.BackupRepository
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.settings.R
import app.feltbook.feature.settings.backup.BackupFiles
import app.feltbook.feature.settings.common.SettingsZone
import app.feltbook.feature.settings.common.uiPlural
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private data class SettingsInputs(
    val editor: RulesEditorUi? = null,
    val themeDialog: Boolean = false,
    val deleteAllDialog: DeleteAllDialogUi? = null,
    val importResult: ImportResultUi? = null,
    val busy: Boolean = false,
)

/**
 * Настройки (m3-ui-spec §5.13): правила для новой игры (`AppSettings.rulesForNewGame` — меняет только будущие игры,
 * правило 3 CLAUDE.md), оформление, резервная копия (`BackupRepository` + SAF), «Удалить все игры…», пункт
 * калькулятора шансов после пасхалки.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gameRepository: GameRepository,
    private val backup: BackupRepository,
    private val files: BackupFiles,
    private val deletions: GameDeletionScheduler,
    private val clock: Clock,
    private val zone: SettingsZone,
) : ViewModel() {
    private val inputs = MutableStateFlow(SettingsInputs())

    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    val uiState: StateFlow<SettingsUiState> =
        combine(settingsRepository.settings, inputs) { settings, input ->
            SettingsUiState(
                loading = false,
                settings = settings,
                editor = input.editor,
                themeDialog = input.themeDialog,
                deleteAllDialog = input.deleteAllDialog,
                importResult = input.importResult,
                busy = input.busy,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SettingsUiState())

    @Suppress("CyclomaticComplexMethod") // Плоский разбор действий экрана.
    fun onAction(action: SettingsAction) {
        when (action) {
            is SettingsAction.EditRule -> openEditor(action.field)

            is SettingsAction.EditorFeeMode -> updateEditor { it.withFeeMode(action.mode) }

            is SettingsAction.EditorRakeMode -> updateEditor { it.withRakeMode(action.mode) }

            is SettingsAction.EditorEntry -> updateEditor { it.copy(entry = action.entry) }

            is SettingsAction.EditorSplit -> updateEditor { it.copy(split = action.split) }

            SettingsAction.SaveRules -> saveRules()

            SettingsAction.DismissEditor -> inputs.update { it.copy(editor = null) }

            SettingsAction.ThemeClick -> inputs.update { it.copy(themeDialog = true) }

            is SettingsAction.SetTheme -> {
                inputs.update { it.copy(themeDialog = false) }
                updateSettings { it.copy(themeMode = action.mode) }
            }

            SettingsAction.DismissTheme -> inputs.update { it.copy(themeDialog = false) }

            is SettingsAction.SetDynamicColor -> updateSettings { it.copy(dynamicColor = action.enabled) }

            is SettingsAction.SetKeepScreenOn -> updateSettings { it.copy(keepScreenOn = action.enabled) }

            is SettingsAction.SetHaptics -> updateSettings { it.copy(haptics = action.enabled) }

            SettingsAction.Export -> eventChannel.trySend(SettingsEvent.CreateBackupDocument(backupFileName()))

            is SettingsAction.ExportTo -> action.uri?.let { export(it) }

            SettingsAction.Import -> eventChannel.trySend(SettingsEvent.OpenBackupDocument)

            is SettingsAction.ImportFrom -> action.uri?.let { import(it) }

            SettingsAction.DismissImportResult -> inputs.update { it.copy(importResult = null) }

            SettingsAction.DeleteAll -> openDeleteAll()

            is SettingsAction.DeleteAllInput -> inputs.update { input ->
                input.copy(deleteAllDialog = input.deleteAllDialog?.copy(typed = action.text.take(MAX_WORD_INPUT)))
            }

            SettingsAction.ConfirmDeleteAll -> deleteAll()

            SettingsAction.DismissDeleteAll -> inputs.update { it.copy(deleteAllDialog = null) }

            SettingsAction.OpenOdds -> eventChannel.trySend(SettingsEvent.OpenOdds)

            SettingsAction.HideOdds -> hideOdds()

            is SettingsAction.Undo -> undo(action.token)
        }
    }

    private fun openEditor(field: RuleField) {
        val rules = uiState.value.rules
        inputs.update { it.copy(editor = RulesEditorUi.open(field, rules)) }
    }

    private fun updateEditor(transform: (RulesEditorUi) -> RulesEditorUi) {
        inputs.update { input -> input.copy(editor = input.editor?.let(transform)) }
    }

    private fun saveRules() {
        val editor = inputs.value.editor ?: return
        if (!editor.canSave) return
        val draft = editor.draft
        inputs.update { it.copy(editor = null) }
        viewModelScope.launch {
            var previous: GameRules? = null
            val ok = safely {
                settingsRepository.update { current ->
                    previous = current.rulesForNewGame
                    current.copy(rulesForNewGame = draft)
                }
            }
            val old = previous
            if (ok && old != null) {
                val message = uiText(R.string.settings_rules_saved)
                eventChannel.send(SettingsEvent.Undoable(message, SettingsUndo.Rules(old)))
            }
        }
    }

    private fun hideOdds() {
        viewModelScope.launch {
            if (safely { settingsRepository.update { it.copy(oddsCalculatorVisible = false) } }) {
                val message = uiText(R.string.settings_odds_hidden)
                eventChannel.send(SettingsEvent.Undoable(message, SettingsUndo.OddsHidden))
            }
        }
    }

    private fun undo(token: SettingsUndo) {
        when (token) {
            is SettingsUndo.Rules -> updateSettings { it.copy(rulesForNewGame = token.previous) }
            SettingsUndo.OddsHidden -> updateSettings { it.copy(oddsCalculatorVisible = true) }
        }
    }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { safely { settingsRepository.update(transform) } }
    }

    /** `feltbook-2026-11-02.feltbook`. */
    private fun backupFileName(): String {
        val date = TimeFormat.localDate(clock.now(), zone.zone())
        return "$FILE_PREFIX$date.$FILE_EXTENSION"
    }

    private fun export(uri: String) {
        if (inputs.value.busy) return
        inputs.update { it.copy(busy = true) }
        viewModelScope.launch {
            // Отложенно удалённые игры не должны попасть в копию — удаляем их сейчас.
            deletions.flush()
            val ok = safely(failure = uiText(R.string.settings_backup_failed)) {
                val text = backup.exportAll()
                files.write(uri, text)
            }
            if (ok) {
                val count = gameRepository.observeAllGames().first().size
                eventChannel.send(
                    SettingsEvent.Message(
                        uiText(R.string.settings_backup_done, uiPlural(UiR.plurals.games_count, count, count)),
                    ),
                )
            }
            inputs.update { it.copy(busy = false) }
        }
    }

    private fun import(uri: String) {
        if (inputs.value.busy) return
        inputs.update { it.copy(busy = true) }
        viewModelScope.launch {
            deletions.flush()
            var text: String? = null
            safely(failure = uiText(R.string.settings_restore_read_failed)) { text = files.read(uri) }
            val content = text
            // importAll не бросает: нечитаемый файл и сбой базы — в отчёте; локальное не затирается.
            val result = content?.let { backup.importAll(it).toUi() }
            inputs.update { it.copy(busy = false, importResult = result ?: it.importResult) }
        }
    }

    /** Диалог называет, что пропадёт кроме истории: идущую игру и открытые долги (последствия удаления). */
    private fun openDeleteAll() {
        viewModelScope.launch {
            val games = gameRepository.observeAllGames().first()
            val active = games.firstOrNull { it.game.status.isActive }
            inputs.update {
                it.copy(
                    deleteAllDialog = DeleteAllDialogUi(
                        activeGameTitle = active?.game?.title,
                        gameCount = games.size,
                        openDebtTotal = Debts.open(games, emptyList()).sumOf { debt -> debt.amount },
                    ),
                )
            }
        }
    }

    private fun deleteAll() {
        val dialog = inputs.value.deleteAllDialog ?: return
        if (!dialog.confirmed() || inputs.value.busy) return
        inputs.update { it.copy(deleteAllDialog = null, busy = true) }
        viewModelScope.launch {
            if (safely {
                    deletions.flush()
                    gameRepository.deleteAllGames()
                }
            ) {
                eventChannel.send(SettingsEvent.Message(uiText(R.string.settings_delete_all_done)))
            }
            inputs.update { it.copy(busy = false) }
        }
    }

    /** Сбой хранилища или файла — сообщение [failure], не падение. */
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private suspend fun safely(failure: UiText = uiText(UiR.string.error_generic), block: suspend () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            eventChannel.send(SettingsEvent.Message(failure))
            false
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_WORD_INPUT = 32
        const val FILE_PREFIX = "feltbook-"
        const val FILE_EXTENSION = "feltbook"
    }
}

/** Смена режима сбора: процент ↔ рубли меняют смысл числа — подставляется значение по умолчанию. */
internal fun RulesEditorUi.withFeeMode(mode: BankerFeeMode): RulesEditorUi {
    if (mode == feeMode) return this
    val entry = when {
        mode.isPercent == feeMode.isPercent -> entry
        mode.isPercent -> AmountEntry.preset(DEFAULT_PERCENT)
        else -> AmountEntry.preset(GameRules.DEFAULT.bankerFeeValue)
    }
    return copy(feeMode = mode, entry = entry)
}

internal fun RulesEditorUi.withRakeMode(mode: RakeMode): RulesEditorUi {
    if (mode == rakeMode) return this
    val entry = if (mode == RakeMode.PERCENT) {
        AmountEntry.preset(GameRules.DEFAULT.rakeValue)
    } else {
        AmountEntry.preset(DEFAULT_RAKE_FIXED)
    }
    return copy(rakeMode = mode, entry = entry)
}

/** Подстановка при переключении на процент сбора / фикс рейка (ТЗ §3.1: «10 %», «100 ₽»). */
private const val DEFAULT_PERCENT = 10L
private const val DEFAULT_RAKE_FIXED = 100L
