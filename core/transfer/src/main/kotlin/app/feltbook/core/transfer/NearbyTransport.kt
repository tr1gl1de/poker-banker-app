package app.feltbook.core.transfer

import java.io.InputStream
import kotlinx.coroutines.flow.Flow

/**
 * Тонкая обёртка над `ConnectionsClient` (стратегия `P2P_POINT_TO_POINT`). Вся логика — в [DefaultTransferSession],
 * поэтому машину состояний тестирует фейковый транспорт. Ошибки вызовов — [TransportException].
 */
@Suppress("TooManyFunctions") // Повторяет API ConnectionsClient один к одному.
interface NearbyTransport {
    /** События Nearby. Один подписчик (сессия); события до подписки не теряются. */
    val events: Flow<TransportEvent>

    suspend fun startAdvertising(localName: String)

    suspend fun startDiscovery()

    fun stopAdvertising()

    fun stopDiscovery()

    suspend fun requestConnection(localName: String, endpointId: String)

    suspend fun acceptConnection(endpointId: String)

    suspend fun rejectConnection(endpointId: String)

    /** Короткое сообщение (< 32 КБ). Возвращает id payload — по нему приходит [TransportEvent.PayloadProgress]. */
    suspend fun sendBytes(endpointId: String, bytes: ByteArray): Long

    /** Поток любого размера (`Payload.fromStream`). Возвращает id payload. */
    suspend fun sendStream(endpointId: String, input: InputStream): Long

    fun disconnect(endpointId: String)

    /** Остановить рекламу, поиск и все соединения. */
    fun stopAll()
}

sealed interface TransportEvent {
    data class EndpointFound(val endpointId: String, val name: String) : TransportEvent

    data class EndpointLost(val endpointId: String) : TransportEvent

    /** `onConnectionInitiated`: [digits] — `ConnectionInfo.authenticationDigits`. */
    data class ConnectionInitiated(
        val endpointId: String,
        val name: String,
        val digits: String,
        val incoming: Boolean,
    ) : TransportEvent

    /** `onConnectionResult`: [success] — оба приняли; [rejected] — второй телефон отклонил. */
    data class ConnectionResult(
        val endpointId: String,
        val success: Boolean,
        val rejected: Boolean,
        val statusCode: Int,
    ) : TransportEvent

    data class Disconnected(val endpointId: String) : TransportEvent

    data class BytesReceived(val endpointId: String, val bytes: ByteArray) : TransportEvent {
        override fun equals(other: Any?): Boolean =
            other is BytesReceived && other.endpointId == endpointId && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * endpointId.hashCode() + bytes.contentHashCode()
    }

    /** Входящий поток: читать в фоне до EOF. */
    data class StreamReceived(val endpointId: String, val input: InputStream) : TransportEvent

    data class PayloadProgress(
        val endpointId: String,
        val payloadId: Long,
        val bytesTransferred: Long,
        val status: PayloadStatus,
    ) : TransportEvent
}

enum class PayloadStatus { IN_PROGRESS, SUCCESS, FAILURE, CANCELED }

class TransportException(val statusCode: Int, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: "Nearby status $statusCode", cause)
