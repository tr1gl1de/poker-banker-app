package app.feltbook.core.data.repository

import androidx.room.withTransaction
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.toDomain
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.repository.PlayerNameRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Справочник имён. Имена пишет `GameRepository.commit` (новые игроки), здесь — только объединение. */
internal class RoomPlayerNameRepository @Inject constructor(private val db: FeltbookDatabase) : PlayerNameRepository {
    override fun observeAll(): Flow<List<PlayerName>> =
        db.playerNames().observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Повторная проверка `PlayerNames.canMerge` внутри транзакции: цикл или неизвестное имя —
     * IllegalArgumentException.
     */
    override suspend fun merge(from: String, into: String) {
        db.withTransaction {
            val directory = db.playerNames().all().map { it.toDomain() }
            require(PlayerNames.canMerge(from, into, directory)) { "names cannot be merged" }
            db.playerNames().setMergedInto(PlayerNames.normalize(from), PlayerNames.normalize(into))
        }
    }

    override suspend fun unmerge(name: String) {
        db.playerNames().setMergedInto(PlayerNames.normalize(name), null)
    }
}
