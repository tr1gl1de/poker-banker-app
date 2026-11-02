package app.feltbook.feature.handover.receive

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.feltbook.core.data.handover.ReceivedTemplateStore
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Journal
import app.feltbook.core.domain.engine.JournalItem
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.transfer.EnvelopeDto
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.transfer.Endpoint
import app.feltbook.core.transfer.FailureReason
import app.feltbook.core.transfer.TransferState
import app.feltbook.core.transfer.file.IncomingFile
import app.feltbook.core.transfer.file.IncomingPackageStore
import app.feltbook.feature.handover.HandoverRoute
import app.feltbook.feature.handover.testing.FakeEnvironment
import app.feltbook.feature.handover.testing.FakeGameRepository
import app.feltbook.feature.handover.testing.FakeSettingsRepository
import app.feltbook.feature.handover.testing.FakeTransferSession
import app.feltbook.feature.handover.testing.Games
import app.feltbook.feature.handover.testing.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** «Принять игру»: сводка, «Я играю как…», запись по «Принять», ACK; отказ при неверном хеше/версии. */
class ReceiveHandoverViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val andrey = Endpoint("ep-9", "Андрей")

    private class Setup(
        val repository: FakeGameRepository,
        val settings: FakeSettingsRepository,
        val session: FakeTransferSession,
        val incoming: IncomingPackageStore,
        val templates: ReceivedTemplateStore,
        val viewModel: ReceiveHandoverViewModel,
    )

    private fun setup(
        local: List<GameSnapshot> = emptyList(),
        settings: AppSettings = AppSettings(lastBankerName = "Влад"),
        fromFile: IncomingFile? = null,
        environment: FakeEnvironment = FakeEnvironment(),
    ): Setup {
        val repository = FakeGameRepository(local)
        val settingsRepository = FakeSettingsRepository(settings)
        val session = FakeTransferSession()
        val store = IncomingPackageStore().apply { fromFile?.let(::put) }
        val templates = ReceivedTemplateStore()
        val viewModel = ReceiveHandoverViewModel(
            savedStateHandle = SavedStateHandle(mapOf(HandoverRoute.ARG_FROM_FILE to (fromFile != null))),
            gameRepository = repository,
            settingsRepository = settingsRepository,
            session = session,
            environment = environment,
            incomingStore = store,
            templateStore = templates,
        )
        return Setup(repository, settingsRepository, session, store, templates, viewModel)
    }

    private fun Setup.receive(text: String) {
        viewModel.onAction(ReceiveAction.Activate)
        session.state.value = TransferState.AwaitingCodeConfirm(andrey, "4719", confirmedLocally = false)
        viewModel.onAction(ReceiveAction.ConfirmCode)
        session.state.value = TransferState.Connected(andrey)
        session.state.value = TransferState.AwaitingAccept(andrey, text)
    }

    @Test
    fun `реклама под своим именем, код, сводка «игроков, фишек, касса»`() = runTest {
        val s = setup()
        s.viewModel.onAction(ReceiveAction.Activate)
        assertEquals(listOf("advertise:Влад"), s.session.commands)
        assertEquals(ReceivePhase.Waiting("Влад"), s.viewModel.uiState.value.phase)

        s.session.state.value = TransferState.AwaitingCodeConfirm(andrey, "4719", confirmedLocally = false)
        assertEquals(ReceivePhase.Code("Андрей", "4719", waitingPeer = false), s.viewModel.uiState.value.phase)
        s.viewModel.onAction(ReceiveAction.ConfirmCode)
        assertTrue("confirmCode" in s.session.commands)

        val prepared = Games.prepared(keepIncome = true)
        s.session.state.value = TransferState.Connected(andrey)
        s.session.state.value = TransferState.AwaitingAccept(andrey, Games.packageText(prepared))
        val state = s.viewModel.uiState.value
        assertEquals(ReceivePhase.Review, state.phase)
        val summary = state.summary!!
        assertEquals("Пятничный стол", summary.title)
        assertEquals("Андрей", summary.fromBanker)
        assertEquals(3, summary.playersAtTable)
        assertEquals(3_000L, summary.chipsInPlay)
        assertEquals(2_100L, summary.cash)
        assertEquals(listOf("Андрей", "Дима", "Егор"), summary.players.map { it.name })
        assertNull(state.playAs)
    }

    @Test
    fun `«Принять» — игра записана с новым банкиром, ACK отправлен, журнал покажет «принята от Андрея»`() = runTest {
        val s = setup()
        val prepared = Games.prepared()
        s.receive(Games.packageText(prepared))
        assertFalse("acknowledge" in s.session.commands)
        val vladAsPlayer = s.viewModel.uiState.value.summary!!.players.first { it.name == "Егор" }
        s.viewModel.onAction(ReceiveAction.PlayAs(vladAsPlayer.id))

        s.viewModel.events.test {
            s.viewModel.onAction(ReceiveAction.Accept)
            assertEquals(ReceiveEvent.GameAccepted(prepared.outgoing.game.id.value.toString()), awaitItem())
        }
        val game = s.repository.snapshot(prepared.outgoing.game.id)!!
        assertEquals(GameStatus.RUNNING, game.game.status)
        assertEquals("Влад", game.game.currentBankerName)
        assertEquals(vladAsPlayer.id, game.game.bankerPlayerId!!.value.toString())
        assertEquals(prepared.outgoing.entries, game.entries)
        assertEquals(listOf(prepared.handover), game.handovers)
        assertTrue("acknowledge" in s.session.commands)
        assertEquals(ReceivePhase.Done, s.viewModel.uiState.value.phase)
        val handoverRow = Journal.items(game).filterIsInstance<JournalItem.HandoverItem>().single()
        assertEquals("Андрей", handoverRow.handover.fromBanker)
    }

    @Test
    fun `неверный хеш — CORRUPTED и отказ отправителю, новая версия — «обновите приложение»`() = runTest {
        val json = Json {
            encodeDefaults = true
            explicitNulls = true
        }
        val text = Games.packageText(Games.prepared())
        val envelope = json.decodeFromString(EnvelopeDto.serializer(), text)

        val wrongHash = setup()
        wrongHash.receive(json.encodeToString(EnvelopeDto.serializer(), envelope.copy(sha256 = "0".repeat(64))))
        assertEquals(ReceivePhase.Failed(ReceiveFailure.CORRUPTED), wrongHash.viewModel.uiState.value.phase)
        assertTrue("decline" in wrongHash.session.commands)
        assertTrue(wrongHash.repository.games.value.isEmpty())

        val newer = setup()
        newer.receive(json.encodeToString(EnvelopeDto.serializer(), envelope.copy(schemaVersion = 2)))
        assertEquals(ReceivePhase.Failed(ReceiveFailure.NEWER_VERSION), newer.viewModel.uiState.value.phase)

        val garbage = setup()
        garbage.receive("{}")
        assertEquals(ReceivePhase.Failed(ReceiveFailure.NOT_FELTBOOK), garbage.viewModel.uiState.value.phase)
    }

    @Test
    fun `на телефоне уже идёт другая игра — ошибка, ничего не записано, ACK не уходит`() = runTest {
        val mine = Games.running(banker = "Влад")
        val s = setup(local = listOf(mine))
        s.receive(Games.packageText(Games.prepared()))
        s.viewModel.onAction(ReceiveAction.Accept)
        assertEquals(AcceptError.ANOTHER_GAME_ACTIVE, s.viewModel.uiState.value.error)
        assertEquals(ReceivePhase.Review, s.viewModel.uiState.value.phase)
        assertEquals(1, s.repository.games.value.size)
        assertFalse("acknowledge" in s.session.commands)
    }

    @Test
    fun `«Не принимать» — отказ отправителю и снова ожидание`() = runTest {
        val s = setup()
        s.receive(Games.packageText(Games.prepared()))
        s.viewModel.onAction(ReceiveAction.Decline)
        assertTrue("decline" in s.session.commands)
        assertEquals(ReceivePhase.Waiting("Влад"), s.viewModel.uiState.value.phase)
        assertNull(s.viewModel.uiState.value.summary)
    }

    @Test
    fun `имени нет — сначала «Как вас зовут», имя сохраняется и уходит в рекламу`() = runTest {
        val s = setup(settings = AppSettings(lastBankerName = ""))
        s.viewModel.onAction(ReceiveAction.Activate)
        assertEquals(ReceivePhase.NeedsName(""), s.viewModel.uiState.value.phase)
        s.viewModel.onAction(ReceiveAction.NameChanged("  "))
        s.viewModel.onAction(ReceiveAction.NameConfirmed)
        assertEquals(ReceivePhase.NeedsName("  ", error = true), s.viewModel.uiState.value.phase)
        s.viewModel.onAction(ReceiveAction.NameChanged(" Влад  Петров "))
        s.viewModel.onAction(ReceiveAction.NameConfirmed)
        assertEquals("Влад Петров", s.settings.state.value.lastBankerName)
        assertEquals("advertise:Влад Петров", s.session.commands.last())
    }

    @Test
    fun `нет Play Services у получателя — ошибка без рекламы, обрыв до пакета — повтор`() = runTest {
        val s = setup(environment = FakeEnvironment(nearby = false))
        s.viewModel.onAction(ReceiveAction.Activate)
        assertEquals(ReceivePhase.Failed(ReceiveFailure.NO_GMS), s.viewModel.uiState.value.phase)
        assertTrue(s.session.commands.isEmpty())

        val t = setup()
        t.viewModel.onAction(ReceiveAction.Activate)
        t.session.state.value = TransferState.Failed(FailureReason.DISCONNECTED)
        assertEquals(ReceivePhase.Failed(ReceiveFailure.CONNECTION), t.viewModel.uiState.value.phase)
        t.viewModel.onAction(ReceiveAction.Retry)
        assertEquals(2, t.session.commands.count { it == "advertise:Влад" })
    }

    @Test
    fun `открытый файл — сразу сводка, «Принять» без ACK, файл убран из хранилища`() = runTest {
        val prepared = Games.prepared()
        val text = Games.packageText(prepared)
        val file = IncomingFile("Пятничный стол.feltbook", FeltbookCodec.decode(text))
        val s = setup(fromFile = file)
        assertEquals(ReceivePhase.Review, s.viewModel.uiState.value.phase)
        assertEquals("Пятничный стол.feltbook", s.viewModel.uiState.value.summary!!.fileName)
        s.viewModel.onAction(ReceiveAction.Activate)
        assertTrue(s.session.commands.isEmpty())

        s.viewModel.onAction(ReceiveAction.Accept)
        assertEquals("Влад", s.repository.snapshot(prepared.outgoing.game.id)!!.game.currentBankerName)
        assertFalse("acknowledge" in s.session.commands)
        assertNull(s.incoming.pending.value)
    }

    @Test
    fun `повторный приём того же файла — без новых записей (Identical)`() = runTest {
        val prepared = Games.prepared()
        val text = Games.packageText(prepared)
        val first = setup(fromFile = IncomingFile(null, FeltbookCodec.decode(text)))
        first.viewModel.onAction(ReceiveAction.Accept)
        val stored = first.repository.games.value.values.toList()

        val again = setup(local = stored, fromFile = IncomingFile(null, FeltbookCodec.decode(text)))
        again.viewModel.onAction(ReceiveAction.Accept)
        assertNull(again.viewModel.uiState.value.error)
        assertEquals(0, again.repository.commits)
        assertEquals(stored.single(), again.repository.games.value.values.single())
    }

    @Test
    fun `шаблон после расчёта — правила и имена только для следующей «Новой игры», настройки не меняются`() = runTest {
        val rules = GameRules(rakeMode = RakeMode.FIXED, rakeValue = 50)
        val s = setup()
        s.receive(Games.templateText(Games.closed(rules)))
        val summary = s.viewModel.uiState.value.summary!!
        assertTrue(summary.isTemplate)
        assertEquals(3, summary.templateNames)

        s.viewModel.events.test {
            s.viewModel.onAction(ReceiveAction.Accept)
            assertEquals(ReceiveEvent.TemplateAccepted, awaitItem())
        }
        assertEquals(GameRules.DEFAULT, s.settings.state.value.rulesForNewGame)
        assertEquals(rules, s.templates.template.value!!.rules)
        assertEquals(listOf("Андрей", "Дима", "Егор"), s.templates.template.value!!.playerNames)
        assertTrue("acknowledge" in s.session.commands)
        assertTrue(s.repository.games.value.isEmpty())
    }

    // region Ревью M5

    @Test
    fun `повторный приём уже принятой передачи — без записи, ACK уходит (потерянный ACK)`() = runTest {
        val prepared = Games.prepared()
        val text = Games.packageText(prepared)
        val first = setup(fromFile = IncomingFile(null, FeltbookCodec.decode(text)))
        first.viewModel.onAction(ReceiveAction.Accept)
        // Здесь банкир уже сделал запись — журнал ушёл вперёд от пакета.
        val accepted = first.repository.games.value.values.single()
        val egor = accepted.players.first { it.name == "Егор" }
        val moved = accepted.apply(Commands.buyIn(accepted, egor.id, 1_000, BuyInPayment.PAID, Games.stamp()))

        val again = setup(local = listOf(moved))
        again.receive(text)
        again.viewModel.events.test {
            again.viewModel.onAction(ReceiveAction.Accept)
            assertEquals(ReceiveEvent.GameAccepted(prepared.outgoing.game.id.value.toString()), awaitItem())
        }
        assertNull(again.viewModel.uiState.value.error)
        assertEquals(0, again.repository.commits)
        assertTrue("acknowledge" in again.session.commands)
    }

    @Test
    fun `связь оборвалась на сводке — предупреждение, принять можно, ACK не ждём`() = runTest {
        val s = setup()
        val text = Games.packageText(Games.prepared())
        s.receive(text)
        assertFalse(s.viewModel.uiState.value.senderDisconnected)
        s.session.state.value = TransferState.AwaitingAccept(andrey, text, peerConnected = false)
        assertTrue(s.viewModel.uiState.value.senderDisconnected)
        assertEquals(ReceivePhase.Review, s.viewModel.uiState.value.phase)
        s.viewModel.onAction(ReceiveAction.Accept)
        assertEquals(ReceivePhase.Done, s.viewModel.uiState.value.phase)
    }

    @Test
    fun `повторный Activate и скрытие вкладки посреди приёма ничего не сбрасывают`() = runTest {
        val s = setup()
        s.viewModel.onAction(ReceiveAction.Activate)
        s.session.state.value = TransferState.AwaitingCodeConfirm(andrey, "4719", confirmedLocally = false)
        s.viewModel.onAction(ReceiveAction.Activate)
        s.viewModel.onAction(ReceiveAction.Deactivate)
        assertEquals(ReceivePhase.Code("Андрей", "4719", waitingPeer = false), s.viewModel.uiState.value.phase)
        assertEquals(1, s.session.commands.count { it.startsWith("advertise") })
        assertFalse("cancel" in s.session.commands)

        s.session.state.value = TransferState.Connected(andrey)
        s.session.state.value = TransferState.AwaitingAccept(andrey, Games.packageText(Games.prepared()))
        s.viewModel.onAction(ReceiveAction.Activate)
        s.viewModel.onAction(ReceiveAction.Deactivate)
        assertEquals(ReceivePhase.Review, s.viewModel.uiState.value.phase)
        assertEquals(1, s.session.commands.count { it.startsWith("advertise") })
        assertFalse("cancel" in s.session.commands)

        val waiting = setup()
        waiting.viewModel.onAction(ReceiveAction.Activate)
        waiting.viewModel.onAction(ReceiveAction.Deactivate)
        assertEquals("cancel", waiting.session.commands.last())
    }

    @Test
    fun `разрешения — отказ навсегда и приблизительная геолокация`() = runTest {
        val blocked = setup(environment = FakeEnvironment(missing = listOf("x")))
        blocked.viewModel.onAction(ReceiveAction.Activate)
        blocked.viewModel.onAction(ReceiveAction.PermissionsResult(allGranted = false, permanentlyDenied = true))
        assertEquals(ReceivePhase.Failed(ReceiveFailure.PERMISSION_BLOCKED), blocked.viewModel.uiState.value.phase)

        val approximate = setup(environment = FakeEnvironment(missing = listOf("x"), approximateOnly = true))
        approximate.viewModel.onAction(ReceiveAction.Activate)
        approximate.viewModel.onAction(ReceiveAction.PermissionsResult(allGranted = false))
        assertEquals(
            ReceivePhase.Failed(ReceiveFailure.APPROXIMATE_LOCATION),
            approximate.viewModel.uiState.value.phase,
        )
    }

    // endregion
}
