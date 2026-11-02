package app.feltbook.feature.handover.receive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.BannerVariant
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.BreakdownList
import app.feltbook.core.ui.component.BreakdownRow
import app.feltbook.core.ui.component.FeltFilterChip
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.HoldButtonSize
import app.feltbook.core.ui.component.HoldToConfirmButton
import app.feltbook.core.ui.component.InfoBanner
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.a11yRubles
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.handover.CardMessage
import app.feltbook.feature.handover.CodeDigits
import app.feltbook.feature.handover.DeviceCard
import app.feltbook.feature.handover.DeviceRow
import app.feltbook.feature.handover.ProgressRow
import app.feltbook.feature.handover.R
import app.feltbook.feature.handover.SheetSection
import app.feltbook.feature.handover.TemplateSection
import app.feltbook.feature.handover.WaitingRow

/** Вкладка «Принять игру» и открытый файл `.feltbook` (m3-ui-spec §5.10, «Принять игру» [без макета]). */
@Composable
fun ColumnScope.ReceiveHandoverScreen(state: ReceiveUiState, onAction: (ReceiveAction) -> Unit) {
    Column(modifier = Modifier.weight(weight = 1f).verticalScroll(rememberScrollState())) {
        StatusCard(state = state, onAction = onAction)
        val summary = state.summary
        if (state.phase == ReceivePhase.Review && summary != null) {
            Summary(state = state, summary = summary, onAction = onAction)
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
    Bottom(state = state, onAction = onAction)
}

@Composable
private fun StatusCard(state: ReceiveUiState, onAction: (ReceiveAction) -> Unit) {
    val colors = FeltbookTheme.colors
    when (val phase = state.phase) {
        ReceivePhase.Loading -> DeviceCard { WaitingRow(text = stringResource(R.string.handover_waiting)) }

        is ReceivePhase.NeedsName -> DeviceCard {
            CardMessage(
                title = stringResource(R.string.handover_your_name_title),
                body = stringResource(R.string.handover_your_name_body),
            )
            FeltTextField(
                value = phase.name,
                onValueChange = { onAction(ReceiveAction.NameChanged(it)) },
                label = stringResource(R.string.handover_your_name_label),
                error = if (phase.error) stringResource(R.string.handover_error_name) else null,
                imeAction = ImeAction.Done,
                onImeAction = { onAction(ReceiveAction.NameConfirmed) },
            )
        }

        ReceivePhase.NeedsPermission -> DeviceCard {
            CardMessage(
                title = stringResource(R.string.handover_permission_title),
                body = stringResource(R.string.handover_permission_body),
            )
            BrassButton(
                text = stringResource(R.string.handover_permission_allow),
                onClick = { onAction(ReceiveAction.AllowPermissions) },
                style = BrassButtonStyle.Flat48,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        is ReceivePhase.Waiting -> DeviceCard {
            WaitingRow(text = stringResource(R.string.handover_waiting))
        }

        is ReceivePhase.Code -> DeviceCard {
            DeviceRow(peerName = phase.peerName)
            CodeDigits(digits = phase.digits)
            if (phase.waitingPeer) WaitingRow(text = stringResource(R.string.handover_waiting_peer_code))
        }

        is ReceivePhase.Receiving -> DeviceCard {
            DeviceRow(peerName = phase.peerName)
            ProgressRow(text = stringResource(R.string.handover_receiving), fraction = phase.fraction)
        }

        ReceivePhase.Review -> Unit

        ReceivePhase.Done -> DeviceCard {
            Icon(imageVector = FeltbookIcons.Check, contentDescription = null, tint = colors.pay)
        }

        is ReceivePhase.Failed -> InfoBanner(
            text = failureText(phase.reason),
            variant = BannerVariant.Danger,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
    }
}

@Composable
private fun failureText(reason: ReceiveFailure): String = stringResource(
    when (reason) {
        ReceiveFailure.NO_GMS -> R.string.handover_no_gms_receive
        ReceiveFailure.PERMISSION_DENIED -> R.string.handover_permission_denied_receive
        ReceiveFailure.LOCATION_OFF -> R.string.handover_location_off
        ReceiveFailure.CONNECTION -> R.string.handover_receive_failed
        ReceiveFailure.CORRUPTED -> R.string.handover_error_corrupted
        ReceiveFailure.NOT_FELTBOOK -> R.string.handover_error_not_feltbook
        ReceiveFailure.NEWER_VERSION -> R.string.handover_error_newer
        ReceiveFailure.BACKUP -> R.string.handover_error_backup
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Summary(state: ReceiveUiState, summary: IncomingSummary, onAction: (ReceiveAction) -> Unit) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    if (summary.fileName != null) {
        Text(
            text = stringResource(R.string.handover_from_file, summary.fileName),
            style = type.ledger,
            color = colors.ledger,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
        )
    }
    if (summary.isTemplate) {
        val rules = summary.rules ?: return
        Text(
            text = stringResource(R.string.handover_incoming_template, summary.fromBanker),
            style = type.title,
            color = colors.ink,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).semantics { heading() },
        )
        TemplateSection(rules = rules, names = summary.templateNames)
    } else {
        SheetSection {
            Text(
                text = stringResource(R.string.handover_incoming, summary.title, summary.fromBanker),
                style = type.title,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            BreakdownList(
                rows = listOf(
                    BreakdownRow(
                        stringResource(R.string.handover_incoming_players),
                        MoneyFormat.number(summary.playersAtTable.toLong()),
                    ),
                    BreakdownRow(
                        stringResource(R.string.handover_incoming_chips),
                        MoneyFormat.number(summary.chipsInPlay),
                    ),
                    BreakdownRow(
                        stringResource(R.string.handover_incoming_cash),
                        MoneyFormat.rub(summary.cash),
                        contentDescription = a11yRubles(summary.cash),
                    ),
                ),
                card = false,
                ledgerLabels = true,
            )
        }
        if (summary.players.isNotEmpty()) {
            Text(
                text = stringResource(R.string.handover_play_as),
                style = type.caption,
                color = colors.inkSoft,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
            )
            FlowRow(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp).selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
                verticalArrangement = Arrangement.spacedBy(space = 8.dp),
            ) {
                FeltFilterChip(
                    text = stringResource(R.string.handover_not_playing),
                    selected = state.playAs == null,
                    onClick = { onAction(ReceiveAction.PlayAs(null)) },
                )
                summary.players.forEach { player ->
                    FeltFilterChip(
                        text = player.name,
                        selected = state.playAs == player.id,
                        onClick = { onAction(ReceiveAction.PlayAs(player.id)) },
                    )
                }
            }
        }
    }
    val error = state.error
    if (error != null) {
        InfoBanner(
            text = stringResource(
                when (error) {
                    AcceptError.ANOTHER_GAME_ACTIVE -> R.string.handover_error_active
                    AcceptError.CONFLICT -> R.string.handover_error_conflict
                    AcceptError.INVALID -> R.string.handover_error_invalid
                    AcceptError.STORAGE -> UiR.string.error_generic
                },
            ),
            variant = BannerVariant.Danger,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
    }
}

@Composable
private fun Bottom(state: ReceiveUiState, onAction: (ReceiveAction) -> Unit) {
    val phase = state.phase
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(space = 8.dp),
    ) {
        when (phase) {
            is ReceivePhase.NeedsName -> BrassButton(
                text = stringResource(R.string.handover_your_name_start),
                onClick = { onAction(ReceiveAction.NameConfirmed) },
                style = BrassButtonStyle.Flat56,
                enabled = phase.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )

            is ReceivePhase.Code -> {
                BrassButton(
                    text = stringResource(R.string.handover_code_confirm),
                    onClick = { onAction(ReceiveAction.ConfirmCode) },
                    style = BrassButtonStyle.Hero64,
                    enabled = !phase.waitingPeer,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlineButton(
                    text = stringResource(R.string.handover_code_mismatch),
                    onClick = { onAction(ReceiveAction.RejectCode) },
                    height = FeltbookDimens.button48,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            ReceivePhase.Review -> {
                val template = state.summary?.isTemplate == true
                HoldToConfirmButton(
                    title = stringResource(
                        if (template) R.string.handover_accept_template else R.string.handover_accept,
                    ),
                    hint = stringResource(R.string.handover_accept_hint),
                    onConfirm = { onAction(ReceiveAction.Accept) },
                    size = HoldButtonSize.H64,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlineButton(
                    text = stringResource(R.string.handover_decline),
                    onClick = { onAction(ReceiveAction.Decline) },
                    height = FeltbookDimens.button48,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is ReceivePhase.Failed -> if (!state.fromFile && phase.reason != ReceiveFailure.NO_GMS) {
                BrassButton(
                    text = stringResource(
                        if (phase.reason == ReceiveFailure.PERMISSION_DENIED) {
                            R.string.handover_permission_allow
                        } else {
                            R.string.handover_retry
                        },
                    ),
                    onClick = {
                        onAction(
                            if (phase.reason == ReceiveFailure.PERMISSION_DENIED) {
                                ReceiveAction.AllowPermissions
                            } else {
                                ReceiveAction.Retry
                            },
                        )
                    },
                    style = BrassButtonStyle.Flat56,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> Unit
        }
    }
}
