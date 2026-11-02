# M1. Контракт `:core:domain`

Статус: контракт для `domain-engine` (реализует дословно, по TDD), `data-layer`, `transfer` и `compose-ui` (потребители). Решения обоснованы в ADR 0005 (журнал), 0006 (репозитории без use case'ов), 0007 (формат пакета).

Любое изменение публичного API ниже — через `architect` (правка этого файла), исполнитель описывает нужду в «Вопросы/риски».

## 0. Рамки модуля

- KMP `commonMain`, цель `jvm()` (ADR 0001). Запрещены `java.*`, `android.*`, `Double`/`Float` в деньгах, изменяемое глобальное состояние, `Clock.System` внутри расчётов (время приходит параметром).
- Зависимости `commonMain`: `kotlinx-coroutines-core` (только тип `Flow` в интерфейсах репозиториев), `kotlinx-serialization-json` + плагин `serialization` (пакет `transfer`). Больше ничего. `kotlinx-datetime` домену не нужен: `kotlin.time.Instant`/`Duration` из stdlib, календарь (месяцы, «18 окт») — в UI.
- Если в Kotlin 2.4 `kotlin.uuid.Uuid` или `kotlin.time.Instant` ещё требуют opt-in — включить на уровне модуля (`optIn("kotlin.uuid.ExperimentalUuidApi")`, `optIn("kotlin.time.ExperimentalTime")`), а не аннотациями по файлам. Это задача `build-release` (см. roadmap, M1.0).
- Тесты: `commonTest`, `kotlin.test`; покрытие Kover ≥ 95 % по строкам модуля.
- Импорты между пакетами домена в кодовых блоках опущены (модель видна движку, репозиториям и `transfer`).
- В кодовых блоках ниже тела функций опущены: исполнитель пишет реализацию, сигнатуры и KDoc не меняет. Где тело тривиально — оно приведено.

## 1. Пакеты

| Пакет | Что |
| --- | --- |
| `app.feltbook.core.domain.model` | Идентификаторы, `Game`, `GameRules`, `Player`, `LedgerEntry`, `PlayerName`, `Handover`, агрегат `GameSnapshot`, единица записи `GameChange`, `AppSettings` |
| `app.feltbook.core.domain.engine` | Чистые функции: деньги и округление, котировки, состояние игры, команды (производят `GameChange`), итог, журнал для экрана, долги, статистика, передача, импорт, валидатор журнала |
| `app.feltbook.core.domain.repository` | `GameRepository`, `PlayerNameRepository`, `SettingsRepository`, `BackupRepository` |
| `app.feltbook.core.domain.transfer` | `@Serializable` DTO пакета `.feltbook`, `FeltbookCodec`, `Sha256` |

Слоя use case'ов нет (ADR 0006): ViewModel берёт снимок из репозитория → вызывает функцию движка → отдаёт `GameChange` в `GameRepository.commit`.

## 2. Модель

### 2.1. Идентификаторы и время

```kotlin
package app.feltbook.core.domain.model

import kotlin.jvm.JvmInline
import kotlin.uuid.Uuid

/** Идентификатор игры. В БД и в пакете — строка UUID (`Uuid.toString()`). */
@JvmInline value class GameId(val value: Uuid)

/** Идентификатор игрока внутри игры. Один человек в двух играх — два разных `PlayerId`, связь — по имени (`PlayerName`). */
@JvmInline value class PlayerId(val value: Uuid)

/** Идентификатор записи журнала. */
@JvmInline value class EntryId(val value: Uuid)

/**
 * Идентификатор пользовательской операции. Все записи, созданные одним действием банкира
 * (закуп = фишки + рейк + сбор + оплата), имеют общий `OperationId`; отмена в интерфейсе — по операции.
 */
@JvmInline value class OperationId(val value: Uuid)

/** Идентификатор акта передачи кассы. */
@JvmInline value class HandoverId(val value: Uuid)
```

Все `Instant` в модели — с точностью до миллисекунды (их создаёт только `Stamp`, см. §4.2), чтобы Room и пакет передачи давали побитово одинаковый снимок.

### 2.2. Правила игры

```kotlin
package app.feltbook.core.domain.model

/** Режим сбора банкира (ТЗ §3.1–3.2). */
enum class BankerFeeMode {
    /** Разово, фиксированная сумма `bankerFeeValue` ₽ — только с первого закупа игрока в этой игре. */
    ONCE_FIXED,
    /** Разово, `bankerFeeValue` % от первого закупа. */
    ONCE_PERCENT,
    /** С каждого закупа и докупа, фиксированная сумма. */
    EACH_FIXED,
    /** С каждого закупа и докупа, `bankerFeeValue` % от покупки. */
    EACH_PERCENT,
    ;

    val isOnce: Boolean get() = this == ONCE_FIXED || this == ONCE_PERCENT
    val isPercent: Boolean get() = this == ONCE_PERCENT || this == EACH_PERCENT
}

/** Режим рейка заведению: процент от каждой покупки или фиксированная сумма за каждую. */
enum class RakeMode { PERCENT, FIXED }

/**
 * Слепок правил, копируется в [Game] при создании и больше не меняется (CLAUDE.md, правило 3).
 *
 * Значения `bankerFeeValue`/`rakeValue` трактуются по режиму: в `*_FIXED` — рубли,
 * в `*_PERCENT` — целые проценты 0..100 (ADR 0005: дробных процентов в v1 нет).
 *
 * @property minBuyIn минимальная покупка в фишках (ТЗ §3.1, «Минимальная покупка»); любая сумма ≥ минимума, без шага.
 * @property splitPayment раздельная оплата: в шторке два переключателя «Фишки» и «Рейк и сбор».
 * @property minPlayMinutes минимальное время игры от первого закупа игрока (ТЗ §3.4).
 */
data class GameRules(
    val bankerFeeMode: BankerFeeMode = BankerFeeMode.ONCE_FIXED,
    val bankerFeeValue: Long = 100,
    val rakeMode: RakeMode = RakeMode.PERCENT,
    val rakeValue: Long = 10,
    val minBuyIn: Long = 500,
    val splitPayment: Boolean = false,
    val minPlayMinutes: Int = 60,
) {
    /** Пустой список — правила допустимы. Экран «Новая игра» и «Настройки» не сохраняют правила с проблемами. */
    fun problems(): List<RulesProblem>

    companion object {
        /** Значения по умолчанию из ТЗ §3.1: разово 100 ₽, рейк 10 %, минимум 500, одним статусом, 60 мин. */
        val DEFAULT: GameRules = GameRules()
        const val MAX_PERCENT: Long = 100
        const val MAX_MIN_PLAY_MINUTES: Int = 24 * 60
    }
}

enum class RulesProblem {
    BANKER_FEE_NEGATIVE,
    BANKER_FEE_PERCENT_ABOVE_MAX,
    BANKER_FEE_ABOVE_MAX_AMOUNT,
    RAKE_NEGATIVE,
    RAKE_PERCENT_ABOVE_MAX,
    RAKE_ABOVE_MAX_AMOUNT,
    MIN_BUY_IN_NOT_POSITIVE,
    MIN_BUY_IN_ABOVE_MAX_AMOUNT,
    MIN_PLAY_MINUTES_OUT_OF_RANGE,
}
```

### 2.3. Игра и игрок

```kotlin
package app.feltbook.core.domain.model

import kotlin.time.Instant

/** Состояние игры (ТЗ §4: создана → идёт → расчёт → закрыта; из «идёт»/«расчёт» — передана). */
enum class GameStatus {
    /** Есть в ТЗ §8; в v1 не создаётся: «Начать игру» сразу даёт [RUNNING]. Импорт принимает и трактует как RUNNING. */
    CREATED,
    /** Идёт: входы, докупы, выходы. */
    RUNNING,
    /** Нажато «Завершить игру»: новые покупки запрещены, идёт вывод оставшихся и итог. Можно вернуть в RUNNING. */
    SETTLING,
    /** Закрыта, в истории. Допускаются только погашение/прощение долгов и их отмена (без «изменено после закрытия»). */
    CLOSED,
    /** Передана другому телефону посреди игры — только чтение. Её долги здесь не считаются открытыми (ушли новому банкиру). */
    HANDED_OVER,
    ;

    /** RUNNING или SETTLING. Активной может быть не больше одной игры (ТЗ §4). */
    val isActive: Boolean get() = this == RUNNING || this == SETTLING
}

/**
 * Игра. Строка игры — единственное изменяемое (upsert) в агрегате вместе с именами игроков;
 * деньги — только в журнале.
 *
 * @property currentBankerName имя банкира сейчас; копируется в `LedgerEntry.bankerName` каждой новой записи.
 * @property bankerPlayerId игрок за этим столом, который сейчас банкир (`null` — банкир не играет).
 *   Заменяет `bankerIsPlayer` + `Player.isBanker` из ТЗ §8 (ADR 0005): один источник правды, переживает передачу комнаты.
 * @property editedAfterClose игру переоткрывали после закрытия — в истории пометка «изменено после закрытия».
 * @property schemaVersion версия семантики журнала и правил, с которой записана игра ([LEDGER_SCHEMA_VERSION]).
 */
data class Game(
    val id: GameId,
    val title: String,
    val createdAt: Instant,
    val closedAt: Instant?,
    val status: GameStatus,
    val currentBankerName: String,
    val bankerPlayerId: PlayerId?,
    val rules: GameRules,
    val editedAfterClose: Boolean = false,
    val schemaVersion: Int = LEDGER_SCHEMA_VERSION,
) {
    val bankerIsPlayer: Boolean get() = bankerPlayerId != null

    companion object {
        const val LEDGER_SCHEMA_VERSION: Int = 1
        const val MAX_TITLE_LENGTH: Int = 60
    }
}

/**
 * Игрок в конкретной игре. Хранится только идентичность; статус, первый закуп, время расчёта и все суммы —
 * производные журнала (`PlayerState`, ADR 0005). Имя меняется upsert'ом («Переименовать»).
 */
data class Player(
    val id: PlayerId,
    val gameId: GameId,
    val name: String,
    val joinedAt: Instant,
)

/** Производный статус игрока (ТЗ §8). Вернувшийся игрок снова [PLAYING]. */
enum class PlayerStatus {
    /** За столом: после последнего действующего `CASH_OUT` есть действующий `BUY_IN`, либо выхода ещё не было. */
    PLAYING,
    /** Вышел и `outstanding ≥ 0` (квиты или банкир ещё должен ему). */
    CASHED_OUT,
    /** Вышел и `outstanding < 0` — «Ушёл с долгом N ₽». */
    LEFT_WITH_DEBT,
}
```

### 2.4. Журнал

```kotlin
package app.feltbook.core.domain.model

import kotlin.time.Instant

/**
 * Тип записи. Все `amount` — рубли = фишки (1:1), строго > 0, кроме `CASH_OUT` (≥ 0: «вышел с нулём»).
 * Семантика каждого типа и знаки — таблица в docs/plan/m1-domain.md §3.
 */
enum class EntryType {
    /** Выдача фишек игроку (закуп или докуп). playerId — кому. */
    BUY_IN,
    /** Рейк с покупки, начисляется игроку. */
    RAKE,
    /** Сбор банкира с покупки, начисляется игроку. Доход банкира `bankerName`. */
    BANKER_FEE,
    /** Наличные игрок → касса (оплата покупки, частичная оплата, расчёт при выходе, погашение долга). */
    PAYMENT,
    /** Сдача фишек при выходе: `amount` = F, всё, что было на руках (включая фишки, отданные чаевыми). */
    CASH_OUT,
    /**
     * Чаевые рублями из вывода игрока playerId:
     * `counterpartyPlayerId == null` — банкиру `bankerName` (в доход);
     * не null — уже вышедшему игроку (добавляются к его выводу W).
     * Фишки физически возвращаются в банк вместе с `CASH_OUT`.
     */
    TIP,
    /**
     * Чаевые фишками игроку `counterpartyPlayerId`, который ещё за столом: фишки переходят к нему из рук в руки,
     * в банк не возвращаются, денег не двигают. Добавлено к ТЗ §8 (ADR 0005) ради сверки фишек.
     */
    TIP_CHIPS,
    /** Наличные касса → игрок (выплата при выходе или позже). */
    PAYOUT,
    /** Касса → заведение: рейк отдан. playerId = null. */
    RAKE_PAID,
    /**
     * Банкир `bankerName` забрал свой доход наличными из кассы при передаче комнаты (ТЗ §6, шаг 3).
     * playerId = null. Добавлено к ТЗ §8 (ADR 0005): иначе касса нового банкира по журналу ≠ наличным.
     */
    INCOME_TAKEN,
    /** Долг игрока прощён: закрывает долг без денег и уменьшает доход банкира `bankerName` этой игры (ТЗ §3.7). */
    DEBT_FORGIVEN,
    /** Отмена записи `reversesEntryId`. Отменённая и отменяющая записи исключаются из всех сумм. */
    REVERSAL,
}

/**
 * Запись журнала. Только добавление: ни одно поле существующей записи не меняется, записи не удаляются
 * (кроме каскадного удаления игры целиком).
 *
 * @property seq позиция в журнале игры: 1, 2, 3… без пропусков; задаёт порядок (время может совпадать внутри операции).
 * @property operationId общая для всех записей одной операции.
 * @property playerId чей счёт затрагивает запись; null только у `RAKE_PAID`, `INCOME_TAKEN`.
 * @property counterpartyPlayerId получатель чаевых (`TIP` → вышедший игрок, `TIP_CHIPS` → игрок за столом).
 * @property bankerName банкир, при котором сделана запись (доход разных банкиров — ТЗ §6).
 * @property reversesEntryId только у `REVERSAL`.
 * @property note причина отмены у `REVERSAL` (необязательна), у остальных — null.
 * @property earlyExit только у `CASH_OUT`: выход раньше `minPlayMinutes`, подтверждён «Исключительный случай» (ТЗ §3.4).
 */
data class LedgerEntry(
    val id: EntryId,
    val gameId: GameId,
    val seq: Long,
    val operationId: OperationId,
    val type: EntryType,
    val playerId: PlayerId?,
    val amount: Long,
    val counterpartyPlayerId: PlayerId?,
    val bankerName: String,
    val createdAt: Instant,
    val reversesEntryId: EntryId? = null,
    val note: String? = null,
    val earlyExit: Boolean = false,
)
```

### 2.5. Справочник имён, акт передачи, агрегат и единица записи

```kotlin
package app.feltbook.core.domain.model

import kotlin.time.Instant

/**
 * Справочник имён для подсказок, статистики и открытых долгов (ТЗ §8).
 * Имена сопоставляются по точному совпадению после `PlayerNames.normalize`.
 * @property mergedInto «объединить двух игроков вручную»: это имя считается именем `mergedInto` (цепочки разрешены, циклы — нет).
 */
data class PlayerName(
    val name: String,
    val firstSeenAt: Instant,
    val mergedInto: String? = null,
)

/**
 * Акт передачи кассы (ТЗ §6, §8). В журнале экрана показывается строкой «Игра принята от <fromBanker>».
 * @property cashHandedOver сколько наличных старый банкир физически отдал новому.
 * @property incomeTaken сколько дохода старый банкир оставил себе (равно сумме записи `INCOME_TAKEN` этой передачи, 0 — не оставлял).
 * @property ledgerSeq последний `seq` журнала на момент передачи — акт воспроизводим по журналу.
 */
data class Handover(
    val id: HandoverId,
    val gameId: GameId,
    val fromBanker: String,
    val toBanker: String,
    val cashHandedOver: Long,
    val incomeTaken: Long,
    val ledgerSeq: Long,
    val at: Instant,
)

/**
 * Игра целиком — то, что читают движок, экран и пакет передачи.
 * @property entries журнал строго по возрастанию `seq` (репозиторий гарантирует порядок).
 * @property handovers по возрастанию `at`.
 */
data class GameSnapshot(
    val game: Game,
    val players: List<Player>,
    val entries: List<LedgerEntry>,
    val handovers: List<Handover>,
) {
    /** `seq` следующей записи. */
    val nextSeq: Long get() = (entries.lastOrNull()?.seq ?: 0L) + 1L

    fun player(id: PlayerId): Player? = players.firstOrNull { it.id == id }
}

/**
 * Единица записи: всё, что меняет одно действие банкира, — пишется одной транзакцией (`GameRepository.commit`).
 * Журнал и акты — только INSERT; игра и игроки — upsert.
 *
 * @property game новая версия строки игры (создание, статус, банкир, closedAt) или null — не менять.
 * @property players новые или переименованные игроки этой игры (upsert по id).
 * @property entries новые записи журнала, `seq` продолжает журнал без пропусков.
 * @property handovers новые акты передачи.
 */
data class GameChange(
    val gameId: GameId,
    val game: Game? = null,
    val players: List<Player> = emptyList(),
    val entries: List<LedgerEntry> = emptyList(),
    val handovers: List<Handover> = emptyList(),
)

/**
 * Применить изменение к снимку в памяти (тесты, цепочки команд, подготовка пакета передачи).
 * Бросает `IllegalArgumentException`, если `change.gameId` ≠ id снимка или `seq` не продолжает журнал.
 * Для `game == null` и пустых списков возвращает равный снимок.
 */
fun GameSnapshot.apply(change: GameChange): GameSnapshot
```

### 2.6. Настройки приложения

```kotlin
package app.feltbook.core.domain.model

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/**
 * Настройки (DataStore). Значения по умолчанию — ТЗ §3.1, §5.1 и макет Settings.
 *
 * @property rulesForNewGame правила, которыми заполняется «Новая игра». Одно значение: его меняют «Настройки → Правила
 *   по умолчанию» и старт игры с изменёнными правилами («подставляются из последних использованных», ТЗ §4). QUESTIONS №4.
 * @property lastBankerName имя банкира для «Новой игры», имя для Nearby-рекламы и `bankerName` погашений вне активной игры.
 * @property dynamicColor «Цвета обоев — Material You вместо сукна», по умолчанию выключено.
 * @property oddsCalculatorVisible пасхалка открыта и пункт «Калькулятор шансов» виден; «скрыть» = false.
 */
data class AppSettings(
    val rulesForNewGame: GameRules = GameRules.DEFAULT,
    val lastBankerName: String = "",
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val keepScreenOn: Boolean = true,
    val haptics: Boolean = true,
    val oddsCalculatorVisible: Boolean = false,
)
```

## 3. Семантика журнала

### 3.1. Поля по типам

| Тип | playerId | counterparty | amount | Кто создаёт |
| --- | --- | --- | --- | --- |
| `BUY_IN` | обязателен | null | > 0, ≥ `minBuyIn`, ≤ `MAX_AMOUNT` | закуп/докуп |
| `RAKE` | обязателен | null | > 0 (нулевой рейк не пишется) | закуп/докуп |
| `BANKER_FEE` | обязателен | null | > 0 (нулевой сбор не пишется) | закуп/докуп |
| `PAYMENT` | обязателен | null | > 0 | закуп «оплачено», «Оплата», расчёт при выходе, «Погасить» |
| `CASH_OUT` | обязателен | null | ≥ 0 | выход |
| `TIP` | даритель | null (банкир) или вышедший игрок | > 0 | выход |
| `TIP_CHIPS` | даритель | игрок за столом (обязателен) | > 0 | выход |
| `PAYOUT` | обязателен | null | > 0 | расчёт при выходе, «Выплатить» |
| `RAKE_PAID` | null | null | > 0 | «Рейк отдан», закрытие игры |
| `INCOME_TAKEN` | null | null | > 0 | передача комнаты с «Оставить доход себе» |
| `DEBT_FORGIVEN` | обязателен | null | > 0 | «Простить долг» |
| `REVERSAL` | как у цели | как у цели | = amount цели | отмена операции |

### 3.2. Действующие записи

`effective(entries)` = все записи, кроме `REVERSAL` и записей, на которые ссылается какой-либо `REVERSAL`. **Все суммы ниже — только по действующим записям.** Отменить можно только операцию целиком (все её ещё не отменённые записи), отменить `REVERSAL` нельзя (ошибка исправляется повтором операции), повторная отмена — ошибка. `INCOME_TAKEN` не отменяется (часть акта передачи).

### 3.3. Производные по игроку p

```
chipsBought  = Σ BUY_IN(p)
charged      = Σ BUY_IN(p) + Σ RAKE(p) + Σ BANKER_FEE(p)             «Начислено»
paid         = Σ PAYMENT(p)                                          «Оплачено»
paidOut      = Σ PAYOUT(p)
forgiven     = Σ DEBT_FORGIVEN(p)
chipsCashedOut = Σ CASH_OUT(p)                                       Σ F
tipsGiven    = Σ TIP(p) + Σ TIP_CHIPS(p)                             p — даритель
tipsReceived = Σ TIP(counterparty = p)                               только рублёвые
W            = chipsCashedOut − tipsGiven + tipsReceived             ТЗ §3.3 (фишечные чаевые уже внутри F получателя)
outstanding  = W + paid + forgiven − charged − paidOut               > 0 банкир должен игроку, < 0 игрок должен банкиру
result       = W − chipsBought                                       ТЗ §3.3, для статистики (только у вышедших)
balanceAtExit = outstanding, посчитанный по записям с seq ≤ последней записи последней операции CASH_OUT игрока
                                                                     = «Баланс» ТЗ §3.3 в момент выхода (до расчёта)
```

Сессия: с первого действующего `BUY_IN` после последнего действующего `CASH_OUT` игрока. `carriedOver` = `outstanding − (paid_сессии − charged_сессии)`: долг/переплата прошлых выходов этой же игры плюс всё, что после начала сессии не является её покупкой или оплатой (например, выплата прошлого долга банкира). Обычно равно `outstanding` по записям с `seq` < начала сессии; для первой сессии 0. Так `carriedOver + paid − charged = outstanding` всегда.

### 3.4. Производные по столу

```
chipsIssued   = Σ BUY_IN
chipsReturned = Σ CASH_OUT − Σ TIP_CHIPS                фишки, физически вернувшиеся в банк («сдано вышедшими»)
chipsInPlay   = chipsIssued − chipsReturned
chipDiff      = chipsReturned − chipsIssued             на закрытии: 0 — «Фишки сошлись»
rakeAccrued   = Σ RAKE;  rakePaid = Σ RAKE_PAID;  rakeDue = rakeAccrued − rakePaid
bankerIncome  = Σ BANKER_FEE + Σ TIP(counterparty=null) − Σ DEBT_FORGIVEN      (ТЗ §3.5, §3.7), по bankerName — раздельно
incomeTaken   = Σ INCOME_TAKEN
cashOnHand    = Σ PAYMENT − Σ PAYOUT − Σ RAKE_PAID − Σ INCOME_TAKEN             «Касса на руках» (получено − выплачено)
playersOwe    = Σ_p max(0, −outstanding_p)
bankerOwes    = Σ_p max(0,  outstanding_p)
expectedCash  = rakeDue + bankerIncome − incomeTaken                            «Должно быть на руках»
```

**Тождество кассы** (алгебраически верно при любом журнале; property-тест):

```
cashOnHand = expectedCash + chipsInPlay + bankerOwes − playersOwe
```

Вывод: Σ_p W = Σ CASH_OUT − Σ TIP_CHIPS − Σ TIP(банкиру) (рублёвые чаевые между игроками взаимно гасятся); подставить в Σ_p outstanding. Следствия: когда все вышли и фишки сошлись, `cashOnHand = expectedCash − Σ долгов + Σ невыплаченного`; недостача ТЗ §3.7 = `playersOwe`.

## 4. Движок

### 4.1. Деньги

```kotlin
package app.feltbook.core.domain.engine

object Money {
    /** Верхняя граница любой суммы во входе (1e8 ₽): `amount * 100` заведомо помещается в Long. */
    const val MAX_AMOUNT: Long = 100_000_000L

    /**
     * `round(amount * percent / 100)` математически, половина — вверх: `(amount * percent + 50) / 100`.
     * 1 250 × 10 % = 125; 1 255 × 10 % = 126 (125,5 → 126); 1 254 × 10 % = 125.
     * @throws IllegalArgumentException если `amount !in 0..MAX_AMOUNT` или `percent !in 0..100`.
     */
    fun percentOf(amount: Long, percent: Long): Long

    /** Деление с округлением половины вверх для средних (средний закуп). `numerator ≥ 0`, `denominator > 0`. */
    fun divRoundHalfUp(numerator: Long, denominator: Long): Long
}
```

### 4.2. Штамп операции, ошибки, предупреждения

```kotlin
package app.feltbook.core.domain.engine

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Время и генератор id для одной операции. Движок не читает часы сам — так команды чистые и детерминированные в тестах.
 * @param at обрезается до миллисекунд.
 */
class Stamp(at: Instant, private val newUuid: () -> Uuid = { Uuid.random() }) {
    val at: Instant
    fun nextUuid(): Uuid

    companion object {
        fun now(clock: Clock = Clock.System): Stamp
    }
}

/**
 * Почему действие невозможно. Котировки возвращают список блокеров (кнопка неактивна + подсказка),
 * команды при блокере бросают [DomainRuleViolation] — UI обязан сначала спросить котировку.
 */
sealed interface DomainError {
    data class InvalidRules(val problems: List<RulesProblem>) : DomainError
    data class WrongGameStatus(val actual: GameStatus, val allowed: Set<GameStatus>) : DomainError
    data class AnotherGameActive(val gameId: GameId) : DomainError
    data class PlayerNotFound(val playerId: PlayerId) : DomainError
    data class PlayerNotPlaying(val playerId: PlayerId, val status: PlayerStatus) : DomainError
    data object NameBlank : DomainError
    data object NameTooLong : DomainError
    data object TitleTooLong : DomainError
    /** Имя уже за этим столом — вместо нового игрока предложить «вернуть» существующего. */
    data class NameTaken(val existing: PlayerId) : DomainError
    data object AmountNotPositive : DomainError
    data object AmountTooLarge : DomainError
    data class BelowMinBuyIn(val minBuyIn: Long) : DomainError
    data object SplitPaymentDisabled : DomainError
    data class PaymentExceedsDebt(val debt: Long) : DomainError
    data class PayoutExceedsOwed(val owed: Long) : DomainError
    data object NothingToSettle : DomainError
    data class PartialOutOfRange(val max: Long) : DomainError
    data object TipNotPositive : DomainError
    data class TipExceedsChips(val chips: Long) : DomainError
    data object TipToSelf : DomainError
    data class TipRecipientInvalid(val playerId: PlayerId) : DomainError
    /** Получатель «за столом», но ещё не покупал (банкир-игрок без закупа) — фишками дарить нельзя. */
    data class TipRecipientNotSeated(val playerId: PlayerId) : DomainError
    data class EarlyExitNotConfirmed(val minutesPlayed: Long, val minPlayMinutes: Int) : DomainError
    data class OperationNotFound(val operationId: OperationId) : DomainError
    data class OperationNotReversible(val reason: NotReversibleReason) : DomainError
    data class NoDebt(val playerId: PlayerId) : DomainError
    data class ForgiveExceedsDebt(val debt: Long) : DomainError
    data object NoRakeDue : DomainError
    data class PlayersStillPlaying(val playerIds: List<PlayerId>) : DomainError
    data class NotConfirmed(val missing: Set<CloseConfirmation>) : DomainError
    data class BankerNotAPlayer(val playerId: PlayerId) : DomainError
    /** Приём передачи: локальная копия расходится с входящей (`Imports.plan` дал `Conflict`). Добавлено в M1 (§11). */
    data class ImportConflict(val reason: ImportConflictReason) : DomainError
}

enum class NotReversibleReason {
    ALREADY_REVERSED, IS_REVERSAL, INCOME_TAKEN, GAME_READ_ONLY, NOT_ALLOWED_AFTER_CLOSE,
    /** Первая покупка игрока при действующих докупах — «Сначала отмените более поздние докупы». */
    LATER_PURCHASES_EXIST,
    /** Выход с TIP_CHIPS, а получатель с тех пор вышел — «Сначала отмените выход получателя чаевых». */
    TIP_RECIPIENT_CASHED_OUT,
}

/** Предупреждения не блокируют, показываются в шторке до подтверждения (ТЗ §5.1 «Подозрительная сумма»). */
sealed interface DomainWarning {
    /** «20 000 фишек — точно? Обычно здесь берут 2 000». `typical` = средняя покупка игры (null — покупок ещё нет). */
    data class SuspiciousBuyIn(val chips: Long, val typical: Long?) : DomainWarning
    /** «Фишек на выходе больше, чем есть в игре». */
    data class ChipsExceedInPlay(val chips: Long, val chipsInPlay: Long) : DomainWarning
}

class DomainRuleViolation(val error: DomainError) : IllegalStateException(error.toString())

/** Пороги ТЗ §5.1. Покупка подозрительна, если `chips > 5 × средняя` или `chips ≥ 20 000` («20 000 фишек — точно?»). */
object Suspicion {
    const val AVERAGE_MULTIPLIER: Long = 5
    const val ABSOLUTE_LIMIT: Long = 20_000
}
```

### 4.3. Журнал: действующие записи

```kotlin
package app.feltbook.core.domain.engine

object Ledger {
    /** Записи без `REVERSAL` и без отменённых (§3.2), порядок сохраняется. */
    fun effective(entries: List<LedgerEntry>): List<LedgerEntry>

    /** id записей, отменённых каким-либо `REVERSAL`. */
    fun reversedIds(entries: List<LedgerEntry>): Set<EntryId>
}
```

### 4.4. Состояние игры (табло, карточки игроков)

```kotlin
package app.feltbook.core.domain.engine

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Производные по игроку (§3.3). Знаки: `outstanding > 0` — банкир должен игроку, `< 0` — игрок должен.
 *
 * @property isBanker игрок — текущий банкир (`game.bankerPlayerId`).
 * @property isVoid у игрока есть записи и все они отменены (ошибочное добавление) — стол его не показывает, журнал показывает.
 * @property firstBuyInAt первый действующий `BUY_IN` в игре — от него таймер «в игре 47 мин» и ранний выход (ТЗ §3.4, §4).
 * @property lastCashOutAt последний действующий `CASH_OUT` («settledAt» ТЗ §8), null — ещё не выходил.
 * @property buyInCount число действующих операций покупки; `rebuyCount = max(0, buyInCount − 1)` — значок «докуп ×N».
 * @property tipsToBanker сколько этот игрок дал чаевых банкиру (расшифровка «Банкиру» по игрокам).
 * @property chipTipsReceived фишечные чаевые, полученные за столом (справочно: уже внутри его F).
 * @property withdrawal W по всем выходам этой игры.
 * @property balanceAtExit «Баланс» ТЗ §3.3 на момент последнего выхода (до расчёта); null — не выходил.
 * @property result `W − chipsBought`; null, пока игрок за столом.
 * @property session текущая сессия (только у PLAYING).
 * @property earlyExit хотя бы один действующий выход помечен «Исключительный случай».
 */
data class PlayerState(
    val player: Player,
    val isBanker: Boolean,
    val status: PlayerStatus,
    val isVoid: Boolean,
    val firstBuyInAt: Instant?,
    val lastCashOutAt: Instant?,
    val buyInCount: Int,
    val rebuyCount: Int,
    val chipsBought: Long,
    val rake: Long,
    val bankerFee: Long,
    val charged: Long,
    val paid: Long,
    val paidOut: Long,
    val forgiven: Long,
    val chipsCashedOut: Long,
    val tipsGiven: Long,
    val tipsToBanker: Long,
    val tipsReceived: Long,
    val chipTipsReceived: Long,
    val withdrawal: Long,
    val outstanding: Long,
    val balanceAtExit: Long?,
    val result: Long?,
    val session: SessionFigures?,
    val earlyExit: Boolean,
) {
    /** Сколько игрок должен банкиру сейчас (≥ 0). */
    val debt: Long get() = maxOf(0L, -outstanding)
    /** Сколько банкир должен игроку сейчас (≥ 0). */
    val owedByBanker: Long get() = maxOf(0L, outstanding)

    /** Время от первого закупа; null — закупов нет. */
    fun playedFor(now: Instant): Duration?

    /** Выход сейчас — ранний (`now < firstBuyInAt + minPlayMinutes`). Без закупов — false. */
    fun isEarlyExit(rules: GameRules, now: Instant): Boolean
}

/** Текущая сессия игрока (после возврата — только новые покупки и оплаты, ТЗ §4). */
data class SessionFigures(
    val startedAt: Instant,
    val chipsBought: Long,
    val charged: Long,
    val paid: Long,
    /**
     * Долг (< 0) или переплата (> 0) прошлых выходов этой игры, переходящие в этот выход:
     * `outstanding − (paid − charged)` — всё, что не входит в покупки и оплаты сессии (§11).
     */
    val carriedOver: Long,
)

/** Доход одного банкира в игре (ТЗ §6 «Доход разных банкиров»). `net = fees + tips − forgiven`. */
data class BankerIncome(
    val bankerName: String,
    val fees: Long,
    val tips: Long,
    val forgiven: Long,
    val taken: Long,
) {
    val net: Long get() = fees + tips - forgiven
}

/** Табло стола (ТЗ §4 «Расчёт и сводка», п. 1), §3.4 этого документа. */
data class TableSummary(
    val chipsIssued: Long,
    val chipsReturned: Long,
    val chipsInPlay: Long,
    val rebuyCount: Int,
    val playersAtTable: Int,
    val playersLeft: Int,
    val playersOwe: Long,
    val bankerOwes: Long,
    val cashOnHand: Long,
    val rakeAccrued: Long,
    val rakePaid: Long,
    val rakeDue: Long,
    val bankerIncome: Long,
    val incomeTaken: Long,
    /** По `bankerName` в порядке первого появления. Один банкир — один элемент. */
    val incomeByBanker: List<BankerIncome>,
    val expectedCash: Long,
)

/** Состояние игры: игроки (без `isVoid` — отдельным списком не нужны, фильтрует UI) и табло. */
data class GameState(
    val snapshot: GameSnapshot,
    val players: List<PlayerState>,
    val table: TableSummary,
) {
    val game: Game get() = snapshot.game
    fun player(id: PlayerId): PlayerState?
    /** «Игра с долгом»: есть игрок LEFT_WITH_DEBT. */
    val hasOpenDebt: Boolean get() = players.any { it.status == PlayerStatus.LEFT_WITH_DEBT }
}

object GameStates {
    fun compute(snapshot: GameSnapshot): GameState
}
```

### 4.5. Покупка: котировка

```kotlin
package app.feltbook.core.domain.engine

/**
 * Статус оплаты покупки (ТЗ §3.2). Одним статусом — [DEBT] или [PAID]; раздельно — любые сочетания,
 * только если `rules.splitPayment`. «Ни то ни другое» не бывает: неоплаченное = долг (ТЗ §3.7).
 */
data class BuyInPayment(val chipsPaid: Boolean, val feesPaid: Boolean) {
    val isSingleStatus: Boolean get() = chipsPaid == feesPaid

    companion object {
        val DEBT = BuyInPayment(chipsPaid = false, feesPaid = false)
        val PAID = BuyInPayment(chipsPaid = true, feesPaid = true)
    }
}

/**
 * Итог покупки до подтверждения: «Фишки 2 000 + рейк 200 + сбор 100 = 2 300 ₽».
 * @property playerId null — новый игрок; id void-игрока с тем же именем — его «вернут» (см. [Pricing.quoteNewPlayer]).
 * @property isFirstBuyIn у игрока нет действующих BUY_IN в этой игре (строка «Закуп», а не «Докуп»).
 * @property isReturn игрок вышел и возвращается — это докуп (ТЗ §4).
 * @property feeWaivedForBanker сбор не взят, потому что игрок — банкир (строка «без сбора»).
 * @property feeAlreadyTaken разовый режим, сбор уже взят («Сбор банкира — уже взят»).
 */
data class BuyInQuote(
    val playerId: PlayerId?,
    val chips: Long,
    val rake: Long,
    val bankerFee: Long,
    val isFirstBuyIn: Boolean,
    val isReturn: Boolean,
    val feeWaivedForBanker: Boolean,
    val feeAlreadyTaken: Boolean,
    val blockers: List<DomainError>,
    val warnings: List<DomainWarning>,
) {
    /** К оплате = D + Рейк(D) + Сбор(D). */
    val total: Long get() = chips + rake + bankerFee
    val fees: Long get() = rake + bankerFee
    /** Сумма `PAYMENT`, которую запишет покупка при данном статусе. */
    fun paidNow(payment: BuyInPayment): Long =
        (if (payment.chipsPaid) chips else 0L) + (if (payment.feesPaid) fees else 0L)
    /** Сумма, уходящая в долг: «Итого в долг 2 200 ₽». */
    fun toDebt(payment: BuyInPayment): Long = total - paidNow(payment)
    val canConfirm: Boolean get() = blockers.isEmpty()
}

object Pricing {
    /** Рейк(D): `percentOf(D, rakeValue)` или `rakeValue`. */
    fun rake(chips: Long, rules: GameRules): Long

    /**
     * Сбор(D), ТЗ §3.2: 0 для банкира; в `ONCE_*` — только при `isFirstBuyIn`; в `EACH_*` — всегда;
     * `*_PERCENT` — `percentOf(D, bankerFeeValue)`, `*_FIXED` — `bankerFeeValue`.
     */
    fun bankerFee(chips: Long, rules: GameRules, isFirstBuyIn: Boolean, isBanker: Boolean): Long

    /** Средняя покупка игры (по действующим BUY_IN, округление половины вверх); null — покупок нет. */
    fun typicalPurchase(snapshot: GameSnapshot): Long?

    /**
     * Котировка покупки существующего игрока (докуп или возврат). Блокеры: статус игры ≠ RUNNING, игрок не найден/void,
     * сумма ≤ 0, > MAX_AMOUNT, < minBuyIn. Предупреждение SuspiciousBuyIn.
     */
    fun quoteBuyIn(snapshot: GameSnapshot, playerId: PlayerId, chips: Long): BuyInQuote

    /**
     * Котировка первого закупа нового игрока. Дополнительно блокеры имени: пустое, длиннее `PlayerNames.MAX_LENGTH`,
     * уже есть за этим столом (`NameTaken`, сравнение после `PlayerNames.normalize`). Совпадение с void-игроком — не блокер:
     * `playerId` котировки = его id, `Commands.addPlayer` его «возвращает» (иначе вернуть void-игрока было бы нечем:
     * `quoteBuyIn` для void — PlayerNotFound).
     */
    fun quoteNewPlayer(snapshot: GameSnapshot, name: String, chips: Long): BuyInQuote
}
```

### 4.6. Выход: котировка

```kotlin
package app.feltbook.core.domain.engine

import kotlin.time.Instant

sealed interface TipTarget {
    data object Banker : TipTarget
    data class ToPlayer(val playerId: PlayerId) : TipTarget
}

/** Чаевые (необязательны, по умолчанию нет — `null` в API). */
data class Tip(val amount: Long, val to: TipTarget)

/**
 * Итог выхода до подтверждения (ТЗ §3.3, макеты CashOut, DebtCashOut).
 * @property tipAsChips получатель за столом → запишется `TIP_CHIPS`, иначе `TIP`.
 * @property withdrawal W этого выхода = F − T.
 * @property outstandingBefore `outstanding` игрока до выхода («Долг игрока −3 400 ₽»).
 * @property balance `outstandingBefore + withdrawal`: > 0 «Банкир выплачивает N ₽», < 0 «Игрок доплачивает N ₽», 0 — квиты.
 * @property resultThisGame «Результат за игру: +753 фишки» = (W всех выходов + этот W) − chipsBought.
 * @property minutesPlayed от первого закупа; null — закупов нет.
 */
data class CashOutQuote(
    val playerId: PlayerId,
    val chips: Long,
    val tip: Tip?,
    val tipAsChips: Boolean,
    val withdrawal: Long,
    val sessionCharged: Long,
    val sessionPaid: Long,
    val carriedOver: Long,
    val outstandingBefore: Long,
    val balance: Long,
    val resultThisGame: Long,
    val minutesPlayed: Long?,
    val isEarlyExit: Boolean,
    val blockers: List<DomainError>,
    val warnings: List<DomainWarning>,
) {
    val canConfirm: Boolean get() = blockers.isEmpty()
}

/** Как закрыт расчёт при выходе (ТЗ §3.7). */
sealed interface SettlementChoice {
    /** «Расчёт произведён» / «Получил N ₽»: `PAYOUT(balance)` или `PAYMENT(−balance)`; при balance = 0 — ничего. */
    data object Full : SettlementChoice
    /** «Отдал часть…»: 0 < amount < |balance|, в направлении знака balance. Остаток — долг (или долг банкира). */
    data class Partial(val amount: Long) : SettlementChoice
    /** «Не заплатил» (или банкир не выплатил): денежных записей нет, игрок → LEFT_WITH_DEBT при balance < 0. */
    data object None : SettlementChoice
}

object CashOuts {
    /**
     * Котировка выхода. Блокеры: игрок не PLAYING, статус игры не RUNNING/SETTLING, F < 0 или > MAX_AMOUNT,
     * чаевые ≤ 0, > F, себе, получателю void/не из этой игры. Предупреждение ChipsExceedInPlay (`F > chipsInPlay`).
     * Ранний выход — не блокер котировки, а флаг `isEarlyExit` (команда потребует подтверждения).
     */
    fun quote(snapshot: GameSnapshot, playerId: PlayerId, chips: Long, tip: Tip?, now: Instant): CashOutQuote

    /** Быстрое округление «до 100»/«до 500»: `chips % step` (3 753 → 53 и 253). `step > 0`. */
    fun roundingTip(chips: Long, step: Long): Long
}
```

### 4.7. Команды (производят `GameChange`)

```kotlin
package app.feltbook.core.domain.engine

/** Ввод экрана «Новая игра». `title` пустой → движок подставляет переданный `defaultTitle` (дата и время форматирует UI). */
data class NewGameInput(
    val title: String,
    val defaultTitle: String,
    val bankerName: String,
    val bankerPlays: Boolean,
    val rules: GameRules,
)

/** Подтверждения, без которых нельзя закрыть игру (ТЗ §3.5, §3.7; диалог/подпись hold-кнопки). */
enum class CloseConfirmation {
    /** Фишки не сошлись (`chipDiff ≠ 0`). */
    CHIPS_NOT_RECONCILED,
    /** Есть долги игроков — «Закрыть игру с долгом». */
    OPEN_DEBTS,
    /** Банкир ещё должен вышедшим (`bankerOwes > 0`). */
    UNPAID_PAYOUTS,
}

/**
 * Все команды чистые: читают снимок, возвращают изменение; при нарушении правила бросают [DomainRuleViolation]
 * с тем же [DomainError], что показала котировка. `bankerName` новых записей = `game.currentBankerName`,
 * если не сказано иное; `seq` продолжает журнал; одна команда — одна операция (`OperationId`), кроме [cashOut].
 */
object Commands {
    /**
     * Старт игры: `Game(status = RUNNING)`, правила — слепок `input.rules`; при `bankerPlays` — игрок-банкир без закупа
     * (`bankerPlayerId`), закупается обычной покупкой. Блокеры: `activeGame != null`, правила с проблемами, пустое имя банкира.
     */
    fun startGame(input: NewGameInput, activeGame: Game?, stamp: Stamp): GameChange

    /**
     * Новый игрок + первый закуп одной операцией: Player + BUY_IN, RAKE?, BANKER_FEE?, PAYMENT?.
     * Если имя совпадает с void-игроком этого стола — возвращает его (без нового Player), а не создаёт дубль.
     */
    fun addPlayer(snapshot: GameSnapshot, name: String, chips: Long, payment: BuyInPayment, stamp: Stamp): GameChange

    /** Докуп или возврат вышедшего: BUY_IN, RAKE?, BANKER_FEE?, PAYMENT?. `payment` раздельный — только при `splitPayment`. */
    fun buyIn(snapshot: GameSnapshot, playerId: PlayerId, chips: Long, payment: BuyInPayment, stamp: Stamp): GameChange

    /**
     * Оплата долга полностью или частично: PAYMENT(amount), `0 < amount ≤ debt`.
     * Статус игры RUNNING, SETTLING или CLOSED (погашение старого долга, ТЗ §3.7 — запись в старую игру, без editedAfterClose).
     * @param bankerName кто принял деньги; по умолчанию — банкир игры. Для погашения из новой игры —
     *   текущий банкир телефона (`AppSettings.lastBankerName` или банкир активной игры).
     */
    fun pay(snapshot: GameSnapshot, playerId: PlayerId, amount: Long, stamp: Stamp, bankerName: String = snapshot.game.currentBankerName): GameChange

    /** «Выплатить»: PAYOUT(amount), `0 < amount ≤ owedByBanker`. Статусы как у [pay]. */
    fun payOut(snapshot: GameSnapshot, playerId: PlayerId, amount: Long, stamp: Stamp, bankerName: String = snapshot.game.currentBankerName): GameChange

    /**
     * Выход: две операции в одном изменении (каждую можно отменить отдельно):
     * 1) CASH_OUT(F, earlyExit) + TIP | TIP_CHIPS — «Выход»;
     * 2) PAYMENT | PAYOUT по [settlement] — «Расчёт» (нет записи при None или balance = 0).
     * Ранний выход без `earlyExitConfirmed` — EarlyExitNotConfirmed.
     */
    fun cashOut(
        snapshot: GameSnapshot,
        playerId: PlayerId,
        chips: Long,
        tip: Tip?,
        settlement: SettlementChoice,
        earlyExitConfirmed: Boolean,
        stamp: Stamp,
    ): GameChange

    /**
     * Отмена операции: REVERSAL на каждую её действующую запись (одна новая операция).
     * Запрещено: операция из REVERSAL, уже отменённая, INCOME_TAKEN, игра HANDED_OVER; в CLOSED — всё, кроме операций
     * PAYMENT/PAYOUT/DEBT_FORGIVEN (ошибка в погашении долга), для остального игру переоткрывают ([reopen]).
     */
    fun reverse(snapshot: GameSnapshot, operationId: OperationId, reason: String?, stamp: Stamp): GameChange

    /** «Простить долг»: DEBT_FORGIVEN(amount ?: debt), игрок LEFT_WITH_DEBT, `0 < amount ≤ debt`. Статусы как у [pay]. */
    fun forgiveDebt(snapshot: GameSnapshot, playerId: PlayerId, amount: Long?, stamp: Stamp): GameChange

    /** «Рейк отдан заведению»: RAKE_PAID(rakeDue), `rakeDue > 0`, статус RUNNING или SETTLING. */
    fun payRake(snapshot: GameSnapshot, stamp: Stamp): GameChange

    /** «Завершить игру»: RUNNING → SETTLING. */
    fun finish(snapshot: GameSnapshot): GameChange

    /** Передумали завершать: SETTLING → RUNNING. */
    fun resume(snapshot: GameSnapshot): GameChange

    /**
     * «Закрыть игру» (hold): SETTLING → CLOSED, `closedAt = stamp.at`; при `payRake` и `rakeDue > 0` — та же транзакция
     * пишет RAKE_PAID (кнопка «Рейк отдан · закрыть игру»). Блокеры: PlayersStillPlaying; NotConfirmed, если
     * `Settlements.compute(...).requiredConfirmations ⊄ acknowledged`.
     */
    fun close(snapshot: GameSnapshot, payRake: Boolean, acknowledged: Set<CloseConfirmation>, stamp: Stamp): GameChange

    /** «Переоткрыть для исправления»: CLOSED → RUNNING, `closedAt = null`, `editedAfterClose = true`. Блокер: другая активная игра. */
    fun reopen(snapshot: GameSnapshot, activeGame: Game?): GameChange

    /** «Переименовать»: upsert Player с новым именем; блокеры имени как у новой игры. Журнал не меняется. */
    fun renamePlayer(snapshot: GameSnapshot, playerId: PlayerId, newName: String): GameChange
}
```

### 4.8. Итог игры

```kotlin
package app.feltbook.core.domain.engine

/**
 * Строка таблицы «Расчёт с игроками» (макеты Settlement, DebtSettlement):
 * «Андрей платит 2 300 ₽ · начислено 2 300 · оплачено 0 · вывод 0».
 * @property paidBeforeExit оплачено до последнего выхода (по `seq`), без расчёта при выходе и поздних погашений.
 * @property balanceAtExit «платит» (< 0) / «получает» (> 0) — сумма расчёта при выходе.
 * @property outstanding остаток сейчас: 0 — «✓ рассчитан», < 0 — «долг N», > 0 — «банкир должен N».
 */
data class SettlementRow(
    val playerId: PlayerId,
    val name: String,
    val isBanker: Boolean,
    val status: PlayerStatus,
    val charged: Long,
    val paidBeforeExit: Long,
    val withdrawal: Long,
    val tipsGiven: Long,
    val balanceAtExit: Long?,
    val outstanding: Long,
    val earlyExit: Boolean,
)

/** Должник/кредитор для блоков «Не оплачено» и «Ещё не выплачено». */
data class PlayerAmount(val playerId: PlayerId, val name: String, val amount: Long)

/**
 * Итоговый расчёт (ТЗ §3.5, §4 п. 3). Тождество: `cashOnHand = expectedCash − chipDiff + Σ unpaidPayouts − Σ unpaidDebts`
 * (когда все вышли; см. §3.4 документа).
 * @property expectedCash «Должно быть на руках/в кассе» = rakeDue + bankerIncome − incomeTaken.
 * @property unpaidDebts блок «Не оплачено»; `shortage = Σ` — «Недостача N ₽ — не получено от X».
 * @property requiredConfirmations что нужно подтвердить для закрытия ([CloseConfirmation]).
 * @property blockers сейчас — только PlayersStillPlaying / WrongGameStatus.
 */
data class GameSettlement(
    val chipsIssued: Long,
    val chipsReturned: Long,
    val chipDiff: Long,
    val rebuyCount: Int,
    val playerCount: Int,
    val rakeAccrued: Long,
    val rakePaid: Long,
    val rakeDue: Long,
    val bankerFees: Long,
    val tipsToBanker: Long,
    val forgiven: Long,
    val bankerIncome: Long,
    val incomeTaken: Long,
    val incomeByBanker: List<BankerIncome>,
    val expectedCash: Long,
    val cashOnHand: Long,
    val unpaidDebts: List<PlayerAmount>,
    val unpaidPayouts: List<PlayerAmount>,
    val rows: List<SettlementRow>,
    val requiredConfirmations: Set<CloseConfirmation>,
    val blockers: List<DomainError>,
) {
    val chipsReconciled: Boolean get() = chipDiff == 0L
    val shortage: Long get() = unpaidDebts.sumOf { it.amount }
}

object Settlements {
    fun compute(snapshot: GameSnapshot): GameSettlement
}
```

### 4.9. Журнал для экрана

```kotlin
package app.feltbook.core.domain.engine

import kotlin.time.Instant

/** Вид операции — выводится из состава записей (в БД не хранится). */
enum class OperationKind {
    /** Первый действующий закуп игрока — «Закуп». */
    FIRST_BUY_IN,
    /** «Докуп» (в т. ч. возврат). */
    REBUY,
    /** PAYMENT отдельно от покупки — «Оплата»/«Погашение». */
    PAYMENT,
    /** PAYOUT отдельно — «Выплачено». */
    PAYOUT,
    /** PAYMENT/PAYOUT, сразу следующие (seq − 1) за выходом того же игрока с тем же временем — «Расчёт при выходе». */
    SETTLEMENT,
    /** CASH_OUT + чаевые — «Выход». */
    CASH_OUT,
    DEBT_FORGIVEN,
    RAKE_PAID,
    INCOME_TAKEN,
}

data class ReversalInfo(val at: Instant, val bankerName: String, val reason: String?)

sealed interface JournalItem {
    val at: Instant

    /**
     * Строка журнала: операция со всеми записями («Закуп 1 000 · в долг», «↳ рейк 10 % 100», «↳ сбор банкира 100»).
     * Отменённая операция остаётся строкой с пометкой «отменено» ([reversal]); операции REVERSAL отдельными строками не выводятся.
     */
    data class Operation(
        val operationId: OperationId,
        override val at: Instant,
        val bankerName: String,
        val playerId: PlayerId?,
        val kind: OperationKind,
        val entries: List<LedgerEntry>,
        val reversal: ReversalInfo?,
        val reversible: Boolean,
    ) : JournalItem

    /** «Игра принята от <fromBanker>». */
    data class HandoverItem(val handover: Handover, override val at: Instant) : JournalItem
}

object Journal {
    /** Весь журнал игры, новые сверху. Строка акта передачи стоит сразу после записи `handover.ledgerSeq`. */
    fun items(snapshot: GameSnapshot): List<JournalItem>

    /** Журнал карточки игрока: операции, где он `playerId` или получатель чаевых. */
    fun forPlayer(snapshot: GameSnapshot, playerId: PlayerId): List<JournalItem.Operation>
}
```

### 4.10. Имена, долги, статистика, история

```kotlin
package app.feltbook.core.domain.engine

import kotlin.time.Instant

object PlayerNames {
    const val MAX_LENGTH: Int = 40

    /** trim + схлопывание пробелов. Сравнение имён везде — после нормализации, с учётом регистра (ТЗ §4: точное совпадение). */
    fun normalize(raw: String): String

    /** Каноническое имя: идёт по `mergedInto` до конца; при цикле — исходное имя. */
    fun canonical(name: String, directory: List<PlayerName>): String

    /** Можно ли объединить `from` → `into`: разные, оба есть, не образует цикл. */
    fun canMerge(from: String, into: String, directory: List<PlayerName>): Boolean

    /** Подсказки для поля «Имя»: по префиксу без учёта регистра, сначала недавние; без `exclude` (уже за столом). */
    fun suggestions(prefix: String, directory: List<PlayerName>, exclude: Set<String>, limit: Int = 8): List<String>
}

/**
 * Открытый долг (экраны Debts, DebtWarning, «Открытые долги» на главной).
 * @property owedAtExit баланс при выходе (> 0); «отдал 600 из 1 200» = `owedAtExit − amount` из `owedAtExit`.
 * @property since время выхода (последний CASH_OUT) — «с 18 окт», «15 дней».
 */
data class OpenDebt(
    val canonicalName: String,
    val playerName: String,
    val playerId: PlayerId,
    val gameId: GameId,
    val gameTitle: String,
    val gameCreatedAt: Instant,
    val amount: Long,
    val owedAtExit: Long,
    val since: Instant,
)

object Debts {
    /**
     * Все открытые долги: игроки LEFT_WITH_DEBT во всех играх, кроме HANDED_OVER (их долги ушли новому банкиру).
     * Сортировка — старые сверху.
     */
    fun open(games: List<GameSnapshot>, directory: List<PlayerName>): List<OpenDebt>

    /** Долги человека по каноническому имени, кроме игры `excludeGameId` (предупреждение «Влад должен 1 053 ₽ с 18 окт»). */
    fun forName(name: String, games: List<GameSnapshot>, directory: List<PlayerName>, excludeGameId: GameId?): List<OpenDebt>
}

/** Строка истории (ТЗ §4 «История» п. 1, макеты History, Home). `turnover` = Σ BUY_IN. */
data class GameHistoryItem(
    val gameId: GameId,
    val title: String,
    val status: GameStatus,
    val startedAt: Instant,
    /** closedAt, время передачи (HANDED_OVER) или null у активной. */
    val endedAt: Instant?,
    val playerCount: Int,
    val rebuyCount: Int,
    val turnover: Long,
    val rake: Long,
    val bankerIncome: Long,
    val hasOpenDebt: Boolean,
    val editedAfterClose: Boolean,
)

/**
 * Статистика по имени за период (ТЗ §4 п. 4). Считаются только игры CLOSED и HANDED_OVER и только вышедшие игроки
 * (QUESTIONS №7). Средние — в UI через `Money.divRoundHalfUp` / форматирование «1,4» из целых счётчиков (без Double).
 * @property totalResult Σ (W − Σ закупов без рейка и сбора).
 * @property totalChipsBought Σ BUY_IN — «средний закуп» = totalChipsBought / gamesPlayed (QUESTIONS №6).
 */
data class PlayerStats(
    val canonicalName: String,
    val gamesPlayed: Int,
    val totalResult: Long,
    val totalChipsBought: Long,
    val buyInCount: Int,
    val rebuyCount: Int,
    val openDebt: Long,
    val lastPlayedAt: Instant?,
) {
    fun averageBuyInPerGame(): Long = if (gamesPlayed == 0) 0L else Money.divRoundHalfUp(totalChipsBought, gamesPlayed.toLong())
}

data class GameIncomePoint(val gameId: GameId, val title: String, val startedAt: Instant, val bankerIncome: Long)

/** Итоги периода (макет Stats): «Игр 12 · Оборот фишек 214 500 · Рейк 21 450 · Доход банкира 7 100». */
data class PeriodStats(
    val gameCount: Int,
    val turnover: Long,
    val rake: Long,
    val bankerIncome: Long,
    /** По возрастанию времени — для графика «Доход банкира, последние N игр». */
    val incomeByGame: List<GameIncomePoint>,
    /** По убыванию totalResult. */
    val players: List<PlayerStats>,
)

object Stats {
    fun historyItem(snapshot: GameSnapshot): GameHistoryItem

    /** Игры CLOSED/HANDED_OVER с `createdAt ∈ [from, to)`; null — без границы. */
    fun period(games: List<GameSnapshot>, directory: List<PlayerName>, from: Instant?, to: Instant?): PeriodStats

    fun player(canonicalName: String, games: List<GameSnapshot>, directory: List<PlayerName>): PlayerStats?
}
```

### 4.11. Передача комнаты и импорт

```kotlin
package app.feltbook.core.domain.engine

/**
 * «Акт передачи кассы» (ТЗ §6 шаг 3, макет Handover).
 * @property fromBankerIncome чистый доход уходящего банкира в этой игре (по его `bankerName`) минус уже забранное.
 * @property incomeTakeable сколько он может забрать сейчас: `clamp(min(fromBankerIncome, cashOnHand − rakeDue), 0)` —
 *   рейк заведения не уходит в карман; 0 — «Оставить доход себе — после расчёта долгов».
 */
data class HandoverAct(
    val fromBanker: String,
    val cashOnHand: Long,
    val rakeDue: Long,
    val playersOwe: Long,
    val bankerOwes: Long,
    val fromBankerIncome: Long,
    val incomeTakeable: Long,
    val playersAtTable: Int,
    val chipsInPlay: Long,
    val entryCount: Int,
) {
    /** «Отдать Владу наличными N ₽». Отрицательная касса (банкир доплатил из своих) → 0; разницу банкиры решают сами, акт её показывает. */
    fun cashToHandOver(keepIncome: Boolean): Long =
        maxOf(0L, cashOnHand - (if (keepIncome) incomeTakeable else 0L))
}

/**
 * Подготовленная передача. Ничего не записано: отправитель коммитит [senderChange] только после подтверждения
 * получателя (ACK по Nearby) или явного «Файл отправлен» (ADR 0007).
 * @property senderChange INCOME_TAKEN? + Handover + статус HANDED_OVER.
 * @property outgoing снимок для пакета: тот же журнал и акт, статус игры прежний (RUNNING/SETTLING).
 */
data class PreparedHandover(
    val act: HandoverAct,
    val handover: Handover,
    val senderChange: GameChange,
    val outgoing: GameSnapshot,
)

/** Шаблон новой игры после расчёта (ТЗ §6 сценарий 2): правила + имена — подсказки в «Новом игроке». */
data class GameTemplate(val fromBanker: String, val rules: GameRules, val playerNames: List<String>)

object Handovers {
    /** Акт по текущему снимку. Статус RUNNING/SETTLING. */
    fun act(snapshot: GameSnapshot): HandoverAct

    /** Сценарий 1 (посреди игры). `toBanker` — имя нового банкира. */
    fun prepare(snapshot: GameSnapshot, toBanker: String, keepIncome: Boolean, stamp: Stamp): PreparedHandover

    /** Сценарий 2: из CLOSED-игры. У отправителя игра не меняется (QUESTIONS №5). */
    fun template(snapshot: GameSnapshot): GameTemplate

    /**
     * Получатель нажал «Принять». `receiverPlayerId` — «я играю как …» (игрок этой игры) или null.
     * Ставит `currentBankerName = handover.toBanker`, `bankerPlayerId = receiverPlayerId`.
     * Бросает [DomainRuleViolation]: AnotherGameActive, BankerNotAPlayer, ImportConflict (конфликт с локальной копией,
     * см. [Imports.plan]); `IllegalArgumentException`, если `handover` нет в `incoming.handovers`.
     * Повторный приём того же пакета (Identical) — пустое изменение.
     */
    fun accept(
        incoming: GameSnapshot,
        handover: Handover,
        local: GameSnapshot?,
        activeGame: Game?,
        receiverPlayerId: PlayerId?,
    ): GameChange
}

/** Результат сопоставления входящей игры с локальной (импорт резервной копии и приём передачи). */
sealed interface ImportPlan {
    /** Такой игры нет — вставить целиком. */
    data class Insert(val change: GameChange) : ImportPlan
    /**
     * Локальный журнал — префикс входящего (те же id в том же порядке, допускается равенство), а хвост, строка игры или
     * игроки отличаются: дописать хвост, обновить игру и игроков. Так игра возвращается первому банкиру, а отправитель
     * может открыть свой же файл, если получатель его не принял (QUESTIONS №13).
     */
    data class Extend(val change: GameChange) : ImportPlan
    /**
     * Журнал, акты, строка игры и игроки совпадают — пропустить. Без новых записей/актов Extend бывает только при
     * возврате своей переданной игры (локальная HANDED_OVER → входящая активная); иначе `Conflict(LOCAL_NEWER)` —
     * закрытая игра не переоткрывается и имена не откатываются молча.
     */
    data object Identical : ImportPlan
    data class Conflict(val reason: ImportConflictReason) : ImportPlan
}

enum class ImportConflictReason {
    /** Журналы разошлись (не префикс) — локальное не трогаем. */
    DIVERGED_LEDGER,
    /** Стало бы две активные игры. */
    SECOND_ACTIVE_GAME,
    /** Входящий журнал не прошёл [LedgerValidator]. */
    INVALID_LEDGER,
    /** Входящий журнал — префикс локального (или равен, а строки игры/игроков отличаются): локальная копия новее, пропустить. */
    LOCAL_NEWER,
}

object Imports {
    /** Чистое решение «что делать с входящей игрой». Никогда не удаляет и не меняет локальные записи журнала. */
    fun plan(incoming: GameSnapshot, local: GameSnapshot?, activeGameId: GameId?): ImportPlan
}
```

### 4.12. Валидатор журнала

```kotlin
package app.feltbook.core.domain.engine

data class LedgerProblem(val entryId: EntryId?, val code: LedgerProblemCode)

enum class LedgerProblemCode {
    DUPLICATE_ENTRY_ID,
    SEQ_NOT_CONTIGUOUS,
    WRONG_GAME_ID,
    UNKNOWN_PLAYER,
    PLAYER_REQUIRED,
    PLAYER_NOT_ALLOWED,
    COUNTERPARTY_REQUIRED,
    COUNTERPARTY_NOT_ALLOWED,
    AMOUNT_OUT_OF_RANGE,
    REVERSAL_TARGET_MISSING,
    REVERSAL_TARGET_NOT_EARLIER,
    REVERSAL_OF_REVERSAL,
    DOUBLE_REVERSAL,
    REVERSAL_MISMATCH,
    EARLY_EXIT_NOT_ON_CASH_OUT,
    NOTE_NOT_ALLOWED,
    BANKER_PLAYER_UNKNOWN,
    INVALID_RULES,
    DUPLICATE_PLAYER_ID,
    /** TIP/TIP_CHIPS, где получатель = даритель. */
    TIP_TO_SELF,
    /** `Handover.incomeTaken` ≠ записи INCOME_TAKEN на `ledgerSeq` (если > 0) или Σ INCOME_TAKEN ≠ Σ incomeTaken. */
    HANDOVER_INCOME_MISMATCH,
    /** `Handover.ledgerSeq` вне `0..последний seq`. */
    HANDOVER_SEQ_OUT_OF_RANGE,
    INCOME_TAKEN_REVERSED,
}

object LedgerValidator {
    /** Структурная проверка снимка по таблице §3.1 (импорт, приём передачи, property-тесты). Пусто — снимок корректен. */
    fun validate(snapshot: GameSnapshot): List<LedgerProblem>
}
```

## 5. Репозитории

```kotlin
package app.feltbook.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Агрегат «игра»: строка игры, игроки, журнал, акты. Отдельного LedgerRepository нет (ADR 0006):
 * журнал пишется только через [commit], который умеет лишь INSERT записей.
 */
interface GameRepository {
    fun observeGame(id: GameId): Flow<GameSnapshot?>

    /** Игра в статусе RUNNING/SETTLING (не больше одной). */
    fun observeActiveGame(): Flow<GameSnapshot?>

    /** Все игры, новые сверху — история, статистика, открытые долги (всё считает движок). */
    fun observeAllGames(): Flow<List<GameSnapshot>>

    suspend fun getGame(id: GameId): GameSnapshot?

    suspend fun getActiveGame(): GameSnapshot?

    /**
     * Все изменения — одной транзакцией: upsert игр/игроков, INSERT записей и актов, upsert имён новых и переименованных
     * игроков в справочник (`firstSeenAt` = `joinedAt` при первом появлении). Проверяет внутри транзакции:
     * `seq` продолжает журнал (уникальный индекс `(gameId, seq)`), активных игр после изменения ≤ 1.
     * Нарушение — исключение и откат всего.
     */
    suspend fun commit(changes: List<GameChange>)

    /** Удаление игр каскадом (игроки, журнал, акты) одной транзакцией. Справочник имён не трогает. */
    suspend fun deleteGames(ids: Set<GameId>)

    suspend fun deleteAllGames()
}

suspend fun GameRepository.commit(change: GameChange): Unit = commit(listOf(change))

interface PlayerNameRepository {
    fun observeAll(): Flow<List<PlayerName>>

    /** «Объединить игроков»: `from.mergedInto = into`. Проверку делает `PlayerNames.canMerge`. */
    suspend fun merge(from: String, into: String)

    /** Отменить объединение: `mergedInto = null`. */
    suspend fun unmerge(name: String)
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

/** Резервная копия всей базы (ТЗ §4 п. 5). Формат — `FeltbookCodec`, вид BACKUP. */
interface BackupRepository {
    /** Текст файла `.feltbook`: все игры + справочник имён. */
    suspend fun exportAll(): String

    /** Импорт по `Imports.plan` для каждой игры одной транзакцией; ничего локального не затирает (конфликты — в отчёте). */
    suspend fun importAll(text: String): ImportReport
}

data class ImportReport(
    val failure: DecodeResult.Failure?,
    val added: Int,
    val extended: Int,
    val identical: Int,
    val conflicts: List<Pair<GameId, ImportConflictReason>>,
    val playerNamesAdded: Int,
)
```

Почему так мало: ViewModel экрана стола держит `observeGame(id)` → `GameStates.compute`; действие = `Commands.x(snapshot, …, Stamp.now(clock))` → `commit`. Долги, история и статистика — `observeAllGames()` + `PlayerNameRepository.observeAll()` → `Debts`/`Stats`. Объём: сотни игр × десятки записей — десятки тысяч строк, пересчёт в памяти — миллисекунды; оптимизировать запросами только по замеру.

## 6. Пакет передачи и резервной копии

```kotlin
package app.feltbook.core.domain.transfer

import kotlinx.serialization.Serializable

/**
 * Конверт файла `.feltbook` и пакета Nearby (один формат, ADR 0007).
 * `payload` — JSON-строка полезной нагрузки (по `kind`); `sha256` — hex (lowercase) SHA-256 от UTF-8 байтов `payload`.
 * Хеш считается по точной строке, поэтому не зависит от порядка ключей и пробелов при повторной сериализации.
 */
@Serializable
data class EnvelopeDto(
    val format: String,          // "feltbook"
    val schemaVersion: Int,      // FeltbookCodec.SCHEMA_VERSION
    val kind: String,            // "HANDOVER" | "TEMPLATE" | "BACKUP"
    val createdAt: Long,         // epoch ms
    val sha256: String,
    val payload: String,
)

@Serializable data class HandoverPayloadDto(val game: GameDto, val handoverId: String)

@Serializable data class TemplatePayloadDto(val fromBanker: String, val rules: GameRulesDto, val playerNames: List<String>)

@Serializable data class BackupPayloadDto(val exportedAt: Long, val games: List<GameDto>, val playerNames: List<PlayerNameDto>)

/** Время — epoch ms (Long), id — строка UUID, перечисления — имя константы (явный маппинг, не доменные enum). */
@Serializable
data class GameDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val closedAt: Long?,
    val status: String,
    val currentBankerName: String,
    val bankerPlayerId: String?,
    val rules: GameRulesDto,
    val editedAfterClose: Boolean,
    val schemaVersion: Int,
    val players: List<PlayerDto>,
    val entries: List<LedgerEntryDto>,
    val handovers: List<HandoverDto>,
)

@Serializable
data class GameRulesDto(
    val bankerFeeMode: String,
    val bankerFeeValue: Long,
    val rakeMode: String,
    val rakeValue: Long,
    val minBuyIn: Long,
    val splitPayment: Boolean,
    val minPlayMinutes: Int,
)

@Serializable data class PlayerDto(val id: String, val name: String, val joinedAt: Long)

@Serializable
data class LedgerEntryDto(
    val id: String,
    val seq: Long,
    val operationId: String,
    val type: String,
    val playerId: String?,
    val amount: Long,
    val counterpartyPlayerId: String?,
    val bankerName: String,
    val createdAt: Long,
    val reversesEntryId: String?,
    val note: String?,
    val earlyExit: Boolean,
)

@Serializable
data class HandoverDto(
    val id: String,
    val fromBanker: String,
    val toBanker: String,
    val cashHandedOver: Long,
    val incomeTaken: Long,
    val ledgerSeq: Long,
    val at: Long,
)

@Serializable data class PlayerNameDto(val name: String, val firstSeenAt: Long, val mergedInto: String?)
```

```kotlin
package app.feltbook.core.domain.transfer

import kotlin.time.Instant

sealed interface FeltbookPackage {
    data class HandoverPackage(val game: GameSnapshot, val handover: Handover) : FeltbookPackage
    data class TemplatePackage(val template: GameTemplate) : FeltbookPackage
    data class BackupPackage(val exportedAt: Instant, val games: List<GameSnapshot>, val playerNames: List<PlayerName>) : FeltbookPackage
}

sealed interface DecodeResult {
    data class Ok(val pkg: FeltbookPackage, val schemaVersion: Int) : DecodeResult

    sealed interface Failure : DecodeResult
    /** Не JSON или `format != "feltbook"` — «Это не файл Feltbook». */
    data object NotFeltbook : Failure
    /** `schemaVersion > SCHEMA_VERSION` — «Файл из более новой версии, обновите приложение». */
    data class UnsupportedVersion(val found: Int, val supported: Int) : Failure
    /** Хеш не совпал — «Файл повреждён». */
    data object ChecksumMismatch : Failure
    /** Структура/значения не разбираются (неизвестный enum, битый UUID). */
    data class Malformed(val detail: String) : Failure
    data class InvalidLedger(val gameId: String, val problems: List<LedgerProblem>) : Failure
}

object FeltbookCodec {
    const val FORMAT: String = "feltbook"
    const val SCHEMA_VERSION: Int = 1
    const val FILE_EXTENSION: String = "feltbook"
    /** Импорт принимает оба расширения (ADR 0003). */
    val ACCEPTED_EXTENSIONS: Set<String> = setOf("feltbook", "pokerbank")
    const val MIME_TYPE: String = "application/json"

    /** `Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }` — без pretty print. */
    fun encode(pkg: FeltbookPackage, createdAt: Instant): String

    /** Проверяет формат → версию → хеш → структуру → `LedgerValidator` каждой игры (в этом порядке). */
    fun decode(text: String): DecodeResult
}

/** SHA-256 на чистом Kotlin (FIPS 180-4), без java.security (ADR 0001). Тесты — векторы NIST: "", "abc", 448-битный, 1 000 000 × 'a'. */
object Sha256 {
    fun digest(input: ByteArray): ByteArray
    /** hex (lowercase) от `digest(input)` — значение поля `sha256` конверта. */
    fun hex(input: ByteArray): String
}
```

