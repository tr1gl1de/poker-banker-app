# 0004. detekt 1.23.8 разбирает код компилятором Kotlin 2.0.21

Статус: принято · 2026-11-02

## Контекст
Проект на Kotlin 2.4. Последний стабильный detekt — 1.23.8; он собран под Kotlin 2.0.21 и не работает с
`kotlin-compiler-embeddable` 2.4. Поэтому `feltbook.quality` принудительно резолвит `org.jetbrains.kotlin:*`
в конфигурации `detekt` до 2.0.21 (`QualityConventionPlugin.DETEKT_KOTLIN_VERSION`). detekt 2.x пока в alpha.

## Решение
- Остаёмся на detekt 1.23.8 с парсером Kotlin 2.0.21; detekt работает без type resolution (задача `detekt`).
- Синтаксис новее Kotlin 2.0 в коде не используем, пока не перейдём на detekt 2.x:
  context parameters (`context(x: T)`), guard conditions в `when` (`is A if …`), multi-dollar интерполяция
  (`$$"…"`), nested type aliases и прочие фичи 2.1+. Такой код ломает разбор — detekt падает или молча
  пропускает файл.
- TODO: перейти на detekt 2.x, когда выйдет стабильная версия с поддержкой текущего Kotlin; убрать
  принудительную версию Kotlin из `QualityConventionPlugin` и снять ограничение на синтаксис.

## Последствия
Компилятор принимает новый синтаксис, но detekt нет — ограничение держится договорённостью и ревью. Ошибка
разбора detekt на новом файле — сигнал, что использована фича Kotlin 2.1+.
