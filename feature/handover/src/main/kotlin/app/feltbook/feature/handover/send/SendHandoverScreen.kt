package app.feltbook.feature.handover.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.HoldButtonSize
import app.feltbook.core.ui.component.HoldToConfirmButton
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.handover.ActSection
import app.feltbook.feature.handover.CardMessage
import app.feltbook.feature.handover.CodeDigits
import app.feltbook.feature.handover.DeviceCard
import app.feltbook.feature.handover.DeviceRow
import app.feltbook.feature.handover.PayloadLine
import app.feltbook.feature.handover.ProgressRow
import app.feltbook.feature.handover.R
import app.feltbook.feature.handover.TemplateSection
import app.feltbook.feature.handover.WaitingRow

/** Вкладка «Отдать игру» (макет Handover + состояния без макета, m3-ui-spec §5.10). */
@Composable
fun ColumnScope.SendHandoverScreen(state: SendUiState, onAction: (SendAction) -> Unit) {
    Column(modifier = Modifier.weight(weight = 1f).verticalScroll(rememberScrollState())) {
        StatusCard(state = state, onAction = onAction)
        if (state.showsPackage) PackageBlock(state = state, onAction = onAction)
        Spacer(modifier = Modifier.height(12.dp))
    }
    Bottom(state = state, onAction = onAction)
    Dialogs(state = state, onAction = onAction)
}

/** Код, акт и hold — только после соединения; при сбое акт остаётся (выбор дохода для файла). */
private val SendUiState.showsPackage: Boolean
    get() = when (phase) {
        is SendPhase.Code, is SendPhase.Sending, is SendPhase.AwaitingAccept -> true
        is SendPhase.Failed -> phase.reason != SendFailure.STALE
        SendPhase.NeedsPermission -> true
        else -> false
    }

