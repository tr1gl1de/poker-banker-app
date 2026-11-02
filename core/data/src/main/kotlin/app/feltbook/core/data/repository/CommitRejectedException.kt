package app.feltbook.core.data.repository

/**
 * `GameRepository.commit` отклонил изменения; транзакция откачена целиком, в базе ничего не поменялось.
 * ViewModel обычно перечитывает снимок: [Reason.STALE_SEQ] — это второе нажатие по тому же снимку.
 */
class CommitRejectedException(val reason: Reason, message: String) : IllegalStateException(message) {
    enum class Reason {
        /** `seq` новых записей не продолжает журнал: изменение построено по устаревшему снимку (двойное нажатие). */
        STALE_SEQ,

        /** После изменения было бы больше одной активной игры (RUNNING/SETTLING). */
        SECOND_ACTIVE_GAME,

        /** Изменение без строки игры для игры, которой нет в базе. */
        UNKNOWN_GAME,

        /** Строка игры, игрок, запись или акт из `GameChange` принадлежат другой игре. */
        FOREIGN_ROW,
    }
}
