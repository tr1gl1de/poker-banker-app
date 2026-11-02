package app.feltbook.feature.settings

import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.repository.BackupRepository
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.ImportReport
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.feature.settings.backup.BackupFiles
import app.feltbook.feature.settings.common.SettingsZone
import java.io.IOException
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)

    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        state.update(transform)
    }
}

/** Игры для «Удалить все игры» и счётчика «Копия сохранена: N игр». */
class FakeGameRepository(initial: List<GameSnapshot> = emptyList()) : GameRepository {
    val games = MutableStateFlow(initial)
    var deleteAllCalls = 0
        private set

    override fun observeGame(id: GameId): Flow<GameSnapshot?> =
        games.map { list -> list.firstOrNull { it.game.id == id } }

    override fun observeActiveGame(): Flow<GameSnapshot?> =
        games.map { list -> list.firstOrNull { it.game.status.isActive } }

    override fun observeAllGames(): Flow<List<GameSnapshot>> = games

    override suspend fun getGame(id: GameId): GameSnapshot? = games.value.firstOrNull { it.game.id == id }

    override suspend fun getActiveGame(): GameSnapshot? = games.value.firstOrNull { it.game.status.isActive }

    override suspend fun commit(changes: List<GameChange>): Unit = error("not used by settings")

    override suspend fun deleteGames(ids: Set<GameId>) {
        games.update { list -> list.filterNot { it.game.id in ids } }
    }

    override suspend fun deleteAllGames() {
        deleteAllCalls++
        games.value = emptyList()
    }
}

class FakeBackupRepository(
    var exportText: String = "{\"format\":\"feltbook\"}",
    var report: ImportReport = EMPTY_REPORT,
) : BackupRepository {
    val imported = mutableListOf<String>()

    override suspend fun exportAll(): String = exportText

    override suspend fun importAll(text: String): ImportReport {
        imported += text
        return report
    }

    companion object {
        val EMPTY_REPORT = ImportReport(
            failure = null,
            added = 0,
            extended = 0,
            identical = 0,
            conflicts = emptyList(),
            playerNamesAdded = 0,
        )
    }
}

class FakeBackupFiles : BackupFiles {
    val written = mutableMapOf<String, String>()
    val readable = mutableMapOf<String, String>()
    var failWrites = false

    override suspend fun write(uri: String, text: String) {
        if (failWrites) throw IOException("disk full")
        written[uri] = text
    }

    override suspend fun read(uri: String): String = readable[uri] ?: throw IOException("no such document")
}

class TestClock(var current: Instant = Instant.parse("2026-11-02T17:05:00Z")) : Clock {
    override fun now(): Instant = current
}

object UtcZone : SettingsZone() {
    override fun zone(): ZoneId = ZoneOffset.UTC
}
