# Feltbook — банк покерного стола

[![build](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml/badge.svg)](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml)

Офлайн-приложение для Android для банкира домашней игры в техасский холдем: закупы и докупы фишек, долги, рейк, сбор банкира, чаевые, расчёт при выходе, итог игры, передача комнаты на другой телефон, история и статистика. Без интернета: разрешения `INTERNET` в манифесте нет.

Статус: этап M6 — пасхалка, полировка и первый релиз. Требования — `docs/SPEC.md`, дизайн — `docs/DESIGN.md`, правила для разработчиков и агентов — `CLAUDE.md`.

## Скачать

**[Скачать Feltbook (APK)](https://github.com/tr1gl1de/poker-banker-app/releases/latest/download/feltbook.apk)** —
всегда самая свежая сборка. Все версии — в [Releases](https://github.com/tr1gl1de/poker-banker-app/releases).

Нужен Android 8.0 или новее.

1. Откройте ссылку на телефоне и скачайте `feltbook.apk`.
2. Откройте скачанный файл. Если телефон спросит — разрешите установку из неизвестных источников для браузера
   или «Файлов» («Настройки → Безопасность → Неизвестные источники» или запрос прямо при установке).
3. Нажмите «Установить». Интернет приложению не нужен и не запрашивается.

Обновление: скачайте новый `feltbook.apk` по той же ссылке и установите поверх — игры, долги и история
сохраняются. Каждая сборка подписана одним ключом владельца; если телефон пишет «Приложение не установлено»
при обновлении, значит стоит сборка с другой подписью (например, собранная самостоятельно) — сделайте резервную
копию в настройках приложения, удалите его и установите заново.

Сборки из `main` выходят автоматически после каждого зелёного прогона CI (тег `build-<номер>`, хранятся
последние 10); стабильные версии — с тегами `vX.Y.Z`.

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
| `:baselineprofile` | генератор Baseline Profile и макробенчмарк холодного старта (запуск только на устройстве) |

Сборка настраивается convention-плагинами из `build-logic/` (`feltbook.android.application`, `feltbook.android.library`, `feltbook.android.compose`, `feltbook.android.feature`, `feltbook.android.test`, `feltbook.hilt`, `feltbook.room`, `feltbook.kmp.library`, `feltbook.quality`), версии — только в `gradle/libs.versions.toml`.

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
./gradlew assembleRelease               # минифицированный release (R8): app/build/outputs/apk/release/
```

## Baseline Profile и замер холодного старта

Профиль ускоряет холодный старт и первый проход по столу (ТЗ §9: старт ≤ 1 с, 60 fps при 20 игроках). Он лежит в
`app/src/release/generated/baselineProfiles/` (`baseline-prof.txt` и `startup-prof.txt` для раскладки dex),
коммитится в репозиторий и попадает в каждый release (пока его нет — действует ручной профиль на весь код
приложения, `app/src/main/baselineProfiles/baseline-prof.txt`); `profileinstaller` ставит его и при установке APK не из Play.

Генерация нужна после заметных изменений Главной, «Новой игры» или стола. Нужен телефон по USB (отладка по USB
включена, `adb devices` его видит) **с Android 13+ или root** — на Android 9–12 без root `BaselineProfileRule` профиль
не собирает (бенчмарк старта работает с Android 9). Либо эмулятор API 33+:

```bash
./gradlew :app:generateBaselineProfile                          # сценарий: старт → Главная → новая игра → 20 игроков → прокрутка
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # холодный старт: без профиля и с профилем
```

Тесты ставят отдельное приложение `app.feltbook.benchmark` (debug-подпись) и стирают только его данные —
настоящий Feltbook на телефоне не затрагивается. На Xiaomi/Redmi в «Для разработчиков» включите
«Установка через USB» и «Отладка по USB (настройки безопасности)», иначе установка и нажатия UiAutomator
запрещены. Результат бенчмарка (`timeToInitialDisplayMs`, медиана) — в выводе Gradle и в
`baselineprofile/build/outputs/connected_android_test_additional_output/`.

Без телефона можно взять Gradle Managed Device (эмулятор, который Gradle поднимает сам): объявить устройство в
`android.testOptions.managedDevices` модуля `:baselineprofile` (DSL — в документации AGP) и указать его в
`baselineProfile { managedDevices += "<имя>"; useConnectedDevices = false }`. Эмулятору нужно ~4 ГБ памяти.

## Версии и сборки для проверки

- Базовая версия — `feltbook.versionName` в `gradle.properties`; `versionCode` — число коммитов в `main`.
- Debug-сборка называется `<версия>-dev.<versionCode>+<sha>` (например `0.1.0-dev.42+abc1234`), ставится рядом с release как «Feltbook dev» (`app.feltbook.dev`).
- Свежий debug APK — в артефактах каждого прогона [build](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml): откройте последний зелёный прогон → Artifacts → `feltbook-…apk`.
- Release-сборка из `main` — автоматически (раздел «Скачать», job `auto-release` в `build.yml`): versionName `<версия>-build.<versionCode>`.
- Стабильный релиз — см. ниже.

## Как выпустить релиз

1. Поднять `feltbook.versionName` в `gradle.properties` (например `0.1.0`), при необходимости обновить Baseline
   Profile (раздел выше), закоммитить и дождаться зелёного [build](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/build.yml)
   (включая `release-smoke` — минифицированный release на эмуляторе).
2. Поставить тег, совпадающий с версией, и отправить его:

   ```bash
   git tag -a v0.1.0 -m "Feltbook 0.1.0"
   git push origin v0.1.0
   ```

3. Workflow [release](https://github.com/tr1gl1de/poker-banker-app/actions/workflows/release.yml) проверяет тег
   (`v0.1.0` ≠ `feltbook.versionName` — ошибка), гоняет те же проверки, что `build`, собирает подписанные
   `feltbook-0.1.0.apk` и `feltbook-0.1.0.aab`, `feltbook-0.1.0-mapping.txt` (карта R8 для расшифровки стектрейсов)
   и `SHA256SUMS`, и публикует GitHub Release с автоматическими заметками. `versionName` = версия из тега,
   `versionCode` = число коммитов в `HEAD`.

### Ключ подписи (один раз)

Ключ хранится только у владельца и в секретах GitHub — в репозиторий не попадает. Потерянный ключ = нельзя
обновить приложение у пользователей, сделайте резервную копию `feltbook-release.jks` и паролей.

```bash
keytool -genkeypair -v -keystore feltbook-release.jks -alias feltbook \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Feltbook"
# Секреты репозитория (Settings → Secrets and variables → Actions или gh CLI):
base64 -w0 feltbook-release.jks | gh secret set FELTBOOK_KEYSTORE_BASE64
gh secret set FELTBOOK_KEYSTORE_PASSWORD   # пароль хранилища
gh secret set FELTBOOK_KEY_ALIAS --body feltbook
gh secret set FELTBOOK_KEY_PASSWORD        # пароль ключа (для PKCS12 — тот же, что у хранилища)
```

Нужны все четыре секрета; ни одного — release собирается без подписи (файлы с суффиксом `-unsigned`), часть —
ошибка. Локально подписанный release собирается так же, через переменные окружения:

```bash
FELTBOOK_KEYSTORE_FILE=$PWD/feltbook-release.jks FELTBOOK_KEYSTORE_PASSWORD=… FELTBOOK_KEY_ALIAS=feltbook \
  FELTBOOK_KEY_PASSWORD=… ./gradlew assembleRelease bundleRelease
```

## Известные ограничения

- detekt 1.23.8 разбирает код компилятором Kotlin 2.0.21 (ADR 0004): синтаксис Kotlin 2.1+ — context parameters, guard conditions в `when`, `$$"…"` — ломает detekt. TODO: перейти на detekt 2.x, когда он станет стабильным.

## Лицензии

Код — [MIT](LICENSE). Шрифты Playfair Display, IBM Plex Sans и IBM Plex Mono — SIL Open Font License 1.1: `OFL-PlayfairDisplay.txt`, `OFL-IBMPlexSans.txt`, `OFL-IBMPlexMono.txt`.
