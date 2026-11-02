package app.feltbook.core.domain.engine

import app.feltbook.core.domain.model.EntryId
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.LedgerEntry

object Ledger {
    /** Записи без `REVERSAL` и без отменённых (§3.2), порядок сохраняется. */
    fun effective(entries: List<LedgerEntry>): List<LedgerEntry> {
        val reversed = reversedIds(entries)
        return entries.filter { it.type != EntryType.REVERSAL && it.id !in reversed }
    }

    /** id записей, отменённых каким-либо `REVERSAL`. */
    fun reversedIds(entries: List<LedgerEntry>): Set<EntryId> =
        entries.mapNotNullTo(HashSet()) { if (it.type == EntryType.REVERSAL) it.reversesEntryId else null }
}
