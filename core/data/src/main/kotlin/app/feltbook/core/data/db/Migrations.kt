package app.feltbook.core.data.db

import androidx.room.migration.Migration

/**
 * Миграции схемы по порядку версий. Схема 1 — первая, миграций пока нет. Новая версия: `FeltbookDatabase.VERSION`++,
 * миграция сюда, экспортированный `schemas/…/N.json` в git и тест в `MigrationTest`.
 */
internal object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
