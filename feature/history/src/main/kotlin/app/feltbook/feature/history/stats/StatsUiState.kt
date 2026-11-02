package app.feltbook.feature.history.stats

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.engine.PlayerNames
import app.feltbook.core.domain.engine.Stats
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.PlayerName
import app.feltbook.core.ui.component.Suit
import app.feltbook.core.ui.format.TimeFormat
import app.feltbook.core.ui.format.UiText
import app.feltbook.feature.history.common.key
import java.time.ZoneId
import kotlin.time.Instant

/**
 * Период статистики (макет Stats, «За 3 месяца ▾»): скользящее окно от начала дня `сегодня − N месяцев`
 * до сейчас; [ALL] — без границ.
 */
@Suppress("MagicNumber") // Длина периода в месяцах — это и есть значение пункта меню.
enum class StatsPeriod(val months: Long?) {
    MONTH(1),
    THREE_MONTHS(3),
    YEAR(12),
    ALL(null),
}

/** Столбец графика «Доход банкира, последние N игр»: подпись `dd.MM`, значение без ₽. */
@Immutable
data class ChartBarUi(val gameId: String, val label: String, val value: Long)

/** Строка игрока: «11 игр · 1,4 докупа за игру» | «+9 450». */
@Immutable
data class StatsPlayerRowUi(
    val name: String,
    val suit: Suit,
    val gamesPlayed: Int,
    val rebuyCount: Int,
    val totalResult: Long,
)

/**
 * Шторка «Игрок» [без макета]: статистика по имени за все игры (ТЗ §4 п. 4) и открытый долг с датой.
 * @property aliases имена, объединённые прямо с этим («Боря» → «Борис») — у каждого «Разъединить».
 * @property mergeCandidates с кем можно объединить (`PlayerNames.canMerge`); [picking] — список открыт.
 */
@Immutable
data class PlayerSheetUi(
    val name: String,
    val suit: Suit,
    val gamesPlayed: Int,
    val totalResult: Long,
    val averageBuyIn: Long,
    val rebuyCount: Int,
    val openDebt: Long,
    val debtSince: Instant?,
    val lastPlayedAt: Instant?,
    val aliases: List<String>,
    val mergeCandidates: List<String>,
    val picking: Boolean,
)

/** Подтверждение «Объединить «Боря» с «Борис»?». */
@Immutable
data class MergeDialogUi(val from: String, val into: String)

@Immutable
data class StatsUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.THREE_MONTHS,
    val periodMenu: Boolean = false,
    val gameCount: Int = 0,
    val turnover: Long = 0,
    val rake: Long = 0,
    val bankerIncome: Long = 0,
    val chart: List<ChartBarUi> = emptyList(),
    val players: List<StatsPlayerRowUi> = emptyList(),
    val sheet: PlayerSheetUi? = null,
    val mergeDialog: MergeDialogUi? = null,
)

sealed interface StatsAction {
    data object Back : StatsAction

    data class PeriodMenu(val open: Boolean) : StatsAction

    data class SetPeriod(val period: StatsPeriod) : StatsAction

    data class PlayerClick(val name: String) : StatsAction

    data object DismissSheet : StatsAction

    /** «Объединить с…» — список имён. */
    data object StartMerge : StatsAction

    data object CancelMerge : StatsAction

    data class PickMergeTarget(val into: String) : StatsAction

    data object ConfirmMerge : StatsAction

    data object DismissDialog : StatsAction

    data class Unmerge(val alias: String) : StatsAction

    /** «Отменить» в snackbar объединения/разъединения. */
    data class Undo(val token: StatsUndo) : StatsAction

    data object OpenDebts : StatsAction
}

/** Что вернуть по «Отменить». */
sealed interface StatsUndo {
    data class Merge(val from: String) : StatsUndo

    data class Unmerge(val alias: String, val into: String) : StatsUndo
}

sealed interface StatsEvent {
    data object Back : StatsEvent

    data object OpenDebts : StatsEvent

    data class Undoable(val message: UiText, val token: StatsUndo) : StatsEvent

    data class Message(val text: UiText) : StatsEvent
}

/** Последние игры на графике (макет: 6). */
const val CHART_GAMES: Int = 6

/** Ввод экрана, которого нет в базе. */
data class StatsInputs(
    val period: StatsPeriod = StatsPeriod.THREE_MONTHS,
    val periodMenu: Boolean = false,
    val sheetName: String? = null,
    val picking: Boolean = false,
    val mergeDialog: MergeDialogUi? = null,
)

/** Начало периода: начало дня `сегодня − months` в поясе [zone]; `null` — за всё время. */
fun StatsPeriod.from(now: Instant, zone: ZoneId): Instant? = months?.let { months ->
    Instant.fromEpochMilliseconds(
        TimeFormat.localDate(now, zone).minusMonths(months).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}

/** Всё считает движок: `Stats.period` за период, `Stats.player` и `Debts.forName` — за все игры. */
fun buildStatsUiState(
    games: List<GameSnapshot>,
    directory: List<PlayerName>,
    inputs: StatsInputs,
    now: Instant,
    zone: ZoneId,
): StatsUiState {
    val period = Stats.period(games, directory, from = inputs.period.from(now, zone), to = null)
    val chart = period.incomeByGame.takeLast(CHART_GAMES).map { point ->
        ChartBarUi(
            gameId = point.gameId.key(),
            label = TimeFormat.chartDate(TimeFormat.localDate(point.startedAt, zone)),
            value = point.bankerIncome,
        )
    }
    return StatsUiState(
        loading = false,
        period = inputs.period,
        periodMenu = inputs.periodMenu,
        gameCount = period.gameCount,
        turnover = period.turnover,
        rake = period.rake,
        bankerIncome = period.bankerIncome,
        chart = chart,
        players = period.players.map { stats ->
            StatsPlayerRowUi(
                name = stats.canonicalName,
                suit = Suit.forName(stats.canonicalName),
                gamesPlayed = stats.gamesPlayed,
                rebuyCount = stats.rebuyCount,
                totalResult = stats.totalResult,
            )
        },
        sheet = inputs.sheetName?.let { playerSheet(it, games, directory, inputs.picking) },
        mergeDialog = inputs.mergeDialog,
    )
}

private fun playerSheet(
    name: String,
    games: List<GameSnapshot>,
    directory: List<PlayerName>,
    picking: Boolean,
): PlayerSheetUi {
    val canonical = PlayerNames.canonical(name, directory)
    val stats = Stats.player(canonical, games, directory)
    val debts = Debts.forName(canonical, games, directory, excludeGameId = null)
    return PlayerSheetUi(
        name = canonical,
        suit = Suit.forName(canonical),
        gamesPlayed = stats?.gamesPlayed ?: 0,
        totalResult = stats?.totalResult ?: 0L,
        averageBuyIn = stats?.averageBuyInPerGame() ?: 0L,
        rebuyCount = stats?.rebuyCount ?: 0,
        openDebt = debts.sumOf { it.amount },
        debtSince = debts.minOfOrNull { it.since },
        lastPlayedAt = stats?.lastPlayedAt,
        aliases = directory.filter { it.mergedInto == canonical }.map { it.name }.sorted(),
        mergeCandidates = directory
            .filter { it.mergedInto == null && it.name != canonical }
            .filter { PlayerNames.canMerge(canonical, it.name, directory) }
            .map { it.name }
            .sorted(),
        picking = picking,
    )
}
