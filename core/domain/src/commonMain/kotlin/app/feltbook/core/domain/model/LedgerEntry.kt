package app.feltbook.core.domain.model

import kotlin.time.Instant

/**
 * Тип записи. Все `amount` — рубли = фишки (1:1), строго > 0, кроме `CASH_OUT` (≥ 0: «вышел с нулём»).
 * Семантика каждого типа и знаки — таблица в docs/plan/m1-domain.md §3.
 */
enum class EntryType {
    /** Выдача фишек игроку (закуп или докуп). playerId — кому. */
    BUY_IN,

    /** Рейк с покупки, начисляется игроку. */
    RAKE,

    /** Сбор банкира с покупки, начисляется игроку. Доход банкира `bankerName`. */
    BANKER_FEE,

    /** Наличные игрок → касса (оплата покупки, частичная оплата, расчёт при выходе, погашение долга). */
    PAYMENT,

    /** Сдача фишек при выходе: `amount` = F, всё, что было на руках (включая фишки, отданные чаевыми). */
    CASH_OUT,

    /**
     * Чаевые рублями из вывода игрока playerId:
     * `counterpartyPlayerId == null` — банкиру `bankerName` (в доход);
     * не null — уже вышедшему игроку (добавляются к его выводу W).
     * Фишки физически возвращаются в банк вместе с `CASH_OUT`.
     */
    TIP,

    /**
     * Чаевые фишками игроку `counterpartyPlayerId`, который ещё за столом: фишки переходят к нему из рук в руки,
     * в банк не возвращаются, денег не двигают. Добавлено к ТЗ §8 (ADR 0005) ради сверки фишек.
     */
    TIP_CHIPS,

    /** Наличные касса → игрок (выплата при выходе или позже). */
    PAYOUT,

    /** Касса → заведение: рейк отдан. playerId = null. */
    RAKE_PAID,

    /**
     * Банкир `bankerName` забрал свой доход наличными из кассы при передаче комнаты (ТЗ §6, шаг 3).
     * playerId = null. Добавлено к ТЗ §8 (ADR 0005): иначе касса нового банкира по журналу ≠ наличным.
     */
    INCOME_TAKEN,

    /** Долг игрока прощён: закрывает долг без денег и уменьшает доход банкира `bankerName` этой игры (ТЗ §3.7). */
    DEBT_FORGIVEN,

    /** Отмена записи `reversesEntryId`. Отменённая и отменяющая записи исключаются из всех сумм. */
    REVERSAL,
}

/**
 * Запись журнала. Только добавление: ни одно поле существующей записи не меняется, записи не удаляются
 * (кроме каскадного удаления игры целиком).
 *
 * @property seq позиция в журнале игры: 1, 2, 3… без пропусков; задаёт порядок (время может совпадать внутри операции).
 * @property operationId общая для всех записей одной операции.
 * @property playerId чей счёт затрагивает запись; null только у `RAKE_PAID`, `INCOME_TAKEN`.
 * @property counterpartyPlayerId получатель чаевых (`TIP` → вышедший игрок, `TIP_CHIPS` → игрок за столом).
 * @property bankerName банкир, при котором сделана запись (доход разных банкиров — ТЗ §6).
 * @property reversesEntryId только у `REVERSAL`.
 * @property note причина отмены у `REVERSAL` (необязательна), у остальных — null.
 * @property earlyExit только у `CASH_OUT`: выход раньше `minPlayMinutes`, подтверждён «Исключительный случай» (ТЗ
 *   §3.4).
 */
data class LedgerEntry(
    val id: EntryId,
    val gameId: GameId,
    val seq: Long,
    val operationId: OperationId,
    val type: EntryType,
    val playerId: PlayerId?,
    val amount: Long,
    val counterpartyPlayerId: PlayerId?,
    val bankerName: String,
    val createdAt: Instant,
    val reversesEntryId: EntryId? = null,
    val note: String? = null,
    val earlyExit: Boolean = false,
)
