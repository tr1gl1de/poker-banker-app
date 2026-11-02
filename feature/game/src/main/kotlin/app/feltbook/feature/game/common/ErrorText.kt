package app.feltbook.feature.game.common

import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.NotReversibleReason
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.game.R

/** Текст блокера котировки/команды (m3-ui-spec §11.16 и экранные строки). */
@Suppress("CyclomaticComplexMethod") // Плоское сопоставление «ошибка → строка».
fun DomainError.toUiText(): UiText = when (this) {
    is DomainError.InvalidRules -> problems.firstOrNull()?.toUiText() ?: uiText(UiR.string.error_generic)
    is DomainError.WrongGameStatus -> uiText(UiR.string.error_wrong_status)
    is DomainError.AnotherGameActive -> uiText(UiR.string.error_another_game_active)
    is DomainError.PlayerNotFound -> uiText(UiR.string.error_player_not_found)
    is DomainError.PlayerNotPlaying -> uiText(R.string.cash_out_not_playing)
    is DomainError.PlayerNotSeated -> uiText(R.string.cash_out_not_seated)
    DomainError.NameBlank -> uiText(R.string.new_player_name_blank)
    DomainError.NameTooLong -> uiText(R.string.new_player_name_too_long)
    DomainError.TitleTooLong -> uiText(R.string.new_game_title_too_long)
    is DomainError.NameTaken -> uiText(R.string.error_name_taken)
    DomainError.AmountNotPositive -> uiText(UiR.string.error_amount_not_positive)
    DomainError.AmountTooLarge -> uiText(UiR.string.error_amount_too_large)
    is DomainError.BelowMinBuyIn -> uiText(R.string.error_below_min_buy_in, MoneyFormat.number(minBuyIn))
    DomainError.SplitPaymentDisabled -> uiText(R.string.error_split_payment_disabled)
    is DomainError.PaymentExceedsDebt -> uiText(R.string.pay_exceeds_debt, MoneyFormat.rub(debt))
    is DomainError.PayoutExceedsOwed -> uiText(R.string.payout_exceeds_owed, MoneyFormat.rub(owed))
    DomainError.NothingToSettle -> uiText(UiR.string.error_nothing_to_settle)
    is DomainError.PartialOutOfRange -> uiText(UiR.string.error_partial_out_of_range, MoneyFormat.rub(max))
    DomainError.TipNotPositive -> uiText(R.string.cash_out_tip_not_positive)
    is DomainError.TipExceedsChips -> uiText(R.string.cash_out_tip_exceeds)
    DomainError.TipToSelf -> uiText(R.string.cash_out_tip_to_self)
    is DomainError.TipRecipientInvalid -> uiText(R.string.cash_out_tip_recipient_invalid)
    is DomainError.TipRecipientNotSeated -> uiText(R.string.cash_out_tip_recipient_not_seated)
    is DomainError.EarlyExitNotConfirmed -> uiText(R.string.cash_out_early_title)
    is DomainError.OperationNotFound -> uiText(UiR.string.error_generic)
    is DomainError.OperationNotReversible -> reason.toUiText()
    is DomainError.NoDebt -> uiText(R.string.error_no_debt)
    is DomainError.ForgiveExceedsDebt -> uiText(R.string.error_forgive_exceeds_debt, MoneyFormat.rub(debt))
    DomainError.NoRakeDue -> uiText(UiR.string.error_no_rake_due)
    is DomainError.PlayersStillPlaying -> uiText(R.string.table_settling_banner)
    is DomainError.NotConfirmed -> uiText(UiR.string.error_wrong_status)
    is DomainError.BankerNotAPlayer -> uiText(UiR.string.error_wrong_status)
    is DomainError.ImportConflict -> uiText(UiR.string.error_generic)
}

fun NotReversibleReason.toUiText(): UiText = uiText(
    when (this) {
        NotReversibleReason.ALREADY_REVERSED -> R.string.journal_not_reversible_already
        NotReversibleReason.IS_REVERSAL -> R.string.journal_not_reversible_reversal
        NotReversibleReason.INCOME_TAKEN -> R.string.journal_not_reversible_income
        NotReversibleReason.GAME_READ_ONLY -> R.string.journal_not_reversible_read_only
        NotReversibleReason.NOT_ALLOWED_AFTER_CLOSE -> R.string.journal_not_reversible_closed
        NotReversibleReason.LATER_PURCHASES_EXIST -> R.string.journal_not_reversible_later_purchases
        NotReversibleReason.TIP_RECIPIENT_CASHED_OUT -> R.string.journal_not_reversible_tip_recipient
    },
)

fun RulesProblem.toUiText(): UiText = uiText(
    when (this) {
        RulesProblem.BANKER_FEE_NEGATIVE -> R.string.rules_problem_banker_fee_negative
        RulesProblem.BANKER_FEE_PERCENT_ABOVE_MAX -> R.string.rules_problem_banker_fee_percent_above_max
        RulesProblem.BANKER_FEE_ABOVE_MAX_AMOUNT -> R.string.rules_problem_banker_fee_above_max_amount
        RulesProblem.RAKE_NEGATIVE -> R.string.rules_problem_rake_negative
        RulesProblem.RAKE_PERCENT_ABOVE_MAX -> R.string.rules_problem_rake_percent_above_max
        RulesProblem.RAKE_ABOVE_MAX_AMOUNT -> R.string.rules_problem_rake_above_max_amount
        RulesProblem.MIN_BUY_IN_NOT_POSITIVE -> R.string.rules_problem_min_buy_in_not_positive
        RulesProblem.MIN_BUY_IN_ABOVE_MAX_AMOUNT -> R.string.rules_problem_min_buy_in_above_max_amount
        RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE -> R.string.rules_problem_min_play_minutes_out_of_range
    },
)

/** Сообщение для исходов записи, кроме успешного. */
fun CommitOutcome.messageOrNull(): UiText? = when (this) {
    CommitOutcome.Ok -> null
    is CommitOutcome.Rejected -> error.toUiText()
    CommitOutcome.Stale -> null
    CommitOutcome.SecondActiveGame -> uiText(UiR.string.error_another_game_active)
    is CommitOutcome.Failed -> uiText(UiR.string.error_generic)
}
