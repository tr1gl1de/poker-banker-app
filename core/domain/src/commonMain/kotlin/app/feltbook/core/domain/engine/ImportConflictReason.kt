package app.feltbook.core.domain.engine

enum class ImportConflictReason {
    /** Журналы разошлись (не префикс) — локальное не трогаем. */
    DIVERGED_LEDGER,

    /** Стало бы две активные игры. */
    SECOND_ACTIVE_GAME,

    /** Входящий журнал не прошёл [LedgerValidator]. */
    INVALID_LEDGER,
}
