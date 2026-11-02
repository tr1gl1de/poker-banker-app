package app.feltbook.core.transfer

import java.io.InputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Транспорт без радио: тест сам шлёт события Nearby ([emit]) и смотрит вызовы ([calls]). */
class FakeNearbyTransport : NearbyTransport {
    private val channel = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = channel.receiveAsFlow()

    val calls = mutableListOf<String>()
    val sentBytes = mutableListOf<ByteArray>()
    val sentStreams = mutableListOf<ByteArray>()
    var failNext: Int? = null
    private var nextPayloadId = 100L

    fun emit(event: TransportEvent) {
        channel.trySend(event)
    }

    private fun call(name: String) {
        calls += name
        failNext?.let {
            failNext = null
            throw TransportException(it)
        }
    }

    override suspend fun startAdvertising(localName: String) = call("startAdvertising:$localName")

    override suspend fun startDiscovery() = call("startDiscovery")

    override fun stopAdvertising() {
        calls += "stopAdvertising"
    }

    override fun stopDiscovery() {
        calls += "stopDiscovery"
    }

    override suspend fun requestConnection(localName: String, endpointId: String) =
        call("requestConnection:$localName:$endpointId")

    override suspend fun acceptConnection(endpointId: String) = call("accept:$endpointId")

    override suspend fun rejectConnection(endpointId: String) = call("reject:$endpointId")

    override suspend fun sendBytes(endpointId: String, bytes: ByteArray): Long {
        call("sendBytes:$endpointId")
        sentBytes += bytes
        return nextPayloadId++
    }

    override suspend fun sendStream(endpointId: String, input: InputStream): Long {
        call("sendStream:$endpointId")
        sentStreams += input.readBytes()
        return nextPayloadId++
    }

    override fun disconnect(endpointId: String) {
        calls += "disconnect:$endpointId"
    }

    override fun stopAll() {
        calls += "stopAll"
    }
}

/**
 * Два телефона в одном процессе: вызовы одного транспорта превращаются в события другого (как Nearby).
 * Поток доставляется кусками случайного размера.
 */
class LoopbackNearby(private val chunks: kotlin.random.Random, private val digits: String = "4719") {
    val sender = Side("sender-endpoint")
    val receiver = Side("receiver-endpoint")
    private var advertisedName: String? = null
    private var accepted = mutableSetOf<Side>()

    inner class Side(val idSeenByPeer: String) : NearbyTransport {
        private val channel = Channel<TransportEvent>(Channel.UNLIMITED)
        override val events: Flow<TransportEvent> = channel.receiveAsFlow()
        var localName: String = ""
        private var nextPayloadId = 1L

        private val peer: Side get() = if (this === sender) receiver else sender

        fun deliver(event: TransportEvent) {
            channel.trySend(event)
        }

        override suspend fun startAdvertising(localName: String) {
            this.localName = localName
            advertisedName = localName
        }

        override suspend fun startDiscovery() {
            advertisedName?.let { deliver(TransportEvent.EndpointFound(receiver.idSeenByPeer, it)) }
        }

        override fun stopAdvertising() = Unit

        override fun stopDiscovery() = Unit

        override suspend fun requestConnection(localName: String, endpointId: String) {
            this.localName = localName
            deliver(TransportEvent.ConnectionInitiated(endpointId, peer.localName, digits, incoming = false))
            peer.deliver(TransportEvent.ConnectionInitiated(idSeenByPeer, localName, digits, incoming = true))
        }

        override suspend fun acceptConnection(endpointId: String) {
            accepted += this
            if (accepted.size == 2) {
                sender.deliver(TransportEvent.ConnectionResult(receiver.idSeenByPeer, true, false, 0))
                receiver.deliver(TransportEvent.ConnectionResult(sender.idSeenByPeer, true, false, 0))
            }
        }

        override suspend fun rejectConnection(endpointId: String) {
            sender.deliver(TransportEvent.ConnectionResult(receiver.idSeenByPeer, false, true, REJECTED))
            receiver.deliver(TransportEvent.ConnectionResult(sender.idSeenByPeer, false, true, REJECTED))
        }

        override suspend fun sendBytes(endpointId: String, bytes: ByteArray): Long {
            val id = nextPayloadId++
            peer.deliver(TransportEvent.BytesReceived(idSeenByPeer, bytes))
            deliver(TransportEvent.PayloadProgress(endpointId, id, bytes.size.toLong(), PayloadStatus.SUCCESS))
            return id
        }

        override suspend fun sendStream(endpointId: String, input: InputStream): Long {
            val id = nextPayloadId++
            val bytes = input.readBytes()
            peer.deliver(TransportEvent.StreamReceived(idSeenByPeer, ChunkedInputStream(bytes, chunks)))
            deliver(TransportEvent.PayloadProgress(endpointId, id, bytes.size / 2L, PayloadStatus.IN_PROGRESS))
            deliver(TransportEvent.PayloadProgress(endpointId, id, bytes.size.toLong(), PayloadStatus.SUCCESS))
            return id
        }

        override fun disconnect(endpointId: String) = Unit

        override fun stopAll() = Unit
    }

    private companion object {
        const val REJECTED = 8004
    }
}
