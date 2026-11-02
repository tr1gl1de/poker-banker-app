# M6. Чек-лист релиза (ТЗ §9)

Статус: чек-лист перед тегом `vX.Y.Z` (см. README → «Как выпустить релиз»). Пункт закрыт, когда проверка
выполнена на коммите, с которого ставится тег. Автоматическое — в CI (`build.yml`, `release.yml`), ручное —
на телефоне владельца (Redmi) и, где указано, на втором устройстве.

Обозначения: **CI** — проверяется каждым прогоном; **Gradle** — команда локально; **Руками** — на устройстве.

## 1. Офлайн, без `INTERNET`

- [ ] **CI** `check<Variant>NoInternetPermission` (входит в `./gradlew build`) — в merged manifest каждого варианта,
  включая `release` и `benchmarkRelease`, нет `android.permission.INTERNET` (вырезается и из библиотек,
  `tools:node="remove"` в `app/src/main/AndroidManifest.xml`). Отчёт — `app/build/reports/manifest/*-no-internet.txt`.
- [ ] **Gradle** итоговый APK: `$ANDROID_HOME/build-tools/<версия>/aapt2 dump permissions dist/feltbook-X.Y.Z.apk` —
  в списке нет `INTERNET`.
- [ ] **Руками** режим полёта: создать игру, игроки, докуп, выход, итог, история, передача файлом — всё работает,
  ошибок сети нет.

## 2. Транзакционные записи и восстановление после убийства процесса

- [ ] **CI** `:core:data:test` — `RestoreAfterKillTest` (БД закрыта посреди игры, открыта заново — журнал целиком),
  `GameRepositoryTest`/`DaoTest` (операции — в одной транзакции Room), `MigrationTest` (каждая версия схемы).
- [ ] **Руками** во время игры: докуп → сразу `adb shell am kill app.feltbook` (или смахнуть из недавних /
  «Остановить» в настройках) → запуск: стол, суммы и журнал те же. То же посреди шторки выхода (ввод сохраняется
  `TableInputsSaver`).
- [ ] **Руками** перезагрузка телефона посреди игры → игра на месте, «Вернуться за стол» на Главной.

## 3. Холодный старт ≤ 1 с

- [ ] **Gradle** (телефон Android 13+ или root, либо эмулятор API 33+; Redmi 6A на Android 9 не подходит) профиль свежий: `./gradlew :app:generateBaselineProfile`; изменения
  `app/src/release/generated/baselineProfiles/` закоммичены.
- [ ] **Gradle** `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest` — `StartupBenchmark.startupBaselineProfile`:
  медиана `timeToInitialDisplayMs` ≤ 1000 на Redmi (средний телефон по ТЗ). Записать обе медианы
  (`startupNoCompilation` / `startupBaselineProfile`) в описание релиза.
- [ ] **CI** `release-smoke` — `TotalTime` из `am start -W` в summary (эмулятор, ориентир, не критерий).
- [ ] **Руками** release APK из Releases: `adb shell am start -W -n app.feltbook/.MainActivity` после
  `adb shell am force-stop app.feltbook` — `TotalTime` ≤ 1000.

## 4. 60 fps при 20 игроках

- [ ] **Gradle** профиль включает сценарий стола (`BaselineProfileGenerator.tableJourney`: 20 игроков + прокрутка).
- [ ] **Руками** release на Redmi: стол с 20 игроками, быстрые прокрутки вверх-вниз, открытие шторок закупа/выхода.
  «Для разработчиков» → «Профилировать HWUI» → «На экране в виде полос»: полосы ниже зелёной линии 16 мс,
  без серий выбросов. Либо `adb shell dumpsys gfxinfo app.feltbook reset`, прокрутка 10 с,
  `adb shell dumpsys gfxinfo app.feltbook` — `Janky frames` ≤ 5 %.
- [ ] **Руками** то же на планшете/складном (широкий макет: список + карточка).

## 5. Приватность

