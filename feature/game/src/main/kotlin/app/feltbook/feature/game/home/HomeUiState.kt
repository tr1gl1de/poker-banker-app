package app.feltbook.feature.game.home

import androidx.compose.runtime.Immutable
import kotlin.time.Duration
import kotlin.time.Instant

/** Главная (m3-ui-spec §5.1). */
@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val active: ActiveGameUi? = null,
    val openDebts: OpenDebtsUi? = null,
    val pastGames: List<PastGameUi> = emptyList(),
    /** Диалог «Уже идёт игра» (FAB «Новая игра» при активной игре). */
    val alreadyRunningDialog: Boolean = false,
)

@Immutable
data class ActiveGameUi(
    val gameId: String,
    val title: String,
    val settling: Boolean,
    val elapsed: Duration,
    val playersAtTable: Int,
    val bankerName: String,
    val chipsInPlay: Long,
    val playersOwe: Long,
    val rakeDue: Long,
)

@Immutable
data class OpenDebtsUi(val total: Long, val people: Int, val games: Int)

@Immutable
data class PastGameUi(
    val gameId: String,
    val title: String,
    val startedAt: Instant,
    val duration: Duration?,
    val playerCount: Int,
    val rebuyCount: Int,
    val bankerIncome: Long,
    val rake: Long,
    val hasOpenDebt: Boolean,
    val handedOver: Boolean,
)

sealed interface HomeAction {
    data object NewGame : HomeAction

    data object BackToTable : HomeAction

    data class OpenPastGame(val gameId: String) : HomeAction

    data object AllHistory : HomeAction

    data object OpenDebts : HomeAction

    data object DismissDialog : HomeAction
}
