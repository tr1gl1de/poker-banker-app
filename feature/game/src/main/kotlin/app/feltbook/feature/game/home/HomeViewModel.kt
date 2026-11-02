package app.feltbook.feature.game.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.GameStates
import app.feltbook.core.domain.engine.Stats
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.feature.game.common.key
import app.feltbook.feature.game.common.minuteTicker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Главная: активная игра, открытые долги, последние 5 прошлых игр. Всё считает движок из `observeAllGames`. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    gameRepository: GameRepository,
    playerNameRepository: PlayerNameRepository,
    clock: Clock,
) : ViewModel() {
    private val dialog = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> =
        combine(
            gameRepository.observeAllGames(),
            playerNameRepository.observeAll(),
            minuteTicker(clock),
            dialog,
        ) { games, directory, now, dialogShown ->
            buildHomeUiState(games, directory, now).copy(alreadyRunningDialog = dialogShown)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), HomeUiState())

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.NewGame -> if (uiState.value.active != null) dialog.update { true }
            HomeAction.DismissDialog -> dialog.update { false }
            else -> Unit
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** Сколько прошлых игр на Главной (m3-ui-spec §5.1). */
const val HOME_PAST_GAMES_LIMIT: Int = 5

fun buildHomeUiState(games: List<GameSnapshot>, directory: List<PlayerName>, now: Instant): HomeUiState {
    val active = games.firstOrNull { it.game.status.isActive }?.let { snapshot ->
        val table = GameStates.compute(snapshot).table
        ActiveGameUi(
            gameId = snapshot.game.id.key(),
            title = snapshot.game.title,
            settling = snapshot.game.status == GameStatus.SETTLING,
            elapsed = now - snapshot.game.createdAt,
            playersAtTable = table.playersAtTable,
            bankerName = snapshot.game.currentBankerName,
            chipsInPlay = table.chipsInPlay,
            playersOwe = table.playersOwe,
            rakeDue = table.rakeDue,
        )
    }
    val debts = Debts.open(games, directory)
    val openDebts = if (debts.isEmpty()) {
        null
    } else {
        OpenDebtsUi(
            total = debts.sumOf { it.amount },
            people = debts.map { it.canonicalName }.distinct().size,
            games = debts.map { it.gameId }.distinct().size,
        )
    }
    val past = games
        .filter { it.game.status == GameStatus.CLOSED || it.game.status == GameStatus.HANDED_OVER }
        .sortedByDescending { it.game.createdAt }
        .take(HOME_PAST_GAMES_LIMIT)
        .map { snapshot ->
            val item = Stats.historyItem(snapshot)
            PastGameUi(
                gameId = item.gameId.key(),
                title = item.title,
                startedAt = item.startedAt,
                duration = item.endedAt?.let { it - item.startedAt },
                playerCount = item.playerCount,
                rebuyCount = item.rebuyCount,
                bankerIncome = item.bankerIncome,
                rake = item.rake,
                hasOpenDebt = item.hasOpenDebt,
                handedOver = item.status == GameStatus.HANDED_OVER,
            )
        }
    return HomeUiState(loading = false, active = active, openDebts = openDebts, pastGames = past)
}
