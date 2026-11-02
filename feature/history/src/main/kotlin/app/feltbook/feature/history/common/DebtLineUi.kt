package app.feltbook.feature.history.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.feltbook.core.domain.engine.Debts
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.feature.history.R

/** Открытый долг, который пропадёт вместе с удаляемыми играми. */
@Immutable
data class DebtLineUi(val name: String, val amount: Long)

/** Долги удаляемых игр (`Debts.open`, без объединения имён — в диалоге имена как за столом). */
fun debtsLostWith(games: List<GameSnapshot>): List<DebtLineUi> =
    Debts.open(games, emptyList()).map { DebtLineUi(it.playerName, it.amount) }

/** Больше стольких долгов — одной суммой. */
private const val MAX_LISTED_DEBTS = 3

/**
 * Последствие удаления для диалога: «Пропадут и открытые долги: Влад 1 053 ₽, Борис 600 ₽» или, если долгов
 * много, «Пропадут и открытые долги 5 игроков на 4 200 ₽». `null` — долгов нет.
 */
@Composable
fun debtsConsequence(lines: List<DebtLineUi>): String? = when {
    lines.isEmpty() -> null

    lines.size <= MAX_LISTED_DEBTS -> stringResource(
        R.string.history_delete_debts_list,
        lines.joinToString(separator = ", ") { "${it.name} ${MoneyFormat.rub(it.amount)}" },
    )

    else -> {
        val people = lines.map { it.name }.distinct().size
        stringResource(
            R.string.history_delete_debts_total,
            pluralStringResource(UiR.plurals.players_count, people, people),
            MoneyFormat.rub(lines.sumOf { it.amount }),
        )
    }
}
