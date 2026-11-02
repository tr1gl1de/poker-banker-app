package app.feltbook.core.data.backup

import androidx.room.withTransaction
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.toDomain
import app.feltbook.core.data.db.toEntity
import app.feltbook.core.data.di.IoDispatcher
import app.feltbook.core.data.repository.GameStore
import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.engine.ImportPlan
import app.feltbook.core.domain.engine.Imports
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.repository.BackupRepository
import app.feltbook.core.domain.repository.ImportReport
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import javax.inject.Inject
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Резервная копия всей базы: `FeltbookCodec`, вид BACKUP (ADR 0007). Импорт — одна транзакция на весь файл,
 * решение по каждой игре — `Imports.plan`: локальный журнал не меняется и не удаляется никогда; `LOCAL_NEWER` и
 * прочие конфликты — в `ImportReport.conflicts` (игра пропущена). Справочник имён: только новые имена.
 */
internal class RoomBackupRepository @Inject constructor(
    private val db: FeltbookDatabase,
    private val store: GameStore,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : BackupRepository {
    override suspend fun exportAll(): String {
        val (games, names) = db.withTransaction { store.loadAll() to db.playerNames().all().map { it.toDomain() } }
        val now = clock.now()
        return withContext(io) {
            FeltbookCodec.encode(FeltbookPackage.BackupPackage(now, games, names), now)
        }
    }

    override suspend fun importAll(text: String): ImportReport {
        val decoded = withContext(io) { FeltbookCodec.decode(text) }
        val pkg = (decoded as? DecodeResult.Ok)?.pkg
        return when {
            decoded is DecodeResult.Failure -> failed(decoded)
            pkg is FeltbookPackage.BackupPackage -> db.withTransaction { importBackup(pkg) }
            else -> failed(DecodeResult.Malformed("not a backup: ${pkg?.let { it::class.simpleName }}"))
        }
    }

    private suspend fun importBackup(pkg: FeltbookPackage.BackupPackage): ImportReport {
        val names = db.playerNames()
        val namesBefore = names.count()
        // Сначала справочник из файла (с mergedInto и firstSeenAt), затем игры — их игроки добавят недостающие имена.
        pkg.playerNames.forEach { names.insertIfAbsent(it.toEntity()) }
        var added = 0
        var extended = 0
        var identical = 0
        val conflicts = mutableListOf<Pair<GameId, ImportConflictReason>>()
        // Файл — новые сверху; вставляем со старых, чтобы порядок игр с равным createdAt совпал с исходным.
        pkg.games.asReversed().forEach { incoming ->
            val id = incoming.game.id
            when (val plan = Imports.plan(incoming, store.load(id), store.activeGameId())) {
                is ImportPlan.Insert -> {
                    store.write(listOf(plan.change))
                    added++
                }

                is ImportPlan.Extend -> {
                    store.write(listOf(plan.change))
                    extended++
                }

                ImportPlan.Identical -> identical++

                is ImportPlan.Conflict -> conflicts += id to plan.reason
            }
        }
        return ImportReport(
            failure = null,
            added = added,
            extended = extended,
            identical = identical,
            conflicts = conflicts.asReversed(),
            playerNamesAdded = names.count() - namesBefore,
        )
    }

    private fun failed(failure: DecodeResult.Failure): ImportReport = ImportReport(
        failure = failure,
        added = 0,
        extended = 0,
        identical = 0,
        conflicts = emptyList(),
        playerNamesAdded = 0,
    )
}