- [ ] **CI** см. п. 1 — без сети данные уйти не могут.
- [ ] **Gradle/код** `android:allowBackup="false"`, `data_extraction_rules.xml` исключает всё из облачной копии;
  перенос между устройствами — только по явному действию.
- [ ] **Руками** передача комнаты и экспорт — только по нажатию пользователя, с подтверждением; файл `.feltbook`
  и резервная копия уходят только через системный диалог (сохранение файла или «Поделиться»), не в общий
  каталог молча.
- [ ] **Gradle** `aapt2 dump badging` release APK — нет лишних разрешений сверх Nearby/Bluetooth/Wi-Fi из
  `docs/plan/m5-manual-check.md`.
- [ ] **Руками** биометрия (опционально по ТЗ) — если не сделана, пункт в «Известные ограничения» README.

## 6. Русский интерфейс, строки в ресурсах

- [ ] **CI** `lint` (warningsAsErrors) — `HardcodedText`, `SetTextI18n`, `PluralsCandidate` и т. п.;
  Roborazzi-скриншоты (`verifyRoborazziDebug`) — тексты на русском, `plurals` («1 игрок», «2 игрока», «5 игроков»).
- [ ] **CI** `androidResources.localeFilters = ru` — в APK нет строк других языков из библиотек;
  `RussianLocale` — интерфейс на русском и на телефоне с английской системой.
- [ ] **Руками** телефон на английском языке системы: все экраны по-русски, суммы `12 500 ₽` (неразрывный пробел,
  знак ₽), даты по-русски.

## 7. TalkBack

- [ ] **Руками** TalkBack включён: Главная → Новая игра → стол → закуп → выход → итог → долги. Каждая кнопка
  озвучивается по смыслу (`contentDescription`), суммы — со знаком и словом («должен 1 500 рублей»),
  hold-кнопка объясняет удержание, сенсорные цели ≥ 48 dp (Accessibility Scanner без замечаний).
- [ ] **CI** lint `ContentDescription`, `ClickableViewAccessibility`; Compose-тесты сценариев (`TableScenarioTest`,
  `DebtScenarioTest`, `SettlementScreenTest`) находят элементы по семантике.

## 8. Шрифт 200 %

- [ ] **Руками** «Настройки → Экран → Размер шрифта» максимум (Android 14+: 200 %) + «Размер элементов» больше:
  суммы не переносятся (`maxLines = 1`, автоуменьшение до 12 sp), кнопки шторок не уходят за экран, клавиатура
  сумм целиком видна, стол прокручивается.
- [ ] **CI** скриншот-тесты с `fontScale = 2f` там, где они есть (`ScreensScreenshotTest`, `SheetsScreenshotTest`).

## 9. Планшеты и альбомная ориентация

- [ ] **CI** Roborazzi-скриншоты широких макетов (`qualifiers = w840dp…`): стол «список + карточка игрока».
- [ ] **Руками** планшет или эмулятор Pixel Tablet / Foldable: поворот посреди шторки — ввод сохраняется,
  игра не теряется; split-screen — интерфейс не ломается.

## 10. Сборка и подпись

- [ ] **CI** `build` зелёный, `release-smoke` зелёный (минифицированный release запускается, Главная → «Новая игра»
  → стол без падения: сериализаторы маршрутов, Room, Hilt после R8 на месте).
- [ ] **CI** `release` по тегу: подписанные APK и AAB (`apksigner verify --print-certs` в summary — отпечаток
  совпадает с ключом владельца), `mapping.txt`, `SHA256SUMS` в GitHub Release.
- [ ] **Руками** APK из Releases ставится поверх предыдущей версии (та же подпись), игры и история на месте.
- [ ] **Руками** иконка: круглая/скруглённая/«капля» маски лаунчера — кольцо и вставки не обрезаны; тематическая
  иконка Android 13+ (кольцо и F) читается на светлых и тёмных обоях.
- [ ] После 3+ зелёных прогонов `release-smoke` подряд — `continue-on-error: false` в `build.yml`.
