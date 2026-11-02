package app.feltbook.core.transfer

import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.EnvelopeDto
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import java.io.ByteArrayInputStream
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Машина состояний на фейковом транспорте (roadmap M5.1) и пакет через транспорт туда-обратно. */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultTransferSessionTest {
    private val receiverEp = Endpoint("ep-1", "Влад")

    private fun TestScope.newSession(transport: NearbyTransport, timeouts: TransferTimeouts = TransferTimeouts()) =
        DefaultTransferSession(transport, backgroundScope, StandardTestDispatcher(testScheduler), timeouts)

    // region Отправитель

    @Test
    fun `отправитель — поиск, код, отправка потоком, ACK с тем же токеном → Done`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        session.discover("Андрей")
        runCurrent()
        assertEquals(TransferState.Discovering, session.state.value)

        transport.emit(TransportEvent.EndpointFound("ep-1", "Влад"))
        runCurrent()
        assertEquals(TransferState.Found(listOf(receiverEp)), session.state.value)

        session.connect("ep-1")
        runCurrent()
        assertEquals(TransferState.Connecting(receiverEp), session.state.value)
        assertTrue("stopDiscovery" in transport.calls)
        assertTrue("requestConnection:Андрей:ep-1" in transport.calls)

        transport.emit(TransportEvent.ConnectionInitiated("ep-1", "Влад", "4719", incoming = false))
        runCurrent()
        assertEquals(TransferState.AwaitingCodeConfirm(receiverEp, "4719", false), session.state.value)

        session.confirmCode()
        runCurrent()
        assertEquals(TransferState.AwaitingCodeConfirm(receiverEp, "4719", true), session.state.value)
        assertTrue("accept:ep-1" in transport.calls)

        transport.emit(TransportEvent.ConnectionResult("ep-1", success = true, rejected = false, statusCode = 0))
        runCurrent()
        assertEquals(TransferState.Connected(receiverEp), session.state.value)

        val text = TestGames(Random(1)).handoverPackageText()
        session.send(text)
        runCurrent()
        val sending = session.state.value as TransferState.Sending
        assertEquals(0L, sending.progress.bytes)
        val frame = transport.sentStreams.single()
        assertEquals(text, PackageFrame.read(ByteArrayInputStream(frame)).text)

        transport.emit(TransportEvent.PayloadProgress("ep-1", 100, frame.size / 2L, PayloadStatus.IN_PROGRESS))
        runCurrent()
        assertEquals(frame.size / 2L, (session.state.value as TransferState.Sending).progress.bytes)
        transport.emit(TransportEvent.PayloadProgress("ep-1", 100, frame.size.toLong(), PayloadStatus.SUCCESS))
        runCurrent()
        assertEquals(TransferState.AwaitingAck(receiverEp), session.state.value)

        // Чужой токен не засчитывается.
        transport.emit(TransportEvent.BytesReceived("ep-1", ControlMessage.ack("deadbeef").encode()))
        runCurrent()
        assertEquals(TransferState.AwaitingAck(receiverEp), session.state.value)

        val token = PackageFrame.encode(text).token
        transport.emit(TransportEvent.BytesReceived("ep-1", ControlMessage.ack(token).encode()))
        runCurrent()
        assertEquals(TransferState.Done(receiverEp), session.state.value)
    }

    @Test
    fun `нет ни одного телефона за минуту — TIMEOUT, найденный и потерянный — снова поиск`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        session.discover("Андрей")
        runCurrent()
        transport.emit(TransportEvent.EndpointFound("ep-1", "Влад"))
        runCurrent()
        transport.emit(TransportEvent.EndpointLost("ep-1"))
        runCurrent()
        assertEquals(TransferState.Discovering, session.state.value)
        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(TransferState.Discovering, session.state.value)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.TIMEOUT), session.state.value)
    }

    @Test
    fun `второй телефон нажал «Код другой» — CONNECTION_REJECTED`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        assertEquals(TransferState.AwaitingCodeConfirm(receiverEp, "4719", false), connectSender(session, transport))
        transport.emit(TransportEvent.ConnectionResult("ep-1", success = false, rejected = true, statusCode = 8004))
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.CONNECTION_REJECTED, statusCode = 8004), session.state.value)
    }

    @Test
    fun `«Код другой» здесь — соединение отклонено и поиск начат заново`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        connectSender(session, transport)
        session.rejectCode()
        runCurrent()
        assertTrue("reject:ep-1" in transport.calls)
        assertEquals(TransferState.Discovering, session.state.value)
        assertEquals(2, transport.calls.count { it == "startDiscovery" })
    }

    @Test
    fun `обрыв после отправки до ACK — Failed с packageSent, обрыв до отправки — без`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        connectSender(session, transport)
        session.confirmCode()
        transport.emit(TransportEvent.ConnectionResult("ep-1", success = true, rejected = false, statusCode = 0))
        runCurrent()
        session.send("пакет")
        runCurrent()
        transport.emit(TransportEvent.PayloadProgress("ep-1", 100, 1, PayloadStatus.SUCCESS))
        runCurrent()
        transport.emit(TransportEvent.Disconnected("ep-1"))
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.DISCONNECTED, packageSent = true), session.state.value)

        session.discover("Андрей")
        runCurrent()
        transport.emit(TransportEvent.EndpointFound("ep-1", "Влад"))
        runCurrent()
        session.connect("ep-1")
        runCurrent()
        transport.emit(TransportEvent.Disconnected("ep-1"))
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.DISCONNECTED, packageSent = false), session.state.value)
    }

    @Test
    fun `получатель отказался — DECLINED, ACK не пришёл за 10 минут — TIMEOUT с packageSent`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        sendAndAwaitAck(session, transport)
        transport.emit(TransportEvent.BytesReceived("ep-1", ControlMessage.decline().encode()))
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.DECLINED, packageSent = true), session.state.value)

        val second = FakeNearbyTransport()
        val other = newSession(second)
        sendAndAwaitAck(other, second)
        advanceTimeBy(TransferTimeouts().ack + 1.seconds)
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.TIMEOUT, packageSent = true), other.state.value)
    }

    @Test
    fun `send до соединения игнорируется, сбой старта поиска — START_FAILED`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        session.send("рано")
        runCurrent()
        assertEquals(TransferState.Idle, session.state.value)
        assertTrue(transport.sentStreams.isEmpty())

        transport.failNext = 8002
        session.discover("Андрей")
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.START_FAILED, statusCode = 8002), session.state.value)
    }

    @Test
    fun `сессия, которая ничего не начинала, не останавливает чужое радио`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        session.cancel()
        runCurrent()
        session.close()
        assertFalse("stopAll" in transport.calls)
    }

    // endregion

    // region Тайм-ауты канала

    @Test
    fun `канал открыт, а пакет не пошёл минуту — TIMEOUT`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        connectSender(session, transport)
        session.confirmCode()
        transport.emit(TransportEvent.ConnectionResult("ep-1", success = true, rejected = false, statusCode = 0))
        runCurrent()
        advanceTimeBy(59.seconds)
        runCurrent()
        assertTrue(session.state.value is TransferState.Connected)
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.TIMEOUT), session.state.value)
    }

    @Test
    fun `отправка без движения байтов 30 с — TIMEOUT, движение продлевает`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        connectSender(session, transport)
        session.confirmCode()
        transport.emit(TransportEvent.ConnectionResult("ep-1", success = true, rejected = false, statusCode = 0))
        runCurrent()
        session.send("пакет")
        runCurrent()
        advanceTimeBy(20.seconds)
        transport.emit(TransportEvent.PayloadProgress("ep-1", 100, 5, PayloadStatus.IN_PROGRESS))
        runCurrent()
        advanceTimeBy(20.seconds)
        runCurrent()
        assertTrue(session.state.value is TransferState.Sending)
        advanceTimeBy(11.seconds)
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.TIMEOUT), session.state.value)
    }

    @Test
    fun `приём завис — TIMEOUT через 30 с без новых байтов`() = runTest {
        val transport = FakeNearbyTransport()
        val release = java.util.concurrent.CountDownLatch(1)
        // Чтение — в настоящем фоновом потоке: поток «завис» на latch, а время теста виртуальное.
        val session = DefaultTransferSession(transport, backgroundScope, kotlinx.coroutines.Dispatchers.IO)
        connectReceiver(session, transport)
        val stuck = object : java.io.InputStream() {
            override fun read(): Int {
                release.await()
                return -1
            }
        }
        transport.emit(TransportEvent.StreamReceived("ep-9", stuck))
        runCurrent()
        assertTrue(session.state.value is TransferState.Receiving)
        advanceTimeBy(31.seconds)
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.TIMEOUT), session.state.value)
        release.countDown()
    }

    @Test
    fun `ошибка или отмена задачи Nearby не роняет очередь — следующая команда работает`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        transport.failNext = 16 // CommonStatusCodes.CANCELED
        session.advertise("Влад")
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.START_FAILED, statusCode = 16), session.state.value)
        session.advertise("Влад")
        runCurrent()
        assertEquals(TransferState.Advertising("Влад"), session.state.value)
    }

    // endregion

    // region Получатель

    @Test
    fun `получатель — реклама, код, поток кусками, «Принять» → ACK с токеном пакета`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        session.advertise("Влад")
        runCurrent()
        assertEquals(TransferState.Advertising("Влад"), session.state.value)
        assertTrue("startAdvertising:Влад" in transport.calls)

        val sender = Endpoint("ep-9", "Андрей")
        transport.emit(TransportEvent.ConnectionInitiated("ep-9", "Андрей", "4719", incoming = true))
        runCurrent()
        assertEquals(TransferState.AwaitingCodeConfirm(sender, "4719", false), session.state.value)
        // Второй отправитель во время первого — отклонён.
        transport.emit(TransportEvent.ConnectionInitiated("ep-8", "Чужой", "0000", incoming = true))
        runCurrent()
        assertTrue("reject:ep-8" in transport.calls)

        session.confirmCode()
        transport.emit(TransportEvent.ConnectionResult("ep-9", success = true, rejected = false, statusCode = 0))
        runCurrent()
        assertEquals(TransferState.Connected(sender), session.state.value)
        assertTrue("stopAdvertising" in transport.calls)

        val text = TestGames(Random(2)).handoverPackageText()
        val encoded = PackageFrame.encode(text)
        transport.emit(TransportEvent.StreamReceived("ep-9", ChunkedInputStream(encoded.bytes, Random(3))))
        runCurrent()
        assertEquals(TransferState.AwaitingAccept(sender, text), session.state.value)

        session.acknowledge()
        runCurrent()
        assertEquals(TransferState.Done(sender), session.state.value)
        val ack = ControlMessage.decode(transport.sentBytes.single())!!
        assertEquals(ControlMessage.ACK, ack.type)
        assertEquals(encoded.token, ack.token)
    }

    @Test
    fun `повреждённый поток — CORRUPTED, обрыв после получения — принять можно без ACK`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        connectReceiver(session, transport)
        val bytes = PackageFrame.encode("пакет").bytes.also { it[it.size - 1] = (it.last() + 1).toByte() }
        transport.emit(TransportEvent.StreamReceived("ep-9", ByteArrayInputStream(bytes)))
        runCurrent()
        assertEquals(TransferState.Failed(FailureReason.CORRUPTED), session.state.value)

        val second = FakeNearbyTransport()
        val other = newSession(second)
        connectReceiver(other, second)
        second.emit(TransportEvent.StreamReceived("ep-9", ByteArrayInputStream(PackageFrame.encode("пакет").bytes)))
        runCurrent()
        second.emit(TransportEvent.Disconnected("ep-9"))
        runCurrent()
        val awaiting = other.state.value as TransferState.AwaitingAccept
        assertFalse(awaiting.peerConnected)
        other.acknowledge()
        runCurrent()
        assertTrue(other.state.value is TransferState.Done)
        assertTrue(second.sentBytes.isEmpty())
    }

    @Test
    fun `«Отклонить» — отправителю уходит DECLINE`() = runTest {
        val transport = FakeNearbyTransport()
        val session = newSession(transport)
        connectReceiver(session, transport)
        transport.emit(TransportEvent.StreamReceived("ep-9", ByteArrayInputStream(PackageFrame.encode("x").bytes)))
        runCurrent()
        session.decline()
        runCurrent()
        assertEquals(ControlMessage.DECLINE, ControlMessage.decode(transport.sentBytes.single())!!.type)
        assertEquals(TransferState.Idle, session.state.value)
    }

    // endregion

    // region Два телефона

    @Test
    fun `два телефона — пакет туда-обратно через транспорт без потерь (property)`() = runTest {
        repeat(LOOPBACK_ITERATIONS) { seed ->
            val random = Random(seed)
            val games = TestGames(random)
            val prepared = games.prepared()
            val text = games.handoverPackageText(prepared)
            val pair = LoopbackNearby(random)
            val receiver = newSession(pair.receiver)
            val sender = newSession(pair.sender)

            receiver.advertise("Влад")
            runCurrent()
            sender.discover("Андрей")
            runCurrent()
            val found = sender.state.value as TransferState.Found
            sender.connect(found.endpoints.single().id)
            runCurrent()
            assertEquals("4719", (receiver.state.value as TransferState.AwaitingCodeConfirm).digits)
            assertEquals("4719", (sender.state.value as TransferState.AwaitingCodeConfirm).digits)
            receiver.confirmCode()
            sender.confirmCode()
            runCurrent()
            sender.send(text)
            runCurrent()

            val awaiting = receiver.state.value as TransferState.AwaitingAccept
            assertEquals("seed $seed", "Андрей", awaiting.endpoint.name)
            val decoded = FeltbookCodec.decode(awaiting.packageText) as DecodeResult.Ok
            assertEquals(
                "seed $seed",
                FeltbookPackage.HandoverPackage(prepared.outgoing, prepared.handover),
                decoded.pkg,
            )
            assertEquals(TransferState.AwaitingAck(Endpoint("receiver-endpoint", "Влад")), sender.state.value)

            receiver.acknowledge()
            runCurrent()
            assertTrue(sender.state.value is TransferState.Done)
            assertTrue(receiver.state.value is TransferState.Done)
            receiver.close()
            sender.close()
        }
    }

    @Test
    fun `через транспорт — неверный хеш и новая версия отклоняются кодеком`() = runTest {
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
        }
        val text = TestGames(Random(5)).handoverPackageText()
        val envelope = json.decodeFromString(EnvelopeDto.serializer(), text)
        val wrongHash = json.encodeToString(EnvelopeDto.serializer(), envelope.copy(sha256 = "0".repeat(64)))
        val tampered = json.encodeToString(
            EnvelopeDto.serializer(),
            envelope.copy(payload = envelope.payload.replaceFirst("\"amount\":", "\"amount\":1")),
        )
        val newer = json.encodeToString(EnvelopeDto.serializer(), envelope.copy(schemaVersion = 2))

        val received = listOf(wrongHash, tampered, newer).map { packageText ->
            val pair = LoopbackNearby(Random(6))
            val receiver = newSession(pair.receiver)
            val sender = newSession(pair.sender)
            receiver.advertise("Влад")
            runCurrent()
            sender.discover("Андрей")
            runCurrent()
            sender.connect("receiver-endpoint")
            runCurrent()
            receiver.confirmCode()
            sender.confirmCode()
            runCurrent()
            sender.send(packageText)
            runCurrent()
            // Кадр цел — транспорт отдаёт текст как есть; порчу содержимого ловит кодек.
            val awaiting = receiver.state.value as TransferState.AwaitingAccept
            FeltbookCodec.decode(awaiting.packageText)
        }
        assertEquals(DecodeResult.ChecksumMismatch, received[0])
        assertEquals(DecodeResult.ChecksumMismatch, received[1])
        assertEquals(DecodeResult.UnsupportedVersion(2, FeltbookCodec.SCHEMA_VERSION), received[2])
    }

    // endregion

    private fun TestScope.connectSender(session: TransferSession, transport: FakeNearbyTransport): TransferState {
        session.discover("Андрей")
        runCurrent()
        transport.emit(TransportEvent.EndpointFound("ep-1", "Влад"))
        runCurrent()
        session.connect("ep-1")
        runCurrent()
        transport.emit(TransportEvent.ConnectionInitiated("ep-1", "Влад", "4719", incoming = false))
        runCurrent()
        return session.state.value
    }

    private fun TestScope.sendAndAwaitAck(session: TransferSession, transport: FakeNearbyTransport) {
        connectSender(session, transport)
        session.confirmCode()
        transport.emit(TransportEvent.ConnectionResult("ep-1", success = true, rejected = false, statusCode = 0))
        runCurrent()
        session.send("пакет")
        runCurrent()
        transport.emit(TransportEvent.PayloadProgress("ep-1", 100, 1, PayloadStatus.SUCCESS))
        runCurrent()
        assertEquals(TransferState.AwaitingAck(receiverEp), session.state.value)
    }

    private fun TestScope.connectReceiver(session: TransferSession, transport: FakeNearbyTransport) {
        session.advertise("Влад")
        runCurrent()
        transport.emit(TransportEvent.ConnectionInitiated("ep-9", "Андрей", "4719", incoming = true))
        runCurrent()
        session.confirmCode()
        transport.emit(TransportEvent.ConnectionResult("ep-9", success = true, rejected = false, statusCode = 0))
        runCurrent()
        assertTrue(session.state.value is TransferState.Connected)
    }

    private companion object {
        const val LOOPBACK_ITERATIONS = 30
    }
}
