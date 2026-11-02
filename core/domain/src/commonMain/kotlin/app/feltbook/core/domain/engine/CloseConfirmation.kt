package app.feltbook.core.domain.engine

/** Подтверждения, без которых нельзя закрыть игру (ТЗ §3.5, §3.7; диалог/подпись hold-кнопки). */
enum class CloseConfirmation {
    /** Фишки не сошлись (`chipDiff ≠ 0`). */
    CHIPS_NOT_RECONCILED,

    /** Есть долги игроков — «Закрыть игру с долгом». */
    OPEN_DEBTS,

    /** Банкир ещё должен вышедшим (`bankerOwes > 0`). */
    UNPAID_PAYOUTS,
}