Маппинг `GameSnapshot ↔ GameDto` — `internal` в пакете `transfer`. Поля 1:1; `Game.status == CREATED` при чтении → RUNNING.

## 7. Инварианты (property-тесты, M1.7)

Генератор: случайная последовательность допустимых команд (добавить, докуп, оплата, выход с чаевыми банкиру/игроку за столом/вышедшему, расчёт Full/Partial/None, возврат, отмена любой операции, прощение, рейк, передача, погашение после закрытия). После каждого шага:

1. `LedgerValidator.validate` пуст; `seq` 1..n без пропусков; ни одна прежняя запись не изменилась (журнал — префикс нового).
2. Тождество кассы §3.4: `cashOnHand == expectedCash + chipsInPlay + bankerOwes − playersOwe`.
3. `chipsInPlay == chipsIssued − chipsReturned`; если все вышли и фишки на выходе равнялись выданным — `chipDiff == 0`.
4. `Σ_p result_p == −Σ TIP(банкиру)` по вышедшим игрокам при сошедшихся фишках (игра с нулевой суммой).
5. Отмена операции даёт те же суммы, статусы и табло, что журнал, из которого эта операция просто вычеркнута (сравнивать `PlayerState` без `isVoid`, `TableSummary` целиком).
6. `GameSnapshot.apply` + `FeltbookCodec` encode→decode дают равный снимок.
7. `bankerIncome == Σ incomeByBanker.net`; `incomeTaken ≤` дохода соответствующего банкира на момент передачи.

