package app.feltbook.core.domain.repository

/**
 * [GameRepository.commit] отклонил изменения; транзакция откачена целиком, в базе ничего не поменялось.
 * ViewModel перечитывает снимок: [Reason.STALE_SEQ] — обычно второе нажатие по тому же снимку.
 * Сообщение содержит только id и `seq` — без имён и сумм (оно может попасть в логи).
 */
class CommitRejectedException(val reason: Reason, message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause) {
    enum class Reason {
        /** Журнал в базе ушёл вперёд от `GameChange.baseSeq` или `seq` новых записей не продолжает его. */
        STALE_SEQ,

        /** После изменения было бы больше одной активной игры (RUNNING/SETTLING). */
        SECOND_ACTIVE_GAME,

        /** Изменение без строки игры для игры, которой нет в базе. */
        UNKNOWN_GAME,

        /** Строка игры, игрок, запись или акт изменения принадлежат другой игре (в изменении или в базе). */
        FOREIGN_ROW,

        /** Запись или акт с таким id уже есть (повторная вставка). */
        DUPLICATE_ID,
    }
}
