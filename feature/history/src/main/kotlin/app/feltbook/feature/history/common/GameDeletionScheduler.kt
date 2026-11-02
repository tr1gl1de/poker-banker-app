package app.feltbook.feature.history.common

import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.ui.component.UNDO_SNACKBAR_MILLIS
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Игры, удаление которых ещё можно отменить. [token] различает удаления подряд. */
data class PendingDeletion(val token: Long, val ids: Set<GameId>)

/**
 * Отложенное удаление игр (roadmap M4.3, m3-ui-spec §5.11): после подтверждения игры только скрываются,
 * физическое каскадное удаление — когда истекло окно «Отменить» (5 с или дольше по настройке
 * «Время на действие»). Отмена — просто забыть отложенное: в базе ничего не менялось, восстанавливать нечего.
 * Смерть процесса до истечения окна ничего не удаляет (игры снова видны после перезапуска).
 *
 * Живёт на уровне приложения (`@Singleton`, свой scope), а не во ViewModel: уход с экрана истории
 * не отменяет удаление и не теряет его. Новое удаление сразу выполняет предыдущее отложенное (его snackbar
 * заменён новым — m3-ui-spec §4.13).
 */
@Singleton
class GameDeletionScheduler internal constructor(
    private val repository: GameRepository,
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(repository: GameRepository) : this(repository, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val state = MutableStateFlow<PendingDeletion?>(null)
    private val mutex = Mutex()
    private var timer: Job? = null
    private var nextToken = 1L

    /** Отложенное удаление (или `null`): эти игры экраны не показывают. */
    val pending: StateFlow<PendingDeletion?> = state.asStateFlow()

    /** Скрыть [ids] и удалить их по истечении [windowMillis], если не отменили. Возвращает токен для отмены. */
    suspend fun schedule(ids: Set<GameId>, windowMillis: Long = UNDO_SNACKBAR_MILLIS): Long {
        require(ids.isNotEmpty()) { "nothing to delete" }
        val previous = mutex.withLock {
            val old = state.value
            timer?.cancel()
            val token = nextToken++
            state.value = PendingDeletion(token, ids)
            timer = startTimer(token, windowMillis)
            old
        }
        previous?.let { runCatching { repository.deleteGames(it.ids) } }
        return checkNotNull(state.value).token
    }

    /**
     * Продлить окно отмены до [windowMillis] от текущего момента — snackbar живёт дольше 5 с, когда TalkBack
     * просит больше «времени на действие».
     */
    suspend fun extend(token: Long, windowMillis: Long) {
        mutex.withLock {
            if (state.value?.token != token) return
            timer?.cancel()
            timer = startTimer(token, windowMillis)
        }
    }

    /** «Отменить»: игры снова видны, в базе ничего не удалялось. `false` — окно уже закрылось. */
    suspend fun undo(token: Long): Boolean = mutex.withLock {
        if (state.value?.token != token) return@withLock false
        timer?.cancel()
        timer = null
        state.value = null
        true
    }

    /** Snackbar закрылся без «Отменить» — удалить сейчас, не дожидаясь таймера. */
    suspend fun commitNow(token: Long) {
        val ids = mutex.withLock {
            val current = state.value?.takeIf { it.token == token } ?: return
            timer?.cancel()
            timer = null
            current.ids
        }
        delete(token, ids)
    }

    private fun startTimer(token: Long, windowMillis: Long): Job = scope.launch {
        delay(windowMillis)
        val ids = mutex.withLock { state.value?.takeIf { it.token == token }?.ids } ?: return@launch
        delete(token, ids)
    }

    /** Удаление и только потом снятие скрытия: строки не мигают обратно, пока идёт транзакция. */
    private suspend fun delete(token: Long, ids: Set<GameId>) {
        runCatching { repository.deleteGames(ids) }
        mutex.withLock { if (state.value?.token == token) state.value = null }
    }
}