## 8. Проверка модели на примерах ТЗ

Обозначения: А — Андрей, Б — Борис, В — Влад; банкир «Олег» не играет; правила по умолчанию (разово 100 ₽, рейк 10 %). Время везде одно — опущено.

### 8.1. §3.6 — сквозной пример

| seq | Операция | Запись | Игрок | amount | Комментарий |
| --- | --- | --- | --- | --- | --- |
| 1 | op1 Закуп А, в долг | BUY_IN | А | 1 000 | котировка 1 000 + 100 + 100 = 1 200 |
| 2 | op1 | RAKE | А | 100 | |
| 3 | op1 | BANKER_FEE | А | 100 | первый закуп |
| 4 | op2 Закуп Б, оплачено | BUY_IN | Б | 1 000 | |
| 5 | op2 | RAKE | Б | 100 | |
| 6 | op2 | BANKER_FEE | Б | 100 | |
| 7 | op2 | PAYMENT | Б | 1 200 | `paidNow(PAID)` |
| 8 | op3 Закуп В, в долг | BUY_IN | В | 2 000 | |
| 9 | op3 | RAKE | В | 200 | |
| 10 | op3 | BANKER_FEE | В | 100 | |
| 11 | op4 Докуп А, в долг | BUY_IN | А | 1 000 | ONCE: сбора нет, 1 100 |
| 12 | op4 | RAKE | А | 100 | |
| 13 | op5 Выход Б, F = 3 753, чаевые 53 банкиру | CASH_OUT | Б | 3 753 | `roundingTip(3753, 100) = 53` |
| 14 | op5 | TIP | Б → банкир | 53 | counterparty = null |
| 15 | op6 Расчёт Б (Full) | PAYOUT | Б | 3 700 | outstandingBefore 0, W 3 700 → balance +3 700 |
| 16 | op7 Выход В, F = 1 247 | CASH_OUT | В | 1 247 | |
| 17 | op8 Расчёт В (Full) | PAYMENT | В | 1 053 | outstandingBefore −2 300 + 1 247 = −1 053 |
| 18 | op9 Выход А, F = 0 | CASH_OUT | А | 0 | |
| 19 | op10 Расчёт А (Full) | PAYMENT | А | 2 300 | −2 300 + 0 |

