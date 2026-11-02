package app.feltbook.feature.handover

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

/** Вкладка экрана «Передача комнаты». */
enum class HandoverMode { GIVE, RECEIVE }

/**
 * «Передача комнаты» (m3-ui-spec §8.1). [gameId] — игра отправителя (null — активная игра, вкладка «Принять игру»);
 * [fromFile] — открыт файл `.feltbook`/`.pokerbank` (пакет уже в `IncomingPackageStore`). Имена полей — аргументы
 * `SavedStateHandle` ViewModel.
 */
@Serializable
data class HandoverRoute(val gameId: String? = null, val fromFile: Boolean = false) {
    /** Вкладка при открытии: со стола — «Отдать игру», с главной и из файла — «Принять игру». */
    val initialMode: HandoverMode
        get() = if (gameId != null && !fromFile) HandoverMode.GIVE else HandoverMode.RECEIVE

    companion object {
        const val ARG_GAME_ID: String = "gameId"
        const val ARG_FROM_FILE: String = "fromFile"
    }
}

/**
 * @param onHandedOver игра передана (у отправителя HANDED_OVER) или шаблон отдан — на главную.
 * @param onGameAccepted игра принята и записана — открыть её стол.
 * @param onTemplateAccepted шаблон принят — открыть «Новую игру».
 */
fun NavGraphBuilder.handoverScreen(
    onBack: () -> Unit,
    onHandedOver: () -> Unit,
    onGameAccepted: (gameId: String) -> Unit,
    onTemplateAccepted: () -> Unit,
) {
    composable<HandoverRoute> { entry ->
        HandoverRouteScreen(
            initialMode = entry.toRoute<HandoverRoute>().initialMode,
            onBack = onBack,
            onHandedOver = onHandedOver,
            onGameAccepted = onGameAccepted,
            onTemplateAccepted = onTemplateAccepted,
        )
    }
}
