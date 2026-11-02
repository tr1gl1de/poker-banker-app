# M3–M6. Спецификация интерфейса (источник для реализации «точь-в-точь»)

Выжимка всех макетов `docs/mockups/*.dc.html` в форме, которую можно переносить в Compose без повторного разбора HTML. Правила расчёта — только `docs/SPEC.md` и `docs/plan/m1-domain.md`; здесь — что где лежит, как выглядит и откуда берутся цифры.

Статус: черновик compose-ui · 2026-11-02. Разделы, помеченные **[без макета]**, — предложения, требующие подтверждения техлида (см. §12).

## 0. Как читать

- Артборд 390 × 844; **1 CSS px = 1 dp**, `font-size` px = **sp**, `letter-spacing` px = sp. Высота артборда не включает статус-бар и жестовую панель — инсеты добавляются сверху/снизу (§10).
- Отступы записаны в порядке CSS: `p(верх право низ лево)`; `p(10 12)` = вертикаль 10, горизонталь 12. `gap` → `Arrangement.spacedBy`. `m(...)` — внешний отступ (в Compose — `padding` снаружи фона).
- `c.x` — токен `FeltbookTheme.colors.x`; **★** — токена ещё нет, предложен в §1.2. `t.x` — токен типографики (§2). `s.x` — форма (`FeltbookShapes`) или явный радиус `r16`.
- Рамка: `border 1 c.hairline` = `Modifier.border(1.dp, c.hairline, shape)`. «inset-рамка» (CSS `box-shadow: inset 0 0 0 Npx`) — рамка внутри габарита, габарит не растёт: в Compose обычный `border` (он тоже рисуется внутрь).
- Тени CSS `0 Y B rgba(0,0,0,a)` → `Modifier.dropShadow`/`shadow`: указаны как `shadow(y, blur, alpha)`; цвет — ★`c.shadow`.
- Шрифтовой вес 800 в макетах: у IBM Plex Sans нет начертания ExtraBold (максимум — Bold 700), браузер тоже рисует 700. **Везде 800 → `FontWeight.Bold`.**
- Все тексты с цифрами — `fontFeatureSettings = "tnum"` (в макетах `font-variant-numeric: tabular-nums` на корне — значит, на всём экране).
- Цифры на макетах — пример. Источник каждой цифры указан как поле домена (`TableSummary.cashOnHand` и т. п.).

### 0.1. Форматирование чисел, времени, знаков (общее для всех экранов)

| Что | Правило | Пример |
| --- | --- | --- |
| Тысячи | группировка по 3 с **неразрывным пробелом U+00A0** с 1 000 (4-значные тоже) | `500`, `1 053`, `214 500` |
| Рубли | число + NBSP + `₽` | `2 300 ₽` |
| Минус | **U+2212 «−»**, не дефис | `−3 400 ₽` |
| Плюс | `+` только там, где на макете | `+353 ₽`, `+2 700`, `+9 450` |
| Проценты | целое: `10 %` (NBSP перед %); дробное (калькулятор): запятая, 1 знак: `38,4 %`, `±0,3 %` | |
| Длительность | `< 60 мин` → `47 мин`; иначе `2 ч 14 мин`, минуты с ведущим нулём: `4 ч 05 мин` | |
| Время | `HH:mm` (24 ч) | `20:05` |
| Дата короткая | `d` + NBSP + месяц из `months_short` (родительный, без точки) | `18 окт`, `27 сен` |
| Месяц-заголовок | `months_full_standalone` + год | `Октябрь 2026` |
| Дата в графике | `dd.MM` | `13.09` |
| «за игру» | целочисленное деление с 1 знаком: `rebuyCount * 10 / games`, округление половины вверх (`Money.divRoundHalfUp`), запятая | `1,4` |

Форматтер — одна функция в `:core:ui` (`MoneyFormat`), без `Locale`-зависимости (результат одинаков на любом телефоне). Плюрализация чисел `Long`: `pluralQuantity(n) = (n % 100).toInt() + if (n >= 100) 100 else 0` → в `pluralStringResource` (русское правило зависит только от двух последних цифр).

---

## 1. Цвета

### 1.1. Сопоставление цветов макетов с токенами

Существующие токены (`FeltbookColors.kt`) покрывают все «сплошные» цвета, кроме отмеченных ★.

| Цвет в макете | Токен | Где |
| --- | --- | --- |
| `#071A14` | `c.feltDeep` | фон под шторками, нижняя панель, контейнер сегментов, ячейки кода, поле «Должно быть в кассе» |
| `#0B2A20` | `c.felt` | середина фона |
| `#10382B` | `c.feltGlow` | верх фона |
| `#0E2E23` | `c.sheet` | шторки, блок «Правила», «Акт передачи», «Памятка» |
| `#12352A` | `c.card` | карточки, поля, выбранный сегмент, шапка мультивыбора |
| `#1B5A43` | `c.feltLight` | старт градиента табло/карточки игры/калькулятора |
| `#12432F` | ★`c.feltLightMid` | средняя точка (60 %) того же градиента |
| `#0D3426` | `c.feltLightEnd` | конец того же градиента |
| `#3A2A18` | `c.rail` | борт овала |
| `#C9A35B` | `c.brass` | кнопки, FAB, вкл. переключатель, чекбокс, ссылки |
| `#D9B66C` | `c.brassGradientEnd` | **верх** (0 %) вертикального градиента крупной кнопки (`180deg: #D9B66C 0% → #C9A35B 100%`) |
| `#E2C27E` | `c.brassLight` | акцентный текст, «докуп ×N», «♥ 800 ₽», значок вкладки |
| `#F0D79E` | `c.brassBright` | крупные «банкирские» суммы, значения настроек, код |
| `#1A1408` | `c.onBrass` | текст/иконки на латуни, бегунок вкл. переключателя |
| `#4A3A1C` | `c.onBrassSecondary` | вторая строка на латунной кнопке |
| `#EDE6D3` | `c.ink` | основной текст |
| `#CFE0D6` | `c.inkSoft` | подписи |
| `#B5AE96` | `c.ledger` | служебные строки |
| `#9FB5AC` | `c.muted` | неактивное, подписи под суммой |
| `#6EDBA4` | `c.pay` | выплатить/оплачено, точка «Идёт игра» |
| `#FF8A80` | `c.owe` | долг, «получить с игрока», опасные действия |
| `#F3EDDC` | `c.ivory` | карты-аватары, кант фишек, иконки на круглых плашках |
| `#B3261E` | `c.suitRed` | ♥ ♦ на картах |
| `#1A1A1A` | ★`c.suitBlack` | ♠ ♣ и инициал на картах |
| `#2A4A3E` | ★`c.cardInactive` | фон карты-аватара вышедшего игрока |
| `#D8CFB4` | ★`c.ledgerLight` | мета-строка активной игры, «↳»-строки журнала, пример расчёта, подписи столбцов графика |
| `#FFB4AC` | ★`c.oweSoft` | подпись «Долг» на плашке долга, легенда «проигрыш» |
| `#FFD0CA` | ★`c.oweTint` | текст внутри красных панелей («Не оплачено», «Вам должны», «доплачивает») |
| `#8E2B2B` | ★`c.oweIcon` | круглая плашка иконки «доплачивает» |
| `#2E7D4F` | ★`c.payIcon` (= значению `chips.chip500`, но смысловой токен) | круглая плашка иконки «выплачивает» |
| `#4CC38A` (только в rgba) | ★ база `payContainer*` | зелёные панели |
| `#E5484D` (только в rgba) | база `c.oweBackground` и ★`owe*Background` | красные панели |
| `#8C7444` | ★`c.holdTrack` | незаполненная часть hold-кнопки |
| `#CFC2A0` | ★`c.brassMuted` | подпись на латунной плитке («сборы 300 · чаевые 53») |
| `#1A3D31` | ★`c.dialog` | поверхность диалога (и snackbar, §4.13) |
| `rgba(0,0,0,a)` | ★`c.shadow` = `#000000` | тени (альфа — в спецификации тени) |
| `rgba(255,255,255,0.35)` | ★`c.brassHighlight` | внутренний блик 1 dp сверху у кнопки «Выдать» |
| `#2E7D4F #1C1C1E #A8322D #5B3C8C` | `c.chips.chip500/1000/2000/5000` | фишки |
| `#2A1F12`, `#0F3A2A` | — (не токены) | нарисованный «стол под шторкой» в макетах шторок — в приложении под шторкой настоящий экран + scrim |
| `#D6E8DE` | — | только `Icon.dc.html` (вариант иконки) |

### 1.2. Недостающие токены (добавить в `FeltbookColors`, тёмная и светлая темы)

Полупрозрачные значения задаются **в теме** как `base.copy(alpha = …)`; экран никогда не вызывает `copy(alpha)` сам. Светлые значения — предложение (светлого макета нет); альфа-токены светлой темы — та же альфа от светлых `ink`/`brass`/`owe`/`pay`.

**Сплошные**

| Токен | Тёмная | Светлая (предл.) | Назначение |
| --- | --- | --- | --- |
| `feltLightMid` | `#12432F` | `#0D4231` | 60 % градиента «стола» |
| `suitBlack` | `#1A1A1A` | `#1A1A1A` | чёрные масти/инициал на `ivory` |
| `cardInactive` | `#2A4A3E` | `#D9D2C0` | аватар вышедшего |
| `ledgerLight` | `#D8CFB4` | `#5A5444` | светлый «гроссбух» |
| `oweSoft` | `#FFB4AC` | `#8C1D18` | подпись на плашке долга |
| `oweTint` | `#FFD0CA` | `#7A1A15` | текст в красных панелях |
| `oweIcon` | `#8E2B2B` | `#8E2B2B` | плашка иконки «доплачивает» |
| `payIcon` | `#2E7D4F` | `#2E7D4F` | плашка иконки «выплачивает» |
| `holdTrack` | `#8C7444` | `#C9B48A` | дорожка hold-кнопки |
| `brassMuted` | `#CFC2A0` | `#6E5A33` | вторичный текст на латунной плитке |
| `dialog` | `#1A3D31` | `#FFFDF8` | диалоги, snackbar |
| `shadow` | `#000000` | `#000000` | тени |
| `brassHighlight` | `#FFFFFF` α 0.35 | `#FFFFFF` α 0.35 | блик кнопки |
| `scrim` | `#071A14` α 0.45 | `#1B241F` α 0.32 | затемнение под шторкой/диалогом (макет: экран под шторкой с opacity 0.55 на `feltDeep` ≡ scrim `feltDeep` α 0.45) |

