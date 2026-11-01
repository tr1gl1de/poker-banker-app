# 0001. Чистые модули готовы к Kotlin Multiplatform

Статус: принято · 2026-11-02

## Контекст
ТЗ §7 говорит «кроссплатформа не нужна, пока iOS не в планах». Владелец проекта сообщил: в будущем приложение будет и на iOS. Сейчас делаем только Android, точь-в-точь по ТЗ и макетам.

## Решение
- `:core:domain` и `:core:odds` — модули Kotlin Multiplatform (`org.jetbrains.kotlin.multiplatform`), код в `commonMain`/`commonTest`, сейчас единственная цель `jvm()`. Android-модули зависят от них как от обычной JVM-библиотеки.
- В этих модулях запрещены `java.*`, `android.*`, JVM-only библиотеки. Время — `kotlinx-datetime`, `kotlin.time`; UUID — `kotlin.uuid.Uuid`; хеши — своя реализация или KMP-библиотека через ADR.
- Интерфейсы репозиториев и use case'ы живут в `:core:domain` (без Android-типов) — чтобы на iOS переиспользовать бизнес-логику целиком.
- `:core:data` пока Android-модуль, но на KMP-совместимых библиотеках (Room 2.8, DataStore, kotlinx.serialization); переезд в KMP — отдельным ADR, когда начнём iOS.
- Формат пакета передачи (`.feltbook`) — kotlinx.serialization в общем коде, чтобы iOS-версия читала те же файлы.

## Альтернативы
- Чистый Kotlin/JVM (`kotlin("jvm")`) — проще, но переезд потом требует переноса исходников и тестов.
- Сразу KMP + Compose Multiplatform на весь UI — противоречит ТЗ (стек Android/Jetpack), рискованно для сроков.

## Последствия
- Тесты в `commonTest` на `kotlin.test`; на JVM исполняются JUnit Platform (JUnit 5).
- Добавление iOS: `iosArm64()`, `iosSimulatorArm64()` в двух модулях + выбор UI-стека (SwiftUI или Compose Multiplatform) — отдельный ADR.
