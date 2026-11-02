package app.feltbook.feature.handover.testing

import app.feltbook.core.data.handover.PendingHandoverStore
import app.feltbook.core.domain.engine.BuyInPayment
import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.NewGameInput
import app.feltbook.core.domain.engine.PreparedHandover
import app.feltbook.core.domain.engine.SettlementChoice
import app.feltbook.core.domain.engine.Stamp
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.apply
import app.feltbook.core.domain.repository.CommitRejectedException
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import app.feltbook.core.transfer.TransferEnvironment
import app.feltbook.core.transfer.TransferSession
import app.feltbook.core.transfer.TransferState
import app.feltbook.core.transfer.file.PackageFileStore
import app.feltbook.core.transfer.file.SharedFile
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Сессия без радио: тест двигает [state] сам и смотрит, какие команды пришли. */
class FakeTransferSession : TransferSession {
    override val state = MutableStateFlow<TransferState>(TransferState.Idle)
    val commands = mutableListOf<String>()
    val sent = mutableListOf<String>()
    var closed = false
        private set

    override fun advertise(localName: String) {
        commands += "advertise:$localName"
        state.value = TransferState.Advertising(localName)
    }

    override fun discover(localName: String) {
        commands += "discover:$localName"
        state.value = TransferState.Discovering
    }

    override fun connect(endpointId: String) {
        commands += "connect:$endpointId"
    }

    override fun confirmCode() {
        commands += "confirmCode"
    }

    override fun rejectCode() {
        commands += "rejectCode"
    }

    override fun send(packageText: String) {
        commands += "send"
        sent += packageText
    }

    override fun acknowledge() {
        commands += "acknowledge"
    }

    override fun decline() {
        commands += "decline"
    }

    override fun cancel() {
        commands += "cancel"
        state.value = TransferState.Idle
    }

    override fun close() {
        closed = true
    }
}

/** Хранилище неподтверждённых передач во временном файле (без Android). */
fun pendingStore(): PendingHandoverStore =
    PendingHandoverStore(File.createTempFile("pending", ".json").apply { delete() }, Dispatchers.Unconfined)

class FakeEnvironment(
    var nearby: Boolean = true,
    var missing: List<String> = emptyList(),
    var locationOff: Boolean = false,
    var approximateOnly: Boolean = false,
) : TransferEnvironment {
    override fun isNearbyAvailable(): Boolean = nearby

    override fun missingPermissions(): List<String> = missing

    override fun isLocationServiceRequiredButOff(): Boolean = locationOff

    override fun isApproximateLocationOnly(): Boolean = approximateOnly
}

class FakeFileStore : PackageFileStore {
    val written = mutableListOf<Pair<String, String>>()

    override suspend fun writeForShare(text: String, baseName: String): SharedFile {
        written += baseName to text
        return SharedFile("content://test/$baseName.feltbook", "$baseName.feltbook")
    }
}

/** Репозиторий в памяти с проверками Room: `baseSeq`, одна активная игра. */
class FakeGameRepository(initial: List<GameSnapshot> = emptyList()) : GameRepository {
    val games = MutableStateFlow(initial.associateBy { it.game.id })
    var commits = 0
        private set

    fun snapshot(id: GameId): GameSnapshot? = games.value[id]

    override fun observeGame(id: GameId): Flow<GameSnapshot?> = games.map { it[id] }.distinctUntilChanged()

    override fun observeActiveGame(): Flow<GameSnapshot?> =
        games.map { map -> map.values.firstOrNull { it.game.status.isActive } }.distinctUntilChanged()

    override fun observeAllGames(): Flow<List<GameSnapshot>> = games.map { it.values.toList() }

    override suspend fun getGame(id: GameId): GameSnapshot? = games.value[id]

    override suspend fun getActiveGame(): GameSnapshot? = games.value.values.firstOrNull { it.game.status.isActive }

    override suspend fun commit(changes: List<GameChange>) {
        var map = games.value
        changes.forEach { change ->
            val existing = map[change.gameId]
            val next = if (existing == null) {
                val game = change.game
                    ?: throw CommitRejectedException(CommitRejectedException.Reason.UNKNOWN_GAME, "unknown")
                GameSnapshot(game, change.players, change.entries, change.handovers)
            } else {
                if (change.baseSeq != existing.lastSeq) {
                    throw CommitRejectedException(CommitRejectedException.Reason.STALE_SEQ, "stale")
                }
                existing.apply(change)
            }
            map = map + (change.gameId to next)
        }
        if (map.values.count { it.game.status.isActive } > 1) {
            throw CommitRejectedException(CommitRejectedException.Reason.SECOND_ACTIVE_GAME, "second active")
        }
        games.value = map
        commits++
    }

    override suspend fun deleteGames(ids: Set<GameId>) {
        games.update { it - ids }
    }

    override suspend fun deleteAllGames() {
        games.value = emptyMap()
    }
}

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}

class TestClock(var current: Instant = Instant.parse("2026-11-02T21:00:00Z")) : Clock {
    override fun now(): Instant = current
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** Игры по примеру 8.4 домена: банкир Андрей играет; Дима в долг, Егор оплатил — доход Андрея 200 ₽. */
object Games {
    private var now = Instant.parse("2026-11-02T17:00:00Z")

    fun stamp(): Stamp {
        now += 1.minutes
        return Stamp(now)
    }

    fun running(banker: String = "Андрей", rules: GameRules = GameRules.DEFAULT): GameSnapshot {
        val start = Commands.startGame(
            NewGameInput("Пятничный стол", "Игра", banker, bankerPlays = true, rules = rules),
            activeGame = null,
            stamp = stamp(),
        )
        var snap = GameSnapshot(start.game!!, start.players, start.entries, start.handovers)
        snap = snap.apply(Commands.buyIn(snap, snap.game.bankerPlayerId!!, 1_000, BuyInPayment.PAID, stamp()))
        snap = snap.apply(Commands.addPlayer(snap, "Дима", 1_000, BuyInPayment.DEBT, stamp()))
        snap = snap.apply(Commands.addPlayer(snap, "Егор", 1_000, BuyInPayment.PAID, stamp()))
        return snap
    }

    /** Игра закрыта: все вышли, рейк отдан — шаблон для сценария 2. */
    fun closed(rules: GameRules = GameRules.DEFAULT): GameSnapshot {
        var snap = running(rules = rules)
        snap.players.forEach { player ->
            snap = snap.apply(
                Commands.cashOut(
                    snap,
                    player.id,
                    chips = 1_000,
                    tip = null,
                    settlement = SettlementChoice.Full,
                    earlyExitConfirmed = true,
                    stamp = stamp(),
                ),
            )
        }
        snap = snap.apply(Commands.finish(snap))
        snap = snap.apply(
            Commands.close(snap, payRake = true, acknowledged = CloseConfirmation.entries.toSet(), stamp = stamp()),
        )
        return snap
    }

    fun prepared(
        snapshot: GameSnapshot = running(),
        to: String = "Влад",
        keepIncome: Boolean = false,
    ): PreparedHandover = Handovers.prepare(snapshot, to, keepIncome, stamp())

    fun packageText(prepared: PreparedHandover): String = FeltbookCodec.encode(
        FeltbookPackage.HandoverPackage(prepared.outgoing, prepared.handover),
        createdAt = now,
    )

    fun templateText(snapshot: GameSnapshot = closed()): String =
        FeltbookCodec.encode(FeltbookPackage.TemplatePackage(Handovers.template(snapshot)), createdAt = now)
}