Табло после op4 (все за столом): выдано 5 000, сдано 0, в игре 5 000, докупов 1; долги игроков А 2 300 + В 2 300 = 4 600; касса 1 200; рейк 500; банкиру 300. Тождество: 1 200 = (500 + 300) + 5 000 + 0 − 4 600 ✓.

Игроки в конце:

| | charged | paid | W | outstanding | balanceAtExit | result | статус |
| --- | --- | --- | --- | --- | --- | --- | --- |
| А | 2 300 | 2 300 | 0 | 0 | −2 300 «платит 2 300» | −2 000 | CASHED_OUT |
| Б | 1 200 | 1 200 | 3 753 − 53 = 3 700 | 3 700 + 1 200 − 1 200 − 3 700 = 0 | +3 700 «получает» | +2 700 | CASHED_OUT |
| В | 2 300 | 1 053 | 1 247 | 0 | −1 053 «платит 1 053» | −753 | CASHED_OUT |

Стол: Σ BUY_IN = 5 000; Σ CASH_OUT − Σ TIP_CHIPS = 3 753 + 1 247 + 0 − 0 = 5 000 → «Фишки сошлись: выдано 5 000 = сдано 5 000» ✓. Рейк = 100 + 100 + 200 + 100 = **500** ✓. Доход банкира = сборы 300 + чаевые 53 = **353** ✓. Пришло = Σ PAYMENT = 1 200 + 1 053 + 2 300 = **4 553** ✓, выплачено = **3 700** ✓, на руках = **853** = expectedCash = 500 + 353 ✓. Σ result = −2 000 + 2 700 − 753 = −53 = −чаевые банкиру ✓. Баланс Влада **−1 053** ✓. Закрытие с `payRake = true`: seq 20 RAKE_PAID 500 → касса 353 = rakeDue 0 + доход 353 ✓.

