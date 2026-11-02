package app.feltbook.feature.game.newgame

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.ui.component.AmountEntry
import app.feltbook.core.ui.format.UiText
import kotlin.time.Instant

/** Какое число правил редактируется в шторке с клавиатурой. */
enum class RuleField { BANKER_FEE, RAKE, MIN_BUY_IN, MIN_PLAY_MINUTES }

/** Сегменты «♥ Сбор банкира»: 3 на макете ↔ 4 режима домена (m3-ui-spec §5.2). */
enum class FeeSegment { ONCE_FIXED, ONCE_PERCENT, EACH }

@Immutable
data class RuleEditorUi(val field: RuleField, val entry: AmountEntry)

/** Пример «закуп 1 000 → к оплате 1 200 ₽ (1 000 фишки + 100 рейк + 100 сбор)». */
@Immutable
data class RulesExample(val chips: Long, val rake: Long, val fee: Long) {
    val total: Long get() = chips + rake + fee
}

@Immutable
data class NewGameUiState(
    val loading: Boolean = true,
    val title: String = "",
    /** Время для плейсхолдера названия «2 ноя, 20:05» (форматирует экран). */
    val defaultTitleAt: Instant = Instant.fromEpochMilliseconds(0),
    val bankerName: String = "",
    val bankerError: UiText? = null,
    val bankerPlays: Boolean = true,
    val rules: GameRules = GameRules.DEFAULT,
    /** Запомненные значения режимов, чтобы подписи «Разово 100 ₽ / Разово 10 %» не прыгали. */
    val feeFixedValue: Long = 100,
    val feePercentValue: Long = 10,
    val problems: List<RulesProblem> = emptyList(),
    val example: RulesExample = RulesExample(chips = 1_000, rake = 100, fee = 100),
    val editor: RuleEditorUi? = null,
    val busy: Boolean = false,
    /** «Уже идёт игра»: название активной игры. */
    val anotherGameTitle: String? = null,
    val anotherGameId: String? = null,
    val error: UiText? = null,
) {
    val feeSegment: FeeSegment
        get() = when {
            !rules.bankerFeeMode.isOnce -> FeeSegment.EACH
            rules.bankerFeeMode.isPercent -> FeeSegment.ONCE_PERCENT
            else -> FeeSegment.ONCE_FIXED
        }

    val canStart: Boolean get() = !loading && !busy && problems.isEmpty()
}

sealed interface NewGameAction {
    data class TitleChange(val value: String) : NewGameAction

    data class BankerChange(val value: String) : NewGameAction

    data class BankerPlaysChange(val value: Boolean) : NewGameAction

    data class FeeSegmentClick(val segment: FeeSegment) : NewGameAction

    /** Второй ряд «С каждого»: процент (true) или фикс. */
    data class FeeEachPercent(val percent: Boolean) : NewGameAction

    data class RakePercent(val percent: Boolean) : NewGameAction

    data class EditRule(val field: RuleField) : NewGameAction

    data class EditorEntry(val entry: AmountEntry) : NewGameAction

    data object EditorDone : NewGameAction

    data object EditorDismiss : NewGameAction

    data object ToggleSplitPayment : NewGameAction

    /** [defaultTitle] — «2 ноя, 20:05», отформатированный экраном. */
    data class Start(val defaultTitle: String) : NewGameAction

    data object DismissDialog : NewGameAction

    data object Back : NewGameAction
}

sealed interface NewGameEvent {
    data class Started(val gameId: String) : NewGameEvent

    data class OpenActiveGame(val gameId: String) : NewGameEvent
}
