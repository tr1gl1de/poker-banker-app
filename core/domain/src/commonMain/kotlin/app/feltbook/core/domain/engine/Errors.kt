package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.OperationId
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.PlayerStatus
import app.feltbook.core.domain.model.RulesProblem

/**
 * Почему действие невозможно. Котировки возвращают список блокеров (кнопка неактивна + подсказка),
 * команды при блокере бросают [DomainRuleViolation] — UI обязан сначала спросить котировку.
 */
sealed interface DomainError {
    data class InvalidRules(val problems: List<RulesProblem>) : DomainError
    data class WrongGameStatus(val actual: GameStatus, val allowed: Set<GameStatus>) : DomainError
    data class AnotherGameActive(val gameId: GameId) : DomainError
    data class PlayerNotFound(val playerId: PlayerId) : DomainError
    data class PlayerNotPlaying(val playerId: PlayerId, val status: PlayerStatus) : DomainError

    /** Игрок «за столом», но ещё не покупал фишки (банкир-игрок без закупа) — выходить ему не с чем. */
    data class PlayerNotSeated(val playerId: PlayerId) : DomainError
    data object NameBlank : DomainError
    data object NameTooLong : DomainError
    data object TitleTooLong : DomainError

    /** Имя уже за этим столом — вместо нового игрока предложить «вернуть» существующего. */
    data class NameTaken(val existing: PlayerId) : DomainError
    data object AmountNotPositive : DomainError
    data object AmountTooLarge : DomainError
    data class BelowMinBuyIn(val minBuyIn: Long) : DomainError
    data object SplitPaymentDisabled : DomainError
    data class PaymentExceedsDebt(val debt: Long) : DomainError
    data class PayoutExceedsOwed(val owed: Long) : DomainError
    data object NothingToSettle : DomainError
    data class PartialOutOfRange(val max: Long) : DomainError
    data object TipNotPositive : DomainError
    data class TipExceedsChips(val chips: Long) : DomainError
    data object TipToSelf : DomainError
    data class TipRecipientInvalid(val playerId: PlayerId) : DomainError

    /**
     * Получатель чаевых «за столом», но ещё не покупал фишки — фишками ему дарить нечего (только банкиру или вышедшим).
     */
    data class TipRecipientNotSeated(val playerId: PlayerId) : DomainError
    data class EarlyExitNotConfirmed(val minutesPlayed: Long, val minPlayMinutes: Int) : DomainError
    data class OperationNotFound(val operationId: OperationId) : DomainError
    data class OperationNotReversible(val reason: NotReversibleReason) : DomainError
    data class NoDebt(val playerId: PlayerId) : DomainError
    data class ForgiveExceedsDebt(val debt: Long) : DomainError
    data object NoRakeDue : DomainError
    data class PlayersStillPlaying(val playerIds: List<PlayerId>) : DomainError
    data class NotConfirmed(val missing: Set<CloseConfirmation>) : DomainError
    data class BankerNotAPlayer(val playerId: PlayerId) : DomainError

    /** Приём передачи: локальная копия игры расходится с входящей ([Imports.plan] дал `Conflict`). */
    data class ImportConflict(val reason: ImportConflictReason) : DomainError
}

enum class NotReversibleReason {
    ALREADY_REVERSED,
    IS_REVERSAL,
    INCOME_TAKEN,
    GAME_READ_ONLY,
    NOT_ALLOWED_AFTER_CLOSE,

    /** Первая покупка игрока, а после неё есть действующие докупы — «Сначала отмените более поздние докупы». */
    LATER_PURCHASES_EXIST,

    /** Выход с чаевыми фишками, а получатель с тех пор вышел — «Сначала отмените выход получателя чаевых». */
    TIP_RECIPIENT_CASHED_OUT,
}

/** Предупреждения не блокируют, показываются в шторке до подтверждения (ТЗ §5.1 «Подозрительная сумма»). */
sealed interface DomainWarning {
    /** «20 000 фишек — точно? Обычно здесь берут 2 000». `typical` = средняя покупка игры (null — покупок ещё нет). */
    data class SuspiciousBuyIn(val chips: Long, val typical: Long?) : DomainWarning

    /** «Фишек на выходе больше, чем есть в игре». */
    data class ChipsExceedInPlay(val chips: Long, val chipsInPlay: Long) : DomainWarning
}

class DomainRuleViolation(val error: DomainError) : IllegalStateException(error.toString())

/** Пороги ТЗ §5.1. Покупка подозрительна, если `chips > 5 × средняя` или `chips > 20 000`. */
object Suspicion {
    const val AVERAGE_MULTIPLIER: Long = 5
    const val ABSOLUTE_LIMIT: Long = 20_000
}

/** Бросить [DomainRuleViolation] по первому блокеру, если он есть. */
internal fun List<DomainError>.throwFirst() {
    firstOrNull()?.let { throw DomainRuleViolation(it) }
}

internal fun violation(error: DomainError): Nothing = throw DomainRuleViolation(error)