### 8.2. §3.7 — Влад не заплатил

Тот же журнал без seq 17 («Не заплатил» → `SettlementChoice.None`; А и закрытие сдвигаются на seq 17–19).

- Влад: outstanding = 1 247 + 0 − 2 300 = −1 053 → **LEFT_WITH_DEBT**, «Ушёл с долгом 1 053 ₽».
- Касса = 1 200 + 2 300 − 3 700 = **−200**; expectedCash = **853**; `unpaidDebts = [Влад 1 053]` → «Недостача 1 053 ₽ — не получено от Влада»; тождество −200 = 853 + 0 + 0 − 1 053 ✓ (макет DebtSettlement: «Должно быть в кассе 853 · Не получено от Влада −1 053 · На руках сейчас −200»).
- `requiredConfirmations = {OPEN_DEBTS}` — «Закрыть игру с долгом». Рейк отдаётся полностью: RAKE_PAID 500 → касса −700, expected 353.
- Новая игра, Влад садится: `Debts.forName("Влад", …, excludeGameId = новая)` → «Влад должен 1 053 ₽ с 18 окт». «Погасить сейчас» → `Commands.pay(старая игра, Влад, 1 053, bankerName = текущий банкир)` → в **старую** игру seq 20 PAYMENT 1 053. Старая игра: касса 353 = expected 353 ✓, Влад CASHED_OUT, `hasOpenDebt = false`, `editedAfterClose` не меняется.
- Вместо погашения «Простить»: seq 20 DEBT_FORGIVEN 1 053 → доход банкира 353 − 1 053 = −700; expected = 0 + (−700) = −700 = касса ✓; долгов нет.

