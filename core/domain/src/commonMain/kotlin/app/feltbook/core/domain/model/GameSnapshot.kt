package app.feltbook.core.domain.model

import kotlin.time.Instant

/**
 * Справочник имён для подсказок, статистики и открытых долгов (ТЗ §8).
 * Имена сопоставляются по точному совпадению после `PlayerNames.normalize`.
 * @property mergedInto «объединить двух игроков вручную»: это имя считается именем `mergedInto` (цепочки разрешены,
 *   циклы — нет).
 */
data class PlayerName(val name: String, val firstSeenAt: Instant, val mergedInto: String? = null)

/**
 * Акт передачи кассы (ТЗ §6, §8). В журнале экрана показывается строкой «Игра принята от <fromBanker>».
 * @property cashHandedOver сколько наличных старый банкир физически отдал новому.
 * @property incomeTaken сколько дохода старый банкир оставил себе (равно сумме записи `INCOME_TAKEN` этой передачи,
 *   0 — не оставлял).
 * @property ledgerSeq последний `seq` журнала на момент передачи — акт воспроизводим по журналу.
 */
data class Handover(
    val id: HandoverId,
    val gameId: GameId,
    val fromBanker: String,
    val toBanker: String,
    val cashHandedOver: Long,
    val incomeTaken: Long,
    val ledgerSeq: Long,
    val at: Instant,
)

/**
 * Игра целиком — то, что читают движок, экран и пакет передачи.
 * @property entries журнал строго по возрастанию `seq` (репозиторий гарантирует порядок).
 * @property handovers по возрастанию `at`.
 */
data class GameSnapshot(
    val game: Game,
    val players: List<Player>,
    val entries: List<LedgerEntry>,
    val handovers: List<Handover>,
) {
    /** `seq` следующей записи. */
    val nextSeq: Long get() = (entries.lastOrNull()?.seq ?: 0L) + 1L

    fun player(id: PlayerId): Player? = players.firstOrNull { it.id == id }
}

/**
 * Единица записи: всё, что меняет одно действие банкира, — пишется одной транзакцией (`GameRepository.commit`).
 * Журнал и акты — только INSERT; игра и игроки — upsert.
 *
 * @property game новая версия строки игры (создание, статус, банкир, closedAt) или null — не менять.
 * @property players новые или переименованные игроки этой игры (upsert по id).
 * @property entries новые записи журнала, `seq` продолжает журнал без пропусков.
 * @property handovers новые акты передачи.
 */
data class GameChange(
    val gameId: GameId,
    val game: Game? = null,
    val players: List<Player> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val handovers: List<Handover> = emptyList(),
)

/**
 * Применить изменение к снимку в памяти (тесты, цепочки команд, подготовка пакета передачи).
 * Бросает `IllegalArgumentException`, если `change.gameId` ≠ id снимка или `seq` не продолжает журнал.
 * Для `game == null` и пустых списков возвращает равный снимок.
 */
fun GameSnapshot.apply(change: GameChange): GameSnapshot {
    require(change.gameId == game.id) { "change for ${change.gameId} applied to ${game.id}" }
    require(change.game == null || change.game.id == game.id) { "game row of another game" }
    val first = nextSeq
    change.entries.forEachIndexed { index, entry ->
        require(entry.seq == first + index) { "seq ${entry.seq} does not continue the ledger at ${first + index}" }
    }
    val upserted = players.map { old -> change.players.lastOrNull { it.id == old.id } ?: old }
    val added = change.players.filter { new -> players.none { it.id == new.id } }.distinctBy { it.id }
    return GameSnapshot(
        game = change.game ?: game,
        players = upserted + added,
        entries = entries + change.entries,
        handovers = (handovers + change.handovers).sortedBy { it.at },
    )
}