@Suppress("CyclomaticComplexMethod") // По карточке на состояние экрана.
@Composable
private fun StatusCard(state: SendUiState, onAction: (SendAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    when (val phase = state.phase) {
        SendPhase.Loading, SendPhase.Idle ->
            DeviceCard { WaitingRow(text = stringResource(R.string.handover_connecting)) }

        SendPhase.NoGame -> DeviceCard { CardMessage(title = stringResource(R.string.handover_no_game), body = null) }

        SendPhase.AlreadyHandedOver ->
            DeviceCard { CardMessage(title = stringResource(R.string.handover_already_handed), body = null) }

        SendPhase.NeedsPermission -> DeviceCard {
            CardMessage(
                title = stringResource(R.string.handover_permission_title),
                body = stringResource(R.string.handover_permission_body),
            )
            BrassButton(
                text = stringResource(R.string.handover_permission_allow),
                onClick = { onAction(SendAction.AllowPermissions) },
                style = BrassButtonStyle.Flat48,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SendPhase.Searching -> DeviceCard {
            WaitingRow(
                text = stringResource(R.string.handover_searching),
                hint = stringResource(R.string.handover_searching_hint),
            )
        }

        is SendPhase.Choose -> DeviceCard {
            Text(text = stringResource(R.string.handover_choose_device), style = type.caption, color = colors.inkSoft)
            phase.endpoints.forEach { endpoint ->
                DeviceRow(peerName = endpoint.name, onClick = { onAction(SendAction.SelectDevice(endpoint.id)) })
            }
        }

        is SendPhase.Connecting -> DeviceCard {
            DeviceRow(peerName = phase.peerName)
            WaitingRow(text = stringResource(R.string.handover_connecting))
        }

        is SendPhase.Code -> DeviceCard {
            DeviceRow(peerName = phase.peerName)
            CodeDigits(digits = phase.digits)
            if (phase.waitingPeer) WaitingRow(text = stringResource(R.string.handover_waiting_peer_code))
        }

        is SendPhase.Sending -> DeviceCard {
            DeviceRow(peerName = phase.peerName)
            ProgressRow(text = stringResource(R.string.handover_sending), fraction = phase.fraction)
        }

        is SendPhase.AwaitingAccept -> DeviceCard {
            DeviceRow(peerName = phase.peerName)
            WaitingRow(text = stringResource(R.string.handover_awaiting_accept))
        }

        is SendPhase.Done -> DeviceCard {
            Icon(imageVector = FeltbookIcons.Check, contentDescription = null, tint = colors.pay)
            CardMessage(
                title = stringResource(
                    if (state.kind == SendKind.TEMPLATE) R.string.handover_template_done else R.string.handover_done,
                ),
                body = if (state.kind == SendKind.HANDOVER) {
                    stringResource(R.string.handover_done_body, phase.peerName)
                } else {
                    null
                },
            )
            phase.cash?.let { cash ->
                Text(
                    text = stringResource(R.string.handover_done_cash, MoneyFormat.rub(cash)),
                    style = type.title,
                    color = colors.brassBright,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is SendPhase.Failed -> Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(space = 8.dp),
        ) {
            InfoBanner(text = failureText(phase.reason), variant = BannerVariant.Danger)
            if (phase.ackLost) {
                InfoBanner(text = stringResource(R.string.handover_ack_lost_hint), variant = BannerVariant.Warn)
            }
        }
    }
}

@Composable
private fun failureText(reason: SendFailure): String = stringResource(
    when (reason) {
        SendFailure.NO_GMS -> R.string.handover_no_gms
        SendFailure.PERMISSION_DENIED -> R.string.handover_permission_denied
        SendFailure.PERMISSION_BLOCKED -> R.string.handover_permission_blocked
        SendFailure.APPROXIMATE_LOCATION -> R.string.handover_approximate_location
        SendFailure.LOCATION_OFF -> R.string.handover_location_off
        SendFailure.CONNECTION -> R.string.handover_failed
        SendFailure.DECLINED -> R.string.handover_declined
        SendFailure.BAD_NAME -> R.string.handover_error_name
        SendFailure.STALE -> R.string.handover_error_stale
    },
)

@Composable
private fun PackageBlock(state: SendUiState, onAction: (SendAction) -> Unit) {
    val phase = state.phase
    val switchEnabled = (phase is SendPhase.Code && !phase.waitingPeer) ||
        phase is SendPhase.Failed ||
        phase == SendPhase.NeedsPermission
    when (state.kind) {
        SendKind.HANDOVER -> {
            val act = state.act ?: return
            ActSection(
                act = act,
                keepIncome = state.keepIncomeShown,
                keepIncomeEnabled = state.keepIncomeEnabled,
                cashToHandOver = state.cashToHandOver,
                onKeepIncome = { onAction(SendAction.KeepIncome(it)) },
                switchEnabled = switchEnabled && !state.busy && !state.incomeLocked,
            )
            PayloadLine(players = state.players, chips = act.chipsInPlay, entries = state.payloadEntries)
        }

        SendKind.TEMPLATE -> {
            val rules = state.rules ?: return
            TemplateSection(
                rules = rules,
                names = state.templateNames,
                body = stringResource(R.string.handover_template_body),
            )
        }
    }
}

@Composable
private fun Bottom(state: SendUiState, onAction: (SendAction) -> Unit) {
    val phase = state.phase
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        when {
            phase is SendPhase.Code -> {
                HoldToConfirmButton(
                    title = stringResource(R.string.handover_hold),
                    hint = stringResource(
                        if (state.kind == SendKind.TEMPLATE) {
                            R.string.handover_template_hold_hint
                        } else {
                            R.string.handover_hold_hint
                        },
                    ),
                    onConfirm = { onAction(SendAction.ConfirmAndSend) },
                    size = HoldButtonSize.H64,
                    enabled = !phase.waitingPeer && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlineButton(
                    text = stringResource(R.string.handover_code_mismatch),
                    onClick = { onAction(SendAction.RejectCode) },
                    height = FeltbookDimens.button48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            phase is SendPhase.Done -> BrassButton(
                text = stringResource(R.string.handover_done_home),
                onClick = { onAction(SendAction.Finish) },
                style = BrassButtonStyle.Flat56,
                modifier = Modifier.fillMaxWidth(),
            )

            phase is SendPhase.Failed -> FailedButtons(state = state, phase = phase, onAction = onAction)

            phase == SendPhase.NeedsPermission -> SendFileButton(state = state, onAction = onAction, primary = false)

            else -> Unit
        }
    }
}

@Composable
private fun FailedButtons(state: SendUiState, phase: SendPhase.Failed, onAction: (SendAction) -> Unit) {
    if (phase.ackLost) {
        OutlineButton(
            text = stringResource(R.string.handover_ack_lost_open),
            onClick = { onAction(SendAction.OpenAckLost) },
            height = FeltbookDimens.button48,
            emphasized = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    when (phase.reason) {
        SendFailure.NO_GMS -> SendFileButton(state = state, onAction = onAction, primary = true)

        SendFailure.STALE -> BrassButton(
            text = stringResource(R.string.handover_retry),
            onClick = { onAction(SendAction.Retry) },
            style = BrassButtonStyle.Flat56,
            modifier = Modifier.fillMaxWidth(),
        )

        else -> {
            val (label, action) = when (phase.reason) {
                SendFailure.PERMISSION_DENIED, SendFailure.APPROXIMATE_LOCATION ->
                    R.string.handover_permission_allow to SendAction.AllowPermissions

                SendFailure.PERMISSION_BLOCKED -> R.string.handover_open_settings to SendAction.OpenSettings

                else -> R.string.handover_retry to SendAction.Retry
            }
            BrassButton(
                text = stringResource(label),
                onClick = { onAction(action) },
                style = BrassButtonStyle.Flat56,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            SendFileButton(state = state, onAction = onAction, primary = false)
        }
    }
}

@Composable
private fun SendFileButton(state: SendUiState, onAction: (SendAction) -> Unit, primary: Boolean) {
    if (!state.canSendFile) return
    if (primary) {
        BrassButton(
            text = stringResource(R.string.handover_send_file),
            onClick = { onAction(SendAction.SendFile) },
            style = BrassButtonStyle.Flat56,
            enabled = !state.busy,
            trailingIcon = FeltbookIcons.Share,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlineButton(
            text = stringResource(R.string.handover_send_file),
            onClick = { onAction(SendAction.SendFile) },
            height = FeltbookDimens.button48,
            icon = FeltbookIcons.Share,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Dialogs(state: SendUiState, onAction: (SendAction) -> Unit) {
    val dismiss = { onAction(SendAction.DismissDialog) }
    when (val dialog = state.dialog) {
        null -> Unit

        is SendDialog.FileRecipient -> FeltbookDialog(
            title = stringResource(R.string.handover_file_to_title),
            body = stringResource(R.string.handover_file_to_body),
            safeLabel = stringResource(UiR.string.common_cancel),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.handover_send_file),
            onConfirm = { onAction(SendAction.FileRecipientConfirmed) },
            destructive = false,
            confirmEnabled = dialog.name.isNotBlank() && !state.busy,
        ) {
            FeltTextField(
                value = dialog.name,
                onValueChange = { onAction(SendAction.FileRecipientChanged(it)) },
                label = stringResource(R.string.handover_file_to_label),
                error = if (dialog.error) stringResource(R.string.handover_error_name) else null,
                imeAction = ImeAction.Done,
                onImeAction = { onAction(SendAction.FileRecipientConfirmed) },
            )
        }

        SendDialog.FileSent -> FeltbookDialog(
            title = stringResource(R.string.handover_file_sent_title),
            body = stringResource(R.string.handover_file_sent_body),
            safeLabel = stringResource(R.string.handover_file_not_yet),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.handover_file_sent_confirm),
            onConfirm = { onAction(SendAction.FileSentConfirmed) },
            destructive = false,
            confirmEnabled = !state.busy,
        )

        SendDialog.Leave -> FeltbookDialog(
            title = stringResource(R.string.handover_leave_title),
            body = stringResource(R.string.handover_leave_body),
            safeLabel = stringResource(R.string.handover_leave_stay),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.handover_leave_confirm),
            onConfirm = { onAction(SendAction.ConfirmLeave) },
            destructive = true,
        )

        SendDialog.AckLost -> FeltbookDialog(
            title = stringResource(R.string.handover_ack_lost_title),
            body = stringResource(R.string.handover_ack_lost_body),
            safeLabel = stringResource(R.string.handover_ack_lost_no),
            onSafe = dismiss,
            confirmLabel = stringResource(R.string.handover_ack_lost_yes),
            onConfirm = { onAction(SendAction.AckLostConfirmed) },
            destructive = false,
            confirmEnabled = !state.busy,
        )
    }
}