### 8.3. Банкир-игрок, чаевые фишками, возврат, частичная оплата

Банкир Андрей играет (`bankerPlayerId = А`), правила по умолчанию. Д — Дима, Е — Егор.

| seq | Операция | Записи |
| --- | --- | --- |
| 1–3 | Закуп А 1 000, оплачено | BUY_IN 1 000, RAKE 100, PAYMENT 1 100 — сбора с банкира нет |
| 4–6 | Закуп Д 1 000, в долг | BUY_IN 1 000, RAKE 100, BANKER_FEE 100 |
| 7–10 | Закуп Е 1 000, оплачено | BUY_IN 1 000, RAKE 100, BANKER_FEE 100, PAYMENT 1 200 |
| 11–12 | Выход Е, F = 1 500, чаевые 100 Диме (за столом) | CASH_OUT 1 500, TIP_CHIPS Е→Д 100 |
| 13 | Расчёт Е: 0 + 1 400 → Full | PAYOUT 1 400 |
| 14–15 | Е вернулся: докуп 500, в долг | BUY_IN 500, RAKE 50 (разовый сбор не повторяется) |
| 16–17 | Выход Д, F = 1 300 (включая 100 от Е), 50 банкиру | CASH_OUT 1 300, TIP 50 |
| 18 | Расчёт Д: −1 200 + 1 250 = +50 | PAYOUT 50 |
| 19 | Выход Е, F = 200: carriedOver 0, сессия −550 → balance −350 | CASH_OUT 200 |
| 20 | «Отдал часть» 100 | PAYMENT 100 → Е LEFT_WITH_DEBT 250 |
| 21 | Выход А, F = 600: balance 0 + 600 | CASH_OUT 600 |
| 22 | Расчёт А | PAYOUT 600 (банкир берёт из кассы себе) |

