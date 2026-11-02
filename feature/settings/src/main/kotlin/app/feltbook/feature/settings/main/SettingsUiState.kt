package app.feltbook.feature.settings.main

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.domain.model.ThemeMode
import app.feltbook.core.domain.repository.ImportFailure
import app.feltbook.core.domain.repository.ImportReport
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.component.DEFAULT_MAX_AMOUNT
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.settings.R

/** Какое правило по умолчанию открыто в шторке (m3-ui-spec §5.13). */
enum class RuleField { BANKER_FEE, RAKE, MIN_BUY_IN, MIN_PLAY_MINUTES, PAYMENT }

/**
 * Черновик правила в шторке. Значение — [AmountEntry] с клавиатуры (системная не открывается); сохраняется
 * только без проблем `GameRules.problems()`.
 */
@Immutable
data class RulesEditorUi(
    val field: RuleField,
    val feeMode: BankerFeeMode,
    val rakeMode: RakeMode,
    val entry: AmountEntry,
    val split: Boolean,
    /** Правила, к которым применяется черновик (значения остальных полей). */
    val base: GameRules,
) {
    /** Правила после сохранения черновика. */
    val draft: GameRules
        get() = when (this.field) {
            RuleField.BANKER_FEE -> base.copy(bankerFeeMode = feeMode, bankerFeeValue = entry.value)
            RuleField.RAKE -> base.copy(rakeMode = rakeMode, rakeValue = entry.value)
            RuleField.MIN_BUY_IN -> base.copy(minBuyIn = entry.value)
            RuleField.MIN_PLAY_MINUTES -> base.copy(minPlayMinutes = entry.value.toInt())
            RuleField.PAYMENT -> base.copy(splitPayment = split)
        }

    /** Проблемы именно этого поля (остальные поля сохранённых правил уже допустимы). */
    val problems: List<RulesProblem>
        get() = draft.problems().filter { it in this.field.problems }

    val canSave: Boolean get() = problems.isEmpty() && draft != base

    /** Предел клавиатуры: проценты — до 100, минуты — до суток, суммы — `MAX_AMOUNT`. */
    val maxValue: Long
        get() = when {
            this.field == RuleField.BANKER_FEE && feeMode.isPercent -> GameRules.MAX_PERCENT
            this.field == RuleField.RAKE && rakeMode == RakeMode.PERCENT -> GameRules.MAX_PERCENT
            this.field == RuleField.MIN_PLAY_MINUTES -> GameRules.MAX_MIN_PLAY_MINUTES.toLong()
            else -> DEFAULT_MAX_AMOUNT
        }

    val usesKeypad: Boolean get() = this.field != RuleField.PAYMENT

    companion object {
        fun open(field: RuleField, rules: GameRules): RulesEditorUi = RulesEditorUi(
            field = field,
            feeMode = rules.bankerFeeMode,
            rakeMode = rules.rakeMode,
            entry = AmountEntry.preset(
                when (field) {
                    RuleField.BANKER_FEE -> rules.bankerFeeValue
                    RuleField.RAKE -> rules.rakeValue
                    RuleField.MIN_BUY_IN -> rules.minBuyIn
                    RuleField.MIN_PLAY_MINUTES -> rules.minPlayMinutes.toLong()
                    RuleField.PAYMENT -> 0L
                },
            ),
            split = rules.splitPayment,
            base = rules,
        )
    }
}

private val RuleField.problems: Set<RulesProblem>
    get() = when (this) {
        RuleField.BANKER_FEE -> setOf(
            RulesProblem.BANKER_FEE_NEGATIVE,
            RulesProblem.BANKER_FEE_PERCENT_ABOVE_MAX,
            RulesProblem.BANKER_FEE_ABOVE_MAX_AMOUNT,
        )

        RuleField.RAKE -> setOf(
            RulesProblem.RAKE_NEGATIVE,
            RulesProblem.RAKE_PERCENT_ABOVE_MAX,
            RulesProblem.RAKE_ABOVE_MAX_AMOUNT,
        )

        RuleField.MIN_BUY_IN -> setOf(
            RulesProblem.MIN_BUY_IN_NOT_POSITIVE,
            RulesProblem.MIN_BUY_IN_ABOVE_MAX_AMOUNT,
        )

        RuleField.MIN_PLAY_MINUTES -> setOf(RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE)

        RuleField.PAYMENT -> emptySet()
    }

/** Итог «Восстановить из файла»: отчёт `ImportReport` или почему файл не принят. */
sealed interface ImportResultUi {
    data class Done(
        val added: Int,
        val extended: Int,
        val identical: Int,
        /** «пропущено, на телефоне новее» (`LOCAL_NEWER`) — не ошибка. */
        val localNewer: Int,
        /** Остальные конфликты — «не тронуто из-за расхождений». */
        val conflicts: Int,
        val namesAdded: Int,
    ) : ImportResultUi