**От `ink` (#EDE6D3)**

| Токен | α | Где |
| --- | --- | --- |
| `outlinedFill` | 0.05 | заливка контурных кнопок |
| `quietFill` | 0.06 | круглые иконки-кнопки 44, клавиши клавиатуры, кнопка «Оставить» в диалоге, ± калькулятора |
| `hairline` | 0.08 | разделители строк, рамка неактивного поля |
| `divider` | 0.12 | черта над итоговой строкой |
| `dialogBorder` | 0.14 | рамка диалога |
| `outline` (есть, 0x38) | 0.22 | рамка контурной кнопки |
| `outlineDialog` | 0.25 | рамка кнопки «Оставить» в диалоге |
| `dragHandle` | 0.30 | ручка шторки |
| `switchOffBorder` | 0.35 | рамка выкл. переключателя |
| `checkboxOffBorder` | 0.40 | рамка пустого чекбокса |

**От `brass` (#C9A35B)**

| Токен | α | Где |
| --- | --- | --- |
| `brassSelectedRow` | 0.12 | фон выбранной строки истории |
| `brassContainer` | 0.14 | фон латунной плитки («♥ Банкиру», «♥ Доход банкира»); верхняя граница нижней панели |
| `brassHairline` | 0.16 | рамка плиток табло и итога |
| `brassBorderSoft` | 0.20 | рамка блока «Правила», нижняя граница шапки мультивыбора |
| `brassIndicator` | 0.22 | индикатор вкладки, фон выбранного чипа |
| `brassBorderMedium` | 0.25 | рамка «Акта передачи», «Памятки» |
| `sheetEdge` | 0.28 | верхняя кромка шторки |
| `brassBorderOdds` | 0.35 | рамка блока карт калькулятора |
| `logoRing` | 0.40 | кольцо логотипа |
| `brassBorder` | 0.45 | рамка карточки активной игры, ячеек кода, плитки «Доход банкира» |
| `brassBorderStrong` | 0.50 | «докуп ×N», плитка «♥ Банкиру», «Поделиться», «За 3 месяца ▾» |
| `focusBorder` | 0.55 | рамка поля в фокусе, внутреннее кольцо овала, столбцы графика |
| `selectedBorder` | 0.60 | inset-рамка выбранного сегмента, «как прошлый», выбранная строка истории |

**От `muted`, `owe`, `pay`, `ivory`, `inkSoft`**

| Токен | База | α | Где |
| --- | --- | --- | --- |
| `chipBorder` | muted | 0.35 | рамка невыбранного чипа, пунктир вышедшего игрока |
| `oweBackground` (есть) | `#E5484D` | 0.12 | красные панели |
| `oweBackgroundSoft` | `#E5484D` | 0.08 | заливка «Не заплатил» |
| `oweBackgroundStrong` | `#E5484D` | 0.14 | кнопка «Удалить» в шапке мультивыбора |
| `oweBorder` | owe | 0.40 | плашка «Долг», «Вам должны» |
| `oweBorderMedium` | owe | 0.45 | рамка «Не заплатил» |
| `oweBorderStrong` | owe | 0.50 | панели «доплачивает», «Не оплачено», предупреждение о долге, пунктир строки должника |
| `oweBar` | owe | 0.55 | «проигрыш» на полосе эквити |
| `payContainer` | `#4CC38A` | 0.12 | баннер «Фишки сошлись», плитка «Шансы банка» |
| `payContainerTop` / `payContainerBottom` | `#4CC38A` | 0.18 / 0.08 | градиент панели «выплачивает» (сверху вниз) |
| `payIconContainer` | pay | 0.15 | плашка иконки телефона |
| `payBorder` | pay | 0.40 | баннер «Фишки сошлись» |
| `payBorderMedium` | pay | 0.45 | плитка «Шансы банка» |
| `payBorderStrong` | pay | 0.55 | панель «выплачивает» |
| `chipInnerRing` | ivory | 0.70 | кольцо центра фишки |
| `placeholderBorder` | ivory | 0.45 | пунктир «+ тёрн» |
| `placeholderBorderDisabled` | ivory | 0.25 | пунктир «+ ривер» |
| `inkSoftDisabled` | inkSoft | 0.50 | «+» недоступного слота |

Состояния «недоступно» (в макетах нет): латунная кнопка — `brass` α 0.38, текст `onBrass` α 0.6 → ★`brassDisabled`, ★`onBrassDisabled`; контурная — содержимое `ink` α 0.38 → ★`inkDisabled`.

### 1.3. Градиенты (геометрия CSS → Compose)

CSS `radial-gradient(RX% RY% at X% Y%, …)` — эллипс с полуосями `RX% ширины`, `RY% высоты` и центром в `(X%, Y%)`. Обобщить `Modifier.feltBackground()` до `Modifier.ellipticalRadialGradient(stops, center: Offset (доли), radiusX: Float (доля ширины), radiusY: Float (доля высоты), shape)` в `:core:ui` (тот же приём со `scale`), и брать из него все четыре варианта:

| Имя | CSS | Стопы |
| --- | --- | --- |
| `feltScreen` (есть) | `120% 70% at 50% 0%` | `feltGlow 0` → `felt 0.45` → `feltDeep 1` |
| `feltTable` (овал табло) | `80% 90% at 50% 40%` | `feltLight 0` → `feltLightMid 0.6` → `feltLightEnd 1` |
| `feltActiveGame` (карточка на Главной) | `90% 120% at 30% 0%` | те же три |
| `feltOdds` (карты калькулятора) | `90% 120% at 50% 0%` | те же три |
| `brassButton` | `linear 180deg` | `brassGradientEnd 0` (верх) → `brass 1` (низ) |
| `payPanel` | `linear 180deg` | `payContainerTop` → `payContainerBottom` |

Фон-«стол под шторкой» в макетах шторок (`120% 60% at 50% 0%`, opacity 0.55, плюс силуэт овала `#2A1F12/#0F3A2A`) — это изображение затемнённого экрана; в приложении — реальный экран + `c.scrim`.

---

## 2. Типографика

Все токены — поля `FeltbookTypography` (существующие `display/amount/title/body/label/ledger` переименовать/дополнить; экранам размер шрифта задавать нельзя). `lh` — `lineHeight` как множитель размера; без указания — Playfair 1.2, Plex 1.35 (как в теме). Для точности по вертикали — `LineHeightStyle(Alignment.Center, Trim.None)`, `includeFontPadding = false`.

### 2.1. Playfair Display (все 700 Bold)

| Токен | sp | ls | Где |
| --- | --- | --- | --- |
| `brand` | 30 | 0.3 | «Feltbook» на Главной (`lh 1`) |
| `displayL` | 26 | 0 | заголовок «Настройки» (и «История» [без макета]) |
| `display` | 24 | 0 | заголовки экранов, название игры на карточке, имя в карточке игрока |
| `displayTable` | 24 | 0.2 | название игры в шапке Стола |
| `displayS` | 22 | 0 | заголовки шторок, «Калькулятор шансов» |
| `cardRankL` | 24 | 0 | ранг своей карты калькулятора (`lh 1`) |
| `avatarInitialL` | 22 | 0 | аватар 44 × 60 (`lh 1`) |
| `avatarInitialM` | 17 | 0 | аватар 34 × 46 (`lh 1`) |
| `cardRankBoard` | 16 | 0 | карта стола калькулятора (`lh 1`) |
| `avatarInitialS` | 15 | 0 | аватар 30 × 40 (`lh 1`) |
| `avatarInitialXS` | 14 | 0 | аватар 28 × 38 (`lh 1`) |

### 2.2. IBM Plex Sans

| Токен | sp | Вес | ls | lh | Где |
| --- | --- | --- | --- | --- | --- |
| `amountHero` | 52 | Bold (800) | −1 | 1.05 | сумма в шторке закупа |
| `amountEquity` | 44 | Bold | −1 | 1.0 | эквити |
| `amountBoard` (= нынешний `amount`) | 40 | Bold (800) | −0.5 | — | «Фишек в игре», поле «Фишки на руках» (в фокусе) |
| `amountInput` | 36 | Bold | 0 | — | поле фишек (не в фокусе), «Первый закуп» |
| `amountResult` | 34 | Bold (800/700) | −0.5 | — | «+353 ₽», «1 053 ₽» в панели итога выхода |
| `amountTotal` | 28 | Bold | 0 | — | «Вам должны 1 653 ₽» |
| `amountCard` | 26 | Bold (800) | 0 | — | плитки итога игры |
| `amountStat` | 24 | Bold | 0 | — | плитки статистики, плитки итога с долгом |
| `amountAct` | 22 | Bold | 0 | — | «Отдать … наличными 950 ₽» |
| `amountTile` | 20 | Bold | 0 | — | плитки табло, сумма в «Не оплачено», число даты на Главной (`lh 1.1`) |
| `headlineSans` | 20 | SemiBold | 0 | — | «Выбрано 2» |
| `keypadDigit` | 20 | SemiBold | 0 | — | цифры клавиатуры |
| `keypadDigitSmall` | 18 | SemiBold | 0 | — | «000» |
| `buttonHero` | 18 | Bold (800) | 0 | 1.15 | «Выдать 2 000 фишек» |
| `amountMetric` | 18 | Bold | 0 | — | метрики карточки игры / игрока, сумма долга в списке, «853 ₽», число игроков калькулятора |
| `fieldValueL` | 18 | SemiBold | 0 | — | поле «Имя» |
| `dialogTitle` | 18 | Bold | 0 | — | заголовок диалога |
| `buttonL` | 17 | Bold | 0 | 1.2 | крупные кнопки 64–68 dp |
| `fieldValue` | 17 | SemiBold | 0 | — | поля «Название», «Банкир» |
| `fieldValueBold` | 17 | Bold | 0 | — | поле процента рейка |
| `button` | 16 | Bold (800 у «Расчёт произведён», «Рейк отдан…») | 0 | — | FAB, «Вернуться за стол», кнопки 56 |
| `emphasisM` | 16 | Bold | 0 | — | «Влад должен 1 053 ₽», поля «Банк/Ставка» |
| `titleM` | 16 | SemiBold | 0 | — | имя в «Открытых долгах» |
| `sectionTitle` | 15 | Bold | 0 | — | «Прошлые игры», «Игроки», «Журнал», «Правила», «Чаевые» |
| `amountRow` | 15 | Bold (800 в итоге) | 0 | — | сумма справа в строке игрока/итога/статистики, «♥ 800 ₽» |
| `title` | 15 | SemiBold | 0 | — | имена, названия игр, текст контурных кнопок 52 |
| `bodyL` | 15 | Regular | 0 | — | пункты настроек |
| `labelBold` | 14 | Bold | 0 | — | выбранный сегмент, кнопки диалога/плиток, «оборот 29 000», «заплатил 2 300 ✓», итоговая строка расшифровки |
| `labelSemi` | 14 | SemiBold | 0 | — | невыбранный сегмент, значения настроек, «Простить…», кнопки карточки игрока |
| `body` | 14 | Regular | 0 | — | строки расшифровок `dl` (диалог — `lh 1.45`) |
| `chipBold` | 13 | Bold | 0 | — | выбранный чип, сегмент 40 dp (выбран) |
| `chip` | 13 | SemiBold | 0 | — | чип, сегмент 40 dp, ссылка «Вся история», «За 3 месяца ▾» |
| `caption` | 13 | Regular | 0 | — | подзаголовки шапок/шторок, подписи |
| `chipSecondary` | 13 | SemiBold | 0 | — | вторая строка `IssueChipsButton` |
| `overline` | 12 | Bold | 1.2 | — | «ИДЁТ ИГРА» (uppercase) |
| `overlineBoard` | 12 | Regular | 1.4 | — | «ФИШЕК В ИГРЕ» (uppercase) |
| `fieldLabelCaps` | 12 | Regular | 1.0 | — | «ФИШКИ НА РУКАХ» (uppercase) |
| `small` | 12 | Regular | 0 | — | подписи полей, подсказки |
| `smallSemi` | 12 | SemiBold | 0 | — | вторая строка hold-кнопки, вердикт «колл окупается», невыбранная вкладка |
| `navSelected` | 12 | Bold | 0 | — | выбранная вкладка |
| `tiny` | 11 | Regular | 0 | — | подписи метрик, «в долг», «рейк 2 900» |
| `tinyBold` | 11 | Bold | 0 | 1.2 | «докуп ×N», «БАНКИР», «как прошлый» |
| `tinyCaps` | 11 | Regular | 1.0 | — | «МОИ КАРТЫ», «СТОЛ · ФЛОП» (uppercase) |
| `chipDenomination` | 12 | Bold (800) | 0 | — | «500 / 1K / 2K / 5K» в фишке |
| `suitXL` / `suitL` / `suitBoard` / `suitM` / `suitS` / `suitXS` | 20 / 15 / 13 / 12 / 11 / 10 | Regular | 0 | 1.0 | масть на картах (символы ♠♥♦♣): своя карта калькулятора / аватар L / карта стола / M / S / XS |
| `suitTab` | 18 | Regular | 0 | — | «♠» в нижней панели |
| `glyphButton` | 20 | Regular | 0 | — | «−», «+» калькулятора, «♠» в «Начать игру»; «+» слота карты — 18 |

### 2.3. IBM Plex Mono

| Токен | sp | Вес | ls | Где |
| --- | --- | --- | --- | --- |
| `codeDigit` | 32 | Medium | 0 | цифры кода передачи |
| `ledgerL` | 13 | Regular | 0 | строки журнала |
| `ledger` (есть) | 12 | Regular | 0 | мета-строки |
| `ledgerTight` | 12 | Regular | −0.2 | мета-строки стола/итога, подписи плиток табло |
| `ledgerCaps` | 12 | Medium | 1.4 | заголовки секций (uppercase): «ОКТЯБРЬ 2026», «ПРАВИЛА ПО УМОЛЧАНИЮ» |
| `ledgerS` | 11 | Regular | 0 | версия, «±0,3 %», даты графика, статус калькулятора |
| `taglineCaps` | 11 | Regular | 1.6 | «БАНК ПОКЕРНОГО СТОЛА» |
| `ledgerSCaps` | 11 | Regular | 1.0 | «♠ СЕКРЕТНЫЙ СТОЛ» |
| `positionLabel` | 12 | Regular / Medium (выбран) | 0 | UTG … BB |

Uppercase делается `text.uppercase()` в UI-слое (строки в `strings.xml` — обычным регистром, кроме «БАНКИР» и «♠ СЕКРЕТНЫЙ СТОЛ», которые в макете написаны прописными).

---

## 3. Иконки (`:core:ui` → `FeltbookIcons`, `ImageVector`)

Все — `viewBox 0 0 24 24`, `fill = none`, `stroke = currentColor` (в `ImageVector` — `SolidColor(Color.Black)`, окраска через `Icon(tint = …)`), `strokeLineCap = Round`; `strokeLineJoin = Round`, если указано. Окружности/прямоугольники переведены в `path`. Толщина зависит от места — дана как «по умолчанию», в скобках — отличия; для разных толщин делать отдельные `ImageVector` (`Check`, `CheckBold`) либо параметр-фабрику `feltIcon(strokeWidth)`.

| Имя | Размер / stroke | Join | Path data | Где |
| --- | --- | --- | --- | --- |
| `Back` | 22 / 2 | round | `M15 5l-7 7 7 7` | шапки |
| `ArrowForward` | 20 / 2.2 | round | `M5 12h14M13 6l6 6-6 6` | «Вернуться за стол» |
| `Plus` | 22 / 2.2 (20 в карточке игрока) | — | `M12 5v14M5 12h14` | FAB, «Докуп» |
| `History` | 22 / 1.8 | — | `M3.5 12a8.5 8.5 0 1 0 17 0a8.5 8.5 0 1 0 -17 0` + `M12 7.5V12l3 2` | вкладка «История» |
| `Settings` (ползунки) | 22 / 1.8 | — | `M4 7h10M18 7h2M4 17h4M12 17h8` + `M14 7a2 2 0 1 0 4 0a2 2 0 1 0 -4 0` + `M8 17a2 2 0 1 0 4 0a2 2 0 1 0 -4 0` | вкладка «Настройки» |
| `Handover` | 22 / 1.8 | round | `M7 7l10 10-5 5V2l5 5L7 17` | шапка стола «Передать комнату» |
| `MoreVert` | 22 / **fill** currentColor, без stroke | — | `M10.2 5a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0` + `M10.2 12a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0` + `M10.2 19a1.8 1.8 0 1 0 3.6 0a1.8 1.8 0 1 0 -3.6 0` | меню «⋮» |
| `Backspace` | 22 / 1.8 | round | `M9 5h11v14H9l-6-7z` + `M13 9l5 6M18 9l-5 6` | клавиатура «Стереть» |
| `Check` | 24 / 2.4 (20 / 2.2 в баннере; 16 / 3 в чекбоксе) | round | `M5 12l5 5 9-10` | «Выдать», «Фишки сошлись», чекбокс |
| `Payment` (купюра) | 20 / 1.9 | round | `M5 6h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V8a2 2 0 0 1 2 -2z` + `M9.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0` | «Оплата» |
| `Exit` | 20 / 1.9 | round | `M14 4h5v16h-5M10 8l-4 4 4 4M6 12h10` | «Выход» |
| `Close` | 18 / 2 (22 / 2 в шапке мультивыбора) | — | `M6 6l12 12M18 6L6 18` | «Отмена», «Снять выбор» |
| `ArrowDown` | 22 / 2.2 | round | `M12 5v14M6 13l6 6 6-6` | «Банкир выплачивает» |
| `ArrowUp` | 22 / 2.2 | round | `M12 19V5M6 11l6-6 6 6` | «доплачивает банкиру» |
| `Alert` | 20 / 2 (22 в предупреждении, 18 в кнопке) | — | `M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0` + `M12 7v6M12 16.5v.5` | «Не заплатил», «Не оплачено», долг при посадке |
| `Share` | 22 / 1.9 | round | `M15.5 5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0` + `M3.5 12a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0` + `M15.5 19a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0` + `M8.2 10.8l7.6-4.3M8.2 13.2l7.6 4.3` | «Поделиться итогом» |
| `Phone` | 20 / 1.9 | round | `M9 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H9a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z` + `M11 18h2` | устройство передачи |
| `Delete` | 18 / 2 | round | `M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13` | «Удалить» в мультивыборе |
| `Reset` | 20 / 1.9 | round | `M4 12a8 8 0 1 0 2.3-5.6M4 4v4h4` | «Сбросить» калькулятор |
| `Spade` | **viewBox 0 0 100 100**, fill | — | `M50 8C50 8 14 36 14 58C14 72 26 80 38 76C42 75 45 72 46 70C45 80 41 88 34 92L66 92C59 88 55 80 54 70C55 72 58 75 62 76C74 80 86 72 86 58C86 36 50 8 50 8Z` | пика логотипа (и иконки приложения) |

**Логотип `FeltbookMark`** (Главная, 40 × 40, `r12`, кольцо `0 0 0 1px c.logoRing` — **снаружи** габарита: рисовать `border` на 1 dp больше либо `drawBehind`): `viewBox 0 0 108 108`; `rect 108×108 fill c.felt`; окружность `M20 54a34 34 0 1 0 68 0a34 34 0 1 0 -68 0` stroke `c.brass` 4; буква «F» — текст Playfair Display Bold 46 (в единицах viewBox) `c.ivory`, по центру, базовая линия y = 70 (в Compose — `drawText`, не path, т. к. в макете это текст; для adaptive icon — кривые, M6.5); пика `Spade` с `translate(49 74) scale(0.1)` `fill c.brass`.

**Текстовые глифы** (не иконки, рисуются шрифтом): масти `♠ ♥ ♦ ♣` (Plex Sans; на картах и в подписях), `›` (в конце значений-ссылок), `▾` (выпадающий период), `✓` (рассчитан), `↳` (подстроки журнала), `●` (легенда), `×` (в «докуп ×N»), `—` (пусто), `−`/`+` (калькулятор).

---

## 4. Общие компоненты `:core:ui`

### 4.1. `PlayerAvatar` — карта-аватар

`Box(size, shape r, bg)` → `Column(center, lh 1)`: инициал (Playfair) + масть (Plex Sans).

| Размер | Габарит | Радиус | Инициал | Масть | Где |
| --- | --- | --- | --- | --- | --- |
| `L` | 44 × 60 | 8 | `t.avatarInitialL` 22 | `t.suitL` 15 | шапка карточки игрока |
| `M` | 34 × 46 | 6 | `t.avatarInitialM` 17 | `t.suitM` 12 | строки стола, шторки, долги |
| `S` | 30 × 40 | 5 | `t.avatarInitialS` 15 | `t.suitS` 11 | строки итога |
| `XS` | 28 × 38 | 5 | `t.avatarInitialXS` 14 | `t.suitXS` 10 | статистика |

- Фон `c.ivory`; инициал и масть — `c.suitRed` для ♥ ♦, ★`c.suitBlack` для ♠ ♣.
- Неактивный (игрок вышел): фон ★`c.cardInactive`, инициал и масть `c.inkSoft`.
- Инициал — первая графема имени, `uppercase()`. Масть: **за столом** — по порядку посадки `players[i]` → `[♠, ♥, ♣, ♦][i % 4]` (совпадает с макетом стола: Дима ♠, Борис ♥, Андрей ♣, Егор ♦, Саша ♠); **вне стола** (статистика, долги) — стабильно от канонического имени: `[♠, ♥, ♣, ♦][abs(name.hashCode()) % 4]` (макеты статистики и стола расходятся — Дима ♣ / ♠ — поэтому одно правило для обоих невозможно, см. §12).
- `contentDescription = null` (декоративный; имя рядом).

### 4.2. `ChipDenomination` — фишка быстрой суммы

```
Box(64×64, CircleShape, bg = chipColor, shadow(4, 10, 0.35))        // выбранная: без тени, см. ниже
  Box(56×56, CircleShape, dashed border 5 c.ivory)                  // кант
    Box(36×36, CircleShape, bg = chipColor, border 1.5 c.chipInnerRing, center)
      Text(label, t.chipDenomination, c.ivory)                      // "500" "1K" "2K" "5K"
```
- Пунктир CSS `5px dashed` по окружности: в Compose `drawCircle(style = Stroke(5.dp, pathEffect = dashPathEffect))`, радиус по середине штриха 25.5 dp; шаг — **10 штрихов** равной длины со скважностью 1:1 (≈ 8 dp штрих / 8 dp пробел), `StrokeCap.Butt`. Точное соответствие сверить Roborazzi-скриншотом с макетом.
- Цвета: 500 `chips.chip500`, 1 000 `chips.chip1000`, 2 000 `chips.chip2000`, 5 000 `chips.chip5000`.
- **Выбранная**: тень убирается, вокруг — кольцо: зазор 3 dp `c.feltDeep` + 2 dp `c.brass` (CSS `0 0 0 3px #071A14, 0 0 0 5px #C9A35B`) — рисуется **снаружи** 64 dp (общий визуальный габарит 74 dp, раскладку не меняет).
- **«как прошлый»**: `Box(64, CircleShape, dashed border 1.5 c.selectedBorder, без фона)` → `Column(center)`: «как» / «прошлый» `t.tinyBold` (lh 1.2) `c.brassLight`. Сумма — последняя покупка этого игрока (`BUY_IN` его последней операции покупки); у нового игрока или без покупок — скрыта (ряд из 4 фишек, `SpaceBetween` сохраняется).
- Ряд: `Row(fillMaxWidth, SpaceBetween)` — 5 × 64 в 350 dp.
- Выбор: фишка выбрана, когда текущая сумма равна её номиналу. Нажатие **устанавливает** сумму (не прибавляет); следующий ввод с клавиатуры начинает новое число.
- a11y: `contentDescription` = «500 фишек», «1 000 фишек», …, «Как прошлый раз, 1 000» + состояние `selected`; роль `RadioButton`.

### 4.3. `AmountKeypad`

`Grid 3 × 4, gap 6`, порядок `1 2 3 / 4 5 6 / 7 8 9 / 000 0 ⌫`.
- Клавиша: высота **48** (макет 46 — увеличено до минимальной цели 48 dp, §12), ширина = ⅓ − gap, `r12`, фон `c.quietFill`, текст `c.ink`; цифры `t.keypadDigit`, «000» `t.keypadDigitSmall`; ⌫ — `Backspace` 22.
- Поведение: ведущие нули не вводятся; «000» к пустому — ничего; длина ограничена `MAX_AMOUNT` домена (лишние нажатия — короткая вибрация отказа); ⌫ — удалить последнюю цифру, **долгое** ⌫ — очистить. Системная клавиатура не открывается никогда.
- a11y: «Стереть» у ⌫; цифры читаются как есть.

### 4.4. Кнопки

**`BrassButton`** (главная, без удержания, с debounce 600 мс):

| Вариант | Высота | Радиус | Фон | Текст | Тень | Где |
| --- | --- | --- | --- | --- | --- | --- |
| `flat56` | 56 | 16 | `c.brass` | `t.button` `c.onBrass` | — | «Расчёт произведён» (макет), «Рейк отдан…» (макет), карточка игрока «Докуп» (56, колонка) |
| `flat52` | 52 | 16 | `c.brass` | `t.button` + иконка 20 справа, gap 8 | — | «Вернуться за стол» |
| `flat48` | 48 | 14 | `c.brass` | `t.labelBold`/15 Bold | — | «Погасить», «Погасить сейчас» |
| `hero64` | 64 | 20 | градиент `brassButton` | `t.buttonL` | `shadow(10, 24, 0.4)` | «Начать игру», «Выдать 1 000 фишек» |
| `hero68` | 68 | 20 | градиент + блик 1 dp сверху `c.brassHighlight` (inset) | см. `IssueChipsButton` | `shadow(10, 24, 0.4)` | «Выдать 2 000 фишек» |

Недоступна: фон ★`c.brassDisabled`, текст ★`c.onBrassDisabled`, без тени. После нажатия блокируется до завершения операции; повтор < 600 мс игнорируется (`DebouncedClick` в `:core:ui`).

**`IssueChipsButton`** (шторка закупа): `Row(h68, r20, p(0 20 0 12), gap 14, centerV)`:
1. Мини-фишка 44: `Box(44, Circle, bg = цвет номинала)` → `Box(38, Circle, dashed border 4 c.ivory)` (без текста). Цвет: выбранная фишка, иначе по сумме: ≥ 5 000 — `chip5000`, ≥ 2 000 — `chip2000`, ≥ 1 000 — `chip1000`, иначе `chip500`.
2. `Column(weight 1, gap 1)`: «Выдать 2 000 фишек» `t.buttonHero` `c.onBrass`; вторая строка `13 SemiBold` (`t.chipSecondary` = 13/600) `c.onBrassSecondary`: «2 200 ₽ записать в долг».
3. `Check` 24 / 2.4 `c.onBrass`.

**`HoldToConfirmButton`** (деньги на руки, закрытие, передача):
```
Box(h = 68|64|56, r20 (r16 у 56), clip)
  [покой]   фон = градиент brassButton на всю ширину (выглядит как hero-кнопка)
  [нажата]  фон = c.holdTrack; поверх слева направо полоса градиента brassButton шириной progress·100 %
  Column(center, lh 1.2)
    Text(title, t.buttonL, c.onBrass)
    Text(hint,  t.smallSemi, c.onBrassSecondary)     // «удерживайте, чтобы подтвердить»
```
- Прогресс 0 → 1 за **600 мс линейно** при удержании; отпустил раньше — полоса возвращается к 0 за 200 мс, кнопка снова в «покое». На 1.0 — `HapticFeedbackType.LongPress` (если «Вибрация при подтверждении» вкл.), вызов `onConfirm`, кнопка заблокирована до завершения операции.
- В макетах: DebtCashOut — состояние «покой» (полоса 0 % + слой градиента сверху), Handover — 38 % (дорожка `#8C7444` видна справа).
- TalkBack: обычное нажатие не подтверждает; `onClickLabel` = hint, кастомное действие «Подтвердить» (`semantics { customActions }`) вызывает `onConfirm` сразу — удержание недоступно в режиме TalkBack.
- Тень — у 64/68 `shadow(10, 24, 0.4)` (как у hero), в Handover/DebtSettlement/DebtCashOut тени в макете нет → **без тени** у hold-кнопок.

**`OutlineButton`** (отмена/второстепенное): `h52` (`48` в Handover, долгах, предупреждении; `56` в карточке игрока), `r16` (`r14` у 48), рамка 1 `c.outline`, фон `c.outlinedFill`, текст `t.title` (15/600) `c.inkSoft` (у «Отдал часть…», «Посадить с долгом», карточки игрока — `c.ink`); иконка слева 18 (`Close`) с gap 8 у «Отмена».

**`DangerOutlineButton`** («Не заплатил»): `h52 r16`, рамка 1 `c.oweBorderMedium`, фон `c.oweBackgroundSoft`, текст 15 Bold `c.owe`, `Alert` 18 слева, gap 6.

**`DangerTextButton`** (в диалоге): `h44 p(0 18) r22`, без фона, текст `t.labelBold` `c.owe`. В списке настроек — просто строка с текстом `t.bodyL` `c.owe`.

**`IconButton44`**: визуально 44 × 44 `CircleShape`, фон `c.quietFill` (в шапках «назад» — без фона), иконка 22 `c.ink`; цель касания 48 (`minimumInteractiveComponentSize`).

**`ShareButton`**: 56 × 56 `r16`, рамка 1 `c.brassBorderStrong`, без фона, `Share` 22 `c.brassLight`.

**`DeleteActionChip`** (шапка мультивыбора): `h40 p(0 14) r20`, фон `c.oweBackgroundStrong`, `Delete` 18 + «Удалить» `t.labelBold`, всё `c.owe`, gap 6.

### 4.5. `RebuyBadge` и `BankerBadge`

- «докуп ×N»: `t.tinyBold` `c.brassLight`, рамка 1 `c.brassBorderStrong`, `r6`, `p(0 5)`; стоит после имени с gap 6 (8 в карточке игрока). Показ при `PlayerState.rebuyCount ≥ 1`.
- «БАНКИР»: `t.tinyBold` `c.onBrass`, фон `c.brass`, `r6`, `p(1 6)`, отступ слева 4 + пробел (≈ 8). Показ при `isBanker`. Если у банкира есть докупы — оба значка, сначала «БАНКИР».

### 4.6. `TableScoreboard` — табло

```
Column(gap 10)
  Box(fillMaxWidth, h132, shape = r66 (стадион), border 6 c.rail,
      внутри границы — inset-кольцо 1.5 c.focusBorder, bg feltTable)
    Column(center, gap 2)
      Text("Фишек в игре", t.overlineBoard, c.inkSoft)         // uppercase
      AmountText(chipsInPlay, t.amountBoard, c.ink)             // без ₽
      Text("выдано 12 500 · сдано 4 200 · докупов 2", t.small, c.inkSoft)
  Grid(2 col, gap 8)  ×4:
    Column(bg c.card, border 1 c.brassHairline, r16, p(10 12), gap 2)
      Text(label, t.ledgerTight, c.ledger, ellipsis)
      AmountText(value, t.amountTile, color)
```
| Ячейка | Подпись | Значение | Цвет |
| --- | --- | --- | --- |
| ♦ | «♦ Касса на руках» | `TableSummary.cashOnHand` ₽ (может быть < 0 — со знаком «−») | `c.ink` |
| ♠ | «♠ Долги игроков» | `playersOwe` ₽ | `c.owe` (0 → `c.ink`) |
| ♣ | «♣ Рейк заведению» | `rakeDue` ₽ | `c.ink` |
| ♥ | «♥ Банкиру» | `bankerIncome` ₽ | `c.brassLight` |

Под суммой ♠, если `bankerOwes > 0`, — третья строка `t.ledgerTight` `c.pay` «банкир должен 300 ₽» **[без макета]** (ТЗ §4 требует показывать долг банкира вышедшим).
Нажатие на любую ячейку или овал — шторка «Расшифровка» (§5.3.4). a11y: каждая ячейка — одна кнопка, описание «Касса на руках, 950 рублей».

### 4.7. Нижняя панель `FeltbookBottomBar`

```
Row(fillMaxWidth, h80 + navigationBars inset, bg c.feltDeep, верхняя граница 1 c.brassContainer,
    p(0 0 8 0) + inset)                                   // 3 равные колонки
  Item × 3: Column(center, gap 4)
    Box(64×32, r16, bg = selected ? c.brassIndicator : none, center) { icon }
    Text(label, selected ? t.navSelected c.ink : t.smallSemi c.muted)
```
- «Игра» — глиф «♠» `t.suitTab`; «История» — `History` 22; «Настройки» — `Settings` 22. Иконка выбранной — `c.brassLight`, невыбранной — `c.muted`.
- Пасхалка: см. §8.3. Повторное нажатие на выбранную вкладку — к корню вкладки.
- Видимость — §8.2.

### 4.8. `BrassFab`

`Row(h56, p(0 22 0 18), r18, bg c.brass, gap 8, centerV)`: `Plus` 22 / 2.2 + текст `t.button` `c.onBrass`; тень `shadow(8, 24, 0.35)`. Позиция: `end 16`, низ — **20 dp над верхом нижней панели** (в макете `bottom 100` при панели 80). Когда панель скрыта прокруткой — `16 dp + navigationBars`. Тексты: «Новая игра» (Главная), «Игрок» (Стол; a11y «Добавить игрока»).

### 4.9. Сегменты `SegmentedToggle`

| Вариант | Контейнер | Сегмент | Выбран | Не выбран | Где |
| --- | --- | --- | --- | --- | --- |
| `regular` | `bg c.feltDeep, r14, p4, gap 4` | `h44 r10` | `bg c.card` + inset 1.5 `c.selectedBorder`, `t.labelBold c.ink` | без фона, `t.labelSemi c.muted` | «В долг / Оплачено сейчас», «Отдать / Принять игру» |
| `compact` | `r12, p3, gap 3` | `h40 r9` | то же, `t.chipBold` | `t.chip c.muted` | правила в «Новой игре» |
| `position` | `r12, p3, gap 4` | `h36 r9` | `bg c.card` + inset 1.5 `c.selectedBorder`, `t.positionLabel` Medium `c.brassBright` | `t.positionLabel c.muted` | калькулятор |

Колонки равные (`weight 1`). Роль — `RadioButton`, группа — `selectableGroup`. Цель касания ≥ 48: контейнер 52 (44 + 2·4) — нажатие засчитывается по всей высоте контейнера.

### 4.10. Чипы

- **`FilterChip`** (чаевые 36, фильтр истории 34): `h36|34, p(0 14), r18|17`. Выбран: фон `c.brassIndicator`, inset 1.5 `c.brass`, текст `t.chipBold` `c.brassLight` (в истории — `c.brassBright`). Не выбран: рамка 1 `c.chipBorder`, без фона, `t.chip` `c.inkSoft`. Ряд `gap 8`, у чаевых — `FlowRow`.
- **`SmallToggle`** («Кому: Банкиру / Игроку…»): `h32 p(0 12) r10`; выбран — фон `c.feltDeep`, `t.chip` `c.ink`; нет — без фона, `c.muted`.
- **`DropdownChip`** («За 3 месяца ▾»): `h36 p(0 12) r18`, рамка 1 `c.brassBorderStrong`, `t.chip` `c.brassBright`.
- Визуальная высота сохраняется, цель касания 48 (`minimumInteractiveComponentSize`).

### 4.11. `FeltbookSwitch`

52 × 32, `r16`. Вкл.: фон `c.brass`, бегунок 24 `c.onBrass` (отступ 4). Выкл.: без фона, рамка 1.5 `c.switchOffBorder`, бегунок 17 `c.muted` (центр в 16,16). Реализация — M3 `Switch` с `SwitchDefaults.colors(checkedTrackColor = brass, checkedThumbColor = onBrass, checkedBorderColor = Transparent, uncheckedTrackColor = Transparent, uncheckedBorderColor = switchOffBorder, uncheckedThumbColor = muted)`; отличие от макета (рамка 2 вместо 1.5, бегунок 16 вместо 17) ≤ 1 dp — допустимо. Вся строка — `toggleable` (цель касания — строка).

### 4.12. Поля

**`FeltTextField`** (имя, название, банкир): `Column(bg c.card, r14, p(10 14), gap 4)`; рамка: не в фокусе 1 `c.hairline`, в фокусе 1.5 `c.focusBorder`. Подпись `t.small` `c.ledger`; значение `t.fieldValue` (имя игрока — `t.fieldValueL`) `c.ink`, `BasicTextField`, курсор `c.brass`. Ошибка — под полем `t.small` `c.owe`, рамка 1.5 `c.oweBorderStrong`.

**`AmountField`** (фишки на руках): `Column(bg c.card, r16, p(12 14), gap 6)`; рамка в фокусе 1.5 `c.focusBorder`, иначе 1 `c.hairline`; подпись `t.fieldLabelCaps` (`c.muted` в фокусе, `c.ledger` вне); значение `AmountText` `t.amountBoard` в фокусе / `t.amountInput` вне. Не редактируется системной клавиатурой: нажатие фокусирует поле и открывает `AmountKeypad` (§5.6).

**`ValueBox`** (процент рейка): `96 × 46, r12, bg c.card, Row(center, gap 4)`: значение `t.fieldValueBold` `c.ink`, единица «%»/«₽» `t.fieldValueBold` `c.ledger`. Нажатие — шторка ввода числа с клавиатурой.

### 4.13. `UndoSnackbar` [без макета]

`Row(m(0 16), bg c.dialog, border 1 c.dialogBorder, r16, p(12 8 12 16), gap 8, shadow(16, 40, 0.5))`: текст `t.body` `c.ink` (1–2 строки, обрезка), действие «Отменить» — `TextButton` `t.labelBold` `c.brassLight`. Длительность **5 с** (`SnackbarDuration` своё — 5000 мс), над FAB/нижней панелью (`bottom = 16 над панелью`). Одна операция — один snackbar; новая заменяет старую (старая остаётся отменяемой через журнал).

### 4.14. `FeltbookDialog` (диалог с глаголом)

Из макета History:
```
Dialog(Alignment.BottomCenter, m(0 16), низ = 16 над нижней панелью (bottom 96) | 16 + navigationBars без панели)
  Column(bg c.dialog, border 1 c.dialogBorder, r24, p18, gap 10, shadow(16, 40, 0.5))
    Text(title, t.dialogTitle, c.ink)
    Text(body, t.body lh 1.45, c.inkSoft)
    [опц. поле ввода — FeltTextField]
    Row(end, gap 8, m(4 0 0 0))
      SafeButton: h44 p(0 18) r22, рамка 1 c.outlineDialog, фон c.quietFill, t.labelBold c.ink   // по умолчанию, фокус TalkBack
      DangerTextButton (или BrassText для неопасного подтверждения: t.labelBold c.brassLight)
```
- Диалог прижат к низу (зона большого пальца) — так в макете. Scrim `c.scrim` (в макете затемнения нет — добавлено, чтобы диалог был модальным; §12).
- «Назад»/тап мимо = безопасная кнопка.

### 4.15. `FeltbookSheet` (шторка)

```
ModalBottomSheet(skipPartiallyExpanded = true, containerColor = c.sheet, scrimColor = c.scrim,
                 shape = s.sheet (r28 сверху), dragHandle = свой)
  верхняя кромка: линия 1 c.sheetEdge по скруглённому верху (drawBehind по пути верхней дуги)
  Column(p(10 20 24 20) + navigationBars/ime, gap = 12 | 14 | 16)
    DragHandle: 36×4, r2, c.dragHandle, CenterHorizontally
```
- Верх шторки в макетах: закуп `top 56`, выход `top 64`, карточка игрока `top 96`, новый игрок `top 200` — это высота содержимого; шторка = высоте содержимого, но не выше `экран − 56 dp − statusBars`; содержимое прокручивается, если не влезло (шрифт 200 %).
- **Закрытие с введённой суммой** (свайп вниз, «назад», тап по scrim) при непустом вводе → `FeltbookDialog`: заголовок «Отменить ввод?», текст «Введённая сумма 2 000 не сохранится.», кнопки «Продолжить ввод» (безопасная) / «Отменить ввод» (опасная). Пустая — закрывается сразу. Реализация: `sheetState.confirmValueChange { it != Hidden || !hasInput }` + `BackHandler`.
- Predictive back — встроенный у `ModalBottomSheet`.

### 4.16. `SectionHeader`

- **Заголовок-строка**: `Row(baseline, SpaceBetween, p(18 20 8))` (Главная `p(22 20 8)`): слева `t.sectionTitle` `c.ink`, справа `t.caption`/`t.small` (`c.muted` или `c.ledger`) или ссылка `t.chip` `c.brass` (нажата — `c.brassLight`).
- **Надзаголовок-гроссбух**: `Text(t.ledgerCaps, c.ledger, uppercase)`, `m(12|14|18 20 8)` — первый на экране 12 (настройки)/14 (история), остальные 18.

### 4.17. Строки списков и `AmountText`

**`AmountText`**: `BasicText(maxLines = 1, softWrap = false, autoSize = TextAutoSize.StepBased(minFontSize = max(12.sp, 0.7 × style.fontSize), maxFontSize = style.fontSize, stepSize = 0.5.sp), style + tnum)`; цвет и знак — по смыслу; `semantics { contentDescription = "Банкир выплачивает 353 рубля" }` (формулы a11y — §11.17).

**`AmountRowLayout`** (строка с суммой справа): кастомный `Layout` из трёх слотов `leading` (аватар/чекбокс/дата), `content` (имя + мета, обрезка многоточием), `trailing` (суммы). Правый слот меряется с `maxWidth = 0.5 × ширины строки`, левый получает остаток. Обрезается левая часть, сумма — никогда (ТЗ §5.1). Все строки списков ниже — через него.

### 4.18. `MetricTile`

`Column(bg c.card, r14|16|18, p(10 12)|(12 14)|14, gap 2|4)`: подпись (`t.tiny`/`t.small` `c.ledger`), значение `AmountText` (`t.amountMetric`/`amountTile`/`amountStat`/`amountCard`), опц. третья строка. Варианты:
- **owe**: фон `c.oweBackground`, рамка 1 `c.oweBorder`, подпись `c.oweSoft`, значение `c.owe`.
- **brass**: фон `c.brassContainer`, рамка 1 `c.brassBorderStrong` (статистика — `c.brassBorder`), подпись `c.brassLight`, значение `c.brassBright`, третья строка `c.brassMuted`.
- **pay** [без макета для плитки]: фон `c.payContainer`, рамка 1 `c.payBorder`, подпись `c.inkSoft`, значение `c.pay`.

### 4.19. Панели итога `ResultPanel`

```
Row(r20, p16, gap 14, centerV, border 1.5)
  Box(44, Circle, bg = iconBg, center) { Icon 22 / 2.2, c.ivory }
  Column(gap 2, minWidth 0) { Text(label, t.caption, labelColor); AmountText(value, t.amountResult, valueColor) }
```
| Вариант | Фон | Рамка | Иконка/плашка | Подпись | Сумма |
| --- | --- | --- | --- | --- | --- |
| `pay` | градиент `payPanel` | `c.payBorderStrong` | `ArrowDown` / ★`c.payIcon` | `c.inkSoft` | `c.pay`, «+353 ₽» |
| `owe` | `c.oweBackground` | `c.oweBorderStrong` | `ArrowUp` / ★`c.oweIcon` | ★`c.oweTint` | `c.owe`, «1 053 ₽» |
| `even` [без макета] | `c.card` | `c.chipBorder` | `Check` / `c.cardInactive` | `c.inkSoft` | `c.ink`, «0 ₽» |

### 4.20. `InfoBanner`

`Row(r14, p(10 12), gap 10, centerV, border 1)`, текст `t.caption` `c.ink`, иконка 20. Варианты: `ok` (фон `c.payContainer`, рамка `c.payBorder`, `Check` 20/2.2 `c.pay`), `danger` (фон `c.oweBackground`, рамка `c.oweBorderStrong`, `Alert` `c.owe`), `warn` [без макета] (фон `c.brassContainer`, рамка `c.brassBorderStrong`, `Alert` `c.brassBright`, текст `c.brassBright`) — для «подозрительной суммы», раннего выхода, «фишек больше, чем в игре».

### 4.21. `BreakdownList` (`dl` расшифровка)

`Column(gap 6)`, строки `Row(SpaceBetween)`: подпись `t.body` (`c.muted` в шторках, `c.ledger` в акте/выходе-с-долгом), значение `t.body` `c.ink` справа, `nowrap` (через `AmountRowLayout`). Итоговая строка — `p(6 0 0 0)`, верхняя черта 1 `c.divider`, подпись `t.labelBold`, значение 14 Bold. Контейнер в закупе — `bg c.card r14 p(12 14)`, в выходе — без фона `p(0 4)`.

---

## 5. Экраны

### 5.1. Главная (`Home`, вкладка «Игра», маршрут `Home`)

Фон `feltScreen`. Нижняя панель — видна, «Игра» выбрана. FAB «Новая игра». Содержимое прокручивается (`LazyColumn`), низ — отступ под FAB (80 + 20 + 56 + 16).

```
Column
  Header: Row(p(24 20 12), gap 12, centerV)
    FeltbookMark(40, r12, внешнее кольцо 1 c.logoRing)
    Column(lh 1)
      Text("Feltbook", t.brand, c.ink)
      Text("банк покерного стола", t.taglineCaps, c.brass, uppercase, m(4 0 0 0))
  [если есть активная игра] ActiveGameCard: Column(m(4 16 0), r24, p18, bg feltActiveGame,
                                                   border 1 c.brassBorder, gap 14)
    Row(gap 8, centerV): Box(8×8, r4, c.pay) + Text("Идёт игра", t.overline, c.inkSoft, uppercase)
    Column(gap 2)
      Text(game.title, t.display, c.ink, ellipsis 2 строки)
      Text("2 ч 14 мин · 6 игроков · банкир Андрей", t.ledger, c.ledgerLight, ellipsis)
    Grid(3 col, gap 8): ×3 Column(gap 2)
      Text("В игре" | "Долги" | "Рейк", t.tiny, c.inkSoft)
      AmountText("8 300" | "9 200 ₽" | "1 250 ₽", t.amountMetric, c.ink | c.owe | c.ink)
    BrassButton.flat52("Вернуться за стол" + ArrowForward 20)       → Table(gameId)
  [без макета] OpenDebtsBlock (если Debts.open не пуст), см. ниже
  SectionHeader(p(22 20 8)): "Прошлые игры" | ссылка "Вся история" → вкладка История
  List(p(0 16), gap 8): PastGameRow × N
  [пусто] Text("Здесь появятся сыгранные игры", t.caption, c.muted, p(8 20))
```

**PastGameRow**: `AmountRowLayout(bg c.card, r16, p(12 14), gap 12, centerV)`:
- leading: `Column(w44, center, lh 1.1)`: день `t.amountTile` `c.ink` («25»), месяц `t.tiny` `c.ledger` («окт»);
- content: `Column(gap 2)`: `game.title` `t.title` ellipsis; мета `t.ledger` `c.ledger` ellipsis «4 ч 05 мин · 8 игроков · 11 докупов»;
- trailing: `Column(end, gap 2)`: «♥ 800 ₽» `t.amountRow` `c.brassLight`; «рейк 2 900» `t.tiny` `c.ledger` (без ₽ — как в макете).
- Нажатие → карточка игры (`GameDetail`, вкладка История).

**Данные**
| Элемент | Источник |
| --- | --- |
| Карточка видна | `GameRepository.observeActive()` ≠ null (статус RUNNING/SETTLING) |
| «Идёт игра» | RUNNING; в SETTLING — «Идёт расчёт» с точкой `c.brass` **[без макета]** |
| длительность | `now − game.createdAt`, тикер раз в минуту |
| «6 игроков» | `TableSummary.playersAtTable` (plural `players_count`) |
| «банкир Андрей» | `game.currentBankerName` |
| В игре / Долги / Рейк | `chipsInPlay` (без ₽) / `playersOwe` ₽ (`c.owe`, при 0 — `c.ink`) / `rakeDue` ₽ |
| Прошлые игры | `Stats.historyItem` для CLOSED/HANDED_OVER, новые сверху, **до 5** |
| день/месяц | `startedAt` |
| мета | `endedAt − startedAt`, `playerCount`, `rebuyCount` (plural `rebuys_count`; 0 → «без докупов») |
| ♥ | `bankerIncome`; рейк — `rake` |
| пометки [без макета] | `hasOpenDebt` → в конце меты « · долг» `c.owe`; HANDED_OVER → « · передана» |

**OpenDebtsBlock [без макета]** (ТЗ §5.2, roadmap M4.1): `SectionHeader(p(22 20 8))` «Открытые долги» | ссылка «Все» → `Debts`; под ним `Row(m(0 16), bg c.oweBackground, border 1 c.oweBorder, r18, p(14 16), SpaceBetween)` — левая колонка «Вам должны» `t.caption` `c.oweTint` + `t.ledger` `c.oweTint` «2 игрока · 2 игры»; справа `t.amountTotal` `c.owe` «1 653 ₽» (тот же блок, что верх экрана «Открытые долги»). Нажатие → `Debts`.

**Взаимодействия**: FAB «Новая игра» → `NewGame`; если активная игра есть — вместо перехода `FeltbookDialog` «Уже идёт игра» / «Сначала закройте «Пятничный стол» — одновременно идёт только одна игра.» / кнопки «Понятно» / «К столу» (брасс-текст) **[без макета]**.

### 5.2. Новая игра (`NewGame`)

Фон `feltScreen`, `Column(fillMaxSize)`, нижняя панель **скрыта**. Содержимое прокручивается, кнопка — закреплена внизу.

```
Header: Row(p(16 16 8 8), gap 8, centerV)
  IconButton44(Back, без фона, a11y "Назад")
  Text("Новая игра", t.display)
Column(p(4 16 0), gap 10)
  FeltTextField("Название", value, placeholder = defaultTitle)      // в фокусе — рамка 1.5 focusBorder
  FeltTextField("Банкир", value)                                   // рамка 1 hairline
  Row(bg c.card, r14, p(12 14), gap 12, centerV, toggleable)
    Column(weight 1, gap 2)
      Text("Я тоже играю", t.title)
      Text("рейк плачу, сбор с себя не беру", t.small, c.ledger)
    FeltbookSwitch(checked)
RulesSection: Column(m(16 16 0), bg c.sheet, border 1 c.brassBorderSoft, r20, p14, gap 14)
  Row(baseline, SpaceBetween)
    Text("Правила", t.sectionTitle)
    Text("из настроек · можно изменить", t.small, c.ledger)
  Column(gap 6)
    Text("♥ Сбор банкира", t.caption, c.inkSoft)
    SegmentedToggle.compact(["Разово 100 ₽", "Разово 10 %", "С каждого"])
    [если "С каждого"] [без макета] Row(gap 8): SegmentedToggle.compact(["Процент","Фикс"]) + ValueBox
  Column(gap 6)
    Text("♣ Рейк заведению с каждой покупки", t.caption, c.inkSoft)
    Row(gap 8): SegmentedToggle.compact(["Процент","Фикс"], weight 1) + ValueBox("10", "%")
  Column  // dl без gap, каждая строка p(10 0), верхняя черта 1 c.hairline
    Row: Text("Минимальная покупка", t.body, c.inkSoft) | Text("500 фишек ›", 14 Bold, c.ink)
    Row: "Минимальное время игры" | "60 мин ›"
    Row: "Оплата покупки" | "одним статусом ›" / "раздельно ›"
  Box(bg c.feltDeep, r12, p(10 12))
    Text("Пример: закуп 1 000 → к оплате 1 200 ₽\n(1 000 фишки + 100 рейк + 100 сбор)", t.ledger lh 1.5, c.ledgerLight)
Spacer(weight 1)
Box(p(12 16 24) + navigationBars)
  BrassButton.hero64(Row(center, gap 10): Text("♠", t.glyphButton) + Text("Начать игру", t.buttonL))
```

**Данные и логика**
- `rules` — `AppSettings.rulesForNewGame`; банкир — `lastBankerName`; «Я тоже играю» — по умолчанию **вкл.** (как в макете; `AppSettings` поля не имеет — запоминать не нужно, §12).
- Название: пустое → `defaultTitle` = `"%d %s, %s"` («2 ноя, 20:05» — `months_short` + `HH:mm`), показывается плейсхолдером `c.muted`; лимит `Game.MAX_TITLE_LENGTH` (60) — счётчик не показываем, ввод обрезается.
- Сбор (3 сегмента ↔ 4 режима домена): «Разово N ₽» = `ONCE_FIXED`, «Разово N %» = `ONCE_PERCENT`, «С каждого» = `EACH_*` + второй ряд «Процент/Фикс» и `ValueBox`. Подписи первых двух сегментов динамические: значение из правил, если режим выбран, иначе — запомненное для режима (по умолчанию 100 ₽ / 10 %). Повторное нажатие на выбранный сегмент — шторка ввода значения (клавиатура). **[решение без макета]**
- Рейк: `RakeMode`; `ValueBox` показывает `rakeValue` и «%» / «₽».
- Строки `dl` → шторка ввода числа (`MIN_BUY_IN` в фишках; минуты 1…`MAX_MIN_PLAY_MINUTES`) или выбор «одним статусом / раздельно» (`splitPayment`).
- Пример — `Pricing` на `chips = 1 000`, первый закуп, не банкир: «закуп 1 000 → к оплате {total} ₽» / «({chips} фишки + {rake} рейк + {fee} сбор)». Пересчёт на каждое изменение.
- Ошибки: `rules.problems()` → текст под соответствующим блоком `t.small` `c.owe` (строки `rules_problem_*`), кнопка недоступна. Пустое имя банкира — ошибка поля «Укажите имя банкира».
- «Начать игру» — `DebouncedClick` → `Commands.startGame(...)`; `AnotherGameActive` → диалог как в §5.1; успех → `Table(gameId)` с `popUpTo(Home)`; изменённые правила сохраняются в `rulesForNewGame` (QUESTIONS №4).

### 5.3. Стол (`Table`, вкладка «Игра»)

Фон `feltScreen`. Нижняя панель — видна, «Игра» выбрана, **скрывается при прокрутке вниз** и возвращается при прокрутке вверх (`nestedScroll`). FAB «Игрок». Весь экран — один `LazyColumn` (шапка и табло прокручиваются вместе со списком; отступ снизу под FAB).

```
Header: Row(p(20 12 8 20), gap 8, centerV)
  Column(weight 1, gap 2)
    Text(game.title, t.displayTable, ellipsis)
    Text("Идёт 2 ч 14 мин · банкир Андрей", t.caption, c.muted, ellipsis)
  IconButton44(Handover, bg c.quietFill, a11y "Передать комнату")   → Handover(gameId)
  IconButton44(MoreVert, bg c.quietFill, a11y "Меню игры")          → меню
TableScoreboard(m(8 16 0))
SectionHeader(p(18 20 8)): "Игроки" | Text("6 за столом · 1 вышел", t.caption, c.muted)
List(p(0 16), gap 8): PlayerRow × N                                   // свайпы, нажатие
[SETTLING] [без макета] InfoBanner.warn(m(0 16 8)): "Игра завершается: выведите оставшихся игроков" + кнопка "К итогу"
```

**PlayerRow** — `AmountRowLayout(r16, p(10 12), gap 12, centerV)`:

| Состояние (`PlayerState`) | Контейнер | Аватар | Строка имени | Мета (2-я строка) | Сумма (`amountRow`) | Подпись (`tiny c.muted`) |
| --- | --- | --- | --- | --- | --- | --- |
| PLAYING, `outstanding < 0` | `bg c.card` | M, обычный | имя `t.title` + `RebuyBadge`/`BankerBadge` (gap 6) | `t.ledgerTight c.ledger`: «закуп 3 000 · 1 ч 52 мин» | «−3 400 ₽» `c.owe` | «в долг» |
| PLAYING, `outstanding == 0` | то же | | | | «оплачено» `c.pay` | банкир — «без сбора», иначе — пусто |
| PLAYING, `outstanding > 0` [без макета] | то же | | | | «+300 ₽» `c.pay` | «банкир должен» |
| PLAYING, ранний (`isEarlyExit(rules, now)`) | то же | | | **`t.small` (Plex Sans) `c.brassLight`**: «закуп 1 000 · 47 мин из 60» | как выше | как выше |
| CASHED_OUT | **без фона**, рамка 1 **пунктир** `c.chipBorder`, `alpha 0.8` | M, неактивный | имя | «вышел · рассчитан» (`outstanding == 0`) / «вышел · банкир должен» | `balanceAtExit` со знаком, **без ₽** `c.inkSoft` («+2 700»); при `outstanding > 0` — `c.pay` «+N ₽» | «выплачено» / «получено» (balanceAtExit < 0) / «не выплачено» |
| LEFT_WITH_DEBT [без макета, по DebtSettlement] | без фона, пунктир 1 `c.oweBorderStrong`, `alpha 0.8` | M, неактивный | имя | «ушёл с долгом» | «−1 053 ₽» `c.owe` | «долг» |

- «закуп N» — `session?.chipsBought ?: chipsBought`; время — `playedFor(now)` (тикер 1 мин).
- Порядок: за столом — по `joinedAt`; затем вышедшие — по `lastCashOutAt`. `isVoid` не показываются.
- **Нажатие** → шторка «Карточка игрока». **Свайп вправо** (за столом и вышедшие) → шторка «Докуп» (для вышедшего — возврат). **Свайп влево** (только за столом) → шторка «Выход». Срабатывание после **40 % ширины** строки; в точке порога — `HapticFeedbackType.SegmentFrequentTick`/`LongPress` один раз; отпущено раньше — пружинный возврат; после срабатывания строка тоже возвращается, открывается шторка (`SwipeToDismissBox` с `positionalThreshold = { it * 0.4f }` и `confirmValueChange` → false + открыть шторку).
- Подложка свайпа **[без макета]**: вправо — `bg c.brassIndicator`, слева `Plus` 22 + «Докуп» `t.labelBold` `c.brassLight`; влево — `bg c.quietFill`, справа «Выход» + `Exit` 20 `c.ink`; радиус 16.
- a11y: строка — кнопка «Борис, докуп 1, должен 2 300 рублей»; кастомные действия «Докуп», «Выход» (замена свайпа).

**Меню «⋮»** (`DropdownMenu`, фон `c.dialog`, текст `t.bodyL`) **[без макета]**: «Журнал игры» → `GameJournal(gameId)`; «Завершить игру» (`c.owe`) → `Commands.finish` → `Settlement(gameId)` (в SETTLING пункт «Вернуться к игре» → `Commands.resume`). Опасное — только тут (ТЗ §5.1).

**FAB «Игрок»** → шторка «Новый игрок» (§5.4). В SETTLING FAB скрыт (покупки запрещены).

**SETTLING**: подзаголовок «Расчёт · 3 ч 40 мин · банкир Андрей»; баннер (см. дерево) с `TextButton` «К итогу» → `Settlement`.

#### 5.3.4. Шторка «Расшифровка» [без макета]
Открывается нажатием на ячейку табло. `FeltbookSheet(gap 12)`: заголовок `t.displayS` («♦ Касса на руках» / «♠ Долги игроков» / «♣ Рейк заведению» / «♥ Банкиру» / «Фишки в игре»), итог `t.amountStat`; список `AmountRowLayout(p(10 0), черта hairline)`: имя `t.title` | сумма `t.amountRow` по смыслу. Источники: касса — `paid − paidOut` по игрокам (+ строка «Рейк отдан» `−rakePaid`, «Доход забран» `−incomeTaken`); долги — `debt` по игрокам и отдельный блок «Банкир должен» (`owedByBanker`); рейк — `rake` по игрокам; банкиру — `bankerFee + tipsToBanker` по игрокам, по банкирам — `incomeByBanker`; фишки — `chipsBought − chipsCashedOut` по игрокам за столом.

### 5.4. Шторка «Закуп / Докуп» и «Новый игрок» (`BuyIn`)

Один компонент `BuyInSheet(mode = NewPlayer | BuyIn(playerId))`. Макеты: `BuyIn` (докуп, клавиатура в фокусе), `DebtWarning` (новый игрок, фокус на имени, есть старый долг).

#### Режим «Докуп / Закуп / Возврат» (макет BuyIn)

```
FeltbookSheet(gap 12, p(10 20 20 20))
  DragHandle
  Row(gap 12, centerV)
    PlayerAvatar.M
    Column(weight 1, gap 2)
      Text("Докуп · Борис", t.displayS, ellipsis)
      Text("уже закуплено 2 000 · долг 2 300 ₽", t.caption, c.muted, ellipsis)
  Column(center, gap 2, p(4 0))
    AmountText(amount | "0", t.amountHero, c.ink | (пусто) c.muted)
    Text("фишек · своё число с клавиатуры или фишка · минимум 500", t.caption, c.muted, center)
  [warn] InfoBanner.warn("20 000 фишек — точно? Обычно здесь берут 2 000")
  Row(SpaceBetween): ChipDenomination ×4 + «как прошлый»
  SegmentedToggle.regular(["В долг", "Оплачено сейчас"])          // или два ряда при splitPayment
  BreakdownList(bg c.card, r14, p(12 14), gap 6, t.body)
    "Фишки"         | "2 000 ₽"
    "Рейк 10 %"     | "200 ₽"
    "Сбор банкира"  | "уже взят" (c.muted) | "100 ₽" | "без сбора" (c.muted)
    ──────────── (divider)
    "Итого в долг"  | "2 200 ₽" (14 Bold, c.owe)
  AmountKeypad
  Spacer(weight 1)
  IssueChipsButton("Выдать 2 000 фишек", "2 200 ₽ записать в долг")
```

| Элемент | Данные |
| --- | --- |
| Заголовок | `quote.isFirstBuyIn` → «Закуп · Имя»; иначе «Докуп · Имя» (возврат вышедшего — тоже «Докуп», подзаголовок «вернулся за стол · …») |
| Подзаголовок | «уже закуплено {chipsBought}» + « · долг {debt} ₽» (debt > 0) / « · оплачено» (debt = 0) / « · банкир должен {owedByBanker} ₽» |
| Сумма | ввод; пусто → «0» `c.muted` |
| Подсказка | `rules.minBuyIn`; при блокере `BelowMinBuyIn` фрагмент «минимум 500» — `c.owe` (`AnnotatedString`) |
| warn | `DomainWarning.SuspiciousBuyIn(chips, typical)`; `typical == null` → «20 000 фишек — точно?» |
| «Рейк 10 %» | `RakeMode.PERCENT` → «Рейк {rakeValue} %», `FIXED` → «Рейк» |
| «Сбор банкира» | `feeAlreadyTaken` → «уже взят»; `feeWaivedForBanker` → «без сбора»; иначе `bankerFee` ₽ |
| Итог | DEBT → «Итого в долг» `c.owe` `toDebt`; PAID → «Итого к оплате» `c.ink` `total`; раздельно → две строки «Оплачено сейчас» `paidNow` `c.ink` и «Итого в долг» `toDebt` `c.owe` |
| Кнопка, 2-я строка | DEBT «{total} ₽ записать в долг»; PAID «{total} ₽ получено сейчас»; раздельно «{paidNow} ₽ сейчас · {toDebt} ₽ в долг» |
| Недоступна | `!quote.canConfirm` (пусто, < минимума, > MAX) |

- Раздельная оплата (`rules.splitPayment`) **[без макета]**: вместо одного сегмента — два ряда `Row(gap 8, centerV)`: подпись `t.caption c.inkSoft` («Фишки» / «Рейк и сбор», ширина 96) + `SegmentedToggle.regular(["В долг","Оплачено"], weight 1)`.
- Статус по умолчанию — «В долг» (ТЗ §3.7, QUESTIONS №11).
- Подтверждение: `Commands.buyIn` → шторка закрывается → `UndoSnackbar` «Докуп 2 000 · Борис» / «Отменить» (`Commands.reverse(operationId)`).

#### Режим «Новый игрок» (макет DebtWarning)

```
FeltbookSheet(gap 14, p(10 20 24 20))
  DragHandle
  Text("Новый игрок", t.displayS)
  FeltTextField("Имя", name, t.fieldValueL, фокус → системная клавиатура, imePadding)
  [подсказки] FlowRow(gap 8): FilterChip(name) × ≤ 8         // PlayerNames.suggestions, [без макета]
  [ошибка] Text("Влад уже за столом", t.small, c.owe) + TextButton("Открыть карточку")   // NameTaken
  [долг] DebtWarningCard
  Column(gap 6)
    Text("Первый закуп", t.caption, c.inkSoft)
    Row(baseline, gap 8)
      AmountText("1 000", t.amountInput)
      Text("фишек · к оплате 1 200 ₽", t.caption, c.ledger)
  SegmentedToggle.regular(["В долг", "Оплачено сейчас"])   // при долге выбрано «Оплачено сейчас»
  [долг] Text("Для игрока со старым долгом по умолчанию выбрано «Оплачено сейчас»", t.small, c.ledger)
  Spacer(weight 1)
  BrassButton.hero64("Выдать 1 000 фишек", t.buttonL)
```

**DebtWarningCard** (`role = alert`, `liveRegion = Polite`): `Column(bg c.oweBackground, border 1.5 c.oweBorderStrong, r18, p14, gap 12)`:
- `Row(top, gap 10)`: `Alert` 22 `c.owe` (`m(1 0 0 0)`) + `Column(gap 4)`: «Влад должен 1 053 ₽» `t.emphasisM` `c.ink`; «с 18 окт · Пятничный стол · 15 дней» `t.ledger` ★`c.oweTint`.
- `Grid(2, gap 8)`: `BrassButton.flat48` «Погасить сейчас» (14 Bold) | `OutlineButton(48, r14)` «Посадить с долгом» (`t.labelSemi c.ink`).
- Данные: `Debts.forName(name, games, directory, excludeGameId = текущая)`: сумма Σ`amount`; «с» — самый старый `since`; игра — `gameTitle` самого старого; «15 дней» — `now − since` (plural `days_count`). Несколько долгов → «Влад должен 1 653 ₽» и «с 11 окт · 2 игры».
- «Погасить сейчас» → шторка «Оплата долга» (§5.7) поверх, `Commands.pay` в старую игру с `bankerName` = банкир текущей игры; по успеху карточка исчезает. «Посадить с долгом» → карточка сворачивается в строку `t.small c.owe` «Старый долг 1 053 ₽ — сядет с долгом».

**Два фокуса** (решение без макета; макет показывает фокус на имени): фокус на имени — системная клавиатура, сумма компактная (`amountInput`), клавиатуры и фишек нет; нажатие на сумму — IME скрывается, раскладка становится как в режиме «Докуп» (`amountHero`, фишки, `BreakdownList`, `AmountKeypad`), кнопка — `IssueChipsButton`. Сумма по умолчанию — пусто (кнопка недоступна, пока сумма < минимума).

Подтверждение: `Commands.addPlayer(snapshot, name, chips, payment)` → закрытие → snackbar «Влад сел за стол · 1 000 фишек» / «Отменить». `NameTaken(existing)` — кнопка недоступна, под полем ошибка + «Открыть карточку» (за столом) / «Вернуть за стол» (вышел → режим «Докуп» этого игрока).

### 5.5. Шторка «Карточка игрока» (`Player`)

```
FeltbookSheet(gap 16, p(10 20 24 20))
  DragHandle
  Row(gap 14, centerV)
    PlayerAvatar.L
    Column(weight 1, minWidth 0, gap 4)
      Row(gap 8, centerV): Text("Борис", t.display, ellipsis) + RebuyBadge/BankerBadge
      Text("в игре 2 ч 10 мин · с 20:05", t.ledger, c.ledger, ellipsis)
    IconButton44(MoreVert, bg c.quietFill, a11y "Ещё: переименовать, ранний выход")
  Grid(3, gap 8): MetricTile(r14, p(10 12), gap 2, подпись t.tiny c.ledger, значение t.amountMetric)
    "Фишек куплено" | "2 000"
    "Начислено"     | "2 300 ₽"
    MetricTile.owe  "Долг" | "2 300 ₽"           // см. варианты
  Grid(3, gap 8): h56 r16 Column(center, gap 2), текст 14
    BrassButton: Plus 20/2.2 + "Докуп" (14 Bold c.onBrass)
    OutlineButton: Payment 20/1.9 + "Оплата" (t.labelSemi c.ink)
    OutlineButton: Exit 20/1.9 + "Выход"
  Row(baseline, SpaceBetween)
    Text("Журнал", t.sectionTitle)
    Text("удержите запись, чтобы отменить", t.small, c.ledger)
  JournalList(t.ledgerL)
```

| Элемент | Данные |
| --- | --- |
| мета | PLAYING: «в игре {playedFor} · с {firstBuyInAt HH:mm}»; вышел: «вышел в {lastCashOutAt HH:mm} · играл {…}»; `earlyExit` → « · ранний выход» |
| Фишек куплено | `chipsBought` (без ₽) |
| Начислено | `charged` ₽ |
| 3-я плитка | `debt > 0` → owe «Долг» `debt` ₽; `owedByBanker > 0` → pay «Банкир должен» ₽; иначе обычная «Оплачено» `paid` ₽ |
| «Оплата» | `debt > 0` → шторка оплаты (§5.7); `owedByBanker > 0` → подпись «Выплатить», шторка выплаты (hold); иначе недоступна |
| «Докуп» | шторка §5.4 (`Commands.buyIn`); недоступна в SETTLING/CLOSED |
| «Выход» | только PLAYING → шторка §5.6; иначе скрыта (ряд из 2) |

Меню «⋮» **[без макета]**: «Переименовать» → `FeltbookDialog` с `FeltTextField("Имя")`, кнопки «Отмена» / «Сохранить» (брасс-текст) → `Commands.renamePlayer` → snackbar «Переименован: Борис → Боря» / «Отменить»; «Ранний выход…» (только PLAYING и ранний) → шторка выхода с уже подтверждённым флагом; «Простить долг…» (`LEFT_WITH_DEBT`) → диалог из §5.9.

**JournalList** (`Journal.forPlayer`, новые сверху): строка `Row(p(11 0), верхняя черта 1 c.hairline, gap 10)` — колонки `52 dp` | `weight 1` | `wrap` (справа, `nowrap`):
- время `c.ledger` («22:40»; не сегодня — «18 окт»); текст `c.ink`; сумма `c.ink`.
- подстроки операции: текст «↳ …» и сумма — ★`c.ledgerLight`.
- отменённая операция: вся строка `alpha 0.55`, текст `TextDecoration.LineThrough`, справа «отменено», подстроки скрыты.

| Операция (`OperationKind`) | Главная строка | Подстроки |
| --- | --- | --- |
| FIRST_BUY_IN | «Закуп {BUY_IN} · в долг» / «· оплачено» / «· частично» | «↳ рейк {p} %» / «↳ рейк», «↳ сбор банкира», «↳ оплачено» (PAYMENT) |
| REBUY | «Докуп {BUY_IN} · …» | те же |
| PAYMENT | «Оплата» | — |
| PAYOUT | «Выплачено» | — |
| SETTLEMENT | «Расчёт при выходе · получено» / «· выплачено» | — |
| CASH_OUT | «Выход · {F} фишек» (+ « · ранний» при `earlyExit`) | «↳ чаевые банкиру {T}», «↳ чаевые: {имя} {T}» |
| DEBT_FORGIVEN | «Долг прощён» | — |
| (получатель чаевых) | «Чаевые от {имя}» | — |

Сумма главной строки — по главной записи операции (BUY_IN / PAYMENT / PAYOUT / CASH_OUT / DEBT_FORGIVEN). Если в игре было несколько банкиров — в конец текста « · {bankerName}».

**Отмена записи**: удержание строки 0,6 с — фон строки заполняется слева направо `c.brassSelectedRow`, вибрация → `FeltbookDialog` «Отменить «Докуп 1 000»?» / «Запись останется в журнале с пометкой «Отменено», суммы пересчитаются.» / поле «Причина (необязательно)» / «Оставить» | «Отменить запись» (опасная) → `Commands.reverse(operationId, reason)`. Snackbar «Отменить» здесь **нет**: отмену отменить нельзя (`IS_REVERSAL`), поэтому — диалог (уровень «необратимое»). `reversible == false` — удержание даёт вибрацию отказа и snackbar с причиной (`journal_not_reversible_*`). a11y — кастомное действие «Отменить запись».

### 5.6. Шторка «Выход» (`CashOut`, `DebtCashOut`)

Один компонент, вид определяется `CashOutQuote.balance`.

```
FeltbookSheet(gap 14, p(10 20 24|20 20))
  DragHandle
  Row(gap 12, centerV)
    PlayerAvatar.M
    Column(weight 1, minWidth 0, gap 2)
      Text("Выход · Дима", t.displayS)
      Text("в игре 1 ч 52 мин · закуп 3 000 · долг 3 400 ₽", t.caption, c.muted, ellipsis)
  AmountField("Фишки на руках", chips)         // в фокусе: amountBoard + рамка focusBorder
  [фокус на поле] AmountKeypad                  // см. ниже
  [warn] InfoBanner.warn("Фишек на выходе больше, чем есть в игре: 8 300")
  [ранний] InfoBanner.warn("Ранний выход: 47 мин из 60 — понадобится подтверждение")
  TipsBlock                                     // balance ≥ 0: развёрнут; < 0: свёрнут в строку
  BreakdownList(p(0 4), gap 6, t.body)
  ResultPanel(pay | owe | even)
  [pay] Text("Результат за игру: +753 фишки", t.ledgerTight, c.ledger, center)
  Spacer(weight 1)
  Actions
```

**TipsBlock** (макет CashOut): `Column(bg c.card, r16, p(12 14), gap 10)`:
- `Row(SpaceBetween, centerV)`: «Чаевые» `t.sectionTitle` | «по желанию игрока» `t.caption c.ledger`.
- `FlowRow(gap 8)`: `FilterChip` «Без чаевых» (по умолчанию) · «до 100 · 53» · «до 500 · 253» · «своя сумма» (выбрана — «своя · 120»). Остаток — `CashOuts.roundingTip(chips, 100|500)`; при 0 чип скрыт. «своя сумма» → поле суммы переключается на чаевые (та же клавиатура, подпись «Чаевые»).
- `Row(gap 8, centerV)`: «Кому:» `t.caption c.muted` + `SmallToggle` «Банкиру» | «Игроку…» (выбран игрок → «Игроку: Борис»). «Игроку…» → шторка выбора **[без макета]**: список игроков этой игры кроме вышедшего (за столом — сначала; `AmountRowLayout` с аватаром M), выбор → `TipTarget.ToPlayer`. Ряд «Кому» `alpha 0.5` и недоступен при «Без чаевых».
- Свёрнутый вид (balance < 0, макет DebtCashOut без блока) **[решение]**: одна строка `Row(bg c.card, r16, h48, p(0 14), SpaceBetween)` «Чаевые» | «без чаевых ›» `c.muted`; нажатие разворачивает.

**BreakdownList**

| Вид | Строки (подпись `c.muted` / `c.ledger` — значение) |
| --- | --- |
| pay (CashOut) | «Фишки на руках» `3 753` · «Чаевые» `—` или `−53` · «К выводу 1:1» **Bold** `3 753 ₽` (`withdrawal`) · «Долг игрока» `c.owe` `−3 400 ₽` (`outstandingBefore < 0`) / «Банкир должен» `c.pay` `+N ₽` (> 0) / нет строки (0) |
| owe (DebtCashOut, подписи `c.ledger`) | «К выводу 1:1» `1 247 ₽` · «Начислено (фишки, рейк, сбор)» `2 300 ₽` (`sessionCharged`) · «Оплачено» `0 ₽` (`sessionPaid`) |
| оба | при `carriedOver ≠ 0` — строка «Долг с прошлого выхода» `−N ₽` `c.owe` / «Переплата с прошлого выхода» `+N ₽` (QUESTIONS №3) |

**ResultPanel**: `balance > 0` → pay, подпись «{Имя} получает от банкира» **(макет: «Банкир выплачивает Диме» — склонять имена мы не умеем, §12)**, сумма «+353 ₽»; `balance < 0` → owe, «{Имя} доплачивает банкиру», «1 053 ₽»; `0` → even, «Квиты — никто никому не должен», «0 ₽».
Строка результата: `resultThisGame` со знаком, plural `chips_count` («+753 фишки»); показывается во всех видах.

**Actions**

| Вид | Главная (hold, h68 r20) | Второстепенные |
| --- | --- | --- |
| pay | «Расчёт произведён» / hint «удерживайте · выплатить 353 ₽» (`SettlementChoice.Full` → PAYOUT) | `Grid(2, gap 8)`: `OutlineButton(52)` «Выплатил часть…» · `OutlineButton(52, Close 18)` «Отмена» **(макет: только «Отмена» во всю ширину — добавлена частичная выплата, ТЗ §5.2)** |
| even | «Расчёт произведён» / «удерживайте, чтобы подтвердить» | `OutlineButton(52, Close)` «Отмена» во всю ширину |
| owe | «Получил 1 053 ₽» / «удерживайте, чтобы подтвердить» (Full → PAYMENT) | `Grid(2, gap 8)`: `OutlineButton(52)` «Отдал часть…» (`t.title c.ink`) · `DangerOutlineButton` «Не заплатил»; под ними `Text(t.small, c.ledger, center)` «„Не заплатил“ запишет долг 1 053 ₽ — он перейдёт на следующие игры» |

- «Отдал часть…» / «Выплатил часть…» → шторка суммы (`AmountKeypad`, сумма `0 < x < |balance|`, блокер `PartialOutOfRange`) с hold-кнопкой «Получил 500 ₽» / «Выплатил 500 ₽», hint «остаток 553 ₽ — в долг» / «остаток — банкир должен»; внизу шторки текстовая кнопка «Пока ничего не выплатил» (только pay → `SettlementChoice.None`) **[без макета]**.
- «Не заплатил» — `SettlementChoice.None` без удержания (деньги не двигаются; обратимо через snackbar) → snackbar «Влад ушёл с долгом 1 053 ₽» / «Отменить».
- Ранний выход: при завершении hold, если `quote.isEarlyExit` — `FeltbookDialog` «Исключительный случай» / «Егор играет 47 мин из 60. Выход будет отмечен в истории как ранний.» / «Не выходить» | «Выйти раньше» (опасная) → `Commands.cashOut(earlyExitConfirmed = true)`.
- После выхода — snackbar «Дима вышел · выплачено 353 ₽» / «Отменить» (отменяет обе операции выхода — две `reverse` подряд, расчёт первым).
- Поле фишек: шторка открывается с фокусом на поле и `AmountKeypad` под ним **[решение без макета: в макете клавиатуры нет]**; пока клавиатура видна, `TipsBlock` и `BreakdownList` скрыты, `ResultPanel` виден (живой итог); кнопка «Готово» справа над клавиатурой (`TextButton t.labelBold c.brassLight`) или нажатие вне поля — клавиатура прячется, раскладка — как в макете.
- Блокеры котировки (`TipExceedsChips`, `TipToSelf`, …) — текст под чаевыми `t.small c.owe`, hold недоступен.

### 5.7. Шторки «Оплата долга» и «Выплатить» [без макета]

Скелет шторки закупа: заголовок «Оплата · Борис» / «Выплатить · Саша», подзаголовок «долг 2 300 ₽» / «банкир должен 2 700 ₽»; сумма `amountHero`, по умолчанию — весь долг; вместо фишек — два `FilterChip` «весь долг» / «половина» (округление вверх); `AmountKeypad`; итог `BreakdownList`: «Долг сейчас» → «Останется» (`c.owe`/`c.ink`).
- Оплата (обратимое): `BrassButton.hero64` «Принять 2 300 ₽» → `Commands.pay` → snackbar «Борис оплатил 2 300 ₽» / «Отменить». Блокер `PaymentExceedsDebt`.
- Выплатить (деньги на руки): `HoldToConfirmButton(68)` «Выплатил 2 700 ₽» / «удерживайте, чтобы подтвердить» → `Commands.payOut`. Блокер `PayoutExceedsOwed`.
- Погашение из «Открытых долгов» / предупреждения: та же шторка, заголовок «Погасить · Влад», подзаголовок «Пятничный стол · 18 окт · долг 1 053 ₽»; запись — в старую игру (`bankerName` = банкир активной игры или `lastBankerName`).

### 5.8. Итог игры (`Settlement`, `DebtSettlement`)

Фон `feltScreen`, `Column(fillMaxSize)`, нижняя панель **скрыта**; середина прокручивается, кнопки закреплены внизу.

```
Header: Row(p(16 16 8 8), gap 8, centerV)
  IconButton44(Back, без фона, a11y "Назад к столу")
  Column(gap 2)
    Text("Итог игры", t.display)
    Text("Пятничный стол · 3 ч 40 мин · 3 игрока", t.caption, c.muted)     // DebtSettlement: t.ledger c.ledger
[есть долги] UnpaidBlock (m(4 16 0))
[нет долгов] InfoBanner.ok(m(4 16 0), "Фишки сошлись: выдано 5 000 = сдано 5 000")
Grid(2, gap 8, m(12|10 16 0))
  MetricTile(bg c.card, border 1 c.brassHairline, r18, p14 | p(12 14), gap 4 | 2)
    Text("♣ Заведению · рейк", t.ledgerTight | t.small, c.ledger)
    AmountText("500 ₽", t.amountCard | t.amountStat)
    Text("одной суммой" | "полностью, включая долг", t.ledgerTight | t.tiny, c.ledger)
  MetricTile.brass(r18, border c.brassBorderStrong)
    Text("♥ Банкиру", t.small, c.brassLight)
    AmountText("353 ₽", t.amountCard | t.amountStat, c.brassBright)
    Text("сборы 300 · чаевые 53" | "после погашения долга", t.small | t.tiny, c.brassMuted)
CashBox(m(8 16 0), bg c.feltDeep, r14, p(12 14))
  нет долгов: Row(SpaceBetween): Text("♦ Должно быть в кассе", t.body, c.inkSoft) | AmountText("853 ₽", t.amountMetric)
  есть долги: Column(gap 6):
    "♦ Должно быть в кассе"        | "853 ₽"     (14 Bold)
    "Не получено: Влад"            | "−1 053 ₽"  (14 Bold, c.owe)        // строка на каждого должника
    ── divider, p(6 0 0) ──
    "На руках сейчас"              | "−200 ₽"    (14 Bold)
[cashOnHand < 0] Text("−200 ₽ — банкир доплатил из своих, вернётся, когда погасят долг", t.ledger, c.ledger, m(6 20 0))
[unpaidPayouts] [без макета] блок «Ещё не выплачено»: строки «Саша» | «+300 ₽» c.pay, подпись «банкир должен вышедшим»
нет долгов: Text("Расчёт с игроками", t.sectionTitle, m(18 20 8)); List(p(0 16), gap 8): SettlementRowDetailed
есть долги: List(m(14 0 0), p(0 16), gap 6): SettlementRowCompact
Spacer(weight 1)
Bottom: p(12 16 24) + navigationBars
  нет долгов: Grid([56, 1fr], gap 8): ShareButton | Hold(h56, r16, "Рейк отдан · закрыть игру", hint "удерживайте")
  есть долги: Grid([56, 1fr], gap 8): ShareButton | Hold(h64, r20, "Закрыть игру с долгом", "удерживайте · рейк 500 ₽ отдан заведению")
```
(В макете DebtSettlement кнопки «Поделиться» нет, а в Settlement главная кнопка без удержания — обе отличия сделаны по ТЗ §4 п. 4 и §5.1, §12.)

**UnpaidBlock** (`aria "Не оплачено"`): `Column(bg c.oweBackground, border 1.5 c.oweBorderStrong, r18, p14, gap 8)`: `Row(gap 8)`: `Alert` 20 `c.owe` + «Не оплачено» `t.sectionTitle` ★`c.oweTint`; на каждого должника `Row(SpaceBetween, gap 12)`: «Влад ушёл с долгом» `t.title` | `AmountText` «1 053 ₽» `t.amountTile` `c.owe`; подпись `t.ledger` `c.oweTint` «долг перейдёт в «Открытые долги» и всплывёт, когда Влад сядет за стол». При долгах фишки всё равно сверяются — баннер сверки идёт **под** блоком (`m(8 16 0)`) **[без макета]**, только если фишки не сошлись.

**SettlementRowDetailed**: `Column(bg c.card, r16, p12, gap 8)`: `AmountRowLayout(gap 12)`: `PlayerAvatar.S` | имя `t.title` | `AmountText` `t.amountRow`: «платит 2 300 ₽» `c.owe` (`balanceAtExit < 0`), «получает 3 700 ₽» `c.pay` (> 0), «квиты» `c.muted` (0 / null); под ним `t.ledgerTight c.ledger` «начислено 2 300 · оплачено 0 · вывод 0» + « (53 чаевых)» при `tipsGiven > 0` + « · ранний выход» при `earlyExit`.
**SettlementRowCompact**: `AmountRowLayout(bg c.card, r14, p(10 12), gap 10)`: имя `t.title` | `t.labelBold`: «заплатил 2 300 ✓» `c.inkSoft` (был `balanceAtExit < 0`, `outstanding == 0`), «получил 3 700 ✓» `c.inkSoft`, должник — «долг 1 053» `c.owe` и рамка 1 пунктир `c.oweBorderStrong`; «банкир должен 300» `c.pay`.

**Данные** (`Settlements.compute`):
| Элемент | Поле |
| --- | --- |
| подзаголовок | `game.title` · `now|closedAt − createdAt` · `playerCount` (+ « · {rebuyCount} докупа», ТЗ §4 «докупы в итоговом расчёте» **[без макета]**) |
| сверка | `chipsReconciled`: «Фишки сошлись: выдано {chipsIssued} = сдано {chipsReturned}»; иначе `InfoBanner.danger` «Фишки не сошлись: выдано 5 000, сдано 4 900 (−100)» (`chipDiff`) |
| рейк | `rakeDue` (+ `rakePaid` > 0 → третья строка «отдано {rakePaid} ₽»); при `shortage > 0` — «полностью, включая долг» |
| банкиру | `bankerIncome`; строка «сборы {bankerFees} · чаевые {tipsToBanker}» (+ « · прощено {forgiven}»); при долгах — «после погашения долга»; несколько банкиров — под плиткой строки `t.ledger` «Андрей 200 ₽ · Влад 153 ₽» (`incomeByBanker`) |
| касса | `expectedCash`; `unpaidDebts` → строки «Не получено: {name}» `−amount`; «На руках сейчас» `cashOnHand` |
| строки | `rows` |
| игроки за столом | `blockers` содержит `PlayersStillPlaying` → вместо кнопок `InfoBanner.warn` «За столом ещё 2 игрока — выведите их, чтобы закрыть игру», под ним строки этих игроков с кнопкой «Выход» (`OutlineButton(48)`) → шторка §5.6 **[без макета]** |

**Закрытие**: hold → `Commands.close(payRake = rakeDue > 0, acknowledged)`. Подписи: `rakeDue == 0` → «Закрыть игру»; есть `OPEN_DEBTS` → «Закрыть игру с долгом» (подтверждение — сама кнопка); `CHIPS_NOT_RECONCILED` → после удержания `FeltbookDialog` «Фишки не сошлись» / «Сдано на 100 фишек меньше, чем выдано. Закрыть игру с расхождением?» / «Проверить ещё» | «Закрыть игру» (опасная); `UNPAID_PAYOUTS` → hint «удерживайте · банкир ещё должен 300 ₽». Успех → `Home` (стек очищен), snackbar нет (переоткрыть — из истории).
«Назад к столу» в SETTLING — к `Table` (игра остаётся SETTLING; «Вернуться к игре» — меню стола).

**Поделиться**: нажатие → `DropdownMenu` «Текстом» / «Картинкой» (M4.6). Текст (`settlement_share_*`):
```
Feltbook · Пятничный стол · 18 окт
Андрей платит 2 300 ₽
Борис получает 3 700 ₽
Влад платит 1 053 ₽
Рейк заведению 500 ₽ · Банкиру 353 ₽
Фишки сошлись: 5 000
```

### 5.9. Открытые долги (`Debts`, вкладка «Игра»)

Фон `feltScreen`; нижняя панель видна, «Игра» выбрана. `LazyColumn`.

```
Header: Row(p(16 16 8 8), gap 8): IconButton44(Back, без фона, "Назад") + Text("Открытые долги", t.display)
Summary: Row(m(4 16 0), bg c.oweBackground, border 1 c.oweBorder, r18, p(14 16), SpaceBetween, centerV, gap 12)
  Column(gap 2)
    Text("Вам должны", t.caption, c.oweTint)
    Text("2 игрока · 2 игры", t.ledger, c.oweTint)
  AmountText("1 653 ₽", t.amountTotal, c.owe)
List(m(16 0 0), p(0 16), gap 10): DebtCard × N
Text("Погашение записывается в ту игру, где возник долг. «Простить» спросит подтверждение и уменьшит доход банкира той игры.",
     t.caption lh 1.5, c.ledger, m(16 20 0))
[пусто] Text("Долгов нет — все рассчитались", t.bodyL, c.muted, center, p(32 20))
```
**DebtCard**: `Column(bg c.card, r18, p14, gap 12)`:
- `AmountRowLayout(gap 12, centerV)`: `PlayerAvatar.M` | `Column(gap 2)`: имя `t.titleM`; мета `t.ledger c.ledger` ellipsis | `AmountText` `t.amountMetric` `c.owe`.
- `Grid([2fr, 1fr], gap 8)`: `BrassButton.flat48` «Погасить» (15 Bold) | `OutlineButton(48, r14)` «Простить…» (`t.labelSemi c.inkSoft`).

| Элемент | `OpenDebt` |
| --- | --- |
| сумма сверху | Σ `amount`; «2 игрока» — различные `canonicalName`; «2 игры» — различные `gameId` |
| имя | `playerName` (как в той игре) |
| мета | `gameTitle` · `gameCreatedAt` (короткая дата) · `now − since` в днях («15 дней»; 0 → «сегодня»); при `owedAtExit > amount` последний фрагмент — «отдал {owedAtExit − amount} из {owedAtExit}» |
| сумма | `amount` |

- «Погасить» → шторка §5.7 (обратимое, snackbar).
- «Простить…» → `FeltbookDialog` «Простить долг 1 053 ₽?» / «Долг закроется без денег, доход банкира игры «Пятничный стол» уменьшится на 1 053 ₽. В журнале останется запись.» / «Оставить долг» | «Простить 1 053 ₽» (опасная) → `Commands.forgiveDebt(amount = null)` → snackbar «Долг прощён» / «Отменить» (`reverse` разрешён в CLOSED для DEBT_FORGIVEN).
- Нажатие на карточку → `GameDetail(gameId)`.

### 5.10. Передача комнаты (`Handover`)

Фон `feltScreen`, `Column(fillMaxSize)`, нижняя панель скрыта.

```
Header: Row(p(16 16 8 8), gap 8): IconButton44(Back, "Назад к столу") + Text("Передача комнаты", t.display)
SegmentedToggle.regular(m(4 16 0), ["Отдать игру", "Принять игру"])
DeviceCard: Column(m(16 16 0), bg c.card, r20, p16, gap 12, CenterHorizontally)
  Row(fillMaxWidth, gap 10, centerV)
    Box(40, Circle, bg c.payIconContainer) { Phone 20/1.9, c.pay }
    Column(weight 1, gap 2)
      Text("Телефон: Влад", t.title)                         // макет «Телефон Влада», §12
      Text("найден рядом · Wi-Fi Direct", t.ledger, c.ledger)
  Text("Сверьте код на обоих телефонах", t.caption, c.inkSoft)
  Row(gap 10, a11y "Код 4 7 1 9"): ×4 Box(52×64, r12, bg c.feltDeep, border 1 c.brassBorder, center)
    Text(digit, t.codeDigit, c.brassBright)
ActSection: Column(m(12 16 0), bg c.sheet, border 1 c.brassBorderMedium, r20, p(14 16), gap 10)
  Text("♦ Акт передачи кассы", t.sectionTitle)
  BreakdownList(row gap 7, подписи c.ledger)
    "Наличные в кассе"            | "950 ₽"
    "Рейк заведению (переходит)"  | "1 250 ₽"
    "Долги игроков (переходят)"   | "9 200 ₽"
    [bankerOwes > 0] "Долг вышедшим (переходит)" | "300 ₽"         // [без макета]
    "Мой доход банкира"           | "600 ₽" (c.brassLight)
  Row(gap 10, centerV, toggleable)
    Text("Оставить доход себе — после расчёта долгов", t.caption, weight 1)
    FeltbookSwitch
  Row(SpaceBetween, centerV, p(10 0 0), верхняя черта 1 c.divider)
    Text("Отдать наличными", 14 Bold)                                 // макет «Отдать Владу наличными», §12
    AmountText("950 ₽", t.amountAct, c.brassBright)
Text("Передаётся: 6 игроков · 8 300 фишек · 23 записи журнала", t.ledger, c.ledger, m(10 20 0))
Spacer(weight 1)
Bottom: Column(p(12 16 24) + navigationBars, gap 8)
  HoldToConfirmButton(h64, r20, "Код совпадает — удерживайте", hint "чтобы передать игру")
  OutlineButton(h48, r16, "Код другой — отмена", t.title c.inkSoft)
```

| Элемент | Данные (`Handovers.act`, `:core:transfer`) |
| --- | --- |
| устройство | имя конечной точки Nearby (реклама получателя = его `lastBankerName`); среда — от транспорта («Bluetooth», «Wi-Fi Direct», «Wi-Fi») |
| код | `authenticationDigits` (4 цифры) |
| касса / рейк / долги / доход | `cashOnHand`, `rakeDue`, `playersOwe`, `fromBankerIncome`; `bankerOwes` |
| переключатель | `incomeTakeable > 0` → подпись «Оставить доход себе · 600 ₽», доступен; `== 0` → «Оставить доход себе — после расчёта долгов», недоступен (`alpha 0.5`) — так в макете |
| «Отдать наличными» | `cashToHandOver(keepIncome)` |
| «Передаётся» | `playersAtTable` (plural), `chipsInPlay` (plural `chips_count`), `entryCount` (plural `entries_count`) |

Подсказка hold — в макете `c.onBrass` (у второй строки цвет не задан) — оставить `c.onBrass`. Hold → `Handovers.prepare` → отправка → коммит `senderChange` после ACK → экран «Игра передана» и `Home`.

**Состояния без макета** (каркас тот же, DeviceCard меняет содержимое): «Нужен доступ к Bluetooth и Wi-Fi рядом» + кнопка «Разрешить» (rationale перед запросом, ТЗ §6); «Ищем телефон рядом…» (`CircularProgressIndicator` `c.brass` 20) + «Попросите нового банкира открыть «Принять игру»»; «Соединяемся…»; «Отправляем…» (прогресс); ошибка `InfoBanner.danger` «Не получилось передать. Попробуйте ещё раз или отправьте файлом.» + `OutlineButton` «Отправить файлом» (фолбэк `.feltbook`, диалог «Файл отправлен?» → «Ещё нет» | «Да, отправлен»). Код, акт и hold видны только после соединения.
**«Принять игру»**: DeviceCard «Ждём телефон банкира…» → код → после получения карточка сводки `bg c.sheet r20 p(14 16)`: «Игра «Пятничный стол» от Андрея» → строки `BreakdownList` «Игроков за столом 6 · Фишек в игре 12 000 · Касса 4 300 ₽»; «Я играю как…» — `FlowRow` `FilterChip` по игрокам + «Не играю» (по умолчанию); `HoldToConfirmButton(64)` «Принять игру» / «удерживайте» → `Handovers.accept`. Ошибки `AnotherGameActive` / `ImportConflict` — `InfoBanner.danger` с текстом (§11.13).

### 5.11. История (`History`, вкладка «История»)

Фон `feltScreen`; нижняя панель видна, «История» выбрана (в т. ч. в мультивыборе). `LazyColumn` с `stickyHeader`-ами месяцев не нужен — заголовки обычные.

**Обычный режим** [без макета — по образцу Настроек]:
```
Header: Row(p(24 20 8), SpaceBetween, centerV)
  Text("История", t.displayL)
  DropdownChip-стиль кнопка "Статистика" (h36 p(0 12) r18, рамка c.brassBorderStrong, t.chip c.brassBright) → Stats
Filters | MonthHeader | HistoryRow (без чекбокса: leading = нет, title "Субботний турнир · 25 окт")
[пусто] "Сыгранные игры появятся здесь"
```
**Режим выбора** (макет):
```
CAB: Row(p(14 12 10 8), bg c.card, нижняя черта 1 c.brassBorderSoft, gap 8, centerV)
  IconButton44(Close 22/2, без фона, a11y "Снять выбор")
  Text("Выбрано 2", t.headlineSans, weight 1)
  DeleteActionChip("Удалить")
Filters: Row(p(14 16 4), gap 8): FilterChip(34) "Все" (выбран) · "Месяц" · "Год"
MonthHeader: Text("Октябрь 2026", t.ledgerCaps, c.ledger, m(14 20 8) первый / m(18 20 8) далее)
List(p(0 16), gap 8): HistoryRow
```
**HistoryRow**: `AmountRowLayout(bg c.card, border 1.5 Transparent, r16, p(12 14), gap 12, centerV)`; выбранная — `bg c.brassSelectedRow`, рамка 1.5 `c.selectedBorder`.
- leading (только в режиме выбора): чекбокс 24 × 24 `r7`: выбран — `bg c.brass` + `Check` 16/3 `c.onBrass`; нет — рамка 2 `c.checkboxOffBorder`.
- content: «{title} · {d мес}» `t.title` ellipsis; мета `t.ledger c.ledger` «4 ч 05 мин · 8 игроков · 11 докупов» (+ « · долг» `c.owe`, « · изменено после закрытия», « · передана» — **[без макета]**).
- trailing: «оборот 29 000» `t.labelBold c.ink`; «рейк 2 900» `t.tiny c.ledger`.
- Данные — `GameHistoryItem` (CLOSED и HANDED_OVER), группировка по месяцу `startedAt`, новые сверху. Фильтр: «Все» — без границ; «Месяц» — текущий календарный месяц; «Год» — текущий год (`Stats.period(from, to)` для тех же границ).

**Взаимодействия**: долгое нажатие на строку → режим выбора с этой строкой; в режиме выбора нажатие переключает; «назад»/✕ — выход из режима. Обычное нажатие → `GameDetail(gameId)`.
«Удалить» → `FeltbookDialog` (прижат к низу, `bottom 96` = 16 над панелью): «Удалить 2 игры?» / «Журналы, расчёты и статистика этих игр исчезнут с телефона. Отменить можно 5 секунд.» (для одной — «этой игры») / «Оставить» | «Удалить 2 игры» → строки скрываются сразу, snackbar «Удалено 2 игры» / «Отменить» 5 с; физическое удаление — по истечении snackbar (до этого игры только скрыты в UI-состоянии; смерть процесса до 5 с ничего не удаляет — roadmap M4.3).

**Карточка игры `GameDetail`** [без макета]: шапка как у Итога («Назад», `title`, подзаголовок «18 окт · 3 ч 40 мин · 5 игроков» + пометки); содержимое — блоки Итога только для чтения (сверка, плитки, касса, строки игроков) и ниже `SectionHeader` «Журнал» + полный журнал (`Journal.items`, строка как в §5.5, но средняя колонка начинается с имени игрока: «Борис · Докуп 1 000 · в долг»; акт передачи — «Игра принята от Андрея» `c.brassLight`). Меню «⋮»: «Поделиться итогом», «Переоткрыть для исправления» (`FeltbookDialog` «Переоткрыть игру?» / «Игра снова станет активной и получит пометку «изменено после закрытия».» / «Оставить» | «Переоткрыть» → `Commands.reopen`, блокер `AnotherGameActive`), «Удалить игру…». Отмена погашений/прощений в CLOSED — удержанием строки, как в §5.5.

### 5.12. Статистика (`Stats`, из Истории)

Фон `feltScreen`, нижняя панель **скрыта** (в макете её нет). `LazyColumn`.

```
Header: Row(p(16 16 8 8), gap 8, centerV)
  IconButton44(Back, без фона, "Назад к истории")
  Text("Статистика", t.display, weight 1)
  DropdownChip("За 3 месяца ▾")          → меню: "За месяц" · "За 3 месяца" · "За год" · "За всё время"
Grid(2, gap 8, m(4 16 0)): MetricTile(r16, p(12 14), gap 2, подпись t.small c.ledger, значение t.amountStat)
  "Игр" | "12"
  "Оборот фишек" | "214 500"
  "♣ Рейк заведению" | "21 450 ₽"
  MetricTile.brass(border c.brassBorder) "♥ Доход банкира" | "7 100 ₽"
ChartSection: Column(m(16 16 0), bg c.card, r18, p(14 16))
  Text("Доход банкира, последние 6 игр", t.labelBold, m(0 0 12))
  Row(h120, gap 10, Alignment.Bottom): ×N Column(weight 1, CenterHorizontally, gap 4)
    Text(value, t.tiny, c.ledgerLight)            // последний: t.tinyBold c.brassBright
    Box(fillMaxWidth, h = value / max × 100 dp, shape r(6 6 2 2), bg c.focusBorder)   // последний: c.brass
  Row(gap 10, m(6 0 0)): ×N Text("13.09", t.ledgerS, c.ledger, center, weight 1)
SectionHeader(p(18 20 8)): "Игроки" | Text("результат в фишках", t.small, c.ledger)
List(p(0 16), gap 6): StatsPlayerRow
```
**StatsPlayerRow**: `AmountRowLayout(bg c.card, r14, p(9 12), gap 12, centerV)`: `PlayerAvatar.XS` | `Column(gap 1)`: имя `t.title`; мета `t.ledger c.ledger` ellipsis «11 игр · 1,4 докупа за игру» | `AmountText` `t.amountRow`: «+9 450» `c.pay` / «−6 750» `c.owe` / «0» `c.muted` (без ₽).

| Элемент | `PeriodStats` / `PlayerStats` |
| --- | --- |
| Игр / Оборот / Рейк / Доход | `gameCount` / `turnover` / `rake` / `bankerIncome` |
| график | последние `min(6, size)` из `incomeByGame` (по возрастанию времени); подпись — `bankerIncome` (без ₽), дата — `startedAt` `dd.MM`; высота столбца — доля от максимума × 100 dp (макет: 800 → 100, 500 → 62); максимум ≤ 0 → столбцы высотой 2 dp; заголовок «последние N игр» с plural |
| игроки | `players` (по убыванию `totalResult`); «{gamesPlayed} игр» (plural `games_count`); «{rebuyCount/gamesPlayed с 1 знаком} докупа за игру» |

График — простой `Canvas`/`Row` по геометрии выше (Vico допустим, но точное совпадение с макетом проще своим компонентом; решает реализующий). a11y графика — `contentDescription` «Доход банкира по играм: 13.09 — 500 рублей, …».
Нажатие на игрока → шторка «Игрок» **[без макета]**: `PlayerAvatar.L` + имя `t.display`; плитки 2 × 2 «Игр», «Результат» (pay/owe), «Средний закуп» (`averageBuyInPerGame`), «Докупов за игру»; при `openDebt > 0` — плитка owe «Долг» и кнопка «Открытые долги»; «Последняя игра {lastPlayedAt}»; `OutlineButton` «Объединить с…» → список других имён (`PlayerNames.canMerge`), подтверждение `FeltbookDialog` «Объединить «Боря» с «Борис»?» / «Статистика и долги будут считаться вместе. Разъединить можно позже.» / «Отмена» | «Объединить» (брасс-текст); у объединённого — «Разъединить».

### 5.13. Настройки (`Settings`, вкладка «Настройки»)

Фон `feltScreen`; нижняя панель видна, «Настройки» выбрана. `Column(verticalScroll)`, низ — отступ под панель.

```
Header: Box(p(24 20 8)): Text("Настройки", t.displayL)
LedgerHeader("Правила по умолчанию", m(12 20 8))
Group: Column(m(0 16), p(0 14), bg c.card, r18)              // разделитель между строками — верхняя черта 1 c.hairline
  ValueRow(p(13 0)): Text("♥ Сбор банкира", t.bodyL) | Text("разово 100 ₽ ›", t.labelSemi, c.brassBright)
  ValueRow: "♣ Рейк" | "10 % с покупки ›"
  ValueRow: "Минимальная покупка" | "500 ›"
  ValueRow: "Минимальное время игры" | "60 мин ›"
  ValueRow: "Оплата покупки" | "одним статусом ›"
LedgerHeader("Оформление и стол", m(18 20 8))
Group
  ValueRow(p(13 0)): "Тема" | "сукно, тёмная ›"
  SwitchRow(p(10 0)): Column(gap 2){ Text("Цвета обоев", t.bodyL); Text("Material You вместо сукна", t.small, c.ledger) } | Switch(off)
  SwitchRow: "Не гасить экран" / "пока идёт игра" | Switch(on)
  SwitchRow: "Вибрация при подтверждении" | Switch(on)
LedgerHeader("Данные", m(18 20 8))
Group
  ValueRow(p(13 0)): "Резервная копия в файл" | Text("›", t.labelSemi Regular, c.ledger)
  ValueRow: "Восстановить из файла" | "›"
  Row(p(13 0)): Text("Удалить все игры…", t.bodyL, c.owe)
[oddsCalculatorVisible] [без макета] LedgerHeader("♠ Секретный стол", m(18 20 8)) +
  Group: ValueRow "Калькулятор шансов" | "›" → Odds;  Row "Скрыть пункт" t.bodyL c.muted → oddsCalculatorVisible = false (snackbar «Пункт скрыт» / «Отменить»)
Text("Feltbook 1.0 · open source · данные только на телефоне", t.ledgerS, c.ledger, m(14 20 0))
```

| Строка | Значение | Действие |
| --- | --- | --- |
| Сбор банкира | «разово 100 ₽» / «разово 10 %» / «с каждого 100 ₽» / «с каждого 10 %» (`rulesForNewGame.bankerFeeMode/Value`) | шторка: 4 варианта (`RadioButton`-строки) + `ValueBox` с клавиатурой |
| Рейк | «10 % с покупки» / «100 ₽ с покупки» | шторка: «Процент / Фикс» + значение |
| Минимальная покупка | `minBuyIn` | шторка-клавиатура |
| Минимальное время | «{minPlayMinutes} мин» | шторка-клавиатура |
| Оплата покупки | «одним статусом» / «раздельно» | шторка с 2 вариантами и пояснением «Раздельно — два переключателя: «Фишки» и «Рейк и сбор»» |
| Тема | «сукно, тёмная» (DARK) / «бумага, светлая» (LIGHT) / «как в системе» (SYSTEM) | `FeltbookDialog` с 3 `RadioButton`-строками |
| Цвета обоев | `dynamicColor`; ниже Android 12 — недоступен, подпись «доступно с Android 12» | |
| Не гасить экран | `keepScreenOn` | |
| Вибрация | `haptics` | |
| Резервная копия | — | SAF `CreateDocument("application/octet-stream")`, имя `feltbook-2026-11-02.feltbook` → snackbar «Копия сохранена: 12 игр» |
| Восстановить | — | SAF `OpenDocument` → отчёт `FeltbookDialog` «Восстановлено» / «Добавлено 3 игры · без изменений 5 · не тронуто из-за расхождений 1» / «Понятно» |
| Удалить все игры… | — | `FeltbookDialog` «Удалить все игры?» / «Все игры, журналы и статистика исчезнут с телефона. Чтобы подтвердить, введите УДАЛИТЬ.» / поле / «Оставить» | «Удалить все игры» (опасная, доступна при вводе ровно «УДАЛИТЬ») |
| версия | `BuildConfig.VERSION_NAME` | |

Проблемы правил (`problems()`) в шторках — текст `t.small c.owe`, «Сохранить» недоступна. Сохранение правил по умолчанию — обратимое (snackbar «Правила сохранены» / «Отменить»).

### 5.14. Калькулятор шансов (`Odds`, из Настроек) — M6

Фон `feltScreen`, нижняя панель скрыта, `Column(verticalScroll)`. Расчёт — `:core:odds`; UI ничего не сохраняет.

```
Header: Row(p(14 16 6 8), gap 8, centerV)
  IconButton44(Back, без фона, "Назад к настройкам")
  Column(weight 1, gap 1)
    Text("Калькулятор шансов", t.displayS)
    Text("♠ СЕКРЕТНЫЙ СТОЛ", t.ledgerSCaps, c.brass)
  IconButton44(Reset 20/1.9, bg c.quietFill, "Сбросить")
CardsSection: Column(m(6 16 0), p14, r22, bg feltOdds, border 1 c.brassBorderOdds, gap 12)
  Row(Alignment.Bottom, gap 16)
    Column(gap 6)
      Text("Мои карты", t.tinyCaps, c.inkSoft)
      Row(gap 6): PlayingCard.L(52×72, r8, shadow(4, 10, 0.35)) ×2          // вторая повёрнута на 4°
    Column(weight 1, minWidth 0, gap 6)
      Text("Стол · флоп", t.tinyCaps, c.inkSoft)
      Row(gap 4): PlayingCard.S(36×50, r6) ×3 + SlotNext + SlotLater
Row(m(10 16 0), gap 8)
  Stepper: Row(bg c.card, r14, p4, gap 4, centerV)
    Box(40×40, r10, bg c.quietFill) { "−" t.glyphButton }  (a11y "Меньше игроков")
    Column(minWidth 64, center, lh 1.15): Text("6", t.amountMetric) + Text("игроков", t.caption)
    Box(40×40 …) { "+" }  (a11y "Больше игроков")
  Grid(2, gap 6, weight 1): ×2 Column(bg c.card, r12, p(5 10))
    Text("Банк" | "Ставка", t.tiny, c.ledger)
    AmountText("1 200" | "300", t.emphasisM)
SegmentedToggle.position(m(10 16 0), ["UTG","MP","CO","BTN","SB","BB"])
EquitySection: Column(m(14 16 0), bg c.card, r20, p(14 16), gap 10)
  Row(baseline, SpaceBetween)
    Text("Шанс выиграть против 5 игроков", t.caption, c.inkSoft)
    Text("±0,3 %", t.ledgerS, c.ledger)
  Text("38,4 %", t.amountEquity, c.brassBright)
  Row(h10, r5, clip): Box(weight 38.4, c.pay) Box(weight 1.2, c.brassLight) Box(weight 60.4, c.oweBar)
  Row(gap 14): Text("● победа 38,4", t.small, c.inkSoft) Text("● ничья 1,2", c.brassLight) Text("● проигрыш 60,4", c.oweSoft)
Grid(2, gap 8, m(8 16 0))
  PotOddsTile: Column(bg c.payContainer, border 1 c.payBorderMedium, r16, p(10 12), gap 3)
    Text("Шансы банка", t.small, c.inkSoft)
    Text("нужно 20,0 %", 15 Bold)
    Text("колл окупается", t.smallSemi, c.pay)
  OutsTile: Column(bg c.card, r16, p(10 12), gap 3)
    Text("Флеш-дро · 9 аутов", t.small, c.inkSoft)
    Text("19,6 % / 35,0 %", 15 Bold)
    Text("тёрн / к риверу · ≈36 % по 2 и 4", t.ledgerS, c.ledger)
Text("Сейчас: старшая карта, туз · 150 000 раздач · офлайн", t.ledgerS, c.ledger, m(10 20 0))
MemoSection: Column(m(12 16 0) + низ 24 + navigationBars, bg c.sheet, border 1 c.brassBorderMedium, r18, p(12 14), gap 8)
  Row(SpaceBetween, centerV): Text("Памятка: кто есть кто", t.labelBold) | Text("по часовой от дилера", t.small, c.ledger)
  Grid([40, 1fr], rowGap 5, colGap 8, 12 sp lh 1.35): dt t.positionLabel c.brassBright | dd t.small c.inkSoft
  Text("Чем позже позиция, тем больше видно чужих решений — и тем шире обычно разыгрывают руки.", t.tiny, c.ledger)
```
- **PlayingCard**: фон `c.ivory`, ранг Playfair (`cardRankL` 24 / `cardRankBoard` 16) + масть (20 / 13), цвет `c.suitBlack` / `c.suitRed`, `lh 1`. Пустая своя карта [без макета] — как `SlotNext` размера L.
- **SlotNext**: 36 × 50 `r6`, пунктир 1.5 `c.placeholderBorder`, «+» 18 `c.inkSoft`. **SlotLater**: пунктир `c.placeholderBorderDisabled`, «+» `c.inkSoftDisabled`, недоступен. Видимые слоты: 0 / 3 / 4 / 5 карт стола (ТЗ §4): пока флопа нет — один слот «+ флоп» шириной 3 карт.
- Подпись стола: «Стол · префлоп» / «Стол · флоп» / «Стол · тёрн» / «Стол · ривер».
- Выбор карты **[без макета]**: шторка «Выберите карту», сетка 13 рангов × 4 масти: 4 строки (♠ ♥ ♦ ♣) × 13 колонок не влезают в 48 dp → сетка 4 колонки (масти) × 13 строк, ячейка `weight 1 × 44`, карта `PlayingCard`-стиль; занятые — `alpha 0.3`, недоступны; там же вкладка «Мёртвые карты» (необязательные, ТЗ §4).
- Позиции по числу игроков: 2 — SB/BTN, BB; 3 — BTN, SB, BB; 4 — CO, BTN, SB, BB; 5 — UTG, CO, BTN, SB, BB; 6 — UTG, MP, CO, BTN, SB, BB; 7–10 — UTG, UTG+1…, MP, …, CO, BTN, SB, BB (сегменты — горизонтальная прокрутка, ширина 56). Список памятки — только позиции текущего числа игроков.
- Эквити: `±` — погрешность Монте-Карло; точный перебор — «точно» вместо «±…». Пока считается — значение прежнее `alpha 0.5` + «считаем…» в статусной строке (промежуточные результаты обновляют полосу).
- Шансы банка: «нужно {ставка ÷ (банк + ставка)} %»; эквити больше — плитка pay «колл окупается» (полная фраза ТЗ — в a11y: «Колл окупается по шансам банка»); иначе — плитка owe-вариант (`bg c.oweBackground`, рамка `c.oweBorder`, «колл не окупается» `c.owe`). Нет ставки — плитка `c.card` «введите банк и ставку».
- Ауты: самый сильный вид дро + общее число; «X % / Y %» — тёрн / к риверу; «≈N % по 2 и 4» (на ривере плитки нет). На префлопе вместо аутов [без макета]: «Сила руки» / «топ 4 % рук» / «ориентир: с BTN разыгрывают шире» (`t.ledgerS`).
- «−»/«+» игроков: 2…10; «Сбросить» — всё к начальному состоянию (без подтверждения: ничего не сохраняется).

---

## 6. Анимации (M6.3)

- Закуп: стопка мини-фишек `chipColor` «съезжает» от кнопки к строке игрока — 300 мс, `EmphasizedEasing` (`CubicBezierEasing(0.2f, 0f, 0f, 1f)`); выход — обратно к табло.
- Смена сумм на табло/в строках: прокрутка цифр (`AnimatedContent` по разрядам, 250 мс).
- Hold — линейно 600 мс; возврат 200 мс.
- Свайп строки — пружина `Spring.DampingRatioMediumBouncy` при возврате.
- Все анимации выключаются при `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (Compose делает сам).

---

## 7. Состояния и защита (сводка ТЗ §5.1 по экранам)

| Действие | Уровень | Где | Реализация |
| --- | --- | --- | --- |
| Закуп/докуп, новый игрок, оплата долга, «Не заплатил», переименование, прощение (после диалога), правила по умолчанию | обратимое | шторки, долги | итог до нажатия, `DebouncedClick`, snackbar «Отменить» 5 с → `Commands.reverse` (переименование — повторный `renamePlayer`) |
| «Расчёт произведён», «Получил N ₽», «Выплатил N ₽», «Рейк отдан · закрыть игру», «Закрыть игру с долгом», передача, приём | деньги на руки / необратимое | выход, выплата, итог, передача | `HoldToConfirmButton` 0,6 с + вибрация; сумма в подписи |
| Удалить игры, удалить всё, переоткрыть, ранний выход, отмена записи журнала, простить долг, расхождение фишек | необратимое | диалоги | `FeltbookDialog`, глагол на кнопке, безопасная — по умолчанию; «УДАЛИТЬ» для «Удалить всё» |
| Подозрительная сумма, фишек больше, чем в игре, ранний выход | предупреждение | шторки | `InfoBanner.warn` до подтверждения (не блокирует) |
| Повтор нажатия < 600 мс | — | все главные кнопки | `DebouncedClick` + блокировка до конца операции |
| Закрытие шторки с вводом | — | все шторки с суммой | «Отменить ввод?» |
| Свайп строки | — | стол | порог 40 %, вибрация в точке порога |
| Опасное — не рядом с частым | — | «Завершить игру» — в «⋮», «Удалить» — в шапке мультивыбора, «Удалить все игры…» — внизу настроек | |

---

## 8. Навигация

### 8.1. Граф (Navigation Compose, `@Serializable` маршруты)

```
FeltbookNavHost
├── GameGraph (вкладка «Игра», start = Home)
│   ├── Home
│   ├── NewGame                         экран, без панели
│   ├── Table(gameId)                   панель (скрывается прокруткой)
│   │     sheets (состояние экрана, не маршруты): NewPlayer, BuyIn(playerId), PlayerCard(playerId),
│   │       Pay(playerId), Payout(playerId), CashOut(playerId), PartialSettle, TipRecipient,
│   │       Breakdown(cell), Rename (dialog), Reverse (dialog), DebtPay(openDebt)
│   ├── Settlement(gameId)              экран, без панели
│   ├── GameJournal(gameId)             экран, без панели
│   ├── Debts                           панель; sheet DebtPay, dialog Forgive
│   └── Handover(gameId?)               экран, без панели (gameId = null → «Принять игру»)
├── HistoryGraph (вкладка «История», start = History)
│   ├── History                         панель; dialog Delete
│   ├── GameDetail(gameId)              экран, без панели
│   └── Stats                           экран, без панели; sheet PlayerStats(name)
└── SettingsGraph (вкладка «Настройки», start = Settings)
    ├── Settings                        панель; sheets редакторов правил, dialogs Theme/DeleteAll/Import
    └── Odds                            экран, без панели; sheet CardPicker
```
- Шторки — состояние `ViewModel` экрана (`sheet: SheetState?` в `UiState`), а не пункты навигации: так «Отменить ввод?» и снимок игры живут в одной ViewModel; predictive back работает силами `ModalBottomSheet`.
- Вкладки — `saveState/restoreState`; повторное нажатие на выбранную — `popBackStack(start)`.
- Холодный старт: `Home`. Если есть активная игра — сразу `Table(gameId)` поверх `Home` (банкир возвращается за стол одним действием «назад» к главной) — **[решение, §12]**.
- Внешний вход: intent `.feltbook`/`.pokerbank` → `Handover(null)` в режиме импорта файла (M5.2).

### 8.2. Видимость нижней панели

| Видна | Скрыта |
| --- | --- |
| Home, Table (кроме прокрутки вниз), Debts, History (в т. ч. мультивыбор), Settings | NewGame, Settlement, GameJournal, Handover, GameDetail, Stats, Odds; при открытой клавиатуре IME; под шторкой и диалогом (перекрыта) |

Анимация показа/скрытия — `slideInVertically/slideOutVertically` 200 мс; FAB смещается вместе с ней.

### 8.3. Пасхалка (11 нажатий по «Настройкам»)

Счётчик — в `:app` (уровень панели), не в экране. Каждое нажатие на пункт «Настройки» (в т. ч. когда вкладка уже открыта): если с прошлого прошло > 1 000 мс — счётчик = 1, иначе +1. С 7-го нажатия — короткий snackbar/`Toast`-стиль без действия: 7 → «Ещё 4…», 8 → «Ещё 3…», 9 → «Ещё 2…», 10 → «Ещё 1…». 11-е — `oddsCalculatorVisible = true`, сброс счётчика, snackbar «Секретный стол открыт» / действие «Открыть» → `Odds`. Если пункт уже виден — счётчик не показывает подсказок. UI-тест счётчика — M6.2.

---

## 9. Планшеты и foldable (M6.3)

- Окно ≥ 600 dp (`WindowSizeClass` Medium+): нижняя панель → `NavigationRail` (`NavigationSuiteScaffold`): фон `c.feltDeep`, правая граница 1 `c.brassContainer`, индикатор 56 × 32 `r16` `c.brassIndicator`, иконки/подписи как в панели.
- **Стол**: `ListDetailPaneScaffold` — список: шапка + табло + игроки (ширина 360–420 dp); детали: содержимое «Карточки игрока» как обычная панель (не шторка), выбранная строка — `bg c.brassSelectedRow` + рамка `c.selectedBorder`. Закуп/выход/оплата — `ModalBottomSheet` (`sheetMaxWidth = 640 dp`, по центру). FAB — в правом нижнем углу панели списка.
- **История**: список + `GameDetail`; **Статистика**: список игроков + шторка «Игрок» как панель деталей; **Долги**: список + `GameDetail` игры долга; **Настройки**: список + редактор правила.
- Одиночные экраны (NewGame, Settlement, Handover, Odds): содержимое `widthIn(max = 600.dp)` по центру, фон — на весь экран.
- Скриншоты Roborazzi: 390 dp и 840 dp.

## 10. Edge-to-edge и инсеты

- `enableEdgeToEdge()` (прозрачные статус- и навигационная панели; значки светлые в тёмной теме, тёмные — в светлой).
- Значения `p(…)` шапок из макетов отсчитываются **от низа статус-бара**: `Modifier.windowInsetsPadding(WindowInsets.statusBars)` + макетный отступ. Фон (`feltScreen`) рисуется под статус-баром.
- Нижняя панель: высота 80 + `navigationBars` (фон `c.feltDeep` уходит под жестовую полосу), содержимое — в верхних 80.
- Экраны без панели: нижний отступ кнопок `24 + navigationBars`.
- Шторки: `contentWindowInsets = { WindowInsets(0) }`, содержимое `navigationBarsPadding()`; поле имени — `imePadding()`.
- Горизонтально — `WindowInsets.displayCutout.only(Horizontal)` для альбомной ориентации.
- Диалоги/snackbar — над панелью и над `navigationBars`.
- `keepScreenOn` — на окне, пока есть активная игра и настройка включена (`:app`).

---

## 11. Строки (`strings.xml`, русский)

Модуль: общие — `core/ui/src/main/res/values/strings.xml`, экранные — в соответствующем `feature/*`. `%1$s` у сумм — уже отформатированное число (§0.1). Неразрывные пробелы в шаблонах — ` `. Значения ниже — дословно из макетов, кроме помеченных «[предл.]».

### 11.1. Общее (`:core:ui`)

```xml
<string name="money_rub">%1$s ₽</string>
<string name="money_rub_signed_minus">−%1$s ₽</string>
<string name="money_rub_signed_plus">+%1$s ₽</string>
<string name="percent">%1$s %%</string>
<string name="duration_minutes">%1$d мин</string>
<string name="duration_hours_minutes">%1$d ч %2$02d мин</string>
<string name="date_day_month">%1$d %2$s</string>
<string name="separator_dot"> · </string>
<string name="common_back">Назад</string>
<string name="common_cancel">Отмена</string>
<string name="common_undo">Отменить</string>
<string name="common_done">Готово</string>
<string name="common_save">Сохранить</string>
<string name="common_ok">Понятно</string>
<string name="common_more">Ещё</string>
<string name="common_dash">—</string>
<string name="common_hold_to_confirm">удерживайте, чтобы подтвердить</string>
<string name="common_hold">удерживайте</string>
<string name="common_confirm_action">Подтвердить</string>
<string name="keypad_delete">Стереть</string>
<string name="keypad_triple_zero">000</string>
<string name="sheet_discard_title">Отменить ввод?</string>
<string name="sheet_discard_body">Введённая сумма %1$s не сохранится.</string>
<string name="sheet_discard_keep">Продолжить ввод</string>
<string name="sheet_discard_confirm">Отменить ввод</string>
<string name="badge_rebuy">докуп ×%1$d</string>
<string name="badge_banker">БАНКИР</string>
<string name="chip_500">500</string>
<string name="chip_1000">1K</string>
<string name="chip_2000">2K</string>
<string name="chip_5000">5K</string>
<string name="chip_last_line1">как</string>
<string name="chip_last_line2">прошлый</string>
<string name="suit_spades">♠</string>
<string name="suit_hearts">♥</string>
<string name="suit_diamonds">♦</string>
<string name="suit_clubs">♣</string>
<string name="chevron"> ›</string>
<string-array name="months_short">
    <item>янв</item><item>фев</item><item>мар</item><item>апр</item><item>мая</item><item>июн</item>
    <item>июл</item><item>авг</item><item>сен</item><item>окт</item><item>ноя</item><item>дек</item>
</string-array>
<string-array name="months_full_standalone">
    <item>Январь</item><item>Февраль</item><item>Март</item><item>Апрель</item><item>Май</item><item>Июнь</item>
    <item>Июль</item><item>Август</item><item>Сентябрь</item><item>Октябрь</item><item>Ноябрь</item><item>Декабрь</item>
</string-array>
<string name="month_year">%1$s %2$d</string>
```

### 11.2. Plurals (`:core:ui`)

```xml
<plurals name="players_count">
    <item quantity="one">%1$d игрок</item><item quantity="few">%1$d игрока</item>
    <item quantity="many">%1$d игроков</item><item quantity="other">%1$d игрока</item>
</plurals>
<plurals name="players_word">  <!-- «6\nигроков» в калькуляторе -->
    <item quantity="one">игрок</item><item quantity="few">игрока</item>
    <item quantity="many">игроков</item><item quantity="other">игрока</item>
</plurals>
<plurals name="against_players">
    <item quantity="one">против %1$d игрока</item><item quantity="few">против %1$d игроков</item>
    <item quantity="many">против %1$d игроков</item><item quantity="other">против %1$d игрока</item>
</plurals>
<plurals name="rebuys_count">
    <item quantity="one">%1$d докуп</item><item quantity="few">%1$d докупа</item>
    <item quantity="many">%1$d докупов</item><item quantity="other">%1$d докупа</item>
</plurals>
<plurals name="chips_count">  <!-- аргумент — отформатированное число, quantity = pluralQuantity(n) -->
    <item quantity="one">%1$s фишка</item><item quantity="few">%1$s фишки</item>
    <item quantity="many">%1$s фишек</item><item quantity="other">%1$s фишки</item>
</plurals>
<plurals name="games_count">
    <item quantity="one">%1$d игра</item><item quantity="few">%1$d игры</item>
    <item quantity="many">%1$d игр</item><item quantity="other">%1$d игры</item>
</plurals>
<plurals name="entries_count">
    <item quantity="one">%1$d запись журнала</item><item quantity="few">%1$d записи журнала</item>
    <item quantity="many">%1$d записей журнала</item><item quantity="other">%1$d записи журнала</item>
</plurals>
<plurals name="days_count">
    <item quantity="one">%1$d день</item><item quantity="few">%1$d дня</item>
    <item quantity="many">%1$d дней</item><item quantity="other">%1$d дня</item>
</plurals>
<plurals name="outs_count">
    <item quantity="one">%1$d аут</item><item quantity="few">%1$d аута</item>
    <item quantity="many">%1$d аутов</item><item quantity="other">%1$d аута</item>
</plurals>
<plurals name="a11y_rubles">
    <item quantity="one">%1$s рубль</item><item quantity="few">%1$s рубля</item>
    <item quantity="many">%1$s рублей</item><item quantity="other">%1$s рубля</item>
</plurals>
```

### 11.3. Навигация (`:app`)

```xml
<string name="nav_game">Игра</string>
<string name="nav_history">История</string>
<string name="nav_settings">Настройки</string>
<string name="nav_sections">Разделы</string>
<string name="easter_more_taps">Ещё %1$d…</string>
<string name="easter_unlocked">Секретный стол открыт</string>
<string name="easter_open">Открыть</string>
```

### 11.4. Главная (`:feature:game`)

```xml
<string name="home_brand">Feltbook</string>
<string name="home_tagline">банк покерного стола</string>
<string name="home_active_game_section">Активная игра</string>
<string name="home_game_running">Идёт игра</string>
<string name="home_game_settling">Идёт расчёт</string>                         <!-- [предл.] -->
<string name="home_active_meta">%1$s · %2$s · банкир %3$s</string>         <!-- 2 ч 14 мин · 6 игроков · банкир Андрей -->
<string name="home_metric_in_play">В игре</string>
<string name="home_metric_debts">Долги</string>
<string name="home_metric_rake">Рейк</string>
<string name="home_back_to_table">Вернуться за стол</string>
<string name="home_past_games">Прошлые игры</string>
<string name="home_all_history">Вся история</string>
<string name="home_past_meta">%1$s · %2$s · %3$s</string>                   <!-- 4 ч 05 мин · 8 игроков · 11 докупов -->
<string name="home_no_rebuys">без докупов</string>                           <!-- [предл.] -->
<string name="home_income">♥ %1$s</string>                                  <!-- ♥ 800 ₽ -->
<string name="home_rake_short">рейк %1$s</string>
<string name="home_meta_debt"> · долг</string>                         <!-- [предл.] -->
<string name="home_meta_handed_over"> · передана</string>              <!-- [предл.] -->
<string name="home_empty_history">Здесь появятся сыгранные игры</string>     <!-- [предл.] -->
<string name="home_open_debts">Открытые долги</string>                     <!-- [предл.] блок -->
<string name="home_open_debts_all">Все</string>                             <!-- [предл.] -->
<string name="home_fab_new_game">Новая игра</string>
<string name="home_game_already_running_title">Уже идёт игра</string>       <!-- [предл.] -->
<string name="home_game_already_running_body">Сначала закройте «%1$s» — одновременно идёт только одна игра.</string>
<string name="home_game_already_running_go">К столу</string>
```

### 11.5. Новая игра

```xml
<string name="new_game_title">Новая игра</string>
<string name="new_game_field_title">Название</string>
<string name="new_game_default_title">%1$s, %2$s</string>                   <!-- «2 ноя, 20:05» -->
<string name="new_game_field_banker">Банкир</string>
<string name="new_game_banker_required">Укажите имя банкира</string>        <!-- [предл.] -->
<string name="new_game_banker_plays">Я тоже играю</string>
<string name="new_game_banker_plays_hint">рейк плачу, сбор с себя не беру</string>
<string name="new_game_rules_section">Правила игры</string>
<string name="new_game_rules">Правила</string>
<string name="new_game_rules_hint">из настроек · можно изменить</string>
<string name="rules_banker_fee">♥ Сбор банкира</string>
<string name="rules_fee_once_fixed">Разово %1$s</string>                     <!-- Разово 100 ₽ -->
<string name="rules_fee_once_percent">Разово %1$s</string>                   <!-- Разово 10 % -->
<string name="rules_fee_each">С каждого</string>
<string name="rules_rake_each_purchase">♣ Рейк заведению с каждой покупки</string>
<string name="rules_rake_type">Тип рейка</string>
<string name="rules_percent">Процент</string>
<string name="rules_fixed">Фикс</string>
<string name="rules_rake_value_a11y">Рейк, процентов</string>
<string name="rules_min_buy_in">Минимальная покупка</string>
<string name="rules_min_buy_in_value">%1$s ›</string>                        <!-- «500 фишек ›»: %1$s = chips_count -->
<string name="rules_min_play_time">Минимальное время игры</string>
<string name="rules_min_play_time_value">%1$d мин ›</string>
<string name="rules_payment">Оплата покупки</string>
<string name="rules_payment_single">одним статусом</string>
<string name="rules_payment_split">раздельно</string>
<string name="new_game_example_line1">Пример: закуп %1$s → к оплате %2$s</string>
<string name="new_game_example_line2">(%1$s фишки + %2$s рейк + %3$s сбор)</string>
<string name="new_game_start">Начать игру</string>
<string name="rules_problem_banker_fee_negative">Сбор не может быть отрицательным</string>
<string name="rules_problem_banker_fee_percent_above_max">Сбор — не больше 100 %</string>
<string name="rules_problem_banker_fee_above_max_amount">Слишком большой сбор</string>
<string name="rules_problem_rake_negative">Рейк не может быть отрицательным</string>
<string name="rules_problem_rake_percent_above_max">Рейк — не больше 100 %</string>
<string name="rules_problem_rake_above_max_amount">Слишком большой рейк</string>
<string name="rules_problem_min_buy_in_not_positive">Минимальная покупка — больше нуля</string>
<string name="rules_problem_min_buy_in_above_max_amount">Слишком большая минимальная покупка</string>
<string name="rules_problem_min_play_minutes_out_of_range">От 0 до 1440 минут</string>
```

### 11.6. Стол

```xml
<string name="table_subtitle_running">Идёт %1$s · банкир %2$s</string>
<string name="table_subtitle_settling">Расчёт · %1$s · банкир %2$s</string>   <!-- [предл.] -->
<string name="table_handover">Передать комнату</string>
<string name="table_menu">Меню игры</string>
<string name="table_menu_journal">Журнал игры</string>                       <!-- [предл.] -->
<string name="table_menu_finish">Завершить игру</string>
<string name="table_menu_resume">Вернуться к игре</string>
<string name="table_scoreboard">Табло стола</string>
<string name="table_chips_in_play">Фишек в игре</string>
<string name="table_chips_meta">выдано %1$s · сдано %2$s · докупов %3$d</string>
<string name="table_cash_on_hand">♦ Касса на руках</string>
<string name="table_players_owe">♠ Долги игроков</string>
<string name="table_banker_owes">банкир должен %1$s</string>                 <!-- [предл.] -->
<string name="table_rake">♣ Рейк заведению</string>
<string name="table_banker_income">♥ Банкиру</string>
<string name="table_players">Игроки</string>
<string name="table_players_meta">%1$d за столом · %2$d вышел</string>       <!-- «вышел» — так в макете, без согласования -->
<string name="table_row_buy_in">закуп %1$s · %2$s</string>                   <!-- закуп 3 000 · 1 ч 52 мин -->
<string name="table_row_buy_in_early">закуп %1$s · %2$d мин из %3$d</string>
<string name="table_row_debt">в долг</string>
<string name="table_row_paid">оплачено</string>
<string name="table_row_no_fee">без сбора</string>
<string name="table_row_banker_owes">банкир должен</string>                   <!-- [предл.] -->
<string name="table_row_left_settled">вышел · рассчитан</string>
<string name="table_row_left_banker_owes">вышел · банкир должен</string>     <!-- [предл.] -->
<string name="table_row_left_with_debt">ушёл с долгом</string>              <!-- [предл.] -->
<string name="table_row_paid_out">выплачено</string>
<string name="table_row_received">получено</string>                         <!-- [предл.] -->
<string name="table_row_not_paid_out">не выплачено</string>                  <!-- [предл.] -->
<string name="table_row_debt_label">долг</string>                            <!-- [предл.] -->
<string name="table_swipe_buy_in">Докуп</string>
<string name="table_swipe_cash_out">Выход</string>
<string name="table_fab_player">Игрок</string>
<string name="table_fab_player_a11y">Добавить игрока</string>
<string name="table_settling_banner">Игра завершается: выведите оставшихся игроков</string>  <!-- [предл.] -->
<string name="table_to_settlement">К итогу</string>
<string name="breakdown_title_chips">Фишки в игре</string>                   <!-- [предл.] шторка расшифровки -->
<string name="breakdown_rake_paid">Рейк отдан</string>
<string name="breakdown_income_taken">Доход забран</string>
<string name="breakdown_banker_owes_section">Банкир должен</string>
```

### 11.7. Закуп, докуп, новый игрок, предупреждение о долге

```xml
<string name="buy_in_title_rebuy">Докуп · %1$s</string>
<string name="buy_in_title_first">Закуп · %1$s</string>
<string name="buy_in_dialog_a11y">Докуп для %1$s</string>
<string name="buy_in_subtitle_bought">уже закуплено %1$s</string>
<string name="buy_in_subtitle_debt"> · долг %1$s</string>
<string name="buy_in_subtitle_paid"> · оплачено</string>
<string name="buy_in_subtitle_banker_owes"> · банкир должен %1$s</string>
<string name="buy_in_subtitle_return">вернулся за стол</string>             <!-- [предл.] -->
<string name="buy_in_hint">фишек · своё число с клавиатуры или фишка · %1$s</string>
<string name="buy_in_hint_min">минимум %1$s</string>
<string name="buy_in_quick_amounts">Быстрые суммы</string>
<string name="buy_in_chip_a11y">%1$s фишек</string>
<string name="buy_in_chip_last_a11y">Как прошлый раз, %1$s</string>
<string name="buy_in_payment">Оплата</string>
<string name="buy_in_debt">В долг</string>
<string name="buy_in_paid_now">Оплачено сейчас</string>
<string name="buy_in_split_chips">Фишки</string>
<string name="buy_in_split_fees">Рейк и сбор</string>
<string name="buy_in_split_paid">Оплачено</string>
<string name="buy_in_row_chips">Фишки</string>
<string name="buy_in_row_rake_percent">Рейк %1$s</string>                    <!-- Рейк 10 % -->
<string name="buy_in_row_rake">Рейк</string>
<string name="buy_in_row_fee">Сбор банкира</string>
<string name="buy_in_row_fee_taken">уже взят</string>
<string name="buy_in_row_fee_waived">без сбора</string>
<string name="buy_in_total_debt">Итого в долг</string>
<string name="buy_in_total_due">Итого к оплате</string>
<string name="buy_in_total_paid_now">Оплачено сейчас</string>
<plurals name="buy_in_issue">
    <item quantity="one">Выдать %1$s фишку</item><item quantity="few">Выдать %1$s фишки</item>
    <item quantity="many">Выдать %1$s фишек</item><item quantity="other">Выдать %1$s фишки</item>
</plurals>
<string name="buy_in_issue_sub_debt">%1$s записать в долг</string>
<string name="buy_in_issue_sub_paid">%1$s получено сейчас</string>
<string name="buy_in_issue_sub_split">%1$s сейчас · %2$s в долг</string>
<string name="buy_in_suspicious">%1$s фишек — точно? Обычно здесь берут %2$s</string>
<string name="buy_in_suspicious_no_typical">%1$s фишек — точно?</string>
<string name="buy_in_done_snackbar">Докуп %1$s · %2$s</string>              <!-- [предл.] Докуп 2 000 · Борис -->
<string name="buy_in_first_done_snackbar">Закуп %1$s · %2$s</string>
<string name="new_player_title">Новый игрок</string>
<string name="new_player_name">Имя</string>
<string name="new_player_name_taken">%1$s уже за столом</string>
<string name="new_player_open_card">Открыть карточку</string>
<string name="new_player_return">Вернуть за стол</string>
<string name="new_player_name_blank">Введите имя</string>
<string name="new_player_name_too_long">Не длиннее 40 символов</string>
<string name="new_player_first_buy_in">Первый закуп</string>
<string name="new_player_amount_hint">фишек · к оплате %1$s</string>
<string name="new_player_done_snackbar">%1$s сел за стол · %2$s</string>     <!-- [предл.] -->
<string name="debt_warning_title">%1$s должен %2$s</string>                  <!-- Влад должен 1 053 ₽ -->
<string name="debt_warning_meta">с %1$s · %2$s · %3$s</string>               <!-- с 18 окт · Пятничный стол · 15 дней -->
<string name="debt_warning_meta_many">с %1$s · %2$s</string>                 <!-- с 11 окт · 2 игры -->
<string name="debt_warning_pay_now">Погасить сейчас</string>
<string name="debt_warning_seat_with_debt">Посадить с долгом</string>
<string name="debt_warning_default_paid">Для игрока со старым долгом по умолчанию выбрано «Оплачено сейчас»</string>
<string name="debt_warning_collapsed">Старый долг %1$s — сядет с долгом</string>   <!-- [предл.] -->
```

### 11.8. Карточка игрока, журнал, оплата, переименование

```xml
<string name="player_card_a11y">Карточка игрока %1$s</string>
<string name="player_meta_playing">в игре %1$s · с %2$s</string>
<string name="player_meta_left">вышел в %1$s · играл %2$s</string>         <!-- [предл.] -->
<string name="player_meta_early"> · ранний выход</string>
<string name="player_more_a11y">Ещё: переименовать, ранний выход</string>
<string name="player_chips_bought">Фишек куплено</string>
<string name="player_charged">Начислено</string>
<string name="player_debt">Долг</string>
<string name="player_banker_owes">Банкир должен</string>                     <!-- [предл.] -->
<string name="player_paid">Оплачено</string>
<string name="player_action_buy_in">Докуп</string>
<string name="player_action_pay">Оплата</string>
<string name="player_action_payout">Выплатить</string>
<string name="player_action_cash_out">Выход</string>
<string name="player_menu_rename">Переименовать</string>
<string name="player_menu_early_exit">Ранний выход…</string>
<string name="player_menu_forgive">Простить долг…</string>
<string name="journal_title">Журнал</string>
<string name="journal_hold_hint">удержите запись, чтобы отменить</string>
<string name="journal_buy_in_first">Закуп %1$s</string>
<string name="journal_buy_in_rebuy">Докуп %1$s</string>
<string name="journal_status_debt"> · в долг</string>
<string name="journal_status_paid"> · оплачено</string>
<string name="journal_status_partial"> · частично</string>
<string name="journal_sub_rake_percent">↳ рейк %1$s</string>
<string name="journal_sub_rake">↳ рейк</string>
<string name="journal_sub_fee">↳ сбор банкира</string>
<string name="journal_sub_payment">↳ оплачено</string>
<string name="journal_sub_tip_banker">↳ чаевые банкиру</string>
<string name="journal_sub_tip_player">↳ чаевые: %1$s</string>
<string name="journal_payment">Оплата</string>
<string name="journal_payout">Выплачено</string>
<string name="journal_settlement_received">Расчёт при выходе · получено</string>
<string name="journal_settlement_paid_out">Расчёт при выходе · выплачено</string>
<string name="journal_cash_out">Выход · %1$s</string>                        <!-- %1$s = chips_count -->
<string name="journal_cash_out_early"> · ранний</string>
<string name="journal_forgiven">Долг прощён</string>
<string name="journal_rake_paid">Рейк отдан заведению</string>
<string name="journal_income_taken">Доход забран: %1$s</string>
<string name="journal_tip_received">Чаевые от %1$s</string>
<string name="journal_handover">Игра принята от %1$s</string>
<string name="journal_reversed">отменено</string>
<string name="journal_reverse_title">Отменить «%1$s»?</string>
<string name="journal_reverse_body">Запись останется в журнале с пометкой «Отменено», суммы пересчитаются.</string>
<string name="journal_reverse_reason">Причина (необязательно)</string>
<string name="journal_reverse_keep">Оставить</string>
<string name="journal_reverse_confirm">Отменить запись</string>
<string name="journal_not_reversible_already">Эта запись уже отменена</string>
<string name="journal_not_reversible_reversal">Отмену нельзя отменить — повторите операцию</string>
<string name="journal_not_reversible_income">Часть акта передачи — не отменяется</string>
<string name="journal_not_reversible_read_only">Игра передана — только чтение</string>
<string name="journal_not_reversible_closed">Чтобы исправить, переоткройте игру</string>
<string name="pay_title">Оплата · %1$s</string>
<string name="payout_title">Выплатить · %1$s</string>
<string name="debt_pay_title">Погасить · %1$s</string>
<string name="pay_subtitle_debt">долг %1$s</string>
<string name="payout_subtitle">банкир должен %1$s</string>
<string name="pay_chip_all">весь долг</string>
<string name="pay_chip_half">половина</string>
<string name="pay_row_debt_now">Долг сейчас</string>
<string name="pay_row_remaining">Останется</string>
<string name="pay_confirm">Принять %1$s</string>
<string name="payout_confirm">Выплатил %1$s</string>
<string name="pay_done_snackbar">%1$s оплатил %2$s</string>
<string name="debt_paid_snackbar">%1$s погасил %2$s</string>
<string name="pay_exceeds_debt">Больше долга: %1$s</string>
<string name="payout_exceeds_owed">Банкир должен только %1$s</string>
<string name="rename_title">Переименовать</string>
<string name="rename_done_snackbar">Переименован: %1$s → %2$s</string>
```

### 11.9. Выход

```xml
<string name="cash_out_title">Выход · %1$s</string>
<string name="cash_out_dialog_a11y">Выход: %1$s</string>
<string name="cash_out_subtitle">в игре %1$s · закуп %2$s</string>
<string name="cash_out_subtitle_debt"> · долг %1$s</string>
<string name="cash_out_subtitle_in_debt"> в долг</string>               <!-- «закуп 2 000 в долг» -->
<string name="cash_out_chips_on_hand">Фишки на руках</string>
<string name="cash_out_tips">Чаевые</string>
<string name="cash_out_tips_hint">по желанию игрока</string>
<string name="cash_out_no_tips">Без чаевых</string>
<string name="cash_out_tip_round_100">до 100 · %1$s</string>
<string name="cash_out_tip_round_500">до 500 · %1$s</string>
<string name="cash_out_tip_custom">своя сумма</string>
<string name="cash_out_tip_custom_value">своя · %1$s</string>
<string name="cash_out_tips_collapsed">без чаевых ›</string>
<string name="cash_out_tip_to">Кому:</string>
<string name="cash_out_tip_banker">Банкиру</string>
<string name="cash_out_tip_player">Игроку…</string>
<string name="cash_out_tip_player_selected">Игроку: %1$s</string>
<string name="cash_out_tip_picker_title">Кому чаевые</string>
<string name="cash_out_row_chips">Фишки на руках</string>
<string name="cash_out_row_tips">Чаевые</string>
<string name="cash_out_row_withdrawal">К выводу 1:1</string>
<string name="cash_out_row_player_debt">Долг игрока</string>
<string name="cash_out_row_banker_owes">Банкир должен</string>
<string name="cash_out_row_charged">Начислено (фишки, рейк, сбор)</string>
<string name="cash_out_row_paid">Оплачено</string>
<string name="cash_out_row_carried_debt">Долг с прошлого выхода</string>
<string name="cash_out_row_carried_credit">Переплата с прошлого выхода</string>
<string name="cash_out_result_pay">%1$s получает от банкира</string>        <!-- макет: «Банкир выплачивает Диме» -->
<string name="cash_out_result_owe">%1$s доплачивает банкиру</string>
<string name="cash_out_result_even">Квиты — никто никому не должен</string>
<plurals name="cash_out_result_game">
    <item quantity="one">Результат за игру: %1$s фишка</item><item quantity="few">Результат за игру: %1$s фишки</item>
    <item quantity="many">Результат за игру: %1$s фишек</item><item quantity="other">Результат за игру: %1$s фишки</item>
</plurals>
<string name="cash_out_settled">Расчёт произведён</string>
<string name="cash_out_settled_hint_pay">удерживайте · выплатить %1$s</string>
<string name="cash_out_received">Получил %1$s</string>
<string name="cash_out_paid_out">Выплатил %1$s</string>
<string name="cash_out_partial_received">Отдал часть…</string>
<string name="cash_out_partial_paid_out">Выплатил часть…</string>
<string name="cash_out_not_paid">Не заплатил</string>
<string name="cash_out_not_paid_hint">«Не заплатил» запишет долг %1$s — он перейдёт на следующие игры</string>
<string name="cash_out_partial_title_received">Сколько отдал %1$s?</string>
<string name="cash_out_partial_title_paid_out">Сколько выплатили?</string>
<string name="cash_out_partial_rest_debt">остаток %1$s — в долг</string>
<string name="cash_out_partial_rest_banker">остаток %1$s — банкир должен</string>
<string name="cash_out_pay_later">Пока ничего не выплатил</string>
<string name="cash_out_exceeds_in_play">Фишек на выходе больше, чем есть в игре: %1$s</string>
<string name="cash_out_early_banner">Ранний выход: %1$d мин из %2$d — понадобится подтверждение</string>
<string name="cash_out_early_title">Исключительный случай</string>
<string name="cash_out_early_body">%1$s играет %2$d мин из %3$d. Выход будет отмечен в истории как ранний.</string>
<string name="cash_out_early_keep">Не выходить</string>
<string name="cash_out_early_confirm">Выйти раньше</string>
<string name="cash_out_done_paid">%1$s вышел · выплачено %2$s</string>
<string name="cash_out_done_received">%1$s вышел · получено %2$s</string>
<string name="cash_out_done_even">%1$s вышел · квиты</string>
<string name="cash_out_done_debt">%1$s ушёл с долгом %2$s</string>
<string name="cash_out_tip_exceeds">Чаевые больше фишек на руках</string>
<string name="cash_out_tip_to_self">Нельзя дать чаевые самому себе</string>
```

### 11.10. Итог игры

```xml
<string name="settlement_title">Итог игры</string>
<string name="settlement_back">Назад к столу</string>
<string name="settlement_subtitle">%1$s · %2$s · %3$s</string>             <!-- Пятничный стол · 3 ч 40 мин · 3 игрока -->
<string name="settlement_subtitle_rebuys"> · %1$s</string>             <!-- [предл.] · 2 докупа -->
<string name="settlement_chips_ok">Фишки сошлись: выдано %1$s = сдано %2$s</string>
<string name="settlement_chips_diff">Фишки не сошлись: выдано %1$s, сдано %2$s (%3$s)</string>
<string name="settlement_rake_title">♣ Заведению · рейк</string>
<string name="settlement_rake_single">одной суммой</string>
<string name="settlement_rake_incl_debt">полностью, включая долг</string>
<string name="settlement_rake_paid">отдано %1$s</string>
<string name="settlement_banker_title">♥ Банкиру</string>
<string name="settlement_banker_breakdown">сборы %1$s · чаевые %2$s</string>
<string name="settlement_banker_forgiven"> · прощено %1$s</string>
<string name="settlement_banker_after_debt">после погашения долга</string>
<string name="settlement_banker_by_name">%1$s %2$s</string>
<string name="settlement_expected_cash">♦ Должно быть в кассе</string>
<string name="settlement_not_received">Не получено: %1$s</string>            <!-- макет: «Не получено от Влада» -->
<string name="settlement_cash_now">На руках сейчас</string>
<string name="settlement_cash_negative_note">%1$s — банкир доплатил из своих, вернётся, когда погасят долг</string>
<string name="settlement_unpaid">Не оплачено</string>
<string name="settlement_left_with_debt">%1$s ушёл с долгом</string>
<string name="settlement_unpaid_note">долг перейдёт в «Открытые долги» и всплывёт, когда %1$s сядет за стол</string>
<string name="settlement_unpaid_payouts">Ещё не выплачено</string>          <!-- [предл.] -->
<string name="settlement_players">Расчёт с игроками</string>
<string name="settlement_row_pays">платит %1$s</string>
<string name="settlement_row_gets">получает %1$s</string>
<string name="settlement_row_even">квиты</string>
<string name="settlement_row_meta">начислено %1$s · оплачено %2$s · вывод %3$s</string>
<string name="settlement_row_meta_tips"> (%1$s чаевых)</string>
<string name="settlement_row_paid">заплатил %1$s ✓</string>
<string name="settlement_row_received">получил %1$s ✓</string>
<string name="settlement_row_debt">долг %1$s</string>
<string name="settlement_row_banker_owes">банкир должен %1$s</string>
<string name="settlement_still_playing">За столом ещё %1$s — выведите их, чтобы закрыть игру</string>
<string name="settlement_share">Поделиться итогом</string>
<string name="settlement_share_text">Текстом</string>
<string name="settlement_share_image">Картинкой</string>
<string name="settlement_close_rake">Рейк отдан · закрыть игру</string>
<string name="settlement_close">Закрыть игру</string>
<string name="settlement_close_with_debt">Закрыть игру с долгом</string>
<string name="settlement_close_with_debt_hint">удерживайте · рейк %1$s отдан заведению</string>
<string name="settlement_close_unpaid_hint">удерживайте · банкир ещё должен %1$s</string>
<string name="settlement_chips_confirm_title">Фишки не сошлись</string>
<string name="settlement_chips_confirm_body">Сдано на %1$s меньше, чем выдано. Закрыть игру с расхождением?</string>
<string name="settlement_chips_confirm_body_more">Сдано на %1$s больше, чем выдано. Закрыть игру с расхождением?</string>
<string name="settlement_chips_confirm_keep">Проверить ещё</string>
<string name="settlement_share_header">Feltbook · %1$s · %2$s</string>
<string name="settlement_share_pays">%1$s платит %2$s</string>
<string name="settlement_share_gets">%1$s получает %2$s</string>
<string name="settlement_share_even">%1$s — квиты</string>
<string name="settlement_share_totals">Рейк заведению %1$s · Банкиру %2$s</string>
<string name="settlement_share_chips_ok">Фишки сошлись: %1$s</string>
<string name="settlement_share_chips_diff">Фишки не сошлись: %1$s</string>
```

### 11.11. Открытые долги

```xml
<string name="debts_title">Открытые долги</string>
<string name="debts_owed_to_you">Вам должны</string>
<string name="debts_summary_meta">%1$s · %2$s</string>                      <!-- 2 игрока · 2 игры -->
<string name="debts_meta">%1$s · %2$s · %3$s</string>                       <!-- Пятничный стол · 18 окт · 15 дней -->
<string name="debts_today">сегодня</string>
<string name="debts_partial">отдал %1$s из %2$s</string>
<string name="debts_pay">Погасить</string>
<string name="debts_forgive">Простить…</string>
<string name="debts_footer">Погашение записывается в ту игру, где возник долг. «Простить» спросит подтверждение и уменьшит доход банкира той игры.</string>
<string name="debts_empty">Долгов нет — все рассчитались</string>
<string name="debts_forgive_title">Простить долг %1$s?</string>
<string name="debts_forgive_body">Долг закроется без денег, доход банкира игры «%1$s» уменьшится на %2$s. В журнале останется запись.</string>
<string name="debts_forgive_keep">Оставить долг</string>
<string name="debts_forgive_confirm">Простить %1$s</string>
<string name="debts_forgiven_snackbar">Долг прощён</string>
```

### 11.12. Передача комнаты (`:feature:handover`)

```xml
<string name="handover_title">Передача комнаты</string>
<string name="handover_back">Назад к столу</string>
<string name="handover_mode">Режим</string>
<string name="handover_give">Отдать игру</string>
<string name="handover_receive">Принять игру</string>
<string name="handover_device">Устройство</string>
<string name="handover_device_name">Телефон: %1$s</string>                  <!-- макет: «Телефон Влада» -->
<string name="handover_device_found">найден рядом · %1$s</string>
<string name="handover_medium_bluetooth">Bluetooth</string>
<string name="handover_medium_wifi_direct">Wi-Fi Direct</string>
<string name="handover_medium_wifi">Wi-Fi</string>
<string name="handover_check_code">Сверьте код на обоих телефонах</string>
<string name="handover_code_a11y">Код %1$s</string>
<string name="handover_act">♦ Акт передачи кассы</string>
<string name="handover_cash">Наличные в кассе</string>
<string name="handover_rake">Рейк заведению (переходит)</string>
<string name="handover_debts">Долги игроков (переходят)</string>
<string name="handover_banker_owes">Долг вышедшим (переходит)</string>
<string name="handover_my_income">Мой доход банкира</string>
<string name="handover_keep_income">Оставить доход себе · %1$s</string>
<string name="handover_keep_income_later">Оставить доход себе — после расчёта долгов</string>
<string name="handover_keep_income_a11y">Оставить доход себе</string>
<string name="handover_cash_to_give">Отдать наличными</string>             <!-- макет: «Отдать Владу наличными» -->
<string name="handover_payload">Передаётся: %1$s · %2$s · %3$s</string>
<string name="handover_hold">Код совпадает — удерживайте</string>
<string name="handover_hold_hint">чтобы передать игру</string>
<string name="handover_code_mismatch">Код другой — отмена</string>
<string name="handover_permission_title">Нужен доступ к Bluetooth и Wi-Fi рядом</string>
<string name="handover_permission_body">Игра передаётся напрямую между телефонами, без интернета. Доступ нужен только на время передачи.</string>
<string name="handover_permission_allow">Разрешить</string>
<string name="handover_searching">Ищем телефон рядом…</string>
<string name="handover_searching_hint">Попросите нового банкира открыть «Принять игру»</string>
<string name="handover_waiting">Ждём телефон банкира…</string>
<string name="handover_connecting">Соединяемся…</string>
<string name="handover_sending">Отправляем…</string>
<string name="handover_failed">Не получилось передать. Попробуйте ещё раз или отправьте файлом.</string>
<string name="handover_send_file">Отправить файлом</string>
<string name="handover_file_sent_title">Файл отправлен?</string>
<string name="handover_file_sent_body">После подтверждения игра на этом телефоне станет «Передана» — только чтение.</string>
<string name="handover_file_not_yet">Ещё нет</string>
<string name="handover_file_sent_confirm">Да, отправлен</string>
<string name="handover_done">Игра передана</string>
<string name="handover_incoming">Игра «%1$s» от %2$s</string>
<string name="handover_incoming_players">Игроков за столом</string>
<string name="handover_incoming_chips">Фишек в игре</string>
<string name="handover_incoming_cash">Касса</string>
<string name="handover_play_as">Я играю как…</string>
<string name="handover_not_playing">Не играю</string>
<string name="handover_accept">Принять игру</string>
<string name="handover_error_active">На этом телефоне уже идёт игра — сначала закройте её</string>
<string name="handover_error_conflict">Эта игра уже есть на телефоне и её журнал отличается — ничего не изменено</string>
<string name="handover_error_invalid">Файл повреждён или из новой версии приложения</string>
```

### 11.13. История и статистика (`:feature:history`)

```xml
<string name="history_title">История</string>
<string name="history_stats">Статистика</string>
<string name="history_selected">Выбрано %1$d</string>
<string name="history_clear_selection">Снять выбор</string>
<string name="history_delete">Удалить</string>
<string name="history_filter_all">Все</string>
<string name="history_filter_month">Месяц</string>
<string name="history_filter_year">Год</string>
<string name="history_row_title">%1$s · %2$s</string>                      <!-- Субботний турнир · 25 окт -->
<string name="history_turnover">оборот %1$s</string>
<string name="history_rake">рейк %1$s</string>
<string name="history_meta_edited"> · изменено после закрытия</string>
<string name="history_meta_handed_over"> · передана</string>
<string name="history_meta_debt"> · долг</string>
<string name="history_checked">Выбрано</string>
<string name="history_unchecked">Не выбрано</string>
<string name="history_empty">Сыгранные игры появятся здесь</string>
<string name="history_delete_dialog_a11y">Подтверждение удаления</string>
<plurals name="history_delete_title">
    <item quantity="one">Удалить %1$d игру?</item><item quantity="few">Удалить %1$d игры?</item>
    <item quantity="many">Удалить %1$d игр?</item><item quantity="other">Удалить %1$d игры?</item>
</plurals>
<string name="history_delete_body_many">Журналы, расчёты и статистика этих игр исчезнут с телефона. Отменить можно 5 секунд.</string>
<string name="history_delete_body_one">Журнал, расчёт и статистика этой игры исчезнут с телефона. Отменить можно 5 секунд.</string>
<string name="history_delete_keep">Оставить</string>
<plurals name="history_delete_confirm">
    <item quantity="one">Удалить %1$d игру</item><item quantity="few">Удалить %1$d игры</item>
    <item quantity="many">Удалить %1$d игр</item><item quantity="other">Удалить %1$d игры</item>
</plurals>
<plurals name="history_deleted_snackbar">
    <item quantity="one">Удалена %1$d игра</item><item quantity="few">Удалено %1$d игры</item>
    <item quantity="many">Удалено %1$d игр</item><item quantity="other">Удалено %1$d игры</item>
</plurals>
<string name="game_detail_menu_share">Поделиться итогом</string>
<string name="game_detail_menu_reopen">Переоткрыть для исправления</string>
<string name="game_detail_menu_delete">Удалить игру…</string>
<string name="game_detail_reopen_title">Переоткрыть игру?</string>
<string name="game_detail_reopen_body">Игра снова станет активной и получит пометку «изменено после закрытия».</string>
<string name="game_detail_reopen_confirm">Переоткрыть</string>
<string name="game_journal_title">Журнал игры</string>
<string name="journal_row_player">%1$s · %2$s</string>
<string name="stats_title">Статистика</string>
<string name="stats_back">Назад к истории</string>
<string name="stats_period_month">За месяц</string>
<string name="stats_period_3_months">За 3 месяца</string>
<string name="stats_period_year">За год</string>
<string name="stats_period_all">За всё время</string>
<string name="stats_period_chip">%1$s ▾</string>
<string name="stats_games">Игр</string>
<string name="stats_turnover">Оборот фишек</string>
<string name="stats_rake">♣ Рейк заведению</string>
<string name="stats_banker_income">♥ Доход банкира</string>
<string name="stats_chart_a11y">Доход банкира за последние игры</string>
<plurals name="stats_chart_title">
    <item quantity="one">Доход банкира, последняя %1$d игра</item><item quantity="few">Доход банкира, последние %1$d игры</item>
    <item quantity="many">Доход банкира, последние %1$d игр</item><item quantity="other">Доход банкира, последние %1$d игры</item>
</plurals>
<string name="stats_players">Игроки</string>
<string name="stats_players_hint">результат в фишках</string>
<string name="stats_player_meta">%1$s · %2$s докупа за игру</string>        <!-- 11 игр · 1,4 докупа за игру -->
<string name="stats_player_games">Игр</string>
<string name="stats_player_result">Результат</string>
<string name="stats_player_avg_buy_in">Средний закуп</string>
<string name="stats_player_rebuys">Докупов за игру</string>
<string name="stats_player_debt">Долг</string>
<string name="stats_player_last">Последняя игра %1$s</string>
<string name="stats_player_merge">Объединить с…</string>
<string name="stats_player_unmerge">Разъединить</string>
<string name="stats_merge_title">Объединить «%1$s» с «%2$s»?</string>
<string name="stats_merge_body">Статистика и долги будут считаться вместе. Разъединить можно позже.</string>
<string name="stats_merge_confirm">Объединить</string>
```

### 11.14. Настройки (`:feature:settings`)

```xml
<string name="settings_title">Настройки</string>
<string name="settings_section_rules">Правила по умолчанию</string>
<string name="settings_fee">♥ Сбор банкира</string>
<string name="settings_fee_once_fixed">разово %1$s ›</string>
<string name="settings_fee_once_percent">разово %1$s ›</string>
<string name="settings_fee_each_fixed">с каждого %1$s ›</string>
<string name="settings_fee_each_percent">с каждого %1$s ›</string>
<string name="settings_rake">♣ Рейк</string>
<string name="settings_rake_value">%1$s с покупки ›</string>
<string name="settings_min_buy_in">Минимальная покупка</string>
<string name="settings_min_buy_in_value">%1$s ›</string>
<string name="settings_min_play_time">Минимальное время игры</string>
<string name="settings_min_play_time_value">%1$d мин ›</string>
<string name="settings_payment">Оплата покупки</string>
<string name="settings_payment_single">одним статусом ›</string>
<string name="settings_payment_split">раздельно ›</string>
<string name="settings_payment_split_hint">Раздельно — два переключателя: «Фишки» и «Рейк и сбор»</string>
<string name="settings_section_look">Оформление и стол</string>
<string name="settings_theme">Тема</string>
<string name="settings_theme_dark">сукно, тёмная ›</string>
<string name="settings_theme_light">бумага, светлая ›</string>
<string name="settings_theme_system">как в системе ›</string>
<string name="settings_theme_dialog_dark">Сукно — тёмная</string>
<string name="settings_theme_dialog_light">Бумага и латунь — светлая</string>
<string name="settings_theme_dialog_system">Как в системе</string>
<string name="settings_dynamic_color">Цвета обоев</string>
<string name="settings_dynamic_color_hint">Material You вместо сукна</string>
<string name="settings_dynamic_color_unavailable">доступно с Android 12</string>
<string name="settings_keep_screen_on">Не гасить экран</string>
<string name="settings_keep_screen_on_hint">пока идёт игра</string>
<string name="settings_haptics">Вибрация при подтверждении</string>
<string name="settings_haptics_a11y">Вибрация</string>
<string name="settings_section_data">Данные</string>
<string name="settings_backup">Резервная копия в файл</string>
<string name="settings_restore">Восстановить из файла</string>
<string name="settings_delete_all">Удалить все игры…</string>
<string name="settings_section_secret">♠ Секретный стол</string>
<string name="settings_odds">Калькулятор шансов</string>
<string name="settings_odds_hide">Скрыть пункт</string>
<string name="settings_odds_hidden">Пункт скрыт</string>
<string name="settings_footer">Feltbook %1$s · open source · данные только на телефоне</string>
<string name="settings_backup_done">Копия сохранена: %1$s</string>
<string name="settings_backup_file_name">feltbook-%1$s.feltbook</string>
<string name="settings_restore_title">Восстановлено</string>
<string name="settings_restore_report">Добавлено %1$s · без изменений %2$s · не тронуто из-за расхождений %3$s</string>
<string name="settings_delete_all_title">Удалить все игры?</string>
<string name="settings_delete_all_body">Все игры, журналы и статистика исчезнут с телефона. Чтобы подтвердить, введите УДАЛИТЬ.</string>
<string name="settings_delete_all_word">УДАЛИТЬ</string>
<string name="settings_delete_all_confirm">Удалить все игры</string>
<string name="settings_rules_saved">Правила сохранены</string>
```

### 11.15. Калькулятор шансов (`:feature:settings`, M6)

```xml
<string name="odds_title">Калькулятор шансов</string>
<string name="odds_subtitle">♠ СЕКРЕТНЫЙ СТОЛ</string>
<string name="odds_back">Назад к настройкам</string>
<string name="odds_reset">Сбросить</string>
<string name="odds_cards">Карты</string>
<string name="odds_my_cards">Мои карты</string>
<string name="odds_board_preflop">Стол · префлоп</string>
<string name="odds_board_flop">Стол · флоп</string>
<string name="odds_board_turn">Стол · тёрн</string>
<string name="odds_board_river">Стол · ривер</string>
<string name="odds_add_flop">Добавить флоп</string>
<string name="odds_add_turn">Добавить тёрн</string>
<string name="odds_add_river">Добавить ривер</string>
<string name="odds_fewer_players">Меньше игроков</string>
<string name="odds_more_players">Больше игроков</string>
<string name="odds_pot">Банк</string>
<string name="odds_bet">Ставка</string>
<string name="odds_position">Позиция</string>
<string name="odds_equity">Эквити</string>
<string name="odds_win_chance">Шанс выиграть %1$s</string>                  <!-- %1$s = against_players -->
<string name="odds_margin">±%1$s</string>
<string name="odds_exact">точно</string>
<string name="odds_legend_win">● победа %1$s</string>
<string name="odds_legend_tie">● ничья %1$s</string>
<string name="odds_legend_lose">● проигрыш %1$s</string>
<string name="odds_bar_a11y">Победа %1$s процента, ничья %2$s, проигрыш %3$s</string>
<string name="odds_pot_odds">Шансы банка</string>
<string name="odds_pot_needed">нужно %1$s</string>
<string name="odds_call_ok">колл окупается</string>
<string name="odds_call_bad">колл не окупается</string>
<string name="odds_call_ok_a11y">Колл окупается по шансам банка</string>
<string name="odds_call_bad_a11y">Колл не окупается</string>
<string name="odds_pot_missing">введите банк и ставку</string>
<string name="odds_outs_title">%1$s · %2$s</string>                         <!-- Флеш-дро · 9 аутов -->
<string name="odds_outs_value">%1$s / %2$s</string>
<string name="odds_outs_hint">тёрн / к риверу · ≈%1$s по 2 и 4</string>
<string name="odds_draw_flush">Флеш-дро</string>
<string name="odds_draw_straight">Стрит-дро</string>
<string name="odds_draw_gutshot">Гатшот</string>
<string name="odds_draw_set">На сет</string>
<string name="odds_draw_pair">На пару</string>
<string name="odds_status">Сейчас: %1$s · %2$s раздач · офлайн</string>
<string name="odds_status_exact">Сейчас: %1$s · точный перебор · офлайн</string>
<string name="odds_calculating">считаем…</string>
<string name="odds_hand_strength">Сила руки</string>
<string name="odds_top_percent">топ %1$s рук</string>
<string name="odds_position_hint">ориентир: из ранней позиции разыгрывают уже, с баттона — шире</string>
<string name="odds_card_picker">Выберите карту</string>
<string name="odds_dead_cards">Мёртвые карты</string>
<string name="odds_memo_section">Памятка по позициям</string>
<string name="odds_memo_title">Памятка: кто есть кто</string>
<string name="odds_memo_hint">по часовой от дилера</string>
<string name="odds_memo_utg">«под прицелом» — первый после блайндов, самая ранняя</string>
<string name="odds_memo_mp">середина стола, между UTG и CO</string>
<string name="odds_memo_co">«катофф» — сразу перед дилером, поздняя</string>
<string name="odds_memo_btn">баттон, дилер — после флопа ходит последним, лучшая</string>
<string name="odds_memo_sb">малый блайнд, слева от дилера</string>
<string name="odds_memo_bb">большой блайнд — на префлопе ходит последним</string>
<string name="odds_memo_footer">Чем позже позиция, тем больше видно чужих решений — и тем шире обычно разыгрывают руки.</string>
<!-- Комбинации (текущая рука): -->
<string name="odds_hand_high_card">старшая карта, %1$s</string>
<string name="odds_hand_pair">пара</string>
<string name="odds_hand_two_pair">две пары</string>
<string name="odds_hand_set">сет</string>
<string name="odds_hand_straight">стрит</string>
<string name="odds_hand_flush">флеш</string>
<string name="odds_hand_full_house">фулл-хаус</string>
<string name="odds_hand_quads">каре</string>
<string name="odds_hand_straight_flush">стрит-флеш</string>
<string name="odds_rank_ace">туз</string>
<!-- названия карт для TalkBack: «Туз пик», «Семёрка червей» — массивы рангов и мастей в родительном -->
<string-array name="odds_rank_names"><item>Двойка</item><item>Тройка</item><item>Четвёрка</item><item>Пятёрка</item><item>Шестёрка</item><item>Семёрка</item><item>Восьмёрка</item><item>Девятка</item><item>Десятка</item><item>Валет</item><item>Дама</item><item>Король</item><item>Туз</item></string-array>
<string-array name="odds_suit_names_genitive"><item>пик</item><item>червей</item><item>бубен</item><item>треф</item></string-array>
```

### 11.16. Ошибки домена (общие, `:core:ui`)

```xml
<string name="error_wrong_status">Сейчас это действие недоступно</string>
<string name="error_another_game_active">Уже идёт другая игра</string>
<string name="error_player_not_found">Игрок не найден</string>
<string name="error_amount_not_positive">Введите сумму</string>
<string name="error_amount_too_large">Слишком большая сумма</string>
<string name="error_nothing_to_settle">Рассчитываться не с чем</string>
<string name="error_partial_out_of_range">Часть — от 1 до %1$s</string>
<string name="error_no_rake_due">Рейк уже отдан</string>
<string name="error_generic">Не получилось сохранить. Попробуйте ещё раз</string>
```

### 11.17. TalkBack (`:core:ui`)

```xml
<string name="a11y_banker_pays">Банкир выплачивает %1$s</string>          <!-- %1$s = a11y_rubles -->
<string name="a11y_player_pays">%1$s доплачивает %2$s</string>
<string name="a11y_debt">Долг %1$s</string>
<string name="a11y_owes_banker">%1$s должен %2$s</string>
<string name="a11y_even">Квиты</string>
<string name="a11y_minus">минус %1$s</string>
<string name="a11y_plus">плюс %1$s</string>
<string name="a11y_scoreboard_cell">%1$s, %2$s</string>
<string name="a11y_player_row">%1$s, %2$s</string>
<string name="a11y_selected">выбрано</string>
```

---

## 12. Отличия от макетов и открытые вопросы

| # | Где | Макет | В спецификации | Почему |
| --- | --- | --- | --- | --- |
| 1 | Клавиатура | клавиши 46 dp | 48 dp | DoD: цели ≥ 48 dp (ТЗ §5.1 вообще говорит 64 — макет и DESIGN.md «≥ 46» победили по высоте экрана) |
| 2 | Итог игры | «Рейк отдан · закрыть игру» — обычная кнопка | hold 0,6 с | ТЗ §5.1: закрытие и «Рейк отдан» — «деньги на руки» |
| 3 | Выход (выплата) | «Расчёт произведён» — обычная 56 dp, только «Отмена» | hold 68 dp с суммой в подсказке; «Выплатил часть…» + «Отмена» | ТЗ §5.1 и §5.2 («Отдал часть…») |
| 4 | Выход (долг) | нет блока чаевых | свёрнутая строка «Чаевые» | ТЗ §3.3 — чаевые возможны всегда |
| 5 | Выход | нет клавиатуры (поле `inputmode=numeric`) | своя клавиатура под полем, пока поле в фокусе | ТЗ §5.1: системная клавиатура не появляется |
| 6 | Склонение имён | «Банкир выплачивает Диме», «не получено от Влада», «долг за Владом», «Отдать Владу наличными», «Телефон Влада» | формулировки с именем в именительном падеже (§11) | склонять произвольные имена нельзя без словаря; неверное склонение хуже нейтральной формы. **Вопрос техлиду/владельцу** |
| 7 | DebtSettlement | нет «Поделиться» | есть (как в Settlement) | ТЗ §4 п. 4 |
| 8 | Подзаголовок итога/выхода | Settlement — Plex Sans 13 `muted`, DebtSettlement — Mono 12 `ledger`; CashOut — Sans 13, DebtCashOut — Mono 12 | по одному стилю на экран: Sans 13 `muted` | один компонент — один стиль; второй вариант — опечатка макета. Подтвердить |
| 9 | Главная | нет блока «Открытые долги» | предложен блок (§5.1) | ТЗ §5.2, roadmap M4.1 |
| 10 | Главная | FAB «Новая игра» при идущей игре | FAB виден, нажатие — диалог «Уже идёт игра» | ТЗ §4 п. 2 |
| 11 | Новая игра | 3 сегмента сбора, значения в подписях | 3 сегмента + второй ряд для «С каждого», ввод значения повторным нажатием | в домене 4 режима (`BankerFeeMode`) |
| 12 | Масть аватара | стол и статистика дают разные масти одному имени | за столом — по порядку посадки, вне — по хешу имени | одно правило под оба макета невозможно |
| 13 | Диалог | без затемнения фона | scrim `feltDeep` α 0.45 | модальность и фокус TalkBack |
| 14 | Hold-подсказка Handover | цвет не задан (= `onBrass`) | `onBrass` (как в макете) | — |
| 15 | «докупов 2» на табло, «1 вышел» | без согласования | дословно | так в макете («докупов N» — подпись, не количество); «%d вышел» — можно заменить на plural «вышли» — **вопрос** |
| 16 | Пример в «Новой игре» | «(1 000 фишки + …)» | дословно (как подпись, не счёт) | макет |
| 17 | Стол, «вышел · рассчитан» | сумма «+2 700» без ₽ | дословно; при долге банкира — с ₽ | макет |
| 18 | Холодный старт | — | при активной игре сразу Стол поверх Главной | быстрее вернуться за стол; **вопрос** |
| 19 | «Я тоже играю» по умолчанию | вкл. | вкл., не запоминается (в `AppSettings` поля нет) | если нужно помнить — поле в `AppSettings` (data-layer) |
| 20 | Калькулятор | нет сетки выбора карт и мёртвых карт | шторка 4 × 13 (масти колонками) | 13 колонок не дают цели 48 dp; решает poker-odds |
| 21 | Snackbar | нет макета | `dialog`-поверхность, «Отменить» `brassLight` | — |

---

## 13. Чек-лист реализации по этапам

- **M3.1**: токены §1.2, градиенты §1.3, типографика §2 (вес 800 → Bold), `MoneyFormat` §0.1, иконки §3.
- **M3.2**: компоненты §4 (превью каждого состояния + Roborazzi тёмная/200 %).
- **M3.3**: граф §8, панель §4.7, инсеты §10, пасхалочный счётчик-заглушка (§8.3, полностью — M6.2).
- **M3.4–M3.8**: экраны §5.1–§5.8, строки §11.1–§11.10.
- **M4**: §5.9, §5.11–§5.13, блок долгов на Главной, строки §11.11, §11.13–§11.14.
- **M5**: §5.10, §11.12.
- **M6**: §5.14, §6, §9, §11.15, a11y §11.17.