Фишки: выдано 3 500; Σ CASH_OUT 3 600 − TIP_CHIPS 100 = 3 500 ✓ (без отдельного типа фишечных чаевых сверка разошлась бы на 100 — двойной счёт). Рейк 350, доход 200 + 50 = 250, expected 600. Касса = (1 100 + 1 200 + 100) − (1 400 + 50 + 600) = 350 = 600 − долг Е 250 ✓. Результаты: А −400, Д +250, Е (1 700 − 100) − 1 500 = +100; Σ = −50 = −чаевые банкиру ✓.

Отмена op «Выход Д» (seq 16–17): Д снова PLAYING, outstanding = −1 200 − 50 = −1 250 (выплата 50 осталась, он её получил), в игре 1 300 фишек, доход 200. Тождество: 350 = (350 + 200) + 1 300 + 0 − (1 250 + 250) ✓. Повторный выход Д с правильным F посчитает только разницу — деньги не задваиваются.

### 8.4. Передача посреди игры

После seq 10 из 8.3 Андрей передаёт игру Владу (Влад не за столом): `act`: касса 2 300, rakeDue 300, долги 1 200, доход Андрея 200, `incomeTakeable = min(200, 2 300 − 300) = 200`. С «Оставить доход себе»: seq 11 INCOME_TAKEN 200 (bankerName = Андрей), Handover(cashHandedOver 2 100, incomeTaken 200, ledgerSeq 11). У Влада: касса 2 100 = наличные, expected = 300 + 200 − 200 = 300; дальнейшие сборы и чаевые — с `bankerName = Влад`, `incomeByBanker` = [Андрей 200 (taken 200), Влад …]. Будущие докупы Андрея в `EACH_*` уже со сбором (он больше не `bankerPlayerId`). Тождество выполняется, потому что INCOME_TAKEN входит и в кассу, и в expected.

