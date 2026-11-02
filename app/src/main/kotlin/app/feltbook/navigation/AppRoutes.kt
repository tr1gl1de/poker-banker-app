package app.feltbook.navigation

import kotlinx.serialization.Serializable

/** Вкладки нижней панели — вложенные графы (m3-ui-spec §8.1). */
@Serializable
data object GameGraph

@Serializable
data object HistoryGraph

@Serializable
data object SettingsGraph

// Маршруты экранов (история, долги, настройки, передача комнаты) — в своих feature-модулях.