    data class Failed(val message: UiText) : ImportResultUi
}

/** Диалог «Удалить все игры?»: подтверждение — ввод ровно «УДАЛИТЬ». */
@Immutable
data class DeleteAllDialogUi(val typed: String = "")

@Immutable
data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val editor: RulesEditorUi? = null,
    val themeDialog: Boolean = false,
    val deleteAllDialog: DeleteAllDialogUi? = null,
    val importResult: ImportResultUi? = null,
    /** Идёт экспорт, импорт или удаление — кнопки данных неактивны. */
    val busy: Boolean = false,
) {
    val rules: GameRules get() = settings.rulesForNewGame
}

sealed interface SettingsAction {
    data class EditRule(val field: RuleField) : SettingsAction

    data class EditorFeeMode(val mode: BankerFeeMode) : SettingsAction

    data class EditorRakeMode(val mode: RakeMode) : SettingsAction

    data class EditorEntry(val entry: AmountEntry) : SettingsAction

    data class EditorSplit(val split: Boolean) : SettingsAction

    data object SaveRules : SettingsAction

    data object DismissEditor : SettingsAction

    data object ThemeClick : SettingsAction

    data class SetTheme(val mode: ThemeMode) : SettingsAction

    data object DismissTheme : SettingsAction

    data class SetDynamicColor(val enabled: Boolean) : SettingsAction

    data class SetKeepScreenOn(val enabled: Boolean) : SettingsAction

    data class SetHaptics(val enabled: Boolean) : SettingsAction

    /** «Резервная копия в файл» — SAF `CreateDocument`. */
    data object Export : SettingsAction

    /** Документ выбран ([uri] = `null` — пользователь отменил выбор). */
    data class ExportTo(val uri: String?) : SettingsAction

    /** «Восстановить из файла» — SAF `OpenDocument`. */
    data object Import : SettingsAction

    data class ImportFrom(val uri: String?) : SettingsAction

    data object DismissImportResult : SettingsAction

    data object DeleteAll : SettingsAction

    data class DeleteAllInput(val text: String) : SettingsAction

    data object ConfirmDeleteAll : SettingsAction

    data object DismissDeleteAll : SettingsAction

    data object OpenOdds : SettingsAction

    /** «Скрыть пункт» калькулятора шансов (snackbar «Пункт скрыт» / «Отменить»). */
    data object HideOdds : SettingsAction

    data class Undo(val token: SettingsUndo) : SettingsAction
}

/** Что вернуть по «Отменить». */
sealed interface SettingsUndo {
    data class Rules(val previous: GameRules) : SettingsUndo

    data object OddsHidden : SettingsUndo
}

sealed interface SettingsEvent {
    /** Запустить SAF `CreateDocument` с именем [fileName]. */
    data class CreateBackupDocument(val fileName: String) : SettingsEvent

    data object OpenBackupDocument : SettingsEvent

    data object OpenOdds : SettingsEvent

    data class Undoable(val message: UiText, val token: SettingsUndo) : SettingsEvent

    data class Message(val text: UiText) : SettingsEvent
}

/** Слово-подтверждение «Удалить все игры» (сравнение — точное, без учёта пробелов по краям). */
const val DELETE_ALL_WORD: String = "УДАЛИТЬ"

fun DeleteAllDialogUi.confirmed(): Boolean = typed.trim() == DELETE_ALL_WORD

fun ImportReport.toUi(): ImportResultUi = when (val problem = failure) {
    null -> ImportResultUi.Done(
        added = added,
        extended = extended,
        identical = identical,
        localNewer = conflicts.count { it.second == ImportConflictReason.LOCAL_NEWER },
        conflicts = conflicts.count { it.second != ImportConflictReason.LOCAL_NEWER },
        namesAdded = playerNamesAdded,
    )

    is ImportFailure.Unreadable -> ImportResultUi.Failed(problem.reason.toUiText())

    is ImportFailure.StorageFailed -> ImportResultUi.Failed(uiText(R.string.settings_restore_storage_failed))
}

private fun DecodeResult.Failure.toUiText(): UiText = when (this) {
    DecodeResult.NotFeltbook -> uiText(R.string.settings_restore_not_feltbook)
    is DecodeResult.UnsupportedVersion -> uiText(R.string.settings_restore_newer_version)
    DecodeResult.ChecksumMismatch -> uiText(R.string.settings_restore_damaged)
    is DecodeResult.Malformed -> uiText(R.string.settings_restore_damaged)
    is DecodeResult.InvalidLedger -> uiText(R.string.settings_restore_invalid_ledger)
    is DecodeResult.WrongKind -> uiText(R.string.settings_restore_wrong_kind)
}
