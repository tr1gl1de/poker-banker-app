package app.feltbook.navigation

import kotlinx.serialization.Serializable

/** Вкладки нижней панели — вложенные графы (m3-ui-spec §8.1). */
@Serializable
data object GameGraph

@Serializable
data object HistoryGraph

@Serializable
data object SettingsGraph

/** Заглушки экранов следующих этапов (M4–M5): История, Настройки, Открытые долги, Карточка игры, Передача. */
@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

@Serializable
data object DebtsRoute

@Serializable
data class GameDetailRoute(val gameId: String)

@Serializable
data class HandoverRoute(val gameId: String? = null)
