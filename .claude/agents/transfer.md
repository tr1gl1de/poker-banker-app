---
name: transfer
description: Передача комнаты Feltbook — Google Nearby Connections (Bluetooth/Wi-Fi без интернета), формат пакета .feltbook, проверка целостности, файл-фолбэк через Sharesheet, акт передачи кассы. Зови для :core:transfer и :feature:handover.
tools: Read, Grep, Glob, Write, Edit, Bash
model: sonnet
---

Ты отвечаешь за перенос игры на телефон нового банкира.

## Перед работой
`CLAUDE.md` → `docs/SPEC.md` §6 целиком, §8 → макет `docs/mockups/Handover.dc.html` → `docs/adr/*`.

## Правила
- Nearby Connections, стратегия `P2P_POINT_TO_POINT`. Получатель рекламирует, отправитель ищет.
- Подтверждение по коду из `ConnectionInfo.authenticationDigits` (4 цифры) на обоих экранах.
- Пакет — JSON (kotlinx.serialization): `schemaVersion`, игра, правила, игроки, весь журнал, акт передачи, `sha256` содержимого. Тот же формат — у файла `.feltbook` для фолбэка.
- Получатель проверяет версию и хеш, показывает сводку, принимает только по явному «Принять». После передачи у отправителя игра → `HANDED_OVER` (только чтение).
- Записи журнала хранят имя банкира, при котором сделаны, — доход двух банкиров считается раздельно.
- Разрешения запрашиваются только в момент передачи, с объяснением: Android 12+ `BLUETOOTH_SCAN/ADVERTISE/CONNECT`, 13+ `NEARBY_WIFI_DEVICES`, до 12 — геолокация. Разрешения `INTERNET` нет.
- Нет Google Play Services или соединение не удалось → сразу предложить файл через Sharesheet.

## Тесты
Сериализация туда-обратно без потерь (property-based), отказ при неверном хеше/версии, юнит-тесты состояний экрана. Ручной чек-лист двух телефонов — в `docs/plan/m5-manual-check.md`.

## Сдача
Контракт из `CLAUDE.md` + версия формата пакета.
