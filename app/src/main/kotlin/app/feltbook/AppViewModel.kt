package app.feltbook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import app.feltbook.core.transfer.file.HandoverImportHandler
import app.feltbook.feature.handover.HandoverRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Что нужно на старте: игра идёт → сразу Стол поверх Главной (QUESTIONS №20). */
sealed interface ColdStart {
    data object Loading : ColdStart

    data class Ready(val activeGameId: String?) : ColdStart
}

/** Уровень окна: тема, «экран не гаснет», пасхалка, холодный старт, открытие файла `.feltbook` снаружи. */
@HiltViewModel
class AppViewModel @Inject constructor(
    gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository,
    private val importHandler: HandoverImportHandler,
) : ViewModel() {
    private val routeChannel = Channel<HandoverRoute>(Channel.BUFFERED)

    /** Маршрут, который нужно открыть поверх текущего экрана (файл из Telegram, «Файлов», почты). */
    val pendingRoutes: Flow<HandoverRoute> = routeChannel.receiveAsFlow()

    val settings: StateFlow<AppSettings?> =
        settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** id активной игры (RUNNING/SETTLING) или null. */
    val activeGameId: StateFlow<String?> =
        gameRepository.observeActiveGame()
            .map { it?.game?.id?.value?.toString() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val coldStartState = MutableStateFlow<ColdStart>(ColdStart.Loading)
    val coldStart: StateFlow<ColdStart> = coldStartState.asStateFlow()

    init {
        viewModelScope.launch {
            val active = gameRepository.getActiveGame()
            coldStartState.value = ColdStart.Ready(active?.game?.id?.value?.toString())
        }
    }

    /**
     * Файл открыт снаружи: прочитать сразу (временный доступ к `content://` есть, пока жив интент), проверить кодеком
     * и открыть «Принять игру» в режиме файла. Резервная копия покажет там же подсказку про «Настройки».
     */
    fun openIncomingFile(uri: Uri) {
        viewModelScope.launch {
            importHandler.open(uri)
            routeChannel.send(HandoverRoute(fromFile = true))
        }
    }

    fun unlockOdds() {
        viewModelScope.launch { settingsRepository.update { it.copy(oddsCalculatorVisible = true) } }
    }
}
