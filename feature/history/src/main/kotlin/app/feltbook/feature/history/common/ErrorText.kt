package app.feltbook.feature.history.common

import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.NotReversibleReason
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.core.ui.format.uiText
import app.feltbook.feature.history.R

/** Текст блокера команды для экранов истории, долгов и карточки игры (m3-ui-spec §11.16 и строки модуля). */
fun DomainError.toUiText(): UiText = when (this) {
    is DomainError.AnotherGameActive -> uiText(UiR.string.error_another_game_active)
    is DomainError.WrongGameStatus -> uiText(UiR.string.error_wrong_status)
    is DomainError.PlayerNotFound -> uiText(UiR.string.error_player_not_found)
    DomainError.AmountNotPositive -> uiText(UiR.string.error_amount_not_positive)
    DomainError.AmountTooLarge -> uiText(UiR.string.error_amount_too_large)
    is DomainError.PaymentExceedsDebt -> uiText(R.string.debts_pay_exceeds_debt, MoneyFormat.rub(debt))
    is DomainError.NoDebt -> uiText(R.string.debts_no_debt)
    is DomainError.ForgiveExceedsDebt -> uiText(R.string.debts_pay_exceeds_debt, MoneyFormat.rub(debt))
    is DomainError.OperationNotReversible -> reason.toUiText()
    else -> uiText(UiR.string.error_generic)
}

fun NotReversibleReason.toUiText(): UiText = uiText(
    when (this) {
        NotReversibleReason.ALREADY_REVERSED -> R.string.detail_journal_not_reversible_already
        NotReversibleReason.IS_REVERSAL -> R.string.detail_journal_not_reversible_reversal
        NotReversibleReason.INCOME_TAKEN -> R.string.detail_journal_not_reversible_income
        NotReversibleReason.GAME_READ_ONLY -> R.string.detail_journal_not_reversible_read_only
        NotReversibleReason.NOT_ALLOWED_AFTER_CLOSE -> R.string.detail_journal_not_reversible_closed
        NotReversibleReason.LATER_PURCHASES_EXIST -> R.string.detail_journal_not_reversible_later_purchases
        NotReversibleReason.TIP_RECIPIENT_CASHED_OUT -> R.string.detail_journal_not_reversible_tip_recipient
    },
)

/** Сообщение для исходов записи, кроме успешного; `Stale` — тоже сообщение (экран уже перечитал снимок). */
fun CommitOutcome.messageOrNull(): UiText? = when (this) {
    CommitOutcome.Ok -> null
    is CommitOutcome.Rejected -> error.toUiText()
    CommitOutcome.Stale -> uiText(UiR.string.error_generic)
    CommitOutcome.SecondActiveGame -> uiText(UiR.string.error_another_game_active)
    is CommitOutcome.Failed -> uiText(UiR.string.error_generic)
}
