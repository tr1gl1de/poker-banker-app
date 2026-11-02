package app.feltbook.core.data.deletion

import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.repository.GameRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Игры, удаление которых ещё можно отменить. [token] различает удаления подряд. */
data class PendingDeletion(val token: Long, val ids: Set<GameId>)

/**
 * Отложенное удаление игр (roadmap M4.3, m3-ui-spec §5.11): после подтверждения игры только скрываются,
 * физическое каскадное удаление — когда истекло окно «Отменить» (5 с или дольше по настройке
 * «Время на действие»). Отмена — забыть отложенное: в базе ничего не менялось, восстанавливать нечего.
 * Смерть процесса до истечения окна ничего не удаляет (игры снова видны после перезапуска).
 *
 * Живёт на уровне приложения (`@Singleton`, свой scope): уход с экрана не отменяет и не теряет удаление.
 * Новое удаление сразу выполняет предыдущее отложенное (его snackbar заменён новым — m3-ui-spec §4.13).
 * Резервная копия и «Удалить все игры» сначала вызывают [flush], чтобы скрытые игры не попали в файл.
 */
@Singleton
class GameDeletionScheduler(private val repository: GameRepository, private val scope: CoroutineScope) {
    @Inject
    constructor(repository: GameRepository) : this(repository, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val state = MutableStateFlow<PendingDeletion?>(null)
    private val failureEvents = MutableSharedFlow<Set<GameId>>(extraBufferCapacity = FAILURE_BUFFER)
    private val mutex = Mutex()
    private var timer: Job? = null
    private var nextToken = 1L

    /** Отложенное удаление (или `null`): эти игры экраны не показывают. */
    val pending: StateFlow<PendingDeletion?> = state.asStateFlow()

    /** Удаление не записалось (сбой базы): игры снова видны, экран показывает «Не получилось удалить». */
    val failures: SharedFlow<Set<GameId>> = failureEvents.asSharedFlow()

    /** Скрыть [ids] и удалить их по истечении [windowMillis], если не отменили. Возвращает токен для отмены. */
    suspend fun schedule(ids: Set<GameId>, windowMillis: Long = UNDO_WINDOW_MILLIS): Long {
        require(ids.isNotEmpty()) { "nothing to delete" }
        val (token, previous) = mutex.withLock {
            val old = state.value
            timer?.cancel()
            val token = nextToken++
            state.value = PendingDeletion(token, ids)
            timer = startTimer(token, windowMillis)
            token to old
        }
        previous?.let { deleteOrReport(it.ids) }
        return token
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

    /** Выполнить отложенное удаление, если оно есть (перед резервной копией, восстановлением, «Удалить все»). */
    suspend fun flush() {
        val current = mutex.withLock { state.value } ?: return
        commitNow(current.token)
    }

    private fun startTimer(token: Long, windowMillis: Long): Job = scope.launch {
        delay(windowMillis)
        val ids = mutex.withLock { state.value?.takeIf { it.token == token }?.ids } ?: return@launch
        delete(token, ids)
    }

    /** Удаление и только потом снятие скрытия: строки не мигают обратно, пока идёт транзакция. */
    private suspend fun delete(token: Long, ids: Set<GameId>) {
        deleteOrReport(ids)
        mutex.withLock { if (state.value?.token == token) state.value = null }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // Сбой базы — сообщение на экране, не падение.
    private suspend fun deleteOrReport(ids: Set<GameId>) {
        try {
            repository.deleteGames(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failureEvents.tryEmit(ids)
        }
    }

    companion object {
        /** Окно «Отменить» по умолчанию — как у snackbar (`UNDO_SNACKBAR_MILLIS` в `:core:ui`, ТЗ §5.1). */
        const val UNDO_WINDOW_MILLIS: Long = 5_000L
        private const val FAILURE_BUFFER = 8
    }
}
