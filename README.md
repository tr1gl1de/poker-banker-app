# Feltbook — банк покерного стола

[![build](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml/badge.svg)](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml)

Офлайн-приложение для Android для банкира домашней игры в техасский холдем: закупы и докупы фишек, долги, рейк, сбор банкира, чаевые, расчёт при выходе, итог игры, передача комнаты на другой телефон, история и статистика. Без интернета: разрешения `INTERNET` в манифесте нет.

Статус: этап M0 — скелет проекта (модули, сборка, тема-заглушка «Сукно», CI). Требования — `docs/SPEC.md`, дизайн — `docs/DESIGN.md`, правила для разработчиков и агентов — `CLAUDE.md`.

## Модули

| Модуль | Что внутри |
| --- | --- |
| `:app` | точка входа, навигация, DI |
| `:core:domain` | модель и движок расчёта, Kotlin Multiplatform (сейчас `jvm()`), без Android |
| `:core:odds` | калькулятор шансов (пасхалка), Kotlin Multiplatform |
| `:core:data` | Room, DataStore, репозитории, экспорт/импорт |
| `:core:transfer` | передача комнаты через Nearby Connections, файл `.feltbook` |
| `:core:ui` | тема «Сукно», токены, шрифты, общие компоненты |
| `:feature:game`, `:feature:settlement`, `:feature:history`, `:feature:settings`, `:feature:handover` | экраны |

Сборка настраивается convention-плагинами из `build-logic/` (`feltbook.android.application`, `feltbook.android.library`, `feltbook.android.compose`, `feltbook.android.feature`, `feltbook.hilt`, `feltbook.kmp.library`, `feltbook.quality`), версии — только в `gradle/libs.versions.toml`.

## Команды

Нужны JDK 17+ и Android SDK (platform 37); путь к SDK — в `local.properties` (`sdk.dir=…`).

```bash
./gradlew assembleDebug                 # debug APK: app/build/outputs/apk/debug/
./gradlew build                         # сборка + тесты + lint + ktlint + detekt
./gradlew test                          # юнит-тесты всех модулей
./gradlew :core:domain:jvmTest          # быстрый цикл движка расчёта
./gradlew ktlintCheck detekt lint       # стиль и статический анализ
./gradlew ktlintFormat                  # автоформат
./gradlew recordRoborazziDebug          # перезаписать эталонные скриншоты (src/test/screenshots)
./gradlew verifyRoborazziDebug          # сверить скриншоты с эталонами
./gradlew :app:printVersion             # текущая версия
```

## Версии и сборки для проверки

- Базовая версия — `feltbook.versionName` в `gradle.properties`; `versionCode` — число коммитов в `main`.
- Debug-сборка называется `<версия>-dev.<versionCode>+<sha>` (например `0.1.0-dev.42+abc1234`), ставится рядом с release как «Feltbook dev» (`app.feltbook.dev`).
- Свежий debug APK — в артефактах каждого прогона [build](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml): откройте последний зелёный прогон → Artifacts → `feltbook-…apk`.
- Релиз: тег `vX.Y.Z` (должен совпадать с `feltbook.versionName`) → workflow `release` собирает APK и AAB и публикует их в Releases. Подпись — секретами `FELTBOOK_KEYSTORE_BASE64`, `FELTBOOK_KEYSTORE_PASSWORD`, `FELTBOOK_KEY_ALIAS`, `FELTBOOK_KEY_PASSWORD`; без них сборка без подписи (`-unsigned`).

## Лицензии

Код — [MIT](LICENSE). Шрифты Playfair Display, IBM Plex Sans и IBM Plex Mono — SIL Open Font License 1.1: `OFL-PlayfairDisplay.txt`, `OFL-IBMPlexSans.txt`, `OFL-IBMPlexMono.txt`.
