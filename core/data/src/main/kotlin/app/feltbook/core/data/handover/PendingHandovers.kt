package app.feltbook.core.data.handover

import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.commit
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart

/** Итог «Да, принял» по неподтверждённой передаче. */
enum class ConfirmOutcome {
    /** Передача записана: игра «Передана». */
    COMMITTED,

    /** Записать нельзя (журнал ушёл вперёд, пакет нечитаем) — запись снята, игра остаётся у отправителя. */
    STALE,

    /** Неподтверждённой передачи нет. */
    NONE,
}

/** Неподтверждённая передача на столе: наблюдение, «Да, принял» (та же передача), «Нет, игра у меня». */
class PendingHandovers @Inject constructor(
    private val store: PendingHandoverStore,
    private val gameRepository: GameRepository,
) {
    /** Передача по игре [gameId], пока игра активна. */
    fun observe(gameId: GameId): Flow<PendingHandover?> =
        combine(store.pending.onStart { store.load() }, gameRepository.observeGame(gameId)) { list, game ->
            val pending = list?.firstOrNull { it.gameId == gameId.value.toString() }
            pending.takeIf { game != null && game.game.status.isActive }
        }

    suspend fun confirm(gameId: GameId): ConfirmOutcome {
        val pending = store.get(gameId) ?: return ConfirmOutcome.NONE
        val change = gameRepository.getGame(gameId)?.let(pending::senderChange)
        val outcome = when {
            change == null -> ConfirmOutcome.STALE
            commitOrFalse(change) -> ConfirmOutcome.COMMITTED
            else -> ConfirmOutcome.STALE
        }
        // Записана или записать уже нельзя — в обоих случаях вопрос снят.
        store.remove(gameId)
        return outcome
    }

    private suspend fun commitOrFalse(change: GameChange): Boolean = try {
        gameRepository.commit(change)
        true
    } catch (_: CommitRejectedException) {
        false
    }

    suspend fun discard(gameId: GameId) = store.remove(gameId)
}
