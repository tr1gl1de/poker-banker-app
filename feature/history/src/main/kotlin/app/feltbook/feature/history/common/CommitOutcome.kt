package app.feltbook.feature.history.common

import app.feltbook.core.domain.engine.DomainError
import app.feltbook.core.domain.engine.DomainRuleViolation
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import kotlin.coroutines.cancellation.CancellationException

/** Чем закончилась запись изменения (ADR 0006: ViewModel → движок → `repository.commit`). */
sealed interface CommitOutcome {
    data object Ok : CommitOutcome

    /** Команда движка отказала — тот же блокер, что показала котировка. */
    data class Rejected(val error: DomainError) : CommitOutcome

    /** Журнал ушёл вперёд (повторное нажатие по старому снимку) — ничего не записано. */
    data object Stale : CommitOutcome

    /** Вторая активная игра (переоткрытие, пока идёт другая). */
    data object SecondActiveGame : CommitOutcome

    data class Failed(val cause: Throwable) : CommitOutcome
}

/** Строит изменения по свежему снимку ([build] может бросить `DomainRuleViolation`) и пишет их одной транзакцией. */
@Suppress("TooGenericExceptionCaught") // Любой сбой базы — сообщение «Не получилось сохранить», не падение.
suspend fun GameRepository.tryCommit(build: suspend () -> List<GameChange>): CommitOutcome = try {
    val changes = build()
    if (changes.isNotEmpty()) commit(changes)
    CommitOutcome.Ok
} catch (e: CancellationException) {
    throw e
} catch (e: DomainRuleViolation) {
    CommitOutcome.Rejected(e.error)
} catch (e: CommitRejectedException) {
    when (e.reason) {
        CommitRejectedException.Reason.STALE_SEQ -> CommitOutcome.Stale
        CommitRejectedException.Reason.SECOND_ACTIVE_GAME -> CommitOutcome.SecondActiveGame
        else -> CommitOutcome.Failed(e)
    }
} catch (e: Exception) {
    CommitOutcome.Failed(e)
}
