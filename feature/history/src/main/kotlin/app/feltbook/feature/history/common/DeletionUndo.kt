package app.feltbook.feature.history.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import app.feltbook.core.data.deletion.GameDeletionScheduler
import app.feltbook.core.data.deletion.PendingDeletion
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.UNDO_SNACKBAR_MILLIS
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.feature.history.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Отложенное удаление для snackbar: токен и число игр («Удалено 2 игры»). */
@Immutable
data class PendingDeletionUi(val token: Long, val count: Int)

fun PendingDeletion.toUi(): PendingDeletionUi = PendingDeletionUi(token, ids.size)

/** Что сделал пользователь со snackbar удаления. */
sealed interface DeletionAction {
    val token: Long

    /** «Отменить». */
    data class Undo(override val token: Long) : DeletionAction

    /** Snackbar показан и живёт [millis] (5 с или дольше по настройке TalkBack) — столько же держится отмена. */
    data class Window(override val token: Long, val millis: Long) : DeletionAction

    /** Snackbar закрылся без «Отменить» — удалить сразу. */
    data class Dismissed(override val token: Long) : DeletionAction
}

/** Передать действие со snackbar планировщику (общая часть ViewModel истории и долгов). */
fun GameDeletionScheduler.handle(action: DeletionAction, scope: CoroutineScope) {
    scope.launch {
        when (action) {
            is DeletionAction.Undo -> undo(action.token)
            is DeletionAction.Window -> extend(action.token, action.millis)
            is DeletionAction.Dismissed -> commitNow(action.token)
        }
    }
}

/** Запас к окну отмены: удаление не должно обогнать закрытие snackbar. */
private const val UNDO_WINDOW_MARGIN_MILLIS = 500L

/**
 * Snackbar отложенного удаления «Удалено N игр» / «Отменить»: показывается по токену — и после удаления в истории,
 * и после «Удалить игру…» в карточке, открытой из долгов.
 */
@Composable
fun DeletionUndoSnackbar(
    pending: PendingDeletionUi?,
    hostState: SnackbarHostState,
    onAction: (DeletionAction) -> Unit,
) {
    val resources = LocalResources.current
    val accessibility = LocalAccessibilityManager.current
    val undoLabel = stringResource(UiR.string.common_undo)
    val action by rememberUpdatedState(onAction)
    LaunchedEffect(pending?.token) {
        val current = pending ?: return@LaunchedEffect
        val window = accessibility?.calculateRecommendedTimeoutMillis(
            originalTimeoutMillis = UNDO_SNACKBAR_MILLIS,
            containsIcons = false,
            containsText = true,
            containsControls = true,
        ) ?: UNDO_SNACKBAR_MILLIS
        action(DeletionAction.Window(current.token, window + UNDO_WINDOW_MARGIN_MILLIS))
        val message = resources.getQuantityString(R.plurals.history_deleted_snackbar, current.count, current.count)
        when (hostState.showUndoSnackbar(message, undoLabel)) {
            SnackbarResult.ActionPerformed -> action(DeletionAction.Undo(current.token))
            SnackbarResult.Dismissed -> action(DeletionAction.Dismissed(current.token))
        }
    }
}
