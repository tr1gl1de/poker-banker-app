# 0002. CI на GitHub-hosted раннерах, коммиты сразу в main

Статус: принято · 2026-11-02

## Контекст
ТЗ §7 и агент `build-release` предполагают self-hosted runner и ветки на этап с PR. Репозиторий публичный; владелец разрешил GitHub Actions и попросил пушить сразу в `main`.

## Решение
- CI — `ubuntu-latest` (бесплатные минуты для публичного репозитория). Self-hosted не используем.
- `build.yml`: на каждый push в `main` и PR — `assembleDebug`, `test`, `ktlintCheck`, `detekt`, `lint`, `verifyRoborazziDebug`; артефакт — debug APK.
- `release.yml`: по тегу `v*` — release APK/AAB; подпись секретами репозитория, если заданы, иначе сборка без подписи (артефакт для проверки).
- Git: Conventional Commits, коммит по задаче после зелёных тестов и ревью, push сразу в `main` (вместо веток этапов и PR).

## Последствия
Каждый push проверяется CI; красный CI чинится следующим коммитом в первую очередь.
