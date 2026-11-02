package app.feltbook.core.transfer

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Тайм-ауты передачи. Ожидание кода и «Принять» — решение человека, поэтому длинные.
 * @property discovery поиск без единого найденного телефона.
 * @property connect от запроса соединения до показа кода.
 * @property codeConfirm от показа кода до подтверждения на обоих телефонах.
 * @property ack от доставки пакета до «Принять» на втором телефоне.
 * @property ackDelivery сколько получатель ждёт доставки своего ACK, прежде чем разорвать соединение.
 */
data class TransferTimeouts(
    val discovery: Duration = 60.seconds,
    val connect: Duration = 30.seconds,
    val codeConfirm: Duration = 2.minutes,
    val ack: Duration = 10.minutes,
    val ackDelivery: Duration = 5.seconds,
)

/**
 * [TransferSession] поверх [NearbyTransport]. Все входы (команды, события Nearby, тайм-ауты, чтение потока) идут
 * через одну очередь и обрабатываются по одному — гонок между колбэками Nearby и нажатиями нет.
 *
 * @param parentScope жизнь сессии; [close] отменяет только её собственную задачу.
 * @param ioDispatcher чтение входящего потока.
 */
// Машина состояний: по обработчику на событие; ранние выходы — «событие не к этому состоянию».
@Suppress("TooManyFunctions", "ReturnCount", "ComplexCondition")
class DefaultTransferSession(
    private val transport: NearbyTransport,
    parentScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeouts: TransferTimeouts = TransferTimeouts(),
) : TransferSession {
    private enum class Role { SENDER, RECEIVER }

    private sealed interface Input {
        data class Command(val run: suspend () -> Unit) : Input

        data class Event(val event: TransportEvent) : Input

        data class Timeout(val id: Int) : Input

        data class ReadProgress(val run: Int, val bytes: Long, val total: Long) : Input

        data class ReadDone(val run: Int, val decoded: PackageFrame.Decoded) : Input

        data class ReadFailed(val run: Int) : Input
    }

    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val inbox = Channel<Input>(Channel.UNLIMITED)
    private val mutableState = MutableStateFlow<TransferState>(TransferState.Idle)
    override val state: StateFlow<TransferState> = mutableState.asStateFlow()

    private var role: Role? = null
    private var localName: String = ""
    private val found = LinkedHashMap<String, Endpoint>()
    private var peer: Endpoint? = null
    private var connected = false
    private var outgoing: PackageFrame.Encoded? = null
    private var outgoingPayloadId: Long? = null
    private var packageSent = false
    private var receivedToken: String? = null
    private var ackPayloadId: Long? = null
    private var timer: Job? = null
    private var timerId = 0
    private var reader: Job? = null
    private var readRun = 0

    init {
        scope.launch { transport.events.collect { inbox.send(Input.Event(it)) } }
        scope.launch { for (input in inbox) handle(input) }
    }

    override fun advertise(localName: String) = post { startReceiver(localName) }

    override fun discover(localName: String) = post { startSender(localName) }

    override fun connect(endpointId: String) = post { onConnect(endpointId) }

    override fun confirmCode() = post { onConfirmCode() }

    override fun rejectCode() = post { onRejectCode() }

    override fun send(packageText: String) = post { onSend(packageText) }

    override fun acknowledge() = post { onAcknowledge() }

    override fun decline() = post { onDecline() }

    override fun cancel() = post {
        stopOwn()
        reset()
        role = null
        mutableState.value = TransferState.Idle
    }

    override fun close() {
        stopOwn()
        role = null
        job.cancel()
        inbox.close()
    }

    /**
     * Радио Nearby у приложения одно. Сессия, которая ничего не начинала (вторая вкладка экрана), не трогает чужую
     * рекламу или поиск.
     */
    private fun stopOwn() {
        if (role != null) transport.stopAll()
    }

    private fun post(run: suspend () -> Unit) {
        inbox.trySend(Input.Command(run))
    }

    private suspend fun handle(input: Input) {
        try {
            dispatch(input)
        } catch (error: TransportException) {
            val reason = when (mutableState.value) {
                TransferState.Idle, is TransferState.Advertising, TransferState.Discovering ->
                    FailureReason.START_FAILED

                else -> FailureReason.CONNECTION_FAILED
            }
            fail(reason, error.statusCode)
        } catch (_: IllegalArgumentException) {
            // Пакет больше PackageFrame.MAX_BODY_BYTES — по воздуху не уйдёт.
            fail(FailureReason.CONNECTION_FAILED)
        }
    }

    private suspend fun dispatch(input: Input) {
        when (input) {
            is Input.Command -> input.run()
            is Input.Event -> onEvent(input.event)
            is Input.Timeout -> if (input.id == timerId) onTimeout()
            is Input.ReadProgress -> if (input.run == readRun) onReadProgress(input.bytes, input.total)
            is Input.ReadDone -> if (input.run == readRun) onReadDone(input.decoded)
            is Input.ReadFailed -> if (input.run == readRun) fail(FailureReason.CORRUPTED)
        }
    }

    // region Команды

    private suspend fun startReceiver(name: String) {
        stopOwn()
        reset()
        role = Role.RECEIVER
        localName = name
        transport.startAdvertising(name)
        mutableState.value = TransferState.Advertising(name)
    }

    private suspend fun startSender(name: String) {
        stopOwn()
        reset()
        role = Role.SENDER
        localName = name
        transport.startDiscovery()
        mutableState.value = TransferState.Discovering
        startTimer(timeouts.discovery)
    }

    private suspend fun onConnect(endpointId: String) {
        if (role != Role.SENDER || peer != null) return
        val endpoint = found[endpointId] ?: return
        peer = endpoint
        // P2P_POINT_TO_POINT: поиск мешает полосе соединения.
        transport.stopDiscovery()
        mutableState.value = TransferState.Connecting(endpoint)
        startTimer(timeouts.connect)
        transport.requestConnection(localName, endpointId)
    }

    private suspend fun onConfirmCode() {
        val current = mutableState.value as? TransferState.AwaitingCodeConfirm ?: return
        if (current.confirmedLocally) return
        mutableState.value = current.copy(confirmedLocally = true)
        transport.acceptConnection(current.endpoint.id)
    }

    private suspend fun onRejectCode() {
        val current = mutableState.value as? TransferState.AwaitingCodeConfirm ?: return
        transport.rejectConnection(current.endpoint.id)
        restart()
    }

    private suspend fun onSend(text: String) {
        val current = mutableState.value as? TransferState.Connected ?: return
        if (role != Role.SENDER) return
        val encoded = PackageFrame.encode(text)
        outgoing = encoded
        val total = encoded.bytes.size.toLong()
        mutableState.value = TransferState.Sending(current.endpoint, TransferProgress(0, total))
        outgoingPayloadId = transport.sendStream(current.endpoint.id, ByteArrayInputStream(encoded.bytes))
    }

    private suspend fun onAcknowledge() {
        val current = mutableState.value as? TransferState.AwaitingAccept ?: return
        val token = receivedToken ?: return
        mutableState.value = TransferState.Done(current.endpoint)
        if (!connected) {
            transport.stopAll()
            return
        }
        try {
            ackPayloadId = transport.sendBytes(current.endpoint.id, ControlMessage.ack(token).encode())
            // Разорвать после доставки ACK (PayloadProgress) или по тайм-ауту.
            startTimer(timeouts.ackDelivery)
        } catch (_: TransportException) {
            // Импорт уже записан: у отправителя сработает «Новый банкир принял игру?».
            transport.stopAll()
        }
    }

    private suspend fun onDecline() {
        val current = mutableState.value as? TransferState.AwaitingAccept ?: return
        if (connected) {
            try {
                transport.sendBytes(current.endpoint.id, ControlMessage.decline().encode())
            } catch (_: TransportException) {
                // Отправитель узнает по обрыву связи.
            }
        }
        transport.stopAll()
        reset()
        role = null
        mutableState.value = TransferState.Idle
    }

    // endregion

    // region События Nearby

    private suspend fun onEvent(event: TransportEvent) {
        when (event) {
            is TransportEvent.EndpointFound -> onEndpointFound(event)
            is TransportEvent.EndpointLost -> onEndpointLost(event.endpointId)
            is TransportEvent.ConnectionInitiated -> onConnectionInitiated(event)
            is TransportEvent.ConnectionResult -> onConnectionResult(event)
            is TransportEvent.Disconnected -> onDisconnected(event.endpointId)
            is TransportEvent.BytesReceived -> onBytes(event)
            is TransportEvent.StreamReceived -> onStream(event)
            is TransportEvent.PayloadProgress -> onPayloadProgress(event)
        }
    }

    private fun onEndpointFound(event: TransportEvent.EndpointFound) {
        if (role != Role.SENDER || peer != null) return
        found[event.endpointId] = Endpoint(event.endpointId, event.name)
        cancelTimer()
        mutableState.value = TransferState.Found(found.values.toList())
    }

    private fun onEndpointLost(endpointId: String) {
        if (role != Role.SENDER || peer != null) return
        if (found.remove(endpointId) == null) return
        if (found.isEmpty()) {
            mutableState.value = TransferState.Discovering
            startTimer(timeouts.discovery)
        } else {
            mutableState.value = TransferState.Found(found.values.toList())
        }
    }

    private suspend fun onConnectionInitiated(event: TransportEvent.ConnectionInitiated) {
        when (role) {
            Role.RECEIVER -> {
                if (peer != null && peer?.id != event.endpointId) {
                    // Второй отправитель, пока идёт первый, — отклоняем.
                    transport.rejectConnection(event.endpointId)
                    return
                }
                peer = Endpoint(event.endpointId, event.name)
            }

            Role.SENDER -> if (peer?.id != event.endpointId) {
                transport.rejectConnection(event.endpointId)
                return
            }

            null -> {
                transport.rejectConnection(event.endpointId)
                return
            }
        }
        val endpoint = checkNotNull(peer)
        mutableState.value = TransferState.AwaitingCodeConfirm(endpoint, event.digits, confirmedLocally = false)
        startTimer(timeouts.codeConfirm)
    }

    private suspend fun onConnectionResult(event: TransportEvent.ConnectionResult) {
        val endpoint = peer ?: return
        if (endpoint.id != event.endpointId) return
        when {
            event.success -> {
                connected = true
                cancelTimer()
                if (role == Role.RECEIVER) transport.stopAdvertising()
                mutableState.value = TransferState.Connected(endpoint)
            }

            mutableState.value !is TransferState.AwaitingCodeConfirm &&
                mutableState.value !is TransferState.Connecting -> Unit

            // «Код другой» здесь — restart() уже сбросил peer и сюда не дойдёт; значит, отклонил второй телефон.
            event.rejected -> fail(FailureReason.CONNECTION_REJECTED, event.statusCode)

            else -> fail(FailureReason.CONNECTION_FAILED, event.statusCode)
        }
    }

    private fun onDisconnected(endpointId: String) {
        val endpoint = peer ?: return
        if (endpoint.id != endpointId) return
        connected = false
        when (val current = mutableState.value) {
            is TransferState.Done, is TransferState.Failed, TransferState.Idle -> Unit
            is TransferState.AwaitingAccept -> mutableState.value = current.copy(peerConnected = false)
            else -> fail(FailureReason.DISCONNECTED)
        }
    }

    private fun onBytes(event: TransportEvent.BytesReceived) {
        val endpoint = peer ?: return
        if (role != Role.SENDER || endpoint.id != event.endpointId) return
        val message = ControlMessage.decode(event.bytes) ?: return
        val current = mutableState.value
        if (current !is TransferState.AwaitingAck && current !is TransferState.Sending) return
        when (message.type) {
            ControlMessage.ACK -> if (message.token == outgoing?.token) {
                cancelTimer()
                mutableState.value = TransferState.Done(endpoint)
                transport.stopAll()
            }

            ControlMessage.DECLINE -> fail(FailureReason.DECLINED)
        }
    }

    private fun onStream(event: TransportEvent.StreamReceived) {
        val endpoint = peer
        if (role != Role.RECEIVER || endpoint == null || endpoint.id != event.endpointId || !connected) {
            runCatching { event.input.close() }
            return
        }
        if (mutableState.value !is TransferState.Connected) {
            runCatching { event.input.close() }
            return
        }
        mutableState.value = TransferState.Receiving(endpoint, TransferProgress(0, -1))
        val run = ++readRun
        reader?.cancel()
        reader = scope.launch(ioDispatcher) {
            val result = try {
                event.input.use { input ->
                    val decoded = PackageFrame.read(input) { bytes, total ->
                        inbox.trySend(Input.ReadProgress(run, bytes, total))
                    }
                    Input.ReadDone(run, decoded)
                }
            } catch (_: PackageFrame.FrameException) {
                Input.ReadFailed(run)
            } catch (_: IOException) {
                Input.ReadFailed(run)
            }
            inbox.trySend(result)
        }
    }

    private fun onReadProgress(bytes: Long, total: Long) {
        val current = mutableState.value as? TransferState.Receiving ?: return
        mutableState.value = current.copy(progress = TransferProgress(bytes, total))
    }

    private fun onReadDone(decoded: PackageFrame.Decoded) {
        val current = mutableState.value as? TransferState.Receiving ?: return
        receivedToken = decoded.token
        mutableState.value = TransferState.AwaitingAccept(current.endpoint, decoded.text, peerConnected = connected)
    }

    private fun onPayloadProgress(event: TransportEvent.PayloadProgress) {
        when (event.payloadId) {
            outgoingPayloadId -> onOutgoingProgress(event)

            ackPayloadId -> if (event.status != PayloadStatus.IN_PROGRESS) {
                cancelTimer()
                transport.stopAll()
            }
        }
    }

    private fun onOutgoingProgress(event: TransportEvent.PayloadProgress) {
        val endpoint = peer ?: return
        val total = outgoing?.bytes?.size?.toLong() ?: return
        val current = mutableState.value
        when (event.status) {
            PayloadStatus.IN_PROGRESS -> if (current is TransferState.Sending) {
                mutableState.value = current.copy(progress = TransferProgress(event.bytesTransferred, total))
            }

            PayloadStatus.SUCCESS -> if (current is TransferState.Sending) {
                packageSent = true
                mutableState.value = TransferState.AwaitingAck(endpoint)
                startTimer(timeouts.ack)
            }

            PayloadStatus.FAILURE, PayloadStatus.CANCELED ->
                if (current is TransferState.Sending) fail(FailureReason.CONNECTION_FAILED)
        }
    }

    // endregion

    private fun onTimeout() {
        when (mutableState.value) {
            // Получатель отправил ACK: тайм-аут доставки — просто разорвать.
            is TransferState.Done -> transport.stopAll()

            TransferState.Discovering,
            is TransferState.Connecting,
            is TransferState.AwaitingCodeConfirm,
            is TransferState.AwaitingAck,
            -> fail(FailureReason.TIMEOUT)

            else -> Unit
        }
    }

    private suspend fun restart() {
        val name = localName
        when (role) {
            Role.SENDER -> startSender(name)
            Role.RECEIVER -> startReceiver(name)
            null -> Unit
        }
    }

    private fun fail(reason: FailureReason, statusCode: Int? = null) {
        val sent = packageSent
        transport.stopAll()
        reset()
        mutableState.value = TransferState.Failed(reason, packageSent = sent, statusCode = statusCode)
    }

    private fun reset() {
        cancelTimer()
        reader?.cancel()
        reader = null
        readRun++
        found.clear()
        peer = null
        connected = false
        outgoing = null
        outgoingPayloadId = null
        packageSent = false
        receivedToken = null
        ackPayloadId = null
    }

    private fun startTimer(duration: Duration) {
        cancelTimer()
        val id = ++timerId
        timer = scope.launch {
            delay(duration)
            inbox.trySend(Input.Timeout(id))
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
        timerId++
    }
}
