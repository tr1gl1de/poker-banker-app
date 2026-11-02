package app.feltbook.feature.handover

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.transfer.file.FeltbookShare
import app.feltbook.feature.handover.receive.ReceiveAction
import app.feltbook.feature.handover.receive.ReceiveEvent
import app.feltbook.feature.handover.receive.ReceiveHandoverScreen
import app.feltbook.feature.handover.receive.ReceiveHandoverViewModel
import app.feltbook.feature.handover.receive.ReceivePhase
import app.feltbook.feature.handover.send.SendAction
import app.feltbook.feature.handover.send.SendEvent
import app.feltbook.feature.handover.send.SendHandoverScreen
import app.feltbook.feature.handover.send.SendHandoverViewModel
import app.feltbook.feature.handover.send.SendPhase

/**
 * Экран «Передача комнаты»: обе вкладки, запрос разрешений (только здесь и только по «Разрешить», ТЗ §6),
 * Sharesheet для файла. Переключение вкладки останавливает поиск/рекламу другой.
 */
@Suppress("CyclomaticComplexMethod") // Разбор событий двух вкладок в одном месте.
@Composable
fun HandoverRouteScreen(
    initialMode: HandoverMode,
    onBack: () -> Unit,
    onHandedOver: () -> Unit,
    onGameAccepted: (gameId: String) -> Unit,
    onTemplateAccepted: () -> Unit,
    sendViewModel: SendHandoverViewModel = hiltViewModel(),
    receiveViewModel: ReceiveHandoverViewModel = hiltViewModel(),
) {
    val sendState by sendViewModel.uiState.collectAsStateWithLifecycle()
    val receiveState by receiveViewModel.uiState.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    val context = LocalContext.current
    val chooserTitle by rememberUpdatedState(stringResource(R.string.handover_share_chooser))
    val currentMode by rememberUpdatedState(mode)
    val handedOver by rememberUpdatedState(onHandedOver)
    val gameAccepted by rememberUpdatedState(onGameAccepted)
    val templateAccepted by rememberUpdatedState(onTemplateAccepted)
    val back by rememberUpdatedState(onBack)

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.isNotEmpty() && result.values.all { it }
        when (currentMode) {
            HandoverMode.GIVE -> sendViewModel.onAction(SendAction.PermissionsResult(granted))
            HandoverMode.RECEIVE -> receiveViewModel.onAction(ReceiveAction.PermissionsResult(granted))
        }
    }

    LaunchedEffect(mode) {
        // Сначала остановить другую вкладку — радио Nearby у приложения одно.
        when (mode) {
            HandoverMode.GIVE -> {
                receiveViewModel.onAction(ReceiveAction.Deactivate)
                sendViewModel.onAction(SendAction.Activate)
            }

            HandoverMode.RECEIVE -> {
                sendViewModel.onAction(SendAction.Deactivate)
                receiveViewModel.onAction(ReceiveAction.Activate)
            }
        }
    }
    LaunchedEffect(sendViewModel) {
        sendViewModel.events.collect { event ->
            when (event) {
                is SendEvent.RequestPermissions -> permissions.launch(event.permissions.toTypedArray())

                is SendEvent.Share -> try {
                    context.startActivity(FeltbookShare.intent(context, event.file, chooserTitle))
                } catch (_: ActivityNotFoundException) {
                    // Поделиться некуда — диалог «Файл отправлен?» останется, ответ «Ещё нет».
                }

                SendEvent.Finished -> handedOver()
            }
        }
    }
    LaunchedEffect(receiveViewModel) {
        receiveViewModel.events.collect { event ->
            when (event) {
                is ReceiveEvent.RequestPermissions -> permissions.launch(event.permissions.toTypedArray())
                is ReceiveEvent.GameAccepted -> gameAccepted(event.gameId)
                ReceiveEvent.TemplateAccepted -> templateAccepted()
                ReceiveEvent.Closed -> back()
            }
        }
    }

    val locked = when (mode) {
        HandoverMode.GIVE -> sendState.busy || sendState.phase.let {
            it is SendPhase.Sending || it is SendPhase.AwaitingAccept || it is SendPhase.Done
        }

        HandoverMode.RECEIVE -> receiveState.busy || receiveState.phase.let {
            it is ReceivePhase.Receiving || it == ReceivePhase.Review || it == ReceivePhase.Done
        }
    }
    HandoverFrame(mode = mode, onModeChange = { mode = it }, onBack = onBack, modeLocked = locked) {
        when (mode) {
            HandoverMode.GIVE -> SendHandoverScreen(state = sendState, onAction = sendViewModel::onAction)
            HandoverMode.RECEIVE -> ReceiveHandoverScreen(state = receiveState, onAction = receiveViewModel::onAction)
        }
    }
}
