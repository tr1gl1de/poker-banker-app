package app.feltbook.navigation

import kotlinx.serialization.Serializable

/** Вкладки нижней панели — вложенные графы (m3-ui-spec §8.1). */
@Serializable
data object GameGraph

@Serializable
data object HistoryGraph

@Serializable
data object SettingsGraph

/** Передача комнаты — заглушка до M5. Маршруты истории, долгов и настроек — в своих feature-модулях. */
@Serializable
data class HandoverRoute(val gameId: String? = null)
