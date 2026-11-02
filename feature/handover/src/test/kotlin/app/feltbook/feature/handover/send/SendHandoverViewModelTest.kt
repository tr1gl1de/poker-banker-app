package app.feltbook.feature.handover.send

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.feltbook.core.data.handover.PendingHandoverStore
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import app.feltbook.core.transfer.Endpoint
import app.feltbook.core.transfer.FailureReason
import app.feltbook.core.transfer.TransferProgress
import app.feltbook.core.transfer.TransferState
import app.feltbook.feature.handover.HandoverRoute
import app.feltbook.feature.handover.testing.FakeEnvironment
import app.feltbook.feature.handover.testing.FakeFileStore
import app.feltbook.feature.handover.testing.FakeGameRepository
import app.feltbook.feature.handover.testing.FakeTransferSession
import app.feltbook.feature.handover.testing.Games
import app.feltbook.feature.handover.testing.MainDispatcherRule
import app.feltbook.feature.handover.testing.TestClock
import app.feltbook.feature.handover.testing.pendingStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** «Отдать игру»: машина экрана на фейковой сессии; запись передачи — только после ACK/«Файл отправлен». */
class SendHandoverViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val vlad = Endpoint("ep-1", "Влад")

    private class Setup(
        val game: GameSnapshot,
        val repository: FakeGameRepository,
        val session: FakeTransferSession,
        val environment: FakeEnvironment,
        val files: FakeFileStore,
        val pending: PendingHandoverStore,
        val viewModel: SendHandoverViewModel,
    )

    private fun setup(game: GameSnapshot = Games.running(), environment: FakeEnvironment = FakeEnvironment()): Setup {
        val repository = FakeGameRepository(listOf(game))
        val session = FakeTransferSession()
        val files = FakeFileStore()
        val pending = pendingStore()
        val viewModel = SendHandoverViewModel(
            savedStateHandle = SavedStateHandle(mapOf(HandoverRoute.ARG_GAME_ID to game.game.id.value.toString())),
            gameRepository = repository,
            session = session,
            environment = environment,
            fileStore = files,
            pendingStore = pending,
            clock = TestClock(),
        )
        return Setup(game, repository, session, environment, files, pending, viewModel)
    }

    private fun Setup.connectToVlad() {
        viewModel.onAction(SendAction.Activate)
        session.state.value = TransferState.Found(listOf(vlad))
        session.state.value = TransferState.Connecting(vlad)
        session.state.value = TransferState.AwaitingCodeConfirm(vlad, "4719", confirmedLocally = false)
    }

    @Test
    fun `посреди игры — поиск, один телефон соединяется сам, код и акт на экране`() = runTest {
        val s = setup()
        s.viewModel.onAction(SendAction.Activate)
        assertEquals(listOf("discover:Андрей"), s.session.commands)
        assertEquals(SendPhase.Searching, s.viewModel.uiState.value.phase)

        s.session.state.value = TransferState.Found(listOf(vlad))
        assertTrue("connect:ep-1" in s.session.commands)
        s.session.state.value = TransferState.AwaitingCodeConfirm(vlad, "4719", confirmedLocally = false)

        val state = s.viewModel.uiState.value
        assertEquals(SendPhase.Code("Влад", "4719", waitingPeer = false), state.phase)
        assertEquals(SendKind.HANDOVER, state.kind)
        assertEquals(2_300L, state.act!!.cashOnHand)
        assertEquals(200L, state.act!!.incomeTakeable)
        assertEquals(2_300L, state.cashToHandOver)
    }

    @Test
    fun `hold «Код совпадает» — пакет уходит, но игра у отправителя пишется только после ACK`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.KeepIncome(true))
        assertEquals(2_100L, s.viewModel.uiState.value.cashToHandOver)
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        assertTrue("confirmCode" in s.session.commands)

        s.session.state.value = TransferState.Connected(vlad)
        val pkg = (FeltbookCodec.decode(s.session.sent.single()) as DecodeResult.Ok).pkg
            as FeltbookPackage.HandoverPackage
        assertEquals("Влад", pkg.handover.toBanker)
        assertEquals(200L, pkg.handover.incomeTaken)
        assertEquals(2_100L, pkg.handover.cashHandedOver)
        assertEquals(GameStatus.RUNNING, pkg.game.game.status)

        s.session.state.value = TransferState.Sending(vlad, TransferProgress(10, 100))
        assertEquals(SendPhase.Sending("Влад", 0.1f), s.viewModel.uiState.value.phase)
        s.session.state.value = TransferState.AwaitingAck(vlad)
        assertEquals(SendPhase.AwaitingAccept("Влад"), s.viewModel.uiState.value.phase)
        assertEquals(0, s.repository.commits)
        assertEquals(GameStatus.RUNNING, s.repository.snapshot(s.game.game.id)!!.game.status)

        s.session.state.value = TransferState.Done(vlad)
        val after = s.repository.snapshot(s.game.game.id)!!
        assertEquals(GameStatus.HANDED_OVER, after.game.status)
        assertEquals(pkg.handover, after.handovers.single())
        assertEquals(pkg.game.entries, after.entries)
        assertEquals(SendPhase.Done("Влад", cash = 2_100L), s.viewModel.uiState.value.phase)

        s.viewModel.events.test {
            s.viewModel.onAction(SendAction.Finish)
            assertEquals(SendEvent.Finished, awaitItem())
        }
    }

    @Test
    fun `нет Play Services — сразу файл`() = runTest {
        val s = setup(environment = FakeEnvironment(nearby = false))
        s.viewModel.onAction(SendAction.Activate)
        assertEquals(SendPhase.Failed(SendFailure.NO_GMS), s.viewModel.uiState.value.phase)
        assertTrue(s.viewModel.uiState.value.canSendFile)
        assertTrue(s.session.commands.none { it.startsWith("discover") })
    }

    @Test
    fun `разрешения — только по «Разрешить», отказ — ошибка с файлом, согласие — поиск`() = runTest {
        val environment = FakeEnvironment(missing = listOf("android.permission.BLUETOOTH_SCAN"))
        val s = setup(environment = environment)
        s.viewModel.onAction(SendAction.Activate)
        assertEquals(SendPhase.NeedsPermission, s.viewModel.uiState.value.phase)

        s.viewModel.events.test {
            s.viewModel.onAction(SendAction.AllowPermissions)
            assertEquals(SendEvent.RequestPermissions(listOf("android.permission.BLUETOOTH_SCAN")), awaitItem())
        }
        s.viewModel.onAction(SendAction.PermissionsResult(allGranted = false))
        assertEquals(SendPhase.Failed(SendFailure.PERMISSION_DENIED), s.viewModel.uiState.value.phase)
        assertTrue(s.viewModel.uiState.value.canSendFile)

        environment.missing = emptyList()
        s.viewModel.onAction(SendAction.PermissionsResult(allGranted = true))
        assertEquals(SendPhase.Searching, s.viewModel.uiState.value.phase)
        assertTrue("discover:Андрей" in s.session.commands)
    }

    @Test
    fun `ACK потерян — «Новый банкир принял игру» → «Да» пишет передачу, повтор шлёт те же id`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        s.session.state.value = TransferState.Failed(FailureReason.DISCONNECTED, packageSent = true)
        assertEquals(SendPhase.Failed(SendFailure.CONNECTION, ackLost = true), s.viewModel.uiState.value.phase)

        // «Попробовать ещё раз» — тот же подготовленный пакет (те же id записей и акта).
        s.viewModel.onAction(SendAction.Retry)
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        // createdAt конверта может отличаться — сравниваем разобранный пакет.
        assertEquals(FeltbookCodec.decode(s.session.sent[0]), FeltbookCodec.decode(s.session.sent[1]))
        s.session.state.value = TransferState.Failed(FailureReason.TIMEOUT, packageSent = true)

        s.viewModel.onAction(SendAction.OpenAckLost)
        assertEquals(SendDialog.AckLost, s.viewModel.uiState.value.dialog)
        s.viewModel.onAction(SendAction.AckLostConfirmed)
        assertEquals(GameStatus.HANDED_OVER, s.repository.snapshot(s.game.game.id)!!.game.status)
        assertEquals(SendPhase.Done("Влад", cash = 2_300L), s.viewModel.uiState.value.phase)
    }

    @Test
    fun `отказ получателя — DECLINED без записи`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        s.session.state.value = TransferState.Failed(FailureReason.DECLINED, packageSent = true)
        assertEquals(SendPhase.Failed(SendFailure.DECLINED), s.viewModel.uiState.value.phase)
        assertEquals(0, s.repository.commits)
    }

    @Test
    fun `файлом — имя получателя, Sharesheet, «Файл отправлен» пишет передачу`() = runTest {
        val s = setup(environment = FakeEnvironment(nearby = false))
        s.viewModel.onAction(SendAction.Activate)
        s.viewModel.onAction(SendAction.SendFile)
        assertEquals(SendDialog.FileRecipient(""), s.viewModel.uiState.value.dialog)

        s.viewModel.onAction(SendAction.FileRecipientChanged("   "))
        s.viewModel.onAction(SendAction.FileRecipientConfirmed)
        assertEquals(SendDialog.FileRecipient("   ", error = true), s.viewModel.uiState.value.dialog)

        s.viewModel.events.test {
            s.viewModel.onAction(SendAction.FileRecipientChanged(" Влад "))
            s.viewModel.onAction(SendAction.FileRecipientConfirmed)
            val share = awaitItem() as SendEvent.Share
            assertEquals("Пятничный стол.feltbook", share.file.fileName)
        }
        val (baseName, text) = s.files.written.single()
        assertEquals("Пятничный стол", baseName)
        val pkg = (FeltbookCodec.decode(text) as DecodeResult.Ok).pkg as FeltbookPackage.HandoverPackage
        assertEquals("Влад", pkg.handover.toBanker)
        assertEquals(SendDialog.FileSent, s.viewModel.uiState.value.dialog)
        assertEquals(0, s.repository.commits)

        // «Ещё нет» — ничего не пишется.
        s.viewModel.onAction(SendAction.DismissDialog)
        assertNull(s.viewModel.uiState.value.dialog)
        assertEquals(GameStatus.RUNNING, s.repository.snapshot(s.game.game.id)!!.game.status)

        // Повторная отправка тому же — тот же пакет (нет расхождения id, если первый файл всё же дошёл).
        s.viewModel.onAction(SendAction.SendFile)
        assertEquals(SendDialog.FileRecipient("Влад"), s.viewModel.uiState.value.dialog)
        s.viewModel.onAction(SendAction.FileRecipientConfirmed)
        val second = (FeltbookCodec.decode(s.files.written[1].second) as DecodeResult.Ok).pkg
        assertEquals(pkg, second)

        s.viewModel.onAction(SendAction.FileSentConfirmed)
        assertEquals(GameStatus.HANDED_OVER, s.repository.snapshot(s.game.game.id)!!.game.status)
        assertEquals(SendPhase.Done("Влад", cash = 2_300L), s.viewModel.uiState.value.phase)
    }

    @Test
    fun `после расчёта — шаблон (правила + имена), игра отправителя остаётся CLOSED`() = runTest {
        val closed = Games.closed()
        val s = setup(game = closed)
        assertEquals(SendKind.TEMPLATE, s.viewModel.uiState.value.kind)
        assertEquals(3, s.viewModel.uiState.value.templateNames)
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        val pkg = (FeltbookCodec.decode(s.session.sent.single()) as DecodeResult.Ok).pkg
            as FeltbookPackage.TemplatePackage
        assertEquals(closed.game.rules, pkg.template.rules)
        assertEquals(listOf("Андрей", "Дима", "Егор"), pkg.template.playerNames)

        s.session.state.value = TransferState.Done(vlad)
        assertEquals(SendPhase.Done("Влад"), s.viewModel.uiState.value.phase)
        assertEquals(0, s.repository.commits)
        assertEquals(GameStatus.CLOSED, s.repository.snapshot(closed.game.id)!!.game.status)
    }

    @Test
    fun `уже переданная игра — только чтение, вкладка скрыта — поиск остановлен, сессия закрыта`() = runTest {
        val running = Games.running()
        val prepared = Games.prepared(running)
        val handed = running.copy(
            game = prepared.senderChange.game!!,
            entries = running.entries + prepared.senderChange.entries,
            handovers = prepared.senderChange.handovers,
        )
        val s = setup(game = handed)
        s.viewModel.onAction(SendAction.Activate)
        assertEquals(SendPhase.AlreadyHandedOver, s.viewModel.uiState.value.phase)
        assertFalse(s.session.commands.any { it.startsWith("discover") })

        val t = setup()
        t.viewModel.onAction(SendAction.Activate)
        t.viewModel.onAction(SendAction.Deactivate)
        assertEquals("cancel", t.session.commands.last())
    }

    // region Ревью M5: передача не теряется

    @Test
    fun `повторный Activate в любой фазе не перезапускает поиск и не трогает передачу`() = runTest {
        val s = setup()
        s.viewModel.onAction(SendAction.Activate)
        fun discovers() = s.session.commands.count { it.startsWith("discover") }
        fun reactivate(expectedPhase: SendPhase) {
            s.viewModel.onAction(SendAction.Activate)
            assertEquals(1, discovers())
            assertEquals(expectedPhase, s.viewModel.uiState.value.phase)
        }
        reactivate(SendPhase.Searching)
        s.session.state.value = TransferState.Found(listOf(vlad, Endpoint("ep-2", "Егор")))
        reactivate(SendPhase.Choose(listOf(vlad, Endpoint("ep-2", "Егор"))))
        s.session.state.value = TransferState.Connecting(vlad)
        reactivate(SendPhase.Connecting("Влад"))
        s.session.state.value = TransferState.AwaitingCodeConfirm(vlad, "4719", confirmedLocally = true)
        reactivate(SendPhase.Code("Влад", "4719", waitingPeer = true))
        s.session.state.value = TransferState.Connected(vlad)
        s.session.state.value = TransferState.Sending(vlad, TransferProgress(1, 10))
        reactivate(SendPhase.Sending("Влад", 0.1f))
        s.session.state.value = TransferState.AwaitingAck(vlad)
        reactivate(SendPhase.AwaitingAccept("Влад"))
        s.session.state.value = TransferState.Failed(FailureReason.DISCONNECTED, packageSent = true)
        reactivate(SendPhase.Failed(SendFailure.CONNECTION, ackLost = true))
        assertTrue(s.session.commands.none { it == "cancel" })
    }

    @Test
    fun `скрыть вкладку посреди передачи нельзя остановить — сессия не отменяется`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        s.session.state.value = TransferState.Sending(vlad, TransferProgress(1, 10))
        s.viewModel.onAction(SendAction.Deactivate)
        assertTrue(s.session.commands.none { it == "cancel" })
        assertTrue(s.viewModel.uiState.value.modeLocked)
        s.session.state.value = TransferState.AwaitingAck(vlad)
        s.viewModel.onAction(SendAction.Deactivate)
        assertTrue(s.session.commands.none { it == "cancel" })
        assertEquals(SendPhase.AwaitingAccept("Влад"), s.viewModel.uiState.value.phase)
    }

    @Test
    fun `пакет ушёл — передача сохранена на диск, после ACK снята`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        val saved = s.pending.get(s.game.game.id)!!
        val sent = (
            FeltbookCodec.decode(
                s.session.sent.single(),
            ) as DecodeResult.Ok
            ).pkg as FeltbookPackage.HandoverPackage
        assertEquals(sent.handover.id.value.toString(), saved.handoverId)
        assertEquals("Влад", saved.toBanker)
        assertEquals(2_300L, saved.cashHandedOver)
        assertEquals(s.session.sent.single(), saved.packageText)

        s.session.state.value = TransferState.AwaitingAck(vlad)
        s.session.state.value = TransferState.Done(vlad)
        assertNull(s.pending.get(s.game.game.id))
    }

    @Test
    fun `ACK не пришёл — передача остаётся на диске, «Да, принял» на столе запишет ту же`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        s.session.state.value = TransferState.Failed(FailureReason.TIMEOUT, packageSent = true)
        val saved = s.pending.get(s.game.game.id)!!
        val change = saved.senderChange(s.repository.snapshot(s.game.game.id)!!)!!
        s.repository.commit(listOf(change))
        val after = s.repository.snapshot(s.game.game.id)!!
        assertEquals(GameStatus.HANDED_OVER, after.game.status)
        assertEquals(saved.handoverId, after.handovers.single().id.value.toString())
    }

    @Test
    fun `отказ получателя или обрыв до отправки — передача снята, акт снова можно менять`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        assertTrue(s.viewModel.uiState.value.incomeLocked)
        s.session.state.value = TransferState.Failed(FailureReason.DECLINED, packageSent = true)
        assertNull(s.pending.get(s.game.game.id))
        assertFalse(s.viewModel.uiState.value.incomeLocked)

        val t = setup()
        t.connectToVlad()
        t.viewModel.onAction(SendAction.ConfirmAndSend)
        t.session.state.value = TransferState.Connected(vlad)
        t.session.state.value = TransferState.Failed(FailureReason.CONNECTION_FAILED, packageSent = false)
        assertNull(t.pending.get(t.game.game.id))
        assertEquals(SendPhase.Failed(SendFailure.CONNECTION, ackLost = false), t.viewModel.uiState.value.phase)
    }

    @Test
    fun `после отправки «Оставить доход себе» не переключается, акт — как ушёл`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.KeepIncome(true))
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        s.viewModel.onAction(SendAction.KeepIncome(false))
        val state = s.viewModel.uiState.value
        assertTrue(state.incomeLocked)
        assertTrue(state.keepIncomeShown)
        assertEquals(2_100L, state.cashToHandOver)
        assertEquals(SentHandover("Влад", 2_100L, 200L, s.game.entries.size + 1), state.sent)
        assertEquals(s.game.entries.size + 1, state.payloadEntries)
    }

    @Test
    fun `пакет однажды дошёл — повторная попытка, оборвавшаяся до отправки, всё равно спрашивает «принял»`() = runTest {
        val s = setup()
        s.connectToVlad()
        s.viewModel.onAction(SendAction.ConfirmAndSend)
        s.session.state.value = TransferState.Connected(vlad)
        s.session.state.value = TransferState.AwaitingAck(vlad)
        s.session.state.value = TransferState.Failed(FailureReason.DISCONNECTED, packageSent = true)
        s.viewModel.onAction(SendAction.Retry)
        s.session.state.value = TransferState.Failed(FailureReason.TIMEOUT, packageSent = false)
        assertEquals(SendPhase.Failed(SendFailure.CONNECTION, ackLost = true), s.viewModel.uiState.value.phase)
        assertTrue(s.pending.get(s.game.game.id) != null)
    }

    @Test
    fun `«Назад» посреди неподтверждённой передачи спрашивает, иначе уходит сразу`() = runTest {
        val s = setup()
        s.viewModel.events.test {
            s.viewModel.onAction(SendAction.Activate)
            s.viewModel.onAction(SendAction.Back)
            assertEquals(SendEvent.Leave, awaitItem())

            s.connectToVlad()
            s.viewModel.onAction(SendAction.ConfirmAndSend)
            s.session.state.value = TransferState.Connected(vlad)
            s.session.state.value = TransferState.AwaitingAck(vlad)
            s.viewModel.onAction(SendAction.Back)
            assertEquals(SendDialog.Leave, s.viewModel.uiState.value.dialog)
            s.viewModel.onAction(SendAction.ConfirmLeave)
            assertEquals(SendEvent.Leave, awaitItem())
        }
    }

    @Test
    fun `отказ навсегда — «Открыть настройки», вернулись с доступом — поиск сам`() = runTest {
        val environment = FakeEnvironment(missing = listOf("android.permission.BLUETOOTH_SCAN"))
        val s = setup(environment = environment)
        s.viewModel.onAction(SendAction.Activate)
        s.viewModel.onAction(SendAction.PermissionsResult(allGranted = false, permanentlyDenied = true))
        assertEquals(SendPhase.Failed(SendFailure.PERMISSION_BLOCKED), s.viewModel.uiState.value.phase)
        s.viewModel.events.test {
            s.viewModel.onAction(SendAction.OpenSettings)
            assertEquals(SendEvent.OpenAppSettings, awaitItem())
        }
        s.viewModel.onAction(SendAction.Resumed)
        assertTrue(s.session.commands.none { it.startsWith("discover") })
        environment.missing = emptyList()
        s.viewModel.onAction(SendAction.Resumed)
        assertEquals(SendPhase.Searching, s.viewModel.uiState.value.phase)

        val approximate = setup(environment = FakeEnvironment(missing = listOf("x"), approximateOnly = true))
        approximate.viewModel.onAction(SendAction.Activate)
        approximate.viewModel.onAction(SendAction.PermissionsResult(allGranted = false))
        assertEquals(
            SendPhase.Failed(SendFailure.APPROXIMATE_LOCATION),
            approximate.viewModel.uiState.value.phase,
        )
    }

    @Test
    fun `«Передаётся» — все игроки игры, не только за столом`() = runTest {
        val s = setup()
        assertEquals(3, s.viewModel.uiState.value.players)
        assertEquals(s.game.entries.size, s.viewModel.uiState.value.payloadEntries)
    }

    // endregion
}
