package app.feltbook.core.transfer

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import java.io.InputStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * [NearbyTransport] на Google Nearby Connections. Колбэки Nearby приходят в главном потоке и складываются в канал.
 * Клиент создаётся лениво: на телефоне без Google Play Services экран до него не доходит
 * ([TransferEnvironment.isNearbyAvailable]).
 */
@Suppress("TooManyFunctions") // Повторяет API ConnectionsClient один к одному.
class GmsNearbyTransport(context: Context) : NearbyTransport {
    private val appContext = context.applicationContext
    private val client: ConnectionsClient by lazy { Nearby.getConnectionsClient(appContext) }
    private val channel = Channel<TransportEvent>(Channel.UNLIMITED)
    override val events: Flow<TransportEvent> = channel.receiveAsFlow()

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            emit(
                TransportEvent.ConnectionInitiated(
                    endpointId = endpointId,
                    name = info.endpointName,
                    digits = info.authenticationDigits,
                    incoming = info.isIncomingConnection,
                ),
            )
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            val code = resolution.status.statusCode
            emit(
                TransportEvent.ConnectionResult(
                    endpointId = endpointId,
                    success = code == ConnectionsStatusCodes.STATUS_OK,
                    rejected = code == ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED,
                    statusCode = code,
                ),
            )
        }

        override fun onDisconnected(endpointId: String) {
            emit(TransportEvent.Disconnected(endpointId))
        }
    }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) emit(TransportEvent.EndpointFound(endpointId, info.endpointName))
        }

        override fun onEndpointLost(endpointId: String) {
            emit(TransportEvent.EndpointLost(endpointId))
        }
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> payload.asBytes()?.let { emit(TransportEvent.BytesReceived(endpointId, it)) }

                Payload.Type.STREAM ->
                    payload.asStream()?.asInputStream()?.let { emit(TransportEvent.StreamReceived(endpointId, it)) }

                // Файлы не принимаем: формат один — поток кадра PackageFrame.
                else -> client.cancelPayload(payload.id)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val status = when (update.status) {
                PayloadTransferUpdate.Status.SUCCESS -> PayloadStatus.SUCCESS
                PayloadTransferUpdate.Status.FAILURE -> PayloadStatus.FAILURE
                PayloadTransferUpdate.Status.CANCELED -> PayloadStatus.CANCELED
                else -> PayloadStatus.IN_PROGRESS
            }
            emit(TransportEvent.PayloadProgress(endpointId, update.payloadId, update.bytesTransferred, status))
        }
    }

    override suspend fun startAdvertising(localName: String) {
        val options = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        client.startAdvertising(localName, SERVICE_ID, lifecycle, options).awaitNearby()
    }

    override suspend fun startDiscovery() {
        val options = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        client.startDiscovery(SERVICE_ID, discovery, options).awaitNearby()
    }

    override fun stopAdvertising() = client.stopAdvertising()

    override fun stopDiscovery() = client.stopDiscovery()

    override suspend fun requestConnection(localName: String, endpointId: String) {
        client.requestConnection(localName, endpointId, lifecycle).awaitNearby()
    }

    override suspend fun acceptConnection(endpointId: String) {
        client.acceptConnection(endpointId, payloads).awaitNearby()
    }

    override suspend fun rejectConnection(endpointId: String) {
        client.rejectConnection(endpointId).awaitNearby()
    }

    override suspend fun sendBytes(endpointId: String, bytes: ByteArray): Long {
        val payload = Payload.fromBytes(bytes)
        client.sendPayload(endpointId, payload).awaitNearby()
        return payload.id
    }

    override suspend fun sendStream(endpointId: String, input: InputStream): Long {
        val payload = Payload.fromStream(input)
        client.sendPayload(endpointId, payload).awaitNearby()
        return payload.id
    }

    override fun disconnect(endpointId: String) = client.disconnectFromEndpoint(endpointId)

    override fun stopAll() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
    }

    private fun emit(event: TransportEvent) {
        channel.trySend(event)
    }

    companion object {
        /** Идентификатор сервиса Nearby: телефоны с Feltbook видят только друг друга. */
        const val SERVICE_ID: String = "app.feltbook.handover"
        private val STRATEGY: Strategy = Strategy.P2P_POINT_TO_POINT
    }
}
