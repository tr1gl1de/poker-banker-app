package app.feltbook.feature.handover

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
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
import androidx.core.app.ActivityCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.transfer.file.FeltbookShare
import app.feltbook.feature.handover.receive.ReceiveAction
import app.feltbook.feature.handover.receive.ReceiveEvent
import app.feltbook.feature.handover.receive.ReceiveHandoverScreen
import app.feltbook.feature.handover.receive.ReceiveHandoverViewModel
import app.feltbook.feature.handover.send.SendAction
import app.feltbook.feature.handover.send.SendEvent
import app.feltbook.feature.handover.send.SendHandoverScreen
import app.feltbook.feature.handover.send.SendHandoverViewModel

/**
 * Экран «Передача комнаты»: обе вкладки, запрос разрешений (только здесь и только по «Разрешить», ТЗ §6),
 * Sharesheet для файла. Переключение вкладки останавливает поиск/рекламу другой; идущую передачу — нет.
 * «Назад» посреди неподтверждённой передачи спрашивает «Прервать передачу?».
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // Разбор событий двух вкладок в одном месте.
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
    val activity = LocalActivity.current
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
        val blocked = !granted && activity != null && permanentlyDenied(activity, result)
        when (currentMode) {
            HandoverMode.GIVE -> sendViewModel.onAction(SendAction.PermissionsResult(granted, blocked))
            HandoverMode.RECEIVE -> receiveViewModel.onAction(ReceiveAction.PermissionsResult(granted, blocked))
        }
    }

    LaunchedEffect(mode) {
        // Сначала остановить другую вкладку — радио Nearby у приложения одно. Повторный Activate (поворот экрана)
        // идущую передачу не перезапускает.
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
    // Вернулись из настроек приложения — разрешения могли появиться.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        when (currentMode) {
            HandoverMode.GIVE -> sendViewModel.onAction(SendAction.Resumed)
            HandoverMode.RECEIVE -> receiveViewModel.onAction(ReceiveAction.Resumed)
        }
    }
    LaunchedEffect(sendViewModel) {
        sendViewModel.events.collect { event ->
            when (event) {
                is SendEvent.RequestPermissions -> permissions.launch(event.permissions.toTypedArray())

                SendEvent.OpenAppSettings -> openAppSettings(context)

                is SendEvent.Share -> try {
                    context.startActivity(FeltbookShare.intent(context, event.file, chooserTitle))
                } catch (_: ActivityNotFoundException) {
                    // Поделиться некуда — диалог «Файл отправлен?» останется, ответ «Ещё нет».
                }

                SendEvent.Leave -> back()

                SendEvent.Finished -> handedOver()
            }
        }
    }
    LaunchedEffect(receiveViewModel) {
        receiveViewModel.events.collect { event ->
            when (event) {
                is ReceiveEvent.RequestPermissions -> permissions.launch(event.permissions.toTypedArray())
                ReceiveEvent.OpenAppSettings -> openAppSettings(context)
                is ReceiveEvent.GameAccepted -> gameAccepted(event.gameId)
                ReceiveEvent.TemplateAccepted -> templateAccepted()
                ReceiveEvent.Closed -> back()
            }
        }
    }

    val confirmLeave = mode == HandoverMode.GIVE && sendState.leaveNeedsConfirm
    BackHandler(enabled = confirmLeave) { sendViewModel.onAction(SendAction.Back) }
    val onBackPressed = {
        if (mode == HandoverMode.GIVE) sendViewModel.onAction(SendAction.Back) else back()
    }

    val locked = when (mode) {
        HandoverMode.GIVE -> sendState.modeLocked
        HandoverMode.RECEIVE -> receiveState.modeLocked
    }
    HandoverFrame(mode = mode, onModeChange = { mode = it }, onBack = onBackPressed, modeLocked = locked) {
        when (mode) {
            HandoverMode.GIVE -> SendHandoverScreen(state = sendState, onAction = sendViewModel::onAction)
            HandoverMode.RECEIVE -> ReceiveHandoverScreen(state = receiveState, onAction = receiveViewModel::onAction)
        }
    }
}

/** Отказ «больше не спрашивать»: по отклонённым разрешениям система пояснение не покажет — только настройки. */
private fun permanentlyDenied(activity: Activity, result: Map<String, Boolean>): Boolean {
    val denied = result.filterValues { !it }.keys
    return denied.isNotEmpty() && denied.none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
}

private fun openAppSettings(context: Context) {
    val details =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    try {
        context.startActivity(details)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}