## 9. Обязательные тесты M1 (имена — по-русски в бэктиках)

- §3.2: все 4 строки таблицы; 1 250 → 125, 1 255 → 126, 1 254 → 125; все 4 режима сбора; EACH для возврата; банкир без сбора во всех режимах, рейк платит; минимум 500 (499 — блокер, 500 — ок); раздельная оплата (комиссии сразу, фишки в долг → PAYMENT = 200); раздельная при выключенной опции — блокер.
- §3.3: W с чаевыми банкиру, игроку за столом, вышедшему; «до 100»/«до 500»; чаевые > F — блокер; себе — блокер.
- §3.4: выход на 59-й минуте без подтверждения — ошибка, с подтверждением — `earlyExit = true`; 60:00 — не ранний.
- §3.5/§3.6: пример 8.1 целиком; расхождение фишек → `CHIPS_NOT_RECONCILED`.
- §3.7: пример 8.2 целиком, частичная оплата, погашение в старую игру, прощение.
- §4: возврат вышедшего = докуп, счётчик докупов; новая выход-сессия; табло после каждого шага; двойная активная игра — блокер; переоткрытие.
- Отмена: каждого вида операции; отмена отмены и повторная — ошибка; отмена в CLOSED (разрешённые/запрещённые); void-игрок.
- Передача: пример 8.4; `Imports.plan` — Insert/Extend/Identical/Conflict; возврат игры первому банкиру (локальная HANDED_OVER-копия — префикс) → Extend.
- Пакет: encode→decode равен; изменённый байт payload → ChecksumMismatch; `schemaVersion = 2` → UnsupportedVersion; неизвестный тип записи → Malformed; SHA-256 по векторам NIST.
- Подозрительные суммы: > 5× средней, > 20 000, без покупок — только порог 20 000; F > фишек в игре.
- Property-тесты §7 (≥ 1 000 последовательностей по 50 шагов, фиксированный seed в CI).

## 10. Не входит в `:core:domain`

Форматирование сумм, дат и множественных форм («1 игрок»), тексты, `Clock.System` (кроме `Stamp.now` по умолчанию), Room/DataStore, Nearby, файлы, корутины в расчётах.

## 11. Уточнения при реализации M1 (domain-engine)

Исправления контракта, найденные при реализации; публичный API выше уже обновлён.

| Где | Было | Стало | Почему |
| --- | --- | --- | --- |
| `Pricing.quoteNewPlayer` | void-игрок даёт `NameTaken` | не блокер, `quote.playerId` = id void-игрока | иначе `addPlayer` не мог бы «вернуть» void-игрока (он бросает по блокерам котировки), а `buyIn` для void — PlayerNotFound |
| `DomainError` | — | `ImportConflict(reason)` | `Handovers.accept` должен бросать `DomainRuleViolation` при конфликте с локальной копией, а подходящей ошибки не было |
| `SessionFigures.carriedOver` | `outstanding` по записям до начала сессии | `outstanding − (paid − charged)` сессии | выплата старого долга банкира после возврата иначе не попадала ни в сессию, ни в перенос, и строки выхода не сходились с балансом |
| Зависимости модуля | `kotlinx-datetime`, `serialization-core` | `kotlinx-serialization-json` (§0) | §0: datetime не нужен, кодеку нужен json |
| Мелочи поведения | — | `Money.percentOf`/`divRoundHalfUp` — `require` (отрицательных сумм нет); `CashOuts.roundingTip` — `chips ≥ 0`; F < 0 в котировке выхода — `AmountNotPositive`; `Imports.plan` — `IllegalArgumentException`, если `local` другой игры; `TableSummary.incomeByBanker` всегда содержит текущего банкира (с нулями, если дохода нет); `renamePlayer` в HANDED_OVER — WrongGameStatus; причина отмены — `trim`, пустая → null | не было сказано |
| Банкир-игрок без закупа | — | не считается «за столом» (`playersAtTable`) и не блокирует закрытие (`PlayersStillPlaying`); не получатель TIP_CHIPS (`TipRecipientNotSeated`) | QUESTIONS №15 |

### Ревью M1 (fix(domain): address M1 review)

| Где | Стало | Почему |
| --- | --- | --- |
| `Pricing.quoteNewPlayer`, `Commands.addPlayer` | void-банкир возвращается банкиром (без сбора); новый игрок с именем банкира при `bankerPlayerId == null` садит банкира за стол: `addPlayer` пишет `game.copy(bankerPlayerId = …)`, сбора нет | банкир-игрок не платит сбор ни в одном режиме |
| `NotReversibleReason.LATER_PURCHASES_EXIST` | первую покупку нельзя отменить, пока есть действующие докупы | иначе следующий докуп стал бы «первым» без разового сбора |
| `DomainError.TipRecipientNotSeated`, `CashOutQuote.tipAsChips` | TIP_CHIPS — только сидящему (PLAYING и покупал); вышедшему — TIP; банкиру — доход | банкир-игрок без закупа получил бы фишки, которых не сдаст |
| `NotReversibleReason.TIP_RECIPIENT_CASHED_OUT` | выход с TIP_CHIPS не отменяется, если получатель с тех пор вышел | после отмены повторный выход записал бы чаевые рублями — двойная выплата |
| `ImportConflictReason.LOCAL_NEWER`, `Imports.plan` | входящее старше/равно локальному с другими строками → `LOCAL_NEWER` (отчёт: пропущено); Extend без новых записей — только HANDED_OVER → активная | не переоткрывать закрытые игры и не откатывать имена из старой копии (QUESTIONS №16) |
| `Suspicion` | порог `chips ≥ 20 000` | пример ТЗ «20 000 фишек — точно?» |
| `LedgerValidator` | + `DUPLICATE_PLAYER_ID`, `TIP_TO_SELF`, `HANDOVER_INCOME_MISMATCH`, `HANDOVER_SEQ_OUT_OF_RANGE`, `INCOME_TAKEN_REVERSED` | целостность входящих пакетов |
