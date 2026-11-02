package app.feltbook.core.domain.model

import kotlin.time.Instant

/** Состояние игры (ТЗ §4: создана → идёт → расчёт → закрыта; из «идёт»/«расчёт» — передана). */
enum class GameStatus {
    /** Есть в ТЗ §8; в v1 не создаётся: «Начать игру» сразу даёт [RUNNING]. Импорт принимает и трактует как RUNNING. */
    CREATED,

    /** Идёт: входы, докупы, выходы. */
    RUNNING,

    /** Нажато «Завершить игру»: новые покупки запрещены, идёт вывод оставшихся и итог. Можно вернуть в RUNNING. */
    SETTLING,

    /** Закрыта, в истории. Допускаются только погашение/прощение долгов и их отмена (без «изменено после закрытия»). */
    CLOSED,

    /**
     * Передана другому телефону посреди игры — только чтение. Её долги здесь не считаются открытыми (ушли новому
     * банкиру).
     */
    HANDED_OVER,
    ;

    /** RUNNING или SETTLING. Активной может быть не больше одной игры (ТЗ §4). */
    val isActive: Boolean get() = this == RUNNING || this == SETTLING
}

/**
 * Игра. Строка игры — единственное изменяемое (upsert) в агрегате вместе с именами игроков;
 * деньги — только в журнале.
 *
 * @property currentBankerName имя банкира сейчас; копируется в `LedgerEntry.bankerName` каждой новой записи.
 * @property bankerPlayerId игрок за этим столом, который сейчас банкир (`null` — банкир не играет).
 *   Заменяет `bankerIsPlayer` + `Player.isBanker` из ТЗ §8 (ADR 0005): один источник правды, переживает передачу
 *   комнаты.
 * @property editedAfterClose игру переоткрывали после закрытия — в истории пометка «изменено после закрытия».
 * @property schemaVersion версия семантики журнала и правил, с которой записана игра ([LEDGER_SCHEMA_VERSION]).
 */
data class Game(
    val id: GameId,
    val title: String,
    val createdAt: Instant,
    val closedAt: Instant?,
    val status: GameStatus,
    val currentBankerName: String,
    val bankerPlayerId: PlayerId?,
    val rules: GameRules,
    val editedAfterClose: Boolean = false,
    val schemaVersion: Int = LEDGER_SCHEMA_VERSION,
) {
    val bankerIsPlayer: Boolean get() = bankerPlayerId != null

    companion object {
        const val LEDGER_SCHEMA_VERSION: Int = 1
        const val MAX_TITLE_LENGTH: Int = 60
    }
}

/**
 * Игрок в конкретной игре. Хранится только идентичность; статус, первый закуп, время расчёта и все суммы —
 * производные журнала (`PlayerState`, ADR 0005). Имя меняется upsert'ом («Переименовать»).
 */
data class Player(val id: PlayerId, val gameId: GameId, val name: String, val joinedAt: Instant)

/** Производный статус игрока (ТЗ §8). Вернувшийся игрок снова [PLAYING]. */
enum class PlayerStatus {
    /** За столом: после последнего действующего `CASH_OUT` есть действующий `BUY_IN`, либо выхода ещё не было. */
    PLAYING,

    /** Вышел и `outstanding ≥ 0` (квиты или банкир ещё должен ему). */
    CASHED_OUT,

    /** Вышел и `outstanding < 0` — «Ушёл с долгом N ₽». */
    LEFT_WITH_DEBT,
}
