# 0008. Room в `:core:data`: что мешает Room KMP и как перейти

Статус: принято · 2026-11-02

## Контекст
ADR 0001 держит бизнес-логику и интерфейсы репозиториев в KMP-модуле `:core:domain`, чтобы будущая iOS-версия переиспользовала их. `:core:data` (M2) пока Android-модуль: Room 2.8 на SupportSQLite, DataStore Preferences, Hilt. Room с 2.7 умеет KMP (`RoomDatabase.Builder` + `SQLiteDriver`), но переход сейчас — лишние зависимости (`sqlite-bundled`) и модуль `commonMain` без потребителя. Нужно зафиксировать, что уже совместимо, а что придётся поменять.

## Решение
Сейчас — Android-only, но так, чтобы переход был механическим:

- **Уже совместимо с Room KMP.** DAO — только `suspend` и `Flow`, без `LiveData`, `Cursor`, `SupportSQLiteDatabase`. Сущности и конвертеры — чистый Kotlin (`kotlin.uuid.Uuid`, `kotlin.time.Instant`, доменные enum по имени). Чтение снимков — `useReaderConnection { it.deferredTransaction { … } }`, это общий API. Потоки — `invalidationTracker.createFlow(…)`. Схема экспортируется плагином `androidx.room` в `core/data/schemas/`.
- **Android-specific — заменить при переходе:**
  1. `Room.databaseBuilder(context, FeltbookDatabase::class.java, name)` → `Room.databaseBuilder<FeltbookDatabase>(name = путь, factory = …)` + `@ConstructedBy(FeltbookDatabaseConstructor::class)` + `setDriver(BundledSQLiteDriver())`. Путь к файлу — `expect/actual` (Android: `context.getDatabasePath`, iOS: `NSDocumentDirectory`).
  2. Запись: `withTransaction` из room-ktx → `useWriterConnection { it.immediateTransaction { … } }`.
  3. `android.database.sqlite.SQLiteConstraintException` в `GameStore` (перевод в `CommitRejectedException.DUPLICATE_ID`) → `androidx.sqlite.SQLiteException` (драйвер бросает его, код ограничения — в сообщении). Проверки `FOREIGN_ROW`/`STALE_SEQ` уже выполняются до вставки и от драйвера не зависят.
  4. `allowMainThreadQueries()` в тестах — при драйвере не нужен; тесты на JVM без Robolectric (`BundledSQLiteDriver` работает на desktop JVM).
  5. DataStore: `PreferenceDataStoreFactory.create(produceFile = File)` → `createWithPath(produceFile = { okio Path })` (datastore-preferences-core есть для KMP). `IOException` в `retryWhen` → `okio.IOException`.
  6. Hilt-модуль остаётся в `androidMain`; на iOS — ручная сборка графа или Koin (отдельный ADR).
- **Не делаем сейчас:** `BundledSQLiteDriver`, `commonMain` в `:core:data`, перенос репозиториев. Повод — появление iOS-цели.

## Альтернативы
- Сразу Room KMP с `BundledSQLiteDriver`: +1 зависимость и собственная нативная SQLite в APK ради платформы, которой пока нет; Robolectric-тесты пришлось бы переписать на драйвер.
- SQLDelight: общий код без `expect` для сущностей, но другой стек (CLAUDE.md: Room + KSP) и переписывание DAO.

## Последствия
- Перечисленные шесть мест — единственные Android-зависимости слоя данных; новый код в `:core:data` их не множит (DAO без Android-типов, транзакции — через `GameStore`).
- Схема `1.json` и тесты миграций (`MigrationTestHelper`) переносятся без изменений: в Room KMP у `MigrationTestHelper` есть конструктор с драйвером.
