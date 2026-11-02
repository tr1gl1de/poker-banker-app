package app.feltbook.feature.game.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.resolve
import app.feltbook.feature.game.R

/** Необратимое — диалог с глаголом на кнопке (ТЗ §5.1). */
@Composable
internal fun TableDialogs(state: TableUiState, onAction: (TableAction) -> Unit) {
    val dismiss = { onAction(TableAction.DialogDismiss) }
    val confirm = { onAction(TableAction.DialogConfirm) }
    when (val dialog = state.dialog) {
        is DialogUi.Reverse -> FeltbookDialog(
            title = stringResource(R.string.journal_reverse_title, dialog.title.resolve()),
            body = stringResource(R.string.journal_reverse_body),
            safeLabel = stringResource(R.string.journal_reverse_keep),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.journal_reverse_confirm),
            onConfirm = confirm,
            confirmEnabled = !state.busy,
        ) {
            FeltTextField(
                value = dialog.reason,
                onValueChange = { onAction(TableAction.ReasonChange(it)) },
                label = stringResource(R.string.journal_reverse_reason),
            )
        }

        is DialogUi.Rename -> FeltbookDialog(
            title = stringResource(R.string.rename_title),
            body = stringResource(R.string.rename_body),
            safeLabel = stringResource(UiR.string.common_cancel),
            onSafe = dismiss,
            confirmLabel = stringResource(UiR.string.common_save),
            onConfirm = confirm,
            destructive = false,
            confirmEnabled = !state.busy && dialog.value.isNotBlank(),
        ) {
            FeltTextField(
                value = dialog.value,
                onValueChange = { onAction(TableAction.RenameChange(it)) },
                label = stringResource(R.string.new_player_name),
                large = true,
                error = dialog.error?.resolve(),
                onImeAction = confirm,
            )
        }

        is DialogUi.EarlyExit -> FeltbookDialog(
            title = stringResource(R.string.cash_out_early_title),
            body =
                stringResource(
                    R.string.cash_out_early_body,
                    dialog.name,
                    dialog.minutes.toInt(),
                    dialog.minPlayMinutes,
                ),
            safeLabel = stringResource(R.string.cash_out_early_keep),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.cash_out_early_confirm),
            onConfirm = confirm,
            confirmEnabled = !state.busy,
        )

        is DialogUi.Forgive -> FeltbookDialog(
            title = stringResource(R.string.forgive_title, MoneyFormat.rub(dialog.amount)),
            body = stringResource(R.string.forgive_body, dialog.gameTitle, MoneyFormat.rub(dialog.amount)),
            safeLabel = stringResource(R.string.forgive_keep),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.forgive_confirm, MoneyFormat.rub(dialog.amount)),
            onConfirm = confirm,
            confirmEnabled = !state.busy,
        )

        null -> Unit
    }
}
