package app.feltbook.core.domain.repository

import app.feltbook.core.domain.engine.ImportConflictReason
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.transfer.DecodeResult
import kotlinx.coroutines.flow.Flow

/**
 * Агрегат «игра»: строка игры, игроки, журнал, акты. Отдельного LedgerRepository нет (ADR 0006):
 * журнал пишется только через [commit], который умеет лишь INSERT записей.
 */
interface GameRepository {
    fun observeGame(id: GameId): Flow<GameSnapshot?>

    /** Игра в статусе RUNNING/SETTLING (не больше одной). */
    fun observeActiveGame(): Flow<GameSnapshot?>

    /** Все игры, новые сверху — история, статистика, открытые долги (всё считает движок). */
    fun observeAllGames(): Flow<List<GameSnapshot>>

    suspend fun getGame(id: GameId): GameSnapshot?

    suspend fun getActiveGame(): GameSnapshot?

    /**
     * Все изменения — одной транзакцией: upsert игр/игроков, INSERT записей и актов, upsert имён новых и
     *   переименованных
     * игроков в справочник (`firstSeenAt` = `joinedAt` при первом появлении). Проверяет внутри транзакции:
     * `lastSeq` игры в базе = `GameChange.baseSeq`, `seq` продолжает журнал (уникальный индекс `(gameId, seq)`), id
     * игроков/записей/актов не заняты другой игрой, активных игр после изменения ≤ 1, время — целые миллисекунды.
     * Нарушение — [CommitRejectedException] (время — IllegalArgumentException) и откат всего.
     */
    suspend fun commit(changes: List<GameChange>)

    /** Удаление игр каскадом (игроки, журнал, акты) одной транзакцией. Справочник имён не трогает. */
    suspend fun deleteGames(ids: Set<GameId>)

    suspend fun deleteAllGames()
}

suspend fun GameRepository.commit(change: GameChange): Unit = commit(listOf(change))

interface PlayerNameRepository {
    fun observeAll(): Flow<List<PlayerName>>

    /** «Объединить игроков»: `from.mergedInto = into`. Проверку делает `PlayerNames.canMerge`. */
    suspend fun merge(from: String, into: String)

    /** Отменить объединение: `mergedInto = null`. */
    suspend fun unmerge(name: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Резервная копия всей базы (ТЗ §4 п. 5). Формат — `FeltbookCodec`, вид BACKUP. */
interface BackupRepository {
    /** Текст файла `.feltbook`: все игры + справочник имён. */
    suspend fun exportAll(): String

    /**
     * Импорт по `Imports.plan` для каждой игры одной транзакцией; ничего локального не затирает (конфликты — в отчёте).
     * Не бросает: нечитаемый файл или сбой базы — [ImportReport.failure], тогда не импортировано ничего.
     */
    suspend fun importAll(text: String): ImportReport
}

/** Почему файл не импортирован целиком. */
sealed interface ImportFailure {
    /** Файл не разобран: не Feltbook, новая версия, повреждён, не резервная копия (`DecodeResult.WrongKind`). */
    data class Unreadable(val reason: DecodeResult.Failure) : ImportFailure

    /** Сбой записи в базу; транзакция откачена. */
    data class StorageFailed(val cause: Throwable) : ImportFailure
}

/**
 * @property conflicts игры, которые пропущены. `LOCAL_NEWER` — не ошибка: «пропущено, на телефоне новее».
 */
data class ImportReport(
    val failure: ImportFailure?,
    val added: Int,
    val extended: Int,
    val identical: Int,
    val conflicts: List<Pair<GameId, ImportConflictReason>>,
    val playerNamesAdded: Int,
)
