package app.feltbook.core.transfer

import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** «Уже рекламируем/ищем/соединены» — не ошибка для машины состояний. */
private val ALREADY = setOf(
    ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING,
    ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING,
    ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT,
)

/**
 * Ждёт задачу ConnectionsClient (`Task<Void>`): успех — Unit, ошибка и **отмена** — [TransportException].
 * CancellationException отсюда не выходит: она остановила бы очередь сессии, и экран завис бы.
 */
internal suspend fun <T> Task<T>.awaitNearby(): Unit = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(Unit) }
    addOnFailureListener { error ->
        val code = (error as? ApiException)?.statusCode ?: CommonStatusCodes.ERROR
        if (code in ALREADY) {
            cont.resume(Unit)
        } else {
            cont.resumeWithException(TransportException(code, error.message, error))
        }
    }
    addOnCanceledListener {
        cont.resumeWithException(TransportException(CommonStatusCodes.CANCELED, "Nearby task canceled"))
    }
}
