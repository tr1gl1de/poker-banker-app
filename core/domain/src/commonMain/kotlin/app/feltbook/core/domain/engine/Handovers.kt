package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.Game
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.HandoverId
import app.feltbook.core.domain.model.PlayerId
import app.feltbook.core.domain.model.apply

/**
 * «Акт передачи кассы» (ТЗ §6 шаг 3, макет Handover).
 * @property fromBankerIncome чистый доход уходящего банкира в этой игре (по его `bankerName`) минус уже забранное.
 * @property incomeTakeable сколько он может забрать сейчас: `clamp(min(fromBankerIncome, cashOnHand − rakeDue), 0)` —
 *   рейк заведения не уходит в карман; 0 — «Оставить доход себе — после расчёта долгов».
 */
data class HandoverAct(
    val fromBanker: String,
    val cashOnHand: Long,
    val rakeDue: Long,
    val playersOwe: Long,
    val bankerOwes: Long,
    val fromBankerIncome: Long,
    val incomeTakeable: Long,
    val playersAtTable: Int,
    val chipsInPlay: Long,
    val entryCount: Int,
) {
    /**
     * «Отдать Владу наличными N ₽». Отрицательная касса (банкир доплатил из своих) → 0; разницу банкиры решают сами,
     * акт её показывает.
     */
    fun cashToHandOver(keepIncome: Boolean): Long = maxOf(0L, cashOnHand - (if (keepIncome) incomeTakeable else 0L))
}

/**
 * Подготовленная передача. Ничего не записано: отправитель коммитит [senderChange] только после подтверждения
 * получателя (ACK по Nearby) или явного «Файл отправлен» (ADR 0007).
 * @property senderChange INCOME_TAKEN? + Handover + статус HANDED_OVER.
 * @property outgoing снимок для пакета: тот же журнал и акт, статус игры прежний (RUNNING/SETTLING).
 */
data class PreparedHandover(
    val act: HandoverAct,
    val handover: Handover,
    val senderChange: GameChange,
    val outgoing: GameSnapshot,
)

/** Шаблон новой игры после расчёта (ТЗ §6 сценарий 2): правила + имена — подсказки в «Новом игроке». */
data class GameTemplate(val fromBanker: String, val rules: GameRules, val playerNames: List<String>)

object Handovers {
    /** Акт по текущему снимку. Статус RUNNING/SETTLING. */
    fun act(snapshot: GameSnapshot): HandoverAct {
        requireStatus(snapshot, CashOuts.CASH_OUT_STATUSES)
        val table = GameStates.compute(snapshot).table
        val from = snapshot.game.currentBankerName
        val own = table.incomeByBanker.firstOrNull { it.bankerName == from }
        val income = own?.let { it.net - it.taken } ?: 0L
        return HandoverAct(
            fromBanker = from,
            cashOnHand = table.cashOnHand,
            rakeDue = table.rakeDue,
            playersOwe = table.playersOwe,
            bankerOwes = table.bankerOwes,
            fromBankerIncome = income,
            incomeTakeable = maxOf(0L, minOf(income, table.cashOnHand - table.rakeDue)),
            playersAtTable = table.playersAtTable,
            chipsInPlay = table.chipsInPlay,
            entryCount = snapshot.entries.size,
        )
    }

    /** Сценарий 1 (посреди игры). `toBanker` — имя нового банкира. */
    fun prepare(snapshot: GameSnapshot, toBanker: String, keepIncome: Boolean, stamp: Stamp): PreparedHandover {
        val act = act(snapshot)
        val to = PlayerNames.normalize(toBanker)
        nameBlockers(to).throwFirst()
        val taken = if (keepIncome) act.incomeTakeable else 0L
        val writer = EntryWriter(snapshot, stamp)
        if (taken > 0) writer.add(writer.operation(), EntryType.INCOME_TAKEN, null, taken, bankerName = act.fromBanker)
        val entries = writer.entries
        val handover = Handover(
            id = HandoverId(stamp.nextUuid()),
            gameId = snapshot.game.id,
            fromBanker = act.fromBanker,
            toBanker = to,
            cashHandedOver = act.cashToHandOver(keepIncome),
            incomeTaken = taken,
            ledgerSeq = entries.lastOrNull()?.seq ?: (snapshot.nextSeq - 1),
            at = stamp.at,
        )
        val outgoingChange = GameChange(
            gameId = snapshot.game.id,
            baseSeq = snapshot.lastSeq,
            entries = entries,
            handovers = listOf(handover),
        )
        return PreparedHandover(
            act = act,
            handover = handover,
            senderChange = outgoingChange.copy(game = snapshot.game.copy(status = GameStatus.HANDED_OVER)),
            outgoing = snapshot.apply(outgoingChange),
        )
    }

    /** Сценарий 2: из CLOSED-игры. У отправителя игра не меняется (QUESTIONS №5). */
    fun template(snapshot: GameSnapshot): GameTemplate {
        requireStatus(snapshot, setOf(GameStatus.CLOSED))
        val players = GameStates.compute(snapshot).players.filterNot { it.isVoid }.map { it.player.name }
        return GameTemplate(snapshot.game.currentBankerName, snapshot.game.rules, players)
    }

    /**
     * Получатель нажал «Принять». `receiverPlayerId` — «я играю как …» (игрок этой игры) или null.
     * Ставит `currentBankerName = handover.toBanker`, `bankerPlayerId = receiverPlayerId`.
     * Бросает [DomainRuleViolation]: AnotherGameActive, BankerNotAPlayer, ImportConflict (конфликт с локальной копией,
     * см. [Imports.plan]). Повторный приём того же пакета — пустое изменение.
     */
    fun accept(
        incoming: GameSnapshot,
        handover: Handover,
        local: GameSnapshot?,
        activeGame: Game?,
        receiverPlayerId: PlayerId?,
    ): GameChange {
        require(handover in incoming.handovers) { "handover ${handover.id} is not in the incoming game" }
        if (activeGame != null &&
            activeGame.id != incoming.game.id
        ) {
            violation(DomainError.AnotherGameActive(activeGame.id))
        }
        if (receiverPlayerId != null && incoming.player(receiverPlayerId) == null) {
            violation(DomainError.BankerNotAPlayer(receiverPlayerId))
        }
        val status = if (incoming.game.status == GameStatus.CREATED) GameStatus.RUNNING else incoming.game.status
        val game = incoming.game.copy(
            status = status,
            currentBankerName = handover.toBanker,
            bankerPlayerId = receiverPlayerId,
        )
        return when (val plan = Imports.plan(incoming.copy(game = game), local, activeGame?.id)) {
            is ImportPlan.Insert -> plan.change
            is ImportPlan.Extend -> plan.change
            ImportPlan.Identical -> GameChange(game.id, baseSeq = local?.lastSeq ?: 0L)
            is ImportPlan.Conflict -> violation(DomainError.ImportConflict(plan.reason))
        }
    }
}
