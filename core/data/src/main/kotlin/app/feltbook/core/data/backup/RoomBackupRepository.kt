package app.feltbook.core.data.backup

import androidx.room.deferredTransaction
import androidx.room.useReaderConnection
import androidx.room.withTransaction
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.PlayerNameEntity
import app.feltbook.core.data.db.toDomain
import app.feltbook.core.data.di.DefaultDispatcher
import app.feltbook.core.data.repository.GameStore
import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.engine.ImportPlan
import app.feltbook.core.domain.engine.Imports
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.repository.BackupRepository
import app.feltbook.core.domain.repository.ImportFailure
import app.feltbook.core.domain.repository.ImportReport
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Резервная копия всей базы: `FeltbookCodec`, вид BACKUP (ADR 0007). Импорт — одна транзакция на весь файл,
 * решение по каждой игре — `Imports.plan`, поэтому негодная игра (конфликт, занятые другой игрой id) только
 * пропускается и не срывает остальные. Локальный журнал не меняется и не удаляется никогда.
 * Справочник имён: добавляются только новые имена (нормализованные); `mergedInto` — только если цель есть и
 * объединение допустимо (`PlayerNames.canMerge`).
 */
internal class RoomBackupRepository @Inject constructor(
    private val db: FeltbookDatabase,
    private val store: GameStore,
    private val clock: Clock,
    @param:DefaultDispatcher private val cpu: CoroutineDispatcher,
) : BackupRepository {
    override suspend fun exportAll(): String {
        val (games, names) = db.useReaderConnection { connection ->
            connection.deferredTransaction { store.loadAll() to db.playerNames().all().map { it.toDomain() } }
        }
        val now = clock.now()
        return withContext(cpu) {
            FeltbookCodec.encode(FeltbookPackage.BackupPackage(now, games, names), now)
        }
    }

    override suspend fun importAll(text: String): ImportReport {
        val decoded = withContext(cpu) { FeltbookCodec.decode(text) }
        val pkg = (decoded as? DecodeResult.Ok)?.pkg
        return when {
            decoded is DecodeResult.Failure -> failed(ImportFailure.Unreadable(decoded))
            pkg is FeltbookPackage.BackupPackage -> importSafely(pkg)
            else -> failed(ImportFailure.Unreadable(wrongKind(pkg)))
        }
    }

    @Suppress("TooGenericExceptionCaught") // Любой сбой базы — в отчёт: транзакция откачена, исключение не выходит.
    private suspend fun importSafely(pkg: FeltbookPackage.BackupPackage): ImportReport = try {
        db.withTransaction { importBackup(pkg) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        failed(ImportFailure.StorageFailed(error))
    }

    private suspend fun importBackup(pkg: FeltbookPackage.BackupPackage): ImportReport {
        val dao = db.playerNames()
        val namesBefore = dao.count()
        // Сначала новые имена из файла (firstSeenAt из файла), затем игры, затем объединения — когда все цели уже есть.
        val wanted = normalized(pkg.playerNames)
        val added = wanted.filter { dao.insertIfAbsent(PlayerNameEntity(it.name, it.firstSeenAt, null)) != -1L }
        var inserted = 0
        var extended = 0
        var identical = 0
        val conflicts = mutableListOf<Pair<GameId, ImportConflictReason>>()
        // Файл — новые сверху; вставляем со старых, чтобы порядок игр с равным createdAt совпал с исходным.
        pkg.games.asReversed().forEach { incoming ->
            val id = incoming.game.id
            val plan = Imports.plan(incoming, store.load(id), store.activeGameId(), store.foreignIds(incoming))
            when (plan) {
                is ImportPlan.Insert -> {
                    store.write(listOf(plan.change))
                    inserted++
                }

                is ImportPlan.Extend -> {
                    store.write(listOf(plan.change))
                    extended++
                }

                ImportPlan.Identical -> identical++

                is ImportPlan.Conflict -> conflicts += id to plan.reason
            }
        }
        applyMerges(added)
        return ImportReport(
            failure = null,
            added = inserted,
            extended = extended,
            identical = identical,
            conflicts = conflicts.asReversed(),
            playerNamesAdded = dao.count() - namesBefore,
        )
    }

    /** Имена из файла после `PlayerNames.normalize`, без пустых и дублей (раннее `firstSeenAt` побеждает). */
    private fun normalized(names: List<PlayerName>): List<PlayerName> = names
        .map { it.copy(name = PlayerNames.normalize(it.name), mergedInto = it.mergedInto?.let(PlayerNames::normalize)) }
        .filter { it.name.isNotEmpty() }
        .groupBy { it.name }
        .map { (_, same) -> same.minBy { it.firstSeenAt } }

    private suspend fun applyMerges(added: List<PlayerName>) {
        val dao = db.playerNames()
        var directory = dao.all().map { it.toDomain() }
        added.forEach { name ->
            val target = name.mergedInto ?: return@forEach
            if (PlayerNames.canMerge(name.name, target, directory)) {
                dao.setMergedInto(name.name, target)
                directory = directory.map { if (it.name == name.name) it.copy(mergedInto = target) else it }
            }
        }
    }

    private fun wrongKind(pkg: FeltbookPackage?): DecodeResult.Failure = if (pkg == null) {
        DecodeResult.Malformed("empty package")
    } else {
        DecodeResult.WrongKind(expected = FeltbookCodec.KIND_BACKUP, found = FeltbookCodec.kindOf(pkg))
    }

    private fun failed(failure: ImportFailure): ImportReport = ImportReport(
        failure = failure,
        added = 0,
        extended = 0,
        identical = 0,
        conflicts = emptyList(),
        playerNamesAdded = 0,
    )
}
